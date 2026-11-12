package com.example.domain

enum class TimerCategory {
    FLOATING,
    NOTIFICATION
}

enum class TimerStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class TimerConfig(
    val id: Int,
    val category: TimerCategory,
    val name: String,
    val isEnabled: Boolean = false,
    val status: TimerStatus = TimerStatus.IDLE,
    val isCountdown: Boolean = false,
    val countdownDurationMs: Long = 60_000L, // Padrão 1 minuto
    val vibrateOnFinish: Boolean = true,
    val autoRepeat: Boolean = false,
    val notifyOnFinish: Boolean = true,
    val accumulatedMs: Long = 0L,
    val lastStartElapsedRealtime: Long = 0L,
    val lastStartWallClockMs: Long = 0L,
    val lastCompletedCycleCount: Int = 0,
    val scaleFactor: Float = 1.0f, // 0.6f a 2.0f
    val runningBgColor: Long = 0xFF059669L, // Emerald 600
    val pausedBgColor: Long = 0xFF1E293BL,  // Slate 800
    val textColor: Long = 0xFFFFFFFFL,      // Branco puro
    val showHundredths: Boolean = true,
    val showHours: Boolean = false,
    val useSevenSegmentFont: Boolean = true,
    val windowX: Int = 48,
    val windowY: Int = 160,
    val isPositionLocked: Boolean = false
) {
    val activeBackgroundColor: Long
        get() = if (status == TimerStatus.RUNNING) runningBgColor else pausedBgColor

    val hoursPart: Int
        get() = ((countdownDurationMs / 1000L) / 3600L).toInt()

    val minutesPart: Int
        get() = (((countdownDurationMs / 1000L) % 3600L) / 60L).toInt()

    val secondsPart: Int
        get() = ((countdownDurationMs / 1000L) % 60L).toInt()
}

data class TimeFormatted(
    val displayMillis: Long,
    val mainDigits: String,
    val hundredthsDigits: String?,
    val isCompleted: Boolean,
    val completedCycles: Int
)

data class TimerCompletionEvent(
    val timerId: Int,
    val timerName: String,
    val category: TimerCategory,
    val vibrate: Boolean,
    val autoRepeated: Boolean,
    val cycleNumber: Int
)

object DefaultTimerFactory {
    private val defaultRunningColors = listOf(
        0xFF059669L, // Esmeralda
        0xFF0284C7L, // Azul Ciano
        0xFF7C3AEDL, // Violeta
        0xFFD97706L, // Âmbar
        0xFFE11D48L  // Rosa Rubi
    )

    private val defaultPausedColors = listOf(
        0xFF1E293BL, // Ardósia Escuro
        0xFF172554L, // Azul Noturno
        0xFF2E1065L, // Roxo Profundo
        0xFF451A03L, // Marrom Escuro
        0xFF4C0519L  // Bordô Escuro
    )

    val presetPalette: List<Pair<String, Long>> = listOf(
        "Esmeralda" to 0xFF059669L,
        "Verde Neon" to 0xFF10B981L,
        "Ciano Elétrico" to 0xFF06B6D4L,
        "Azul Oceano" to 0xFF0284C7L,
        "Índigo" to 0xFF4F46E5L,
        "Violeta" to 0xFF7C3AEDL,
        "Rosa Rubi" to 0xFFE11D48L,
        "Laranja Vivo" to 0xFFEA580CL,
        "Âmbar" to 0xFFD97706L,
        "Ardósia Escuro" to 0xFF1E293BL,
        "Grafite" to 0xFF0F172AL,
        "Preto Puro" to 0xFF000000L,
        "Branco Puro" to 0xFFFFFFFFL,
        "Amarelo Solar" to 0xFFFACC15L,
        "Ciano Claro" to 0xFF67E8F9L,
        "Verde Matriz" to 0xFF4ADE80L
    )

    fun createDefaultTimers(): List<TimerConfig> {
        val floating = (1..5).map { index ->
            TimerConfig(
                id = index,
                category = TimerCategory.FLOATING,
                name = "Cronômetro $index",
                isEnabled = false,
                status = TimerStatus.IDLE,
                isCountdown = index == 2, // O 2º já vem configurado como exemplo de regressivo (editável)
                countdownDurationMs = if (index == 2) 5 * 60_000L else 60_000L,
                vibrateOnFinish = true,
                autoRepeat = false,
                notifyOnFinish = true,
                scaleFactor = 1.0f,
                runningBgColor = defaultRunningColors[(index - 1) % defaultRunningColors.size],
                pausedBgColor = defaultPausedColors[(index - 1) % defaultPausedColors.size],
                textColor = 0xFFFFFFFFL,
                showHundredths = true,
                showHours = false,
                useSevenSegmentFont = true,
                windowX = 36 + ((index - 1) % 2) * 260,
                windowY = 140 + ((index - 1) / 2) * 150
            )
        }

        val notifications = (1..5).map { index ->
            TimerConfig(
                id = 100 + index,
                category = TimerCategory.NOTIFICATION,
                name = "Notificação $index",
                isEnabled = false,
                status = TimerStatus.IDLE,
                isCountdown = index == 2,
                countdownDurationMs = if (index == 2) 10 * 60_000L else 3 * 60_000L,
                vibrateOnFinish = true,
                autoRepeat = false,
                notifyOnFinish = true,
                scaleFactor = 1.0f,
                runningBgColor = defaultRunningColors[(index - 1) % defaultRunningColors.size],
                pausedBgColor = defaultPausedColors[(index - 1) % defaultPausedColors.size],
                textColor = 0xFFFFFFFFL,
                showHundredths = false,
                showHours = true,
                useSevenSegmentFont = false,
                windowX = 0,
                windowY = 0
            )
        }

        return floating + notifications
    }
}
