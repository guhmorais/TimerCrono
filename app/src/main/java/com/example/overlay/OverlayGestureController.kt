package com.example.overlay

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Controlador de gestos desacoplado e livre de conflitos para as janelas flutuantes:
 * - Um arrasto (movimento > touchSlop) NUNCA é interpretado como toque simples ou duplo.
 * - Um toque duplo NUNCA dispara acidentalmente a ação de toque simples.
 * - O posicionamento (X, Y) é estritamente limitado aos limites úteis da tela.
 * - Respeita o bloqueio de posição global ou individual.
 */
class OverlayGestureController(
    private val touchSlopPx: Int = 18,
    private val doubleTapTimeoutMs: Long = 250L,
    private val longPressTimeoutMs: Long = 520L,
    private val isPositionLockedProvider: () -> Boolean,
    private val isLongPressEnabledProvider: () -> Boolean,
    private val screenBoundsProvider: () -> Pair<Int, Int>,
    private val viewSizeProvider: () -> Pair<Int, Int>,
    private val initialPositionProvider: () -> Pair<Int, Int>,
    private val onSingleTap: () -> Unit,
    private val onDoubleTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onPositionChanged: (x: Int, y: Int, isDragFinished: Boolean) -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper())
) : View.OnTouchListener {

    private var downRawX = 0f
    private var downRawY = 0f
    private var startWindowX = 0
    private var startWindowY = 0
    private var currentWindowX = 0
    private var currentWindowY = 0

    private var isDragging = false
    private var longPressConsumed = false
    private var waitingForSecondTap = false

    private val singleTapRunnable = Runnable {
        if (waitingForSecondTap) {
            waitingForSecondTap = false
            onSingleTap()
        }
    }

    private val longPressRunnable = Runnable {
        if (!isDragging && isLongPressEnabledProvider()) {
            longPressConsumed = true
            waitingForSecondTap = false
            handler.removeCallbacks(singleTapRunnable)
            onLongPress()
        }
    }

    override fun onTouch(view: View?, event: MotionEvent): Boolean {
        return handleTouchEvent(
            action = event.actionMasked,
            rawX = event.rawX,
            rawY = event.rawY
        )
    }

    fun handleTouchEvent(action: Int, rawX: Float, rawY: Float): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = rawX
                downRawY = rawY
                val (initX, initY) = initialPositionProvider()
                startWindowX = initX
                startWindowY = initY
                currentWindowX = initX
                currentWindowY = initY
                isDragging = false
                longPressConsumed = false

                if (!waitingForSecondTap && isLongPressEnabledProvider()) {
                    handler.postDelayed(longPressRunnable, longPressTimeoutMs)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = rawX - downRawX
                val dy = rawY - downRawY
                val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                if (!isDragging && distance > touchSlopPx) {
                    isDragging = true
                    // Cancela imediatamente qualquer toque simples, duplo ou toque longo pendente
                    waitingForSecondTap = false
                    handler.removeCallbacks(singleTapRunnable)
                    handler.removeCallbacks(longPressRunnable)
                }

                if (isDragging && !isPositionLockedProvider() && !longPressConsumed) {
                    val (screenW, screenH) = screenBoundsProvider()
                    val (viewW, viewH) = viewSizeProvider()
                    val clamped = clampPositionToBounds(
                        targetX = (startWindowX + dx.toInt()),
                        targetY = (startWindowY + dy.toInt()),
                        viewWidth = viewW,
                        viewHeight = viewH,
                        screenWidth = screenW,
                        screenHeight = screenH
                    )
                    currentWindowX = clamped.first
                    currentWindowY = clamped.second
                    onPositionChanged(currentWindowX, currentWindowY, false)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)

                if (longPressConsumed) {
                    longPressConsumed = false
                    return true
                }

                if (isDragging) {
                    isDragging = false
                    if (!isPositionLockedProvider()) {
                        onPositionChanged(currentWindowX, currentWindowY, true)
                    }
                    return true
                }

                // Não houve arrasto nem pressão longa: discrimina 1 toque vs 2 toques
                if (waitingForSecondTap) {
                    waitingForSecondTap = false
                    handler.removeCallbacks(singleTapRunnable)
                    onDoubleTap()
                } else {
                    waitingForSecondTap = true
                    handler.postDelayed(singleTapRunnable, doubleTapTimeoutMs)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                isDragging = false
                longPressConsumed = false
                return true
            }
        }
        return false
    }

    companion object {
        /**
         * Garante que a janela flutuante nunca ultrapasse os limites visíveis da tela
         * tanto na orientação vertical quanto na horizontal.
         */
        fun clampPositionToBounds(
            targetX: Int,
            targetY: Int,
            viewWidth: Int,
            viewHeight: Int,
            screenWidth: Int,
            screenHeight: Int
        ): Pair<Int, Int> {
            val safeScreenW = screenWidth.coerceAtLeast(100)
            val safeScreenH = screenHeight.coerceAtLeast(100)
            val safeViewW = viewWidth.coerceIn(40, safeScreenW)
            val safeViewH = viewHeight.coerceIn(30, safeScreenH)

            val maxX = (safeScreenW - safeViewW).coerceAtLeast(0)
            val maxY = (safeScreenH - safeViewH).coerceAtLeast(0)

            val clampedX = targetX.coerceIn(0, maxX)
            val clampedY = targetY.coerceIn(0, maxY)
            return clampedX to clampedY
        }
    }
}
