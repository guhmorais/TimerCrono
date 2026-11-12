package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.example.FloatingTimerApplication
import com.example.domain.TimerCategory
import com.example.domain.TimerConfig
import com.example.domain.TimerStatus
import com.example.notification.TimerNotificationHelper
import com.example.overlay.OverlayWindowManager
import com.example.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço coordenador em primeiro plano (Foreground Service) que gerencia:
 * 1. As 5 janelas flutuantes no WindowManager (TYPE_APPLICATION_OVERLAY).
 * 2. Os 5 temporizadores independentes por notificação.
 * 3. Vibração e alertas ao finalizar contagens regressivas.
 * 4. Ciclo de vida inteligente: encerra-se automaticamente quando nenhum cronômetro
 *    flutuante ou de notificação estiver habilitado.
 */
class FloatingTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickLoopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var overlayWindowManager: OverlayWindowManager
    private lateinit var notificationHelper: TimerNotificationHelper

    private val app: FloatingTimerApplication
        get() = application as FloatingTimerApplication

    override fun onCreate() {
        super.onCreate()
        notificationHelper = app.notificationHelper
        overlayWindowManager = OverlayWindowManager(
            context = this,
            timerEngine = app.timerEngine,
            scope = serviceScope
        )

        observeStateAndSettings()
        observeCompletionEvents()
        startAdaptiveTickLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground(app.timerEngine.timersState.value)
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayWindowManager.onScreenConfigurationChanged()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickLoopJob?.cancel()
        overlayWindowManager.removeAllOverlays()
        releaseWakeLockIfHeld()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeStateAndSettings() {
        serviceScope.launch {
            combine(
                app.timerEngine.timersState,
                app.settingsRepository.settingsFlow
            ) { timers, settings ->
                timers to settings
            }.collect { (timers, settings) ->
                handleStateUpdate(timers, settings)
            }
        }
    }

    private fun observeCompletionEvents() {
        serviceScope.launch {
            app.timerEngine.completionEvents.collect { event ->
                if (event.vibrate) {
                    triggerVibration()
                }
                notificationHelper.showCompletionAlertNotification(event)
            }
        }
    }

    private fun handleStateUpdate(timers: List<TimerConfig>, settings: AppSettings) {
        val activeFloating = timers.count { it.category == TimerCategory.FLOATING && it.isEnabled }
        val activeNotif = timers.count { it.category == TimerCategory.NOTIFICATION && it.isEnabled }
        val runningCount = timers.count { it.isEnabled && it.status == TimerStatus.RUNNING }

        overlayWindowManager.synchronizeOverlays(timers, settings)
        notificationHelper.syncNotificationTimers(timers, app.timerEngine, forceUpdate = true)
        updateWakeLock(settings.keepScreenOn && runningCount > 0)

        if (activeFloating == 0 && activeNotif == 0) {
            notificationHelper.cancelAllTimerNotifications()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            promoteToForeground(timers)
        }
    }

    /**
     * Loop de atualização visual adaptativo:
     * - Só executa trabalho quando há cronômetros em execução (RUNNING).
     * - Ajusta a taxa de atualização: ~33ms (30fps) se algum cronômetro flutuante visível exibe
     *   centésimos de segundo, ou 200ms se apenas segundos são exibidos, economizando recursos.
     */
    private fun startAdaptiveTickLoop() {
        tickLoopJob?.cancel()
        tickLoopJob = serviceScope.launch {
            var lastNotificationSyncMs = 0L
            while (isActive) {
                val timers = app.timerEngine.timersState.value
                val anyRunning = timers.any { it.status == TimerStatus.RUNNING }

                if (anyRunning) {
                    app.timerEngine.checkCountdownsAndEmitEvents()
                    overlayWindowManager.tickVisibleOverlays(timers)

                    val now = app.timerEngine.clockProvider.elapsedRealtime()
                    if (now - lastNotificationSyncMs >= 250L) {
                        lastNotificationSyncMs = now
                        notificationHelper.syncNotificationTimers(timers, app.timerEngine)
                    }

                    val needsFastTick = timers.any {
                        it.category == TimerCategory.FLOATING &&
                            it.isEnabled &&
                            it.status == TimerStatus.RUNNING &&
                            it.showHundredths
                    }
                    delay(if (needsFastTick) 33L else 200L)
                } else {
                    delay(400L)
                }
            }
        }
    }

    private fun promoteToForeground(timers: List<TimerConfig>) {
        val activeFloating = timers.count { it.category == TimerCategory.FLOATING && it.isEnabled }
        val activeNotif = timers.count { it.category == TimerCategory.NOTIFICATION && it.isEnabled }
        val runningCount = timers.count { it.isEnabled && it.status == TimerStatus.RUNNING }

        val notification = notificationHelper.buildForegroundServiceNotification(
            activeFloatingCount = activeFloating,
            activeNotificationCount = activeNotif,
            runningCount = runningCount
        )

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    TimerNotificationHelper.FOREGROUND_SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    TimerNotificationHelper.FOREGROUND_SERVICE_NOTIFICATION_ID,
                    notification
                )
            }
        }
    }

    private fun updateWakeLock(shouldHold: Boolean) {
        if (shouldHold) {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FloatingTimer::PrecisionTimerWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L) // Limite de segurança de 4 horas
            }
        } else {
            releaseWakeLockIfHeld()
        }
    }

    private fun releaseWakeLockIfHeld() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        }
    }

    private fun triggerVibration() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0L, 250L, 120L, 350L)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500L)
            }
        }
    }

    companion object {
        fun ensureServiceRunningIfNeeded(context: Context, timers: List<TimerConfig>) {
            val needsService = timers.any { it.isEnabled }
            if (needsService) {
                val intent = Intent(context, FloatingTimerService::class.java)
                runCatching {
                    ContextCompat.startForegroundService(context, intent)
                }
            }
        }
    }
}
