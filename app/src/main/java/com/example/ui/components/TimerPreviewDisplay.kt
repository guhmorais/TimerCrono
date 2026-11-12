package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.TimeFormatted
import com.example.domain.TimerConfig
import com.example.domain.TimerStatus
import com.example.overlay.SevenSegmentDigitDrawer
import com.example.ui.theme.ShareTechMonoFontFamily

/**
 * Componente de prévia visual fiel à janela flutuante real (WindowManager).
 * Renderiza o retângulo arredondado com a cor de fundo ativa, cor do texto,
 * escala proporcional, fonte de 7 segmentos ou fonte digital e centésimos menores.
 */
@Composable
fun FloatingWindowPreviewBadge(
    config: TimerConfig,
    formatted: TimeFormatted,
    modifier: Modifier = Modifier,
    overrideBgColor: Long? = null,
    respectScaleInPreview: Boolean = false
) {
    val effectiveScale = if (respectScaleInPreview) {
        config.scaleFactor.coerceIn(0.65f, 1.55f)
    } else {
        1.0f
    }

    val bgColor = Color((overrideBgColor ?: config.activeBackgroundColor).toInt())
    val txtColor = Color(config.textColor.toInt())
    val borderColor = if (config.status == TimerStatus.COMPLETED) {
        Color(0xFFFACC15)
    } else {
        Color.White.copy(alpha = 0.22f)
    }

    val cornerRadius = (14f * effectiveScale).dp
    val charCount = formatted.mainDigits.length + (formatted.hundredthsDigits?.length ?: 0)
    val boxWidth = ((charCount * 17f + 26f) * effectiveScale).dp
    val boxHeight = (42f * effectiveScale).dp

    Box(
        modifier = modifier
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(cornerRadius))
            .padding(
                horizontal = (12f * effectiveScale).dp,
                vertical = (6f * effectiveScale).dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (config.isCountdown) "⏳ ${config.name}" else config.name,
                color = txtColor.copy(alpha = 0.82f),
                fontSize = (9.5f * effectiveScale).sp,
                maxLines = 1
            )

            if (config.useSevenSegmentFont) {
                Canvas(
                    modifier = Modifier
                        .width(boxWidth)
                        .height(boxHeight)
                ) {
                    SevenSegmentDigitDrawer.drawTimeOnCanvas(
                        canvas = drawContext.canvas.nativeCanvas,
                        width = size.width,
                        height = size.height,
                        mainDigits = formatted.mainDigits,
                        hundredthsDigits = formatted.hundredthsDigits,
                        textColorInt = config.textColor.toInt()
                    )
                }
            } else {
                val annotatedTime = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontFamily = ShareTechMonoFontFamily,
                            fontSize = (26f * effectiveScale).sp,
                            fontWeight = FontWeight.Normal,
                            color = txtColor
                        )
                    ) {
                        append(formatted.mainDigits)
                    }
                    if (!formatted.hundredthsDigits.isNullOrEmpty()) {
                        withStyle(
                            SpanStyle(
                                fontFamily = ShareTechMonoFontFamily,
                                fontSize = (16f * effectiveScale).sp,
                                fontWeight = FontWeight.Normal,
                                color = txtColor.copy(alpha = 0.92f)
                            )
                        ) {
                            append(".")
                            append(formatted.hundredthsDigits)
                        }
                    }
                }
                Text(
                    text = annotatedTime,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ColorSwatchesSummaryRow(
    runningBgColor: Long,
    pausedBgColor: Long,
    textColor: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ColorSwatchPill(label = "Iniciar", colorLong = runningBgColor)
        ColorSwatchPill(label = "Pausar", colorLong = pausedBgColor)
        ColorSwatchPill(label = "Texto", colorLong = textColor)
    }
}

@Composable
private fun ColorSwatchPill(label: String, colorLong: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(colorLong.toInt()))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
