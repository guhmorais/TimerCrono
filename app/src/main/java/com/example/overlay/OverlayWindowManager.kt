package com.example.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.ViewConfiguration
import android.view.WindowManager
import com.example.domain.TimerCategory
import com.example.domain.TimerConfig
import com.example.settings.AppSettings
import com.example.timer.TimerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Gerenciador coordenador das múltiplas janelas flutuantes no WindowManager.
 * Mantém até 5 cronômetros flutuantes simultâneos sem interferir no foco ou toque
 * dos demais aplicativos fora da área da própria janela.
 */
class OverlayWindowManager(
    private val context: Context,
    private val timerEngine: TimerEngine,
    private val scope: CoroutineScope
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private data class ActiveOverlayHolder(
        val view: FloatingOverlayView,
        val layoutParams: WindowManager.LayoutParams,
        var lastConfig: TimerConfig
    )

    private val activeOverlays = mutableMapOf<Int, ActiveOverlayHolder>()
    private var currentSettings = AppSettings()

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Sincroniza o conjunto de janelas abertas no WindowManager com a lista de cronômetros
     * flutuantes ativos e as configurações gerais.
     */
    fun synchronizeOverlays(timers: List<TimerConfig>, settings: AppSettings) {
        val keepScreenOnChanged = currentSettings.keepScreenOn != settings.keepScreenOn
        val lockChanged = currentSettings.lockPosition != settings.lockPosition
        currentSettings = settings

        if (!canDrawOverlays()) {
            removeAllOverlays()
            return
        }

        val enabledFloatingTimers = timers.filter {
            it.category == TimerCategory.FLOATING && it.isEnabled
        }
        val enabledIds = enabledFloatingTimers.map { it.id }.toSet()

        // Remove imediatamente as janelas dos cronômetros que foram desativados
        val toRemove = activeOverlays.keys.filter { it !in enabledIds }
        for (timerId in toRemove) {
            removeOverlayWindow(timerId)
        }

        val (screenW, screenH) = getUsableScreenBounds()

        // Cria ou atualiza as janelas dos cronômetros ativos
        for (timer in enabledFloatingTimers) {
            val formatted = timerEngine.getFormattedTime(timer)
            val existing = activeOverlays[timer.id]

            if (existing == null) {
                createAndAddOverlayWindow(timer, formatted, screenW, screenH)
            } else {
                val prev = existing.lastConfig
                val appearanceChanged =
                    prev.name != timer.name ||
                        prev.status != timer.status ||
                        prev.scaleFactor != timer.scaleFactor ||
                        prev.runningBgColor != timer.runningBgColor ||
                        prev.pausedBgColor != timer.pausedBgColor ||
                        prev.textColor != timer.textColor ||
                        prev.showHundredths != timer.showHundredths ||
                        prev.showHours != timer.showHours ||
                        prev.useSevenSegmentFont != timer.useSevenSegmentFont ||
                        prev.isCountdown != timer.isCountdown ||
                        prev.isPositionLocked != timer.isPositionLocked ||
                        lockChanged

                existing.lastConfig = timer

                if (appearanceChanged) {
                    existing.view.applyConfiguration(
                        config = timer,
                        formatted = formatted,
                        globalLocked = currentSettings.lockPosition
                    )
                } else {
                    existing.view.updateTimeOnly(
                        formatted = formatted,
                        statusChanged = false,
                        config = timer,
                        globalLocked = currentSettings.lockPosition
                    )
                }

                val positionChangedExternally =
                    existing.layoutParams.x != timer.windowX || existing.layoutParams.y != timer.windowY

                if (keepScreenOnChanged || positionChangedExternally || prev.scaleFactor != timer.scaleFactor) {
                    applyKeepScreenOnFlag(existing.layoutParams, currentSettings.keepScreenOn)
                    val clamped = OverlayGestureController.clampPositionToBounds(
                        targetX = timer.windowX,
                        targetY = timer.windowY,
                        viewWidth = existing.view.width.takeIf { it > 0 } ?: 220,
                        viewHeight = existing.view.height.takeIf { it > 0 } ?: 90,
                        screenWidth = screenW,
                        screenHeight = screenH
                    )
                    existing.layoutParams.x = clamped.first
                    existing.layoutParams.y = clamped.second
                    runCatching {
                        windowManager.updateViewLayout(existing.view, existing.layoutParams)
                    }
                }
            }
        }
    }

    /**
     * Atualiza apenas o tempo das janelas visíveis no loop de renderização (60fps para centésimos
     * ou 10fps quando centésimos estão ocultos), sem recalcular LayoutParams estáticos.
     */
    fun tickVisibleOverlays(timers: List<TimerConfig>) {
        if (activeOverlays.isEmpty()) return
        if (!canDrawOverlays()) {
            removeAllOverlays()
            return
        }

        val timerById = timers.associateBy { it.id }
        for ((timerId, holder) in activeOverlays) {
            val timer = timerById[timerId] ?: continue
            if (!timer.isEnabled) continue
            val formatted = timerEngine.getFormattedTime(timer)
            val statusChanged = holder.lastConfig.status != timer.status
            holder.lastConfig = timer
            holder.view.updateTimeOnly(
                formatted = formatted,
                statusChanged = statusChanged,
                config = timer,
                globalLocked = currentSettings.lockPosition
            )
        }
    }

    /**
     * Reajusta o posicionamento de todas as janelas ativas quando a orientação da tela muda
     * (retrato <-> paisagem), garantindo que nenhuma janela fique fora da área útil.
     */
    fun onScreenConfigurationChanged() {
        if (activeOverlays.isEmpty()) return
        val (screenW, screenH) = getUsableScreenBounds()

        for ((timerId, holder) in activeOverlays) {
            val viewW = holder.view.width.takeIf { it > 0 } ?: 220
            val viewH = holder.view.height.takeIf { it > 0 } ?: 90
            val (clampedX, clampedY) = OverlayGestureController.clampPositionToBounds(
                targetX = holder.layoutParams.x,
                targetY = holder.layoutParams.y,
                viewWidth = viewW,
                viewHeight = viewH,
                screenWidth = screenW,
                screenHeight = screenH
            )
            if (clampedX != holder.layoutParams.x || clampedY != holder.layoutParams.y) {
                holder.layoutParams.x = clampedX
                holder.layoutParams.y = clampedY
                runCatching {
                    windowManager.updateViewLayout(holder.view, holder.layoutParams)
                }
                scope.launch {
                    timerEngine.updateWindowPosition(timerId, clampedX, clampedY)
                }
            }
        }
    }

    fun removeAllOverlays() {
        val ids = activeOverlays.keys.toList()
        for (id in ids) {
            removeOverlayWindow(id)
        }
    }

    private fun createAndAddOverlayWindow(
        timer: TimerConfig,
        formatted: com.example.domain.TimeFormatted,
        screenW: Int,
        screenH: Int
    ) {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val (initialX, initialY) = OverlayGestureController.clampPositionToBounds(
            targetX = timer.windowX,
            targetY = timer.windowY,
            viewWidth = 220,
            viewHeight = 90,
            screenWidth = screenW,
            screenHeight = screenH
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }
        applyKeepScreenOnFlag(params, currentSettings.keepScreenOn)

        lateinit var overlayView: FloatingOverlayView
        overlayView = FloatingOverlayView(
            context = context,
            initialConfig = timer,
            onSingleTapAction = {
                scope.launch { timerEngine.toggleStartPause(timer.id) }
            },
            onConfirmResetAction = {
                scope.launch { timerEngine.reset(timer.id) }
            },
            onToggleLockAction = {
                val current = activeOverlays[timer.id]?.lastConfig ?: timer
                scope.launch {
                    timerEngine.setIndividualPositionLock(timer.id, !current.isPositionLocked)
                }
            },
            onCloseWindowAction = {
                scope.launch { timerEngine.setTimerEnabled(timer.id, enabled = false) }
            }
        )

        overlayView.applyConfiguration(timer, formatted, currentSettings.lockPosition)

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val gestureController = OverlayGestureController(
            touchSlopPx = touchSlop,
            isPositionLockedProvider = {
                val latest = activeOverlays[timer.id]?.lastConfig ?: timer
                currentSettings.lockPosition || latest.isPositionLocked
            },
            isLongPressEnabledProvider = {
                currentSettings.longPressOpensControls
            },
            screenBoundsProvider = {
                getUsableScreenBounds()
            },
            viewSizeProvider = {
                val w = overlayView.width.takeIf { it > 0 } ?: 220
                val h = overlayView.height.takeIf { it > 0 } ?: 90
                w to h
            },
            initialPositionProvider = {
                params.x to params.y
            },
            onSingleTap = {
                scope.launch { timerEngine.toggleStartPause(timer.id) }
            },
            onDoubleTap = {
                overlayView.requestResetWithConfirmation(currentSettings.confirmBeforeReset)
            },
            onLongPress = {
                overlayView.toggleExpandedControls()
            },
            onPositionChanged = { newX, newY, isFinished ->
                params.x = newX
                params.y = newY
                runCatching {
                    windowManager.updateViewLayout(overlayView, params)
                }
                if (isFinished) {
                    scope.launch {
                        timerEngine.updateWindowPosition(timer.id, newX, newY)
                    }
                }
            }
        )

        overlayView.setOnTouchListener(gestureController)

        runCatching {
            windowManager.addView(overlayView, params)
            activeOverlays[timer.id] = ActiveOverlayHolder(
                view = overlayView,
                layoutParams = params,
                lastConfig = timer
            )
        }
    }

    private fun removeOverlayWindow(timerId: Int) {
        val holder = activeOverlays.remove(timerId) ?: return
        runCatching {
            windowManager.removeViewImmediate(holder.view)
        }.onFailure {
            runCatching { windowManager.removeView(holder.view) }
        }
    }

    private fun applyKeepScreenOnFlag(params: WindowManager.LayoutParams, keepOn: Boolean) {
        params.flags = if (keepOn) {
            params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }
    }

    private fun getUsableScreenBounds(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = context.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }
}
