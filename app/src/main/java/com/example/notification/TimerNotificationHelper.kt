package com.example.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.TimerCategory
import com.example.domain.TimerCompletionEvent
import com.example.domain.TimerConfig
import com.example.domain.TimerStatus
import com.example.service.TimerActionReceiver
import com.example.timer.TimerCalculator
import com.example.timer.TimerEngine

/**
 * Gerenciador de canais, notificações de serviço em primeiro plano,
 * 5 temporizadores independentes por notificação e alertas de conclusão.
 * Evita atualizações redundantes verificando se o texto em segundos ou o estado mudaram.
 */
class TimerNotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Cache da última string renderizada por temporizador para evitar chamadas excessivas ao NotificationManager
    private val lastPublishedSignature = mutableMapOf<Int, String>()

    fun hasPostNotificationsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                context.getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém os cronômetros flutuantes e temporizadores ativos em segundo plano"
                setShowBadge(false)
            }

            val timersChannel = NotificationChannel(
                CHANNEL_TIMERS_ID,
                context.getString(R.string.channel_timers_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Exibe os 5 temporizadores independentes na barra de notificações"
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                context.getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas quando uma contagem regressiva chega a zero"
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(
                listOf(serviceChannel, timersChannel, alertsChannel)
            )
        }
    }

    fun buildForegroundServiceNotification(
        activeFloatingCount: Int,
        activeNotificationCount: Int,
        runningCount: Int
    ): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopAllIntent = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_STOP_ALL
        }
        val stopAllPending = PendingIntent.getBroadcast(
            context,
            999,
            stopAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryParts = mutableListOf<String>()
        if (activeFloatingCount > 0) {
            summaryParts.add("$activeFloatingCount janela(s) flutuante(s)")
        }
        if (activeNotificationCount > 0) {
            summaryParts.add("$activeNotificationCount notificação(ões)")
        }
        val contentText = if (summaryParts.isEmpty()) {
            "Serviço de cronometragem pronto"
        } else {
            "${summaryParts.joinToString(" • ")} ($runningCount em execução)"
        }

        return NotificationCompat.Builder(context, CHANNEL_SERVICE_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(contentText)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Fechar Todos",
                stopAllPending
            )
            .build()
    }

    /**
     * Sincroniza as 5 notificações independentes da aba "Notificações".
     * Atualiza apenas quando o segundo exibido ou o estado mudar, economizando CPU e bateria.
     */
    fun syncNotificationTimers(
        timers: List<TimerConfig>,
        timerEngine: TimerEngine,
        forceUpdate: Boolean = false
    ) {
        val notificationTimers = timers.filter { it.category == TimerCategory.NOTIFICATION }

        for (timer in notificationTimers) {
            val notifId = NOTIFICATION_TIMER_BASE_ID + timer.id
            if (!timer.isEnabled) {
                if (lastPublishedSignature.containsKey(timer.id)) {
                    lastPublishedSignature.remove(timer.id)
                    notificationManager.cancel(notifId)
                }
                continue
            }

            if (!hasPostNotificationsPermission()) continue

            // Para notificações, formatamos sem centésimos na assinatura de comparação para atualizar 1x/segundo
            val nowElapsed = timerEngine.clockProvider.elapsedRealtime()
            val evaluated = TimerCalculator.evaluateAndFormat(
                timer.copy(showHundredths = false),
                nowElapsed
            )

            val signature = "${timer.name}|${timer.status}|${timer.isCountdown}|${evaluated.mainDigits}"
            if (!forceUpdate && lastPublishedSignature[timer.id] == signature) {
                continue
            }
            lastPublishedSignature[timer.id] = signature

            val notification = buildIndividualTimerNotification(timer, evaluated.mainDigits)
            notificationManager.notify(notifId, notification)
        }
    }

    fun showCompletionAlertNotification(event: TimerCompletionEvent) {
        if (!hasPostNotificationsPermission()) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            event.timerId + 500,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resetIntent = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_RESET_TIMER
            putExtra(TimerActionReceiver.EXTRA_TIMER_ID, event.timerId)
        }
        val resetPending = PendingIntent.getBroadcast(
            context,
            event.timerId + 600,
            resetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (event.autoRepeated) {
            "Ciclo #${event.cycleNumber} concluído — reiniciando automaticamente."
        } else {
            "Contagem regressiva finalizada (00:00)!"
        }

        val alertNotification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏱ ${event.timerName}")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_revert, "Zerar", resetPending)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_BASE_ID + event.timerId, alertNotification)
    }

    fun cancelAllTimerNotifications() {
        lastPublishedSignature.clear()
        for (i in 101..105) {
            notificationManager.cancel(NOTIFICATION_TIMER_BASE_ID + i)
        }
    }

    private fun buildIndividualTimerNotification(
        timer: TimerConfig,
        formattedMainTime: String
    ): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            timer.id,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_TOGGLE_TIMER
            putExtra(TimerActionReceiver.EXTRA_TIMER_ID, timer.id)
        }
        val togglePending = PendingIntent.getBroadcast(
            context,
            timer.id + 1000,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resetIntent = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_RESET_TIMER
            putExtra(TimerActionReceiver.EXTRA_TIMER_ID, timer.id)
        }
        val resetPending = PendingIntent.getBroadcast(
            context,
            timer.id + 2000,
            resetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disableIntent = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_DISABLE_TIMER
            putExtra(TimerActionReceiver.EXTRA_TIMER_ID, timer.id)
        }
        val disablePending = PendingIntent.getBroadcast(
            context,
            timer.id + 3000,
            disableIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeLabel = if (timer.isCountdown) "Regressivo" else "Progressivo"
        val statusLabel = when (timer.status) {
            TimerStatus.IDLE -> "Pronto"
            TimerStatus.RUNNING -> "Em execução"
            TimerStatus.PAUSED -> "Pausado"
            TimerStatus.COMPLETED -> "Concluído!"
        }
        val toggleLabel = when (timer.status) {
            TimerStatus.RUNNING -> context.getString(R.string.btn_pause)
            TimerStatus.PAUSED -> context.getString(R.string.btn_resume)
            else -> context.getString(R.string.btn_start)
        }

        return NotificationCompat.Builder(context, CHANNEL_TIMERS_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${timer.name}  •  $formattedMainTime")
            .setContentText("$modeLabel • $statusLabel")
            .setSubText(formattedMainTime)
            .setContentIntent(openPending)
            .setOngoing(timer.status == TimerStatus.RUNNING)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setColor(timer.activeBackgroundColor.toInt())
            .addAction(
                if (timer.status == TimerStatus.RUNNING) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                toggleLabel,
                togglePending
            )
            .addAction(
                android.R.drawable.ic_menu_revert,
                context.getString(R.string.btn_reset),
                resetPending
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Ocultar",
                disablePending
            )
            .build()
    }

    companion object {
        const val CHANNEL_SERVICE_ID = "floating_timer_service_channel"
        const val CHANNEL_TIMERS_ID = "floating_timer_individual_channel"
        const val CHANNEL_ALERTS_ID = "floating_timer_alerts_channel"

        const val FOREGROUND_SERVICE_NOTIFICATION_ID = 1001
        const val NOTIFICATION_TIMER_BASE_ID = 2000
        const val ALERT_NOTIFICATION_BASE_ID = 3000
    }
}
