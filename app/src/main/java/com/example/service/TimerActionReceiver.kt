package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.FloatingTimerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver que processa ações das notificações interativas
 * (Iniciar/Pausar, Zerar, Ocultar, Fechar Todos) e reconcilia o estado
 * após reinicialização do dispositivo (ACTION_BOOT_COMPLETED).
 */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = context.applicationContext as? FloatingTimerApplication ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_TOGGLE_TIMER -> {
                        val timerId = intent.getIntExtra(EXTRA_TIMER_ID, -1)
                        if (timerId != -1) {
                            app.timerEngine.toggleStartPause(timerId)
                        }
                    }

                    ACTION_RESET_TIMER -> {
                        val timerId = intent.getIntExtra(EXTRA_TIMER_ID, -1)
                        if (timerId != -1) {
                            app.timerEngine.reset(timerId)
                        }
                    }

                    ACTION_DISABLE_TIMER -> {
                        val timerId = intent.getIntExtra(EXTRA_TIMER_ID, -1)
                        if (timerId != -1) {
                            app.timerEngine.setTimerEnabled(timerId, enabled = false)
                        }
                    }

                    ACTION_STOP_ALL -> {
                        app.timerEngine.disableAll()
                    }

                    Intent.ACTION_BOOT_COMPLETED -> {
                        app.timerEngine.initializeFromStorage(forceBootPolicy = true)
                        val timers = app.timerEngine.timersState.value
                        FloatingTimerService.ensureServiceRunningIfNeeded(context, timers)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_TIMER = "com.example.action.TOGGLE_TIMER"
        const val ACTION_RESET_TIMER = "com.example.action.RESET_TIMER"
        const val ACTION_DISABLE_TIMER = "com.example.action.DISABLE_TIMER"
        const val ACTION_STOP_ALL = "com.example.action.STOP_ALL"
        const val EXTRA_TIMER_ID = "extra_timer_id"
    }
}
