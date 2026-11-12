package com.example.timer

import com.example.domain.TimeFormatted
import com.example.domain.TimerConfig
import com.example.domain.TimerStatus
import java.util.Locale
import kotlin.math.max

/**
 * Motor matemático puro de cronometragem.
 * Utiliza exclusivamente diferenças monotônicas (elapsedRealtime) sem depender de contadores
 * incrementais por frame, garantindo zero desvio (drift) mesmo sob carga de CPU ou tela bloqueada.
 */
object TimerCalculator {

    /**
     * Calcula o tempo total decorrido (em milissegundos) desde o início da contagem atual.
     */
    fun calculateTotalElapsedMs(timer: TimerConfig, nowElapsedRealtime: Long): Long {
        return when (timer.status) {
            TimerStatus.IDLE -> 0L
            TimerStatus.PAUSED, TimerStatus.COMPLETED -> max(0L, timer.accumulatedMs)
            TimerStatus.RUNNING -> {
                val delta = max(0L, nowElapsedRealtime - timer.lastStartElapsedRealtime)
                max(0L, timer.accumulatedMs + delta)
            }
        }
    }

    /**
     * Avalia o estado instantâneo do cronômetro (progressivo ou regressivo com repetição exata)
     * e formata os dígitos principais e centésimos.
     */
    fun evaluateAndFormat(timer: TimerConfig, nowElapsedRealtime: Long): TimeFormatted {
        val totalElapsed = calculateTotalElapsedMs(timer, nowElapsedRealtime)

        if (!timer.isCountdown) {
            // Modo Cronômetro Progressivo
            return formatMillis(
                displayMillis = totalElapsed,
                showHours = timer.showHours,
                showHundredths = timer.showHundredths,
                isCompleted = false,
                completedCycles = 0
            )
        }

        // Modo Temporizador Regressivo
        val targetDuration = max(1_000L, timer.countdownDurationMs)

        if (timer.status == TimerStatus.IDLE) {
            return formatMillis(
                displayMillis = targetDuration,
                showHours = timer.showHours,
                showHundredths = timer.showHundredths,
                isCompleted = false,
                completedCycles = 0
            )
        }

        if (timer.status == TimerStatus.COMPLETED) {
            return formatMillis(
                displayMillis = 0L,
                showHours = timer.showHours,
                showHundredths = timer.showHundredths,
                isCompleted = true,
                completedCycles = max(1, timer.lastCompletedCycleCount)
            )
        }

        if (totalElapsed < targetDuration) {
            val remaining = targetDuration - totalElapsed
            return formatMillis(
                displayMillis = remaining,
                showHours = timer.showHours,
                showHundredths = timer.showHundredths,
                isCompleted = false,
                completedCycles = timer.lastCompletedCycleCount
            )
        }

        // Atingiu ou ultrapassou a duração configurada
        return if (timer.autoRepeat && timer.status == TimerStatus.RUNNING) {
            // Preserva a precisão exata do ciclo via aritmética modular monotônica
            val completedCycles = (totalElapsed / targetDuration).toInt()
            val elapsedInCurrentCycle = totalElapsed % targetDuration
            val remainingInCurrentCycle = targetDuration - elapsedInCurrentCycle
            formatMillis(
                displayMillis = remainingInCurrentCycle,
                showHours = timer.showHours,
                showHundredths = timer.showHundredths,
                isCompleted = false,
                completedCycles = completedCycles
            )
        } else {
            formatMillis(
                displayMillis = 0L,
                showHours = timer.showHours,
                showHundredths = timer.showHundredths,
                isCompleted = true,
                completedCycles = 1
            )
        }
    }

    /**
     * Formata um valor em milissegundos para exibição visual.
     * - Quando [showHours] é verdadeiro (ou quando o tempo é >= 1 hora), exibe HH:MM:SS.
     * - Caso contrário, exibe MM:SS.
     * - Quando [showHundredths] é verdadeiro, retorna os centésimos ("00".."99") separadamente
     *   para renderização proporcionalmente menor.
     */
    fun formatMillis(
        displayMillis: Long,
        showHours: Boolean,
        showHundredths: Boolean,
        isCompleted: Boolean = false,
        completedCycles: Int = 0
    ): TimeFormatted {
        val safeMillis = max(0L, displayMillis)
        val totalSeconds = safeMillis / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val hundredths = (safeMillis % 1000L) / 10L

        val mainDigits = if (showHours || hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            val totalMinutes = totalSeconds / 60L
            String.format(Locale.US, "%02d:%02d", totalMinutes, seconds)
        }

        val hundredthsDigits = if (showHundredths) {
            String.format(Locale.US, "%02d", hundredths)
        } else {
            null
        }

        return TimeFormatted(
            displayMillis = safeMillis,
            mainDigits = mainDigits,
            hundredthsDigits = hundredthsDigits,
            isCompleted = isCompleted,
            completedCycles = completedCycles
        )
    }

    /**
     * Política explícita de restauração pós-reinicialização (Reboot Reconciliation Policy):
     * Como SystemClock.elapsedRealtime() é zerado quando o dispositivo reinicia, não podemos
     * presumir que a referência monotônica anterior continua válida após um reboot.
     *
     * Política aplicada:
     * 1. Detecta reinicialização se:
     *    - O evento for explicitamente um BOOT_COMPLETED (`forceBootPolicy = true`), OU
     *    - `lastStartElapsedRealtime > nowElapsedRealtime`, OU
     *    - A diferença entre o delta monotônico e o delta de relógio de parede (wall clock) divergir
     *      em mais de 5 segundos.
     * 2. Quando detectado um reboot enquanto o cronômetro estava `RUNNING`:
     *    - O tempo acumulado até o momento da última persistência é preservado integralmente
     *      e o estado do cronômetro é convertido de forma segura para `PAUSED` (evitando contagens
     *      fantasmas ou negativas causadas pela mudança de referência monotônica).
     */
    fun reconcileAfterReboot(
        timer: TimerConfig,
        nowElapsedRealtime: Long,
        nowWallClockMs: Long,
        forceBootPolicy: Boolean = false
    ): TimerConfig {
        if (timer.status != TimerStatus.RUNNING) {
            return timer
        }

        val monotonicDelta = nowElapsedRealtime - timer.lastStartElapsedRealtime
        val wallDelta = if (timer.lastStartWallClockMs > 0L) {
            nowWallClockMs - timer.lastStartWallClockMs
        } else {
            monotonicDelta
        }

        val clockDiscontinuity = kotlin.math.abs(monotonicDelta - wallDelta) > 5_000L
        val isRebootDetected = forceBootPolicy ||
            timer.lastStartElapsedRealtime <= 0L ||
            nowElapsedRealtime < timer.lastStartElapsedRealtime ||
            clockDiscontinuity

        return if (isRebootDetected) {
            // Pausa de forma consistente preservando o tempo acumulado válido
            timer.copy(
                status = TimerStatus.PAUSED,
                lastStartElapsedRealtime = 0L,
                lastStartWallClockMs = 0L
            )
        } else {
            timer
        }
    }
}
