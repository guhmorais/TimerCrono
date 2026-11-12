package com.example.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.min

/**
 * Renderizador vetorial de dígitos digitais de 7 segmentos para alta legibilidade
 * tanto nas janelas flutuantes do WindowManager quanto nas prévias em Jetpack Compose.
 */
object SevenSegmentDigitDrawer {

    // Mapa de segmentos ativos (A, B, C, D, E, F, G) para cada dígito de 0 a 9
    // A: topo, B: superior direito, C: inferior direito, D: base,
    // E: inferior esquerdo, F: superior esquerdo, G: centro
    private val DIGIT_SEGMENTS: Array<BooleanArray> = arrayOf(
        booleanArrayOf(true, true, true, true, true, true, false),     // 0
        booleanArrayOf(false, true, true, false, false, false, false), // 1
        booleanArrayOf(true, true, false, true, true, false, true),    // 2
        booleanArrayOf(true, true, true, true, false, false, true),    // 3
        booleanArrayOf(false, true, true, false, false, true, true),   // 4
        booleanArrayOf(true, false, true, true, false, true, true),    // 5
        booleanArrayOf(true, false, true, true, true, true, true),     // 6
        booleanArrayOf(true, true, true, false, false, false, false),  // 7
        booleanArrayOf(true, true, true, true, true, true, true),      // 8
        booleanArrayOf(true, true, true, true, false, true, true)      // 9
    )

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val segmentPath = Path()

    /**
     * Desenha o texto principal (ex: "00:00" ou "00:00:00") e os centésimos menores (ex: "00")
     * centralizados dentro da área [width] x [height].
     */
    fun drawTimeOnCanvas(
        canvas: Canvas,
        width: Float,
        height: Float,
        mainDigits: String,
        hundredthsDigits: String?,
        textColorInt: Int
    ) {
        if (width <= 0f || height <= 0f) return

        activePaint.color = textColorInt
        ghostPaint.color = textColorInt
        ghostPaint.alpha = 32 // ~12.5% opacidade para segmentos apagados (efeito LCD/LED real)

        // Calcula unidades de largura proporcional:
        // Dígito principal = 1.0 unidade, ':' = 0.36 unidade, centésimo = 0.62 unidade
        var totalUnits = 0f
        mainDigits.forEach { ch ->
            totalUnits += if (ch == ':') 0.36f else 1.18f
        }
        if (!hundredthsDigits.isNullOrEmpty()) {
            totalUnits += 0.28f // Ponto separador
            hundredthsDigits.forEach { _ ->
                totalUnits += 0.74f
            }
        }

        val horizontalPadding = width * 0.08f
        val verticalPadding = height * 0.18f
        val availableWidth = (width - horizontalPadding * 2f).coerceAtLeast(10f)
        val availableHeight = (height - verticalPadding * 2f).coerceAtLeast(10f)

        val unitWidthByWidth = availableWidth / totalUnits
        val unitWidthByHeight = availableHeight * 0.54f
        val digitWidth = min(unitWidthByWidth, unitWidthByHeight)
        val digitHeight = digitWidth * 1.78f

        val actualTotalWidth = totalUnits * digitWidth
        var currentX = (width - actualTotalWidth) / 2f
        val mainTopY = (height - digitHeight) / 2f

        // Renderiza dígitos principais (HH:MM:SS ou MM:SS)
        for (ch in mainDigits) {
            if (ch == ':') {
                val colonWidth = digitWidth * 0.36f
                drawColon(canvas, currentX, mainTopY, colonWidth, digitHeight)
                currentX += colonWidth
            } else if (ch in '0'..'9') {
                drawDigit(
                    canvas = canvas,
                    digit = ch - '0',
                    left = currentX,
                    top = mainTopY,
                    width = digitWidth,
                    height = digitHeight
                )
                currentX += digitWidth * 1.18f
            }
        }

        // Renderiza centésimos proporcionalmente menores alinhados na base dos dígitos principais
        if (!hundredthsDigits.isNullOrEmpty()) {
            val dotWidth = digitWidth * 0.26f
            val dotRadius = (digitWidth * 0.09f).coerceAtLeast(1.5f)
            val baselineY = mainTopY + digitHeight
            canvas.drawCircle(
                currentX + dotWidth * 0.45f,
                baselineY - dotRadius,
                dotRadius,
                activePaint
            )
            currentX += dotWidth

            val smallWidth = digitWidth * 0.62f
            val smallHeight = digitHeight * 0.62f
            val smallTopY = baselineY - smallHeight

            for (ch in hundredthsDigits) {
                if (ch in '0'..'9') {
                    drawDigit(
                        canvas = canvas,
                        digit = ch - '0',
                        left = currentX,
                        top = smallTopY,
                        width = smallWidth,
                        height = smallHeight
                    )
                    currentX += digitWidth * 0.74f
                }
            }
        }
    }

    private fun drawColon(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val radius = (width * 0.24f).coerceAtLeast(2f)
        val cx = left + width * 0.42f
        canvas.drawCircle(cx, top + height * 0.33f, radius, activePaint)
        canvas.drawCircle(cx, top + height * 0.67f, radius, activePaint)
    }

    private fun drawDigit(
        canvas: Canvas,
        digit: Int,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val segments = DIGIT_SEGMENTS[digit.coerceIn(0, 9)]
        val thickness = (width * 0.18f).coerceAtLeast(2f)
        val gap = (thickness * 0.18f).coerceAtLeast(0.8f)
        val halfHeight = height / 2f

        // Segmento A (Topo horizontal)
        drawHorizontalSegment(
            canvas,
            left + gap,
            top,
            left + width - gap,
            thickness,
            if (segments[0]) activePaint else ghostPaint
        )
        // Segmento B (Superior direito vertical)
        drawVerticalSegment(
            canvas,
            left + width - thickness,
            top + gap,
            top + halfHeight - gap / 2f,
            thickness,
            if (segments[1]) activePaint else ghostPaint
        )
        // Segmento C (Inferior direito vertical)
        drawVerticalSegment(
            canvas,
            left + width - thickness,
            top + halfHeight + gap / 2f,
            top + height - gap,
            thickness,
            if (segments[2]) activePaint else ghostPaint
        )
        // Segmento D (Base horizontal)
        drawHorizontalSegment(
            canvas,
            left + gap,
            top + height - thickness,
            left + width - gap,
            thickness,
            if (segments[3]) activePaint else ghostPaint
        )
        // Segmento E (Inferior esquerdo vertical)
        drawVerticalSegment(
            canvas,
            left,
            top + halfHeight + gap / 2f,
            top + height - gap,
            thickness,
            if (segments[4]) activePaint else ghostPaint
        )
        // Segmento F (Superior esquerdo vertical)
        drawVerticalSegment(
            canvas,
            left,
            top + gap,
            top + halfHeight - gap / 2f,
            thickness,
            if (segments[5]) activePaint else ghostPaint
        )
        // Segmento G (Centro horizontal)
        drawHorizontalSegment(
            canvas,
            left + gap,
            top + halfHeight - thickness / 2f,
            left + width - gap,
            thickness,
            if (segments[6]) activePaint else ghostPaint
        )
    }

    private fun drawHorizontalSegment(
        canvas: Canvas,
        x1: Float,
        yTop: Float,
        x2: Float,
        thickness: Float,
        paint: Paint
    ) {
        val halfT = thickness / 2f
        val yMid = yTop + halfT
        val yBot = yTop + thickness
        segmentPath.reset()
        segmentPath.moveTo(x1 + halfT, yTop)
        segmentPath.lineTo(x2 - halfT, yTop)
        segmentPath.lineTo(x2, yMid)
        segmentPath.lineTo(x2 - halfT, yBot)
        segmentPath.lineTo(x1 + halfT, yBot)
        segmentPath.lineTo(x1, yMid)
        segmentPath.close()
        canvas.drawPath(segmentPath, paint)
    }

    private fun drawVerticalSegment(
        canvas: Canvas,
        xLeft: Float,
        y1: Float,
        y2: Float,
        thickness: Float,
        paint: Paint
    ) {
        val halfT = thickness / 2f
        val xMid = xLeft + halfT
        val xRight = xLeft + thickness
        segmentPath.reset()
        segmentPath.moveTo(xMid, y1)
        segmentPath.lineTo(xRight, y1 + halfT)
        segmentPath.lineTo(xRight, y2 - halfT)
        segmentPath.lineTo(xMid, y2)
        segmentPath.lineTo(xLeft, y2 - halfT)
        segmentPath.lineTo(xLeft, y1 + halfT)
        segmentPath.close()
        canvas.drawPath(segmentPath, paint)
    }
}
