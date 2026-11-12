package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.DefaultTimerFactory
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Long,
    onDismiss: () -> Unit,
    onColorSelected: (Long) -> Unit
) {
    val initialArgb = initialColor.toInt()
    var red by remember { mutableIntStateOf((initialArgb shr 16) and 0xFF) }
    var green by remember { mutableIntStateOf((initialArgb shr 8) and 0xFF) }
    var blue by remember { mutableIntStateOf(initialArgb and 0xFF) }

    val composedColorLong: Long =
        (0xFF000000L) or ((red.toLong() and 0xFF) shl 16) or ((green.toLong() and 0xFF) shl 8) or (blue.toLong() and 0xFF)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Prévia da cor escolhida com código Hexadecimal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(composedColorLong.toInt()))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hexString = String.format(Locale.US, "#%02X%02X%02X", red, green, blue)
                    val luma = (0.299 * red + 0.587 * green + 0.114 * blue)
                    val contrastText = if (luma > 150) Color.Black else Color.White
                    Text(
                        text = "Cor selecionada",
                        color = contrastText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = hexString,
                        color = contrastText,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Cores predefinidas",
                    style = MaterialTheme.typography.labelLarge
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DefaultTimerFactory.presetPalette.forEach { (name, presetLong) ->
                        val isSelected = composedColorLong == presetLong
                        val presetInt = presetLong.toInt()
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(presetInt))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable {
                                    red = (presetInt shr 16) and 0xFF
                                    green = (presetInt shr 8) and 0xFF
                                    blue = presetInt and 0xFF
                                }
                                .testTag("preset_color_$name"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                val luma = (0.299 * ((presetInt shr 16) and 0xFF) +
                                    0.587 * ((presetInt shr 8) and 0xFF) +
                                    0.114 * (presetInt and 0xFF))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = name,
                                    tint = if (luma > 150) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ajuste personalizado (RGB)",
                    style = MaterialTheme.typography.labelLarge
                )

                RgbSliderRow("R (Vermelho)", red, Color(0xFFEF4444)) { red = it }
                RgbSliderRow("G (Verde)", green, Color(0xFF10B981)) { green = it }
                RgbSliderRow("B (Azul)", blue, Color(0xFF3B82F6)) { blue = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorSelected(composedColorLong) },
                modifier = Modifier.testTag("confirm_color_button")
            ) {
                Text("Aplicar cor")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun RgbSliderRow(
    label: String,
    value: Int,
    accent: Color,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = accent)
            Text(text = value.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DurationPickerDialog(
    initialDurationMs: Long,
    onDismiss: () -> Unit,
    onDurationConfirmed: (Long) -> Unit
) {
    val totalSec = (initialDurationMs / 1000L).coerceAtLeast(1L)
    var hours by remember { mutableIntStateOf((totalSec / 3600L).toInt().coerceIn(0, 23)) }
    var minutes by remember { mutableIntStateOf(((totalSec % 3600L) / 60L).toInt().coerceIn(0, 59)) }
    var seconds by remember { mutableIntStateOf((totalSec % 60L).toInt().coerceIn(0, 59)) }

    val quickPresets = listOf(
        "30s" to (0 to (0 to 30)),
        "1 min" to (0 to (1 to 0)),
        "3 min" to (0 to (3 to 0)),
        "5 min" to (0 to (5 to 0)),
        "10 min" to (0 to (10 to 0)),
        "15 min" to (0 to (15 to 0)),
        "30 min" to (0 to (30 to 0)),
        "1 hora" to (1 to (0 to 0))
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Configurar Intervalo Regressivo", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = String.format(Locale.US, "%02dh : %02dm : %02ds", hours, minutes, seconds),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }

                Text("Atalhos rápidos", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPresets.forEach { (label, hms) ->
                        val (h, ms) = hms
                        val (m, s) = ms
                        FilterChip(
                            selected = hours == h && minutes == m && seconds == s,
                            onClick = {
                                hours = h
                                minutes = m
                                seconds = s
                            },
                            label = { Text(label) }
                        )
                    }
                }

                TimeUnitSlider("Horas (0–23)", hours, 23) { hours = it }
                TimeUnitSlider("Minutos (0–59)", minutes, 59) { minutes = it }
                TimeUnitSlider("Segundos (0–59)", seconds, 59) { seconds = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val computedSec = (hours * 3600L + minutes * 60L + seconds).coerceAtLeast(1L)
                    onDurationConfirmed(computedSec * 1000L)
                },
                modifier = Modifier.testTag("confirm_duration_button")
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun TimeUnitSlider(
    label: String,
    value: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(String.format(Locale.US, "%02d", value), fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, maxValue)) },
            valueRange = 0f..maxValue.toFloat()
        )
    }
}

@Composable
fun TutorialDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Como usar o Floating Timer", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_banner_1794440143495),
                    contentDescription = "Ilustração de cronômetros flutuantes",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                TutorialStepItem(
                    badge = "1 Toque",
                    title = "Iniciar ou Pausar",
                    description = "Toque uma vez sobre qualquer janela flutuante para iniciar, pausar ou continuar a contagem instantaneamente."
                )
                TutorialStepItem(
                    badge = "2 Toques",
                    title = "Zerar o Cronômetro",
                    description = "Toque duas vezes rapidamente na janela flutuante para zerar o tempo (com confirmação opcional)."
                )
                TutorialStepItem(
                    badge = "Arrastar",
                    title = "Mover Livremente",
                    description = "Arraste a janela para qualquer posição da tela sobre outros apps. A posição de cada um dos 5 cronômetros é memorizada."
                )
                TutorialStepItem(
                    badge = "Segurar",
                    title = "Controles Adicionais",
                    description = "Mantenha pressionado para abrir os botões rápidos de bloqueio individual, zerar e fechar a janela."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("tutorial_got_it_button")
            ) {
                Text("Entendi")
            }
        }
    )
}

@Composable
private fun TutorialStepItem(badge: String, title: String, description: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 14.sp
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ResetConfirmationDialog(
    timerName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zerar $timerName?") },
        text = {
            Text("Deseja realmente zerar o tempo acumulado deste cronômetro?")
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_reset_dialog_button")
            ) {
                Text("Zerar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
