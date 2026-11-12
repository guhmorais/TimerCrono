package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.TimerConfig
import com.example.domain.TimerStatus
import com.example.timer.TimerCalculator
import com.example.ui.components.ColorPickerDialog
import com.example.ui.components.DurationPickerDialog
import com.example.ui.components.FloatingWindowPreviewBadge
import java.util.Locale
import kotlin.math.roundToInt

private enum class ColorTarget {
    RUNNING_BG,
    PAUSED_BG,
    TEXT_COLOR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTimerScreen(
    initialTimer: TimerConfig,
    onSave: (TimerConfig) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    var name by remember(initialTimer.id) { mutableStateOf(initialTimer.name) }
    var isCountdown by remember(initialTimer.id) { mutableStateOf(initialTimer.isCountdown) }
    var countdownDurationMs by remember(initialTimer.id) { mutableLongStateOf(initialTimer.countdownDurationMs) }
    var vibrateOnFinish by remember(initialTimer.id) { mutableStateOf(initialTimer.vibrateOnFinish) }
    var autoRepeat by remember(initialTimer.id) { mutableStateOf(initialTimer.autoRepeat) }

    var scaleFactor by remember(initialTimer.id) { mutableFloatStateOf(initialTimer.scaleFactor) }
    var runningBgColor by remember(initialTimer.id) { mutableLongStateOf(initialTimer.runningBgColor) }
    var pausedBgColor by remember(initialTimer.id) { mutableLongStateOf(initialTimer.pausedBgColor) }
    var textColor by remember(initialTimer.id) { mutableLongStateOf(initialTimer.textColor) }
    var showHundredths by remember(initialTimer.id) { mutableStateOf(initialTimer.showHundredths) }
    var showHours by remember(initialTimer.id) { mutableStateOf(initialTimer.showHours) }
    var useSevenSegmentFont by remember(initialTimer.id) { mutableStateOf(initialTimer.useSevenSegmentFont) }

    var activeColorTarget by remember { mutableStateOf<ColorTarget?>(null) }
    var showDurationDialog by remember { mutableStateOf(false) }

    val draftConfig = remember(
        name,
        isCountdown,
        countdownDurationMs,
        vibrateOnFinish,
        autoRepeat,
        scaleFactor,
        runningBgColor,
        pausedBgColor,
        textColor,
        showHundredths,
        showHours,
        useSevenSegmentFont
    ) {
        initialTimer.copy(
            name = name.ifBlank { initialTimer.name },
            isCountdown = isCountdown,
            countdownDurationMs = countdownDurationMs,
            vibrateOnFinish = vibrateOnFinish,
            autoRepeat = autoRepeat,
            scaleFactor = scaleFactor,
            runningBgColor = runningBgColor,
            pausedBgColor = pausedBgColor,
            textColor = textColor,
            showHundredths = showHundredths,
            showHours = showHours,
            useSevenSegmentFont = useSevenSegmentFont
        )
    }

    val sampleMillis = if (isCountdown) countdownDurationMs else 125_480L
    val sampleFormatted = remember(sampleMillis, showHours, showHundredths) {
        TimerCalculator.formatMillis(
            displayMillis = sampleMillis,
            showHours = showHours,
            showHundredths = showHundredths
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${stringResource(R.string.edit_timer_title)} #${initialTimer.id % 100}",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("edit_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            onSave(draftConfig)
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("save_timer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_save))
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card de Prévia ao Vivo (Exibe tanto a cor Ao Iniciar quanto Ao Pausar)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "PRÉVIA EM TEMPO REAL (${(scaleFactor * 100).roundToInt()}%)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        FloatingWindowPreviewBadge(
                            config = draftConfig.copy(status = TimerStatus.RUNNING),
                            formatted = sampleFormatted,
                            overrideBgColor = runningBgColor,
                            respectScaleInPreview = true
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Ao Pausar", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                FloatingWindowPreviewBadge(
                                    config = draftConfig.copy(status = TimerStatus.PAUSED),
                                    formatted = sampleFormatted,
                                    overrideBgColor = pausedBgColor,
                                    respectScaleInPreview = false
                                )
                            }
                        }
                    }
                }

                // Seção 1: Identificação
                SectionCard(title = stringResource(R.string.section_identification)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(28) },
                        label = { Text(stringResource(R.string.label_timer_name)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_name_input")
                    )
                }

                // Seção 2: Contagem
                SectionCard(title = stringResource(R.string.section_counting)) {
                    SwitchSettingRow(
                        title = stringResource(R.string.label_enable_countdown),
                        subtitle = "Conta de forma regressiva até 00:00",
                        checked = isCountdown,
                        enabled = true,
                        testTag = "edit_countdown_switch",
                        onCheckedChange = { isCountdown = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    val countdownOptionsAlpha = if (isCountdown) 1f else 0.4f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(countdownOptionsAlpha)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = isCountdown) { showDurationDialog = true }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.label_configure_interval),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Horas, minutos e segundos da contagem regressiva",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = { showDurationDialog = true },
                            enabled = isCountdown,
                            modifier = Modifier.testTag("edit_interval_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val totalS = countdownDurationMs / 1000L
                            val h = totalS / 3600L
                            val m = (totalS % 3600L) / 60L
                            val s = totalS % 60L
                            Text(String.format(Locale.US, "%02d:%02d:%02d", h, m, s))
                        }
                    }

                    SwitchSettingRow(
                        title = stringResource(R.string.label_vibrate_on_finish),
                        subtitle = "Aciona vibração quando a contagem chega a zero",
                        checked = vibrateOnFinish,
                        enabled = isCountdown,
                        testTag = "edit_vibrate_switch",
                        onCheckedChange = { vibrateOnFinish = it }
                    )

                    SwitchSettingRow(
                        title = stringResource(R.string.label_auto_repeat),
                        subtitle = "Reinicia o ciclo automaticamente preservando a precisão",
                        checked = autoRepeat,
                        enabled = isCountdown,
                        testTag = "edit_autorepeat_switch",
                        onCheckedChange = { autoRepeat = it }
                    )
                }

                // Seção 3: Aparência
                SectionCard(title = stringResource(R.string.section_appearance)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.label_window_size),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${(scaleFactor * 100).roundToInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = scaleFactor,
                            onValueChange = { scaleFactor = it },
                            valueRange = 0.6f..2.0f,
                            modifier = Modifier.testTag("edit_scale_slider")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    ColorPickerRow(
                        label = stringResource(R.string.label_color_running),
                        colorLong = runningBgColor,
                        testTag = "pick_running_color_row",
                        onClick = { activeColorTarget = ColorTarget.RUNNING_BG }
                    )

                    ColorPickerRow(
                        label = stringResource(R.string.label_color_paused),
                        colorLong = pausedBgColor,
                        testTag = "pick_paused_color_row",
                        onClick = { activeColorTarget = ColorTarget.PAUSED_BG }
                    )

                    ColorPickerRow(
                        label = stringResource(R.string.label_color_text),
                        colorLong = textColor,
                        testTag = "pick_text_color_row",
                        onClick = { activeColorTarget = ColorTarget.TEXT_COLOR }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SwitchSettingRow(
                        title = stringResource(R.string.label_show_hundredths),
                        subtitle = "Exibe os centésimos (.00) proporcionalmente menores",
                        checked = showHundredths,
                        enabled = true,
                        testTag = "edit_hundredths_switch",
                        onCheckedChange = { showHundredths = it }
                    )

                    SwitchSettingRow(
                        title = stringResource(R.string.label_show_hours),
                        subtitle = "Exibe o formato completo HH:MM:SS mesmo abaixo de 1 hora",
                        checked = showHours,
                        enabled = true,
                        testTag = "edit_hours_switch",
                        onCheckedChange = { showHours = it }
                    )

                    SwitchSettingRow(
                        title = stringResource(R.string.label_use_seven_segment),
                        subtitle = "Renderiza dígitos clássicos em display de 7 segmentos LED",
                        checked = useSevenSegmentFont,
                        enabled = true,
                        testTag = "edit_seven_segment_switch",
                        onCheckedChange = { useSevenSegmentFont = it }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    activeColorTarget?.let { target ->
        val (dialogTitle, initialColor) = when (target) {
            ColorTarget.RUNNING_BG -> stringResource(R.string.label_color_running) to runningBgColor
            ColorTarget.PAUSED_BG -> stringResource(R.string.label_color_paused) to pausedBgColor
            ColorTarget.TEXT_COLOR -> stringResource(R.string.label_color_text) to textColor
        }
        ColorPickerDialog(
            title = dialogTitle,
            initialColor = initialColor,
            onDismiss = { activeColorTarget = null },
            onColorSelected = { chosen ->
                when (target) {
                    ColorTarget.RUNNING_BG -> runningBgColor = chosen
                    ColorTarget.PAUSED_BG -> pausedBgColor = chosen
                    ColorTarget.TEXT_COLOR -> textColor = chosen
                }
                activeColorTarget = null
            }
        )
    }

    if (showDurationDialog) {
        DurationPickerDialog(
            initialDurationMs = countdownDurationMs,
            onDismiss = { showDurationDialog = false },
            onDurationConfirmed = { newDurationMs ->
                countdownDurationMs = newDurationMs
                showDurationDialog = false
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun ColorPickerRow(
    label: String,
    colorLong: Long,
    testTag: String,
    onClick: () -> Unit
) {
    val argb = colorLong.toInt()
    val hex = String.format(Locale.US, "#%06X", 0xFFFFFF and argb)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = hex,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
    }
}
