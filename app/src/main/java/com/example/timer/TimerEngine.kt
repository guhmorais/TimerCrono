package com.example.timer

import com.example.data.TimerPersistenceDataSource
import com.example.domain.DefaultTimerFactory
import com.example.domain.TimeFormatted
import com.example.domain.TimerCategory
import com.example.domain.TimerCompletionEvent
import com.example.domain.TimerConfig
import com.example.domain.TimerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/**
 * Coordenador central de estado e operações de temporização.
 * Centraliza toda a lógica de contagem para evitar inconsistências entre
 * Tela Principal, Janelas Flutuantes (WindowManager) e Notificações.
 */
class TimerEngine(
    private val repository: TimerPersistenceDataSource,
    val clockProvider: ClockProvider = SystemClockProvider(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val mutex = Mutex()

    private val _timersState = MutableStateFlow<List<TimerConfig>>(DefaultTimerFactory.createDefaultTimers())
    val timersState: StateFlow<List<TimerConfig>> = _timersState.asStateFlow()

    private val _completionEvents = MutableSharedFlow<TimerCompletionEvent>(extraBufferCapacity = 16)
    val completionEvents: SharedFlow<TimerCompletionEvent> = _completionEvents.asSharedFlow()

    init {
        scope.launch {
            initializeFromStorage(forceBootPolicy = false)
            repository.timersFlow.collect { storedTimers ->
                if (storedTimers.isNotEmpty()) {
                    _timersState.value = storedTimers
                }
            }
        }
    }

    suspend fun initializeFromStorage(forceBootPolicy: Boolean = false) = mutex.withLock {
        val loaded = repository.ensureSeeded()
        val nowElapsed = clockProvider.elapsedRealtime()
        val nowWall = clockProvider.currentTimeMillis()

        val reconciled = loaded.map { timer ->
            TimerCalculator.reconcileAfterReboot(
                timer = timer,
                nowElapsedRealtime = nowElapsed,
                nowWallClockMs = nowWall,
                forceBootPolicy = forceBootPolicy
            )
        }

        if (reconciled != loaded) {
            repository.saveAllTimers(reconciled)
        }
        _timersState.value = reconciled
    }

    /**
     * Ativa ou desativa a janela flutuante (ou temporizador de notificação) de um cronômetro.
     * Por padrão não inicia automaticamente a contagem, salvo se [autoStart] for verdadeiro.
     */
    suspend fun setTimerEnabled(timerId: Int, enabled: Boolean, autoStart: Boolean = false) = mutex.withLock {
        val current = findTimer(timerId) ?: return@withLock
        val nowElapsed = clockProvider.elapsedRealtime()
        val nowWall = clockProvider.currentTimeMillis()

        val updated = if (enabled) {
            if (autoStart && current.status != TimerStatus.RUNNING) {
                val baseAccumulated = if (current.status == TimerStatus.COMPLETED) 0L else current.accumulatedMs
                current.copy(
                    isEnabled = true,
                    status = TimerStatus.RUNNING,
                    accumulatedMs = baseAccumulated,
                    lastStartElapsedRealtime = nowElapsed,
                    lastStartWallClockMs = nowWall,
                    lastCompletedCycleCount = 0
                )
            } else {
                current.copy(isEnabled = true)
            }
        } else {
            // Quando desativado, se estiver em execução, pausa preservando o tempo acumulado
            if (current.status == TimerStatus.RUNNING) {
                val totalElapsed = TimerCalculator.calculateTotalElapsedMs(current, nowElapsed)
                current.copy(
                    isEnabled = false,
                    status = TimerStatus.PAUSED,
                    accumulatedMs = totalElapsed,
                    lastStartElapsedRealtime = 0L,
                    lastStartWallClockMs = 0L
                )
            } else {
                current.copy(isEnabled = false)
            }
        }

        persistAndPublish(updated)
    }

    /**
     * Inicia ou retoma (Continuar) um cronômetro a partir do tempo acumulado.
     */
    suspend fun startOrResume(timerId: Int) = mutex.withLock {
        val current = findTimer(timerId) ?: return@withLock
        if (current.status == TimerStatus.RUNNING) return@withLock

        val nowElapsed = clockProvider.elapsedRealtime()
        val nowWall = clockProvider.currentTimeMillis()
        val baseAccumulated = if (current.status == TimerStatus.COMPLETED) 0L else current.accumulatedMs

        val updated = current.copy(
            status = TimerStatus.RUNNING,
            accumulatedMs = baseAccumulated,
            lastStartElapsedRealtime = nowElapsed,
            lastStartWallClockMs = nowWall,
            lastCompletedCycleCount = if (current.status == TimerStatus.COMPLETED) 0 else current.lastCompletedCycleCount
        )

        persistAndPublish(updated)
    }

    /**
     * Pausa um cronômetro em execução, preservando o tempo exato acumulado até o instante monotônico atual.
     */
    suspend fun pause(timerId: Int) = mutex.withLock {
        val current = findTimer(timerId) ?: return@withLock
        if (current.status != TimerStatus.RUNNING) return@withLock

        val nowElapsed = clockProvider.elapsedRealtime()
        val totalElapsed = TimerCalculator.calculateTotalElapsedMs(current, nowElapsed)

        val effectiveAccumulated = if (current.isCountdown && current.autoRepeat) {
            val duration = max(1_000L, current.countdownDurationMs)
            totalElapsed % duration
        } else {
            totalElapsed
        }

        val updated = current.copy(
            status = TimerStatus.PAUSED,
            accumulatedMs = effectiveAccumulated,
            lastStartElapsedRealtime = 0L,
            lastStartWallClockMs = 0L
        )

        persistAndPublish(updated)
    }

    /**
     * Alterna entre Iniciar/Continuar e Pausar (ação de 1 toque na janela flutuante).
     */
    suspend fun toggleStartPause(timerId: Int) {
        val current = findTimer(timerId) ?: return
        if (current.status == TimerStatus.RUNNING) {
            pause(timerId)
        } else {
            startOrResume(timerId)
        }
    }

    /**
     * Zera um cronômetro, voltando ao estado não iniciado (IDLE) e limpando o tempo acumulado.
     */
    suspend fun reset(timerId: Int) = mutex.withLock {
        val current = findTimer(timerId) ?: return@withLock
        val updated = current.copy(
            status = TimerStatus.IDLE,
            accumulatedMs = 0L,
            lastStartElapsedRealtime = 0L,
            lastStartWallClockMs = 0L,
            lastCompletedCycleCount = 0
        )
        persistAndPublish(updated)
    }

    /**
     * Atualiza a posição (X, Y) da janela flutuante individual do cronômetro.
     */
    suspend fun updateWindowPosition(timerId: Int, x: Int, y: Int) = mutex.withLock {
        val current = findTimer(timerId) ?: return@withLock
        if (current.windowX == x && current.windowY == y) return@withLock
        val updated = current.copy(windowX = x, windowY = y)
        persistAndPublish(updated)
    }

    /**
     * Atualiza o bloqueio individual de posição de um cronômetro.
     */
    suspend fun setIndividualPositionLock(timerId: Int, locked: Boolean) = mutex.withLock {
        val current = findTimer(timerId) ?: return@withLock
        val updated = current.copy(isPositionLocked = locked)
        persistAndPublish(updated)
    }

    /**
     * Salva edições completas de configuração (nome, modo regressivo, cores, tamanho, formato).
     */
    suspend fun updateTimerConfiguration(updatedConfig: TimerConfig) = mutex.withLock {
        val existing = findTimer(updatedConfig.id) ?: return@withLock
        val modeChanged = existing.isCountdown != updatedConfig.isCountdown ||
            existing.countdownDurationMs != updatedConfig.countdownDurationMs

        val merged = if (modeChanged) {
            updatedConfig.copy(
                status = TimerStatus.IDLE,
                accumulatedMs = 0L,
                lastStartElapsedRealtime = 0L,
                lastStartWallClockMs = 0L,
                lastCompletedCycleCount = 0
            )
        } else {
            updatedConfig.copy(
                status = existing.status,
                accumulatedMs = existing.accumulatedMs,
                lastStartElapsedRealtime = existing.lastStartElapsedRealtime,
                lastStartWallClockMs = existing.lastStartWallClockMs,
                lastCompletedCycleCount = existing.lastCompletedCycleCount
            )
        }
        persistAndPublish(merged)
    }

    /**
     * Avalia se algum temporizador regressivo atingiu zero ou avançou de ciclo na repetição automática.
     * Deve ser chamado periodicamente pelo serviço ou motor de atualização.
     */
    suspend fun checkCountdownsAndEmitEvents() = mutex.withLock {
        val nowElapsed = clockProvider.elapsedRealtime()
        val currentList = _timersState.value
        var listChanged = false

        val updatedList = currentList.map { timer ->
            if (!timer.isCountdown || timer.status != TimerStatus.RUNNING) {
                return@map timer
            }

            val targetDuration = max(1_000L, timer.countdownDurationMs)
            val totalElapsed = TimerCalculator.calculateTotalElapsedMs(timer, nowElapsed)

            if (totalElapsed < targetDuration) {
                return@map timer
            }

            if (timer.autoRepeat) {
                val currentCycles = (totalElapsed / targetDuration).toInt()
                if (currentCycles > timer.lastCompletedCycleCount) {
                    listChanged = true
                    _completionEvents.tryEmit(
                        TimerCompletionEvent(
                            timerId = timer.id,
                            timerName = timer.name,
                            category = timer.category,
                            vibrate = timer.vibrateOnFinish,
                            autoRepeated = true,
                            cycleNumber = currentCycles
                        )
                    )
                    val updated = timer.copy(lastCompletedCycleCount = currentCycles)
                    repository.saveTimer(updated)
                    updated
                } else {
                    timer
                }
            } else {
                listChanged = true
                _completionEvents.tryEmit(
                    TimerCompletionEvent(
                        timerId = timer.id,
                        timerName = timer.name,
                        category = timer.category,
                        vibrate = timer.vibrateOnFinish,
                        autoRepeated = false,
                        cycleNumber = 1
                    )
                )
                val updated = timer.copy(
                    status = TimerStatus.COMPLETED,
                    accumulatedMs = targetDuration,
                    lastStartElapsedRealtime = 0L,
                    lastStartWallClockMs = 0L,
                    lastCompletedCycleCount = 1
                )
                repository.saveTimer(updated)
                updated
            }
        }

        if (listChanged) {
            _timersState.value = updatedList
        }
    }

    /**
     * Pausa todos os cronômetros em execução de uma categoria (ou todos).
     */
    suspend fun pauseAll(category: TimerCategory? = null) = mutex.withLock {
        val nowElapsed = clockProvider.elapsedRealtime()
        val updated = _timersState.value.map { timer ->
            if ((category == null || timer.category == category) && timer.status == TimerStatus.RUNNING) {
                val totalElapsed = TimerCalculator.calculateTotalElapsedMs(timer, nowElapsed)
                timer.copy(
                    status = TimerStatus.PAUSED,
                    accumulatedMs = totalElapsed,
                    lastStartElapsedRealtime = 0L,
                    lastStartWallClockMs = 0L
                )
            } else {
                timer
            }
        }
        repository.saveAllTimers(updated)
        _timersState.value = updated
    }

    /**
     * Zera todos os cronômetros de uma categoria (ou todos).
     */
    suspend fun resetAll(category: TimerCategory? = null) = mutex.withLock {
        val updated = _timersState.value.map { timer ->
            if (category == null || timer.category == category) {
                timer.copy(
                    status = TimerStatus.IDLE,
                    accumulatedMs = 0L,
                    lastStartElapsedRealtime = 0L,
                    lastStartWallClockMs = 0L,
                    lastCompletedCycleCount = 0
                )
            } else {
                timer
            }
        }
        repository.saveAllTimers(updated)
        _timersState.value = updated
    }

    /**
     * Desativa todas as janelas flutuantes e/ou notificações ativas.
     */
    suspend fun disableAll(category: TimerCategory? = null) = mutex.withLock {
        val nowElapsed = clockProvider.elapsedRealtime()
        val updated = _timersState.value.map { timer ->
            if ((category == null || timer.category == category) && timer.isEnabled) {
                val totalElapsed = if (timer.status == TimerStatus.RUNNING) {
                    TimerCalculator.calculateTotalElapsedMs(timer, nowElapsed)
                } else {
                    timer.accumulatedMs
                }
                timer.copy(
                    isEnabled = false,
                    status = if (timer.status == TimerStatus.RUNNING) TimerStatus.PAUSED else timer.status,
                    accumulatedMs = totalElapsed,
                    lastStartElapsedRealtime = 0L,
                    lastStartWallClockMs = 0L
                )
            } else {
                timer
            }
        }
        repository.saveAllTimers(updated)
        _timersState.value = updated
    }

    /**
     * Restaura todos os 10 cronômetros para as configurações padrão de fábrica.
     */
    suspend fun restoreAllDefaults() = mutex.withLock {
        val defaults = repository.resetToDefaults()
        _timersState.value = defaults
    }

    /**
     * Obtém a formatação instantânea de um cronômetro usando o relógio monotônico atual.
     */
    fun getFormattedTime(timer: TimerConfig): TimeFormatted {
        return TimerCalculator.evaluateAndFormat(timer, clockProvider.elapsedRealtime())
    }

    fun hasAnyActiveServiceRequirement(): Boolean {
        return _timersState.value.any { it.isEnabled }
    }

    private fun findTimer(timerId: Int): TimerConfig? {
        return _timersState.value.firstOrNull { it.id == timerId }
    }

    private suspend fun persistAndPublish(updatedTimer: TimerConfig) {
        repository.saveTimer(updatedTimer)
        _timersState.value = _timersState.value.map {
            if (it.id == updatedTimer.id) updatedTimer else it
        }
    }
}
