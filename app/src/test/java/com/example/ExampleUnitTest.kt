package com.example

import com.example.data.TimerPersistenceDataSource
import com.example.domain.DefaultTimerFactory
import com.example.domain.TimerCategory
import com.example.domain.TimerCompletionEvent
import com.example.domain.TimerConfig
import com.example.domain.TimerStatus
import com.example.overlay.OverlayGestureController
import com.example.timer.FakeClockProvider
import com.example.timer.TimerCalculator
import com.example.timer.TimerEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repositório em memória para testes determinísticos do motor de temporização.
 */
class FakeTimerPersistenceDataSource : TimerPersistenceDataSource {
    private val state = MutableStateFlow(DefaultTimerFactory.createDefaultTimers())
    override val timersFlow: Flow<List<TimerConfig>> = state

    override suspend fun getAllTimersOnce(): List<TimerConfig> = state.value

    override suspend fun getTimerById(id: Int): TimerConfig? =
        state.value.firstOrNull { it.id == id }

    override suspend fun saveTimer(timer: TimerConfig) {
        state.value = state.value.map { if (it.id == timer.id) timer else it }
    }

    override suspend fun saveAllTimers(timers: List<TimerConfig>) {
        state.value = timers
    }

    override suspend fun ensureSeeded(): List<TimerConfig> = state.value

    override suspend fun resetToDefaults(): List<TimerConfig> {
        val defaults = DefaultTimerFactory.createDefaultTimers()
        state.value = defaults
        return defaults
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

    // Cenário 1: Iniciar e pausar um cronômetro
    @Test
    fun scenario01_startAndPauseStopwatch_preservesExactAccumulatedTime() = runTest {
        val clock = FakeClockProvider(currentElapsedMs = 10_000L)
        val repo = FakeTimerPersistenceDataSource()
        val engine = TimerEngine(repo, clock, backgroundScope)
        engine.initializeFromStorage()

        engine.startOrResume(1)
        clock.advanceBy(4_350L) // 4.35 segundos
        engine.pause(1)

        val timer1 = engine.timersState.value.first { it.id == 1 }
        assertEquals(TimerStatus.PAUSED, timer1.status)
        assertEquals(4_350L, timer1.accumulatedMs)

        // Mesmo que o relógio avance enquanto pausado, o tempo exibido não muda
        clock.advanceBy(10_000L)
        val formatted = engine.getFormattedTime(timer1)
        assertEquals("00:04", formatted.mainDigits)
        assertEquals("35", formatted.hundredthsDigits)
    }

    // Cenário 2: Continuar após pausa
    @Test
    fun scenario02_resumeAfterPause_accumulatesCorrectly() = runTest {
        val clock = FakeClockProvider(currentElapsedMs = 50_000L)
        val repo = FakeTimerPersistenceDataSource()
        val engine = TimerEngine(repo, clock, backgroundScope)
        engine.initializeFromStorage()

        engine.startOrResume(1)
        clock.advanceBy(2_500L)
        engine.pause(1)

        clock.advanceBy(8_000L) // Pausa de 8s
        engine.startOrResume(1) // Continuar
        clock.advanceBy(3_250L) // +3.25s em execução

        val timer1 = engine.timersState.value.first { it.id == 1 }
        val formatted = engine.getFormattedTime(timer1)
        assertEquals(5_750L, formatted.displayMillis)
        assertEquals("00:05", formatted.mainDigits)
        assertEquals("75", formatted.hundredthsDigits)
    }

    // Cenário 3: Zerar
    @Test
    fun scenario03_resetStopwatch_clearsAccumulatedTimeAndReturnsToIdle() = runTest {
        val clock = FakeClockProvider(currentElapsedMs = 10_000L)
        val repo = FakeTimerPersistenceDataSource()
        val engine = TimerEngine(repo, clock, backgroundScope)
        engine.initializeFromStorage()

        engine.startOrResume(1)
        clock.advanceBy(15_890L)
        engine.reset(1)

        val timer1 = engine.timersState.value.first { it.id == 1 }
        assertEquals(TimerStatus.IDLE, timer1.status)
        assertEquals(0L, timer1.accumulatedMs)
        val formatted = engine.getFormattedTime(timer1)
        assertEquals("00:00", formatted.mainDigits)
        assertEquals("00", formatted.hundredthsDigits)
    }

    // Cenário 4: Executar cinco cronômetros simultaneamente sem interferência
    @Test
    fun scenario04_fiveSimultaneousFloatingTimers_runIndependently() = runTest {
        val clock = FakeClockProvider(currentElapsedMs = 100_000L)
        val repo = FakeTimerPersistenceDataSource()
        val engine = TimerEngine(repo, clock, backgroundScope)
        engine.initializeFromStorage()

        // Configura todos os 5 como progressivos e inicia em instantes escalonados
        for (id in 1..5) {
            val t = engine.timersState.value.first { it.id == id }
            engine.updateTimerConfiguration(t.copy(isCountdown = false))
            engine.setTimerEnabled(id, enabled = true, autoStart = false)
            engine.startOrResume(id)
            clock.advanceBy(1_000L)
        }

        // No final do loop:
        // Timer 1 rodou 5000ms, Timer 2: 4000ms, Timer 3: 3000ms, Timer 4: 2000ms, Timer 5: 1000ms
        for (id in 1..5) {
            val timer = engine.timersState.value.first { it.id == id }
            val expectedMs = (6 - id) * 1_000L
            assertEquals(expectedMs, engine.getFormattedTime(timer).displayMillis)
        }

        // Pausar o Timer 3 não afeta os demais
        engine.pause(3)
        clock.advanceBy(2_000L)

        assertEquals(7_000L, engine.getFormattedTime(engine.timersState.value.first { it.id == 1 }).displayMillis)
        assertEquals(3_000L, engine.getFormattedTime(engine.timersState.value.first { it.id == 3 }).displayMillis)
        assertEquals(3_000L, engine.getFormattedTime(engine.timersState.value.first { it.id == 5 }).displayMillis)
    }

    // Cenários 5, 6 e 7: Alternar entre apps, abrir recentes e bloquear/desbloquear tela
    @Test
    fun scenarios05_06_07_backgroundRecentsAndScreenLock_maintainsMonotonicAccuracy() = runTest {
        val clock = FakeClockProvider(currentElapsedMs = 200_000L)
        val repo = FakeTimerPersistenceDataSource()
        val engine = TimerEngine(repo, clock, backgroundScope)
        engine.initializeFromStorage()

        engine.startOrResume(1)
        // Simula usuário alternando para outro app, abrindo recentes e bloqueando a tela por 45 minutos
        val fortyFiveMinutesMs = 45 * 60 * 1_000L + 420L
        clock.advanceBy(fortyFiveMinutesMs)

        val timer1 = engine.timersState.value.first { it.id == 1 }
        val formatted = engine.getFormattedTime(timer1)
        assertEquals(fortyFiveMinutesMs, formatted.displayMillis)
        assertEquals("45:00", formatted.mainDigits)
        assertEquals("42", formatted.hundredthsDigits)
    }

    // Cenário 8: Alterar orientação da tela (retrato <-> paisagem) limitando aos limites úteis
    @Test
    fun scenario08_orientationChange_clampsWindowPositionToNewScreenBounds() {
        // Janela estava em X=850, Y=1700 no modo retrato (1080x2400)
        // Ao girar para paisagem (2400x1080), Y=1700 ultrapassaria a altura de 1080
        val (clampedX, clampedY) = OverlayGestureController.clampPositionToBounds(
            targetX = 850,
            targetY = 1700,
            viewWidth = 220,
            viewHeight = 90,
            screenWidth = 2400,
            screenHeight = 1080
        )
        assertEquals(850, clampedX)
        assertEquals(1080 - 90, clampedY)
    }

    // Cenário 11: Alterar cores, tamanho e formato (horas/centésimos)
    @Test
    fun scenario11_customizeColorsSizeAndFormat_appliesCorrectly() = runTest {
        val clock = FakeClockProvider(currentElapsedMs = 10_000L)
        val repo = FakeTimerPersistenceDataSource()
        val engine = TimerEngine(repo, clock, backgroundScope)
        engine.initializeFromStorage()

        val original = engine.timersState.value.first { it.id == 1 }
        val customized = original.copy(
            name = "Treino HIIT",
            scaleFactor = 1.6f,
            runningBgColor = 0xFFE11D48L,
            pausedBgColor = 0xFF0F172AL,
            textColor = 0xFFFACC15L,
            showHours = true,
            showHundredths = false,
            useSevenSegmentFont = false
        )
        engine.updateTimerConfiguration(customized)

        val updated = engine.timersState.value.first { it.id == 1 }
        assertEquals("Treino HIIT", updated.name)
        assertEquals(1.6f, updated.scaleFactor, 0.001f)
        assertEquals(0xFFE11D48L, updated.runningBgColor)
        assertEquals(0xFF0F172AL, updated.pausedBgColor)
        assertEquals(0xFFFACC15L, updated.textColor)

        val formatted = engine.getFormattedTime(updated)
        assertEquals("00:00:00", formatted.mainDigits)
        assertNull(formatted.hundredthsDigits)
    }

    // Cenário 12: Finalizar uma contagem regressiva
    @Test
    fun scenario12_countdownReachesZero_completesAndEmitsCompletionEvent() = runTest {
        val clock = FakeClockProvider(currentElapsedMs = 10_000L)
        val repo = FakeTimerPersistenceDataSource()
        val engine = TimerEngine(repo, clock, backgroundScope)
        engine.initializeFromStorage()

        val events = mutableListOf<TimerCompletionEvent>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completionEvents.collect { events.add(it) }
        }

        val timer2 = engine.timersState.value.first { it.id == 2 }
        engine.updateTimerConfiguration(
            timer2.copy(
                isCountdown = true,
                countdownDurationMs = 10_000L,
                autoRepeat = false,
                vibrateOnFinish = true
            )
        )

        engine.startOrResume(2)
        clock.advanceBy(10_500L)
        engine.checkCountdownsAndEmitEvents()

        val completedTimer = engine.timersState.value.first { it.id == 2 }
        assertEquals(TimerStatus.COMPLETED, completedTimer.status)
        assertEquals(1, events.size)
        assertEquals(2, events.first().timerId)
        assertFalse(events.first().autoRepeated)
        collectJob.cancel()
    }

    // Cenário 13: Repetir uma contagem regressiva preservando a precisão do ciclo
    @Test
    fun scenario13_countdownAutoRepeat_preservesExactCycleWithoutDrift() = runTest {
        val clock = FakeClockProvider(currentElapsedMs = 10_000L)
        val repo = FakeTimerPersistenceDataSource()
        val engine = TimerEngine(repo, clock, backgroundScope)
        engine.initializeFromStorage()

        val timer2 = engine.timersState.value.first { it.id == 2 }
        engine.updateTimerConfiguration(
            timer2.copy(
                isCountdown = true,
                countdownDurationMs = 10_000L, // Ciclo de 10s
                autoRepeat = true
            )
        )

        engine.startOrResume(2)
        // Simula atraso de renderização: avança 23.400ms (2 ciclos completos + 3.4s do 3º ciclo)
        clock.advanceBy(23_400L)
        engine.checkCountdownsAndEmitEvents()

        val runningRepeatTimer = engine.timersState.value.first { it.id == 2 }
        assertEquals(TimerStatus.RUNNING, runningRepeatTimer.status)
        assertEquals(2, runningRepeatTimer.lastCompletedCycleCount)

        val formatted = engine.getFormattedTime(runningRepeatTimer)
        // Deve restar exatamente 10_000 - 3_400 = 6_600ms no 3º ciclo, sem perder 400ms!
        assertEquals(6_600L, formatted.displayMillis)
        assertEquals("00:06", formatted.mainDigits)
        assertEquals("60", formatted.hundredthsDigits)
    }

    // Cenários 15 e 16: Encerrar todos os serviços/janelas e reabrir o aplicativo restaurando estado
    @Test
    fun scenarios15_16_disableAllAndReopenApp_restoresPersistedState() = runTest {
        val clock = FakeClockProvider(currentElapsedMs = 50_000L)
        val repo = FakeTimerPersistenceDataSource()
        val engine1 = TimerEngine(repo, clock, backgroundScope)
        engine1.initializeFromStorage()

        engine1.setTimerEnabled(1, enabled = true, autoStart = true)
        clock.advanceBy(7_200L)
        engine1.disableAll()

        assertFalse(engine1.hasAnyActiveServiceRequirement())

        // Simula reabertura do aplicativo criando nova instância do TimerEngine sobre o mesmo repositório
        val engine2 = TimerEngine(repo, clock, backgroundScope)
        engine2.initializeFromStorage()
        val restoredTimer1 = engine2.timersState.value.first { it.id == 1 }
        assertEquals(TimerStatus.PAUSED, restoredTimer1.status)
        assertEquals(7_200L, restoredTimer1.accumulatedMs)
    }

    // Cenário 17: Reiniciar o dispositivo (Reboot Reconciliation Policy)
    @Test
    fun scenario17_deviceReboot_reconcilesMonotonicReferenceSafely() {
        val runningBeforeBoot = DefaultTimerFactory.createDefaultTimers().first().copy(
            status = TimerStatus.RUNNING,
            accumulatedMs = 12_000L,
            lastStartElapsedRealtime = 500_000L,
            lastStartWallClockMs = 1_700_000_500_000L
        )

        // Após reiniciar o aparelho, elapsedRealtime volta para um valor baixo (ex: 4_000ms)
        val reconciled = TimerCalculator.reconcileAfterReboot(
            timer = runningBeforeBoot,
            nowElapsedRealtime = 4_000L,
            nowWallClockMs = 1_700_000_600_000L,
            forceBootPolicy = true
        )

        assertEquals(TimerStatus.PAUSED, reconciled.status)
        assertEquals(12_000L, reconciled.accumulatedMs)
        assertTrue(reconciled.lastStartElapsedRealtime == 0L)
    }
}
