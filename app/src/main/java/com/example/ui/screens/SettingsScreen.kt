package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.ThemeMode
import com.example.settings.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onLockPositionChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onConfirmBeforeResetChange: (Boolean) -> Unit,
    onLongPressControlsChange: (Boolean) -> Unit,
    onShowIntroChange: (Boolean) -> Unit,
    onOpenTutorialNow: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onRestoreDefaults: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
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
                // Card 1: Janelas Flutuantes e Energia
                SettingsGroupCard(title = "Janelas Flutuantes e Energia") {
                    PreferenceSwitchItem(
                        title = stringResource(R.string.pref_lock_position),
                        subtitle = stringResource(R.string.pref_lock_position_desc),
                        checked = settings.lockPosition,
                        testTag = "pref_lock_position_switch",
                        onCheckedChange = onLockPositionChange
                    )

                    HorizontalDivider()

                    PreferenceSwitchItem(
                        title = stringResource(R.string.pref_keep_screen_on),
                        subtitle = stringResource(R.string.pref_keep_screen_on_desc),
                        checked = settings.keepScreenOn,
                        testTag = "pref_keep_screen_on_switch",
                        onCheckedChange = onKeepScreenOnChange
                    )

                    HorizontalDivider()

                    PreferenceSwitchItem(
                        title = stringResource(R.string.pref_auto_start),
                        subtitle = stringResource(R.string.pref_auto_start_desc),
                        checked = settings.autoStartOnActivate,
                        testTag = "pref_auto_start_switch",
                        onCheckedChange = onAutoStartChange
                    )
                }

                // Card 2: Gestos e Confirmação
                SettingsGroupCard(title = "Gestos e Interação") {
                    PreferenceSwitchItem(
                        title = stringResource(R.string.pref_confirm_reset),
                        subtitle = stringResource(R.string.pref_confirm_reset_desc),
                        checked = settings.confirmBeforeReset,
                        testTag = "pref_confirm_reset_switch",
                        onCheckedChange = onConfirmBeforeResetChange
                    )

                    HorizontalDivider()

                    PreferenceSwitchItem(
                        title = stringResource(R.string.pref_long_press_controls),
                        subtitle = stringResource(R.string.pref_long_press_controls_desc),
                        checked = settings.longPressOpensControls,
                        testTag = "pref_long_press_switch",
                        onCheckedChange = onLongPressControlsChange
                    )

                    HorizontalDivider()

                    PreferenceSwitchItem(
                        title = stringResource(R.string.pref_show_intro),
                        subtitle = stringResource(R.string.pref_show_intro_desc),
                        checked = settings.showIntroOnFirstActivation,
                        testTag = "pref_show_intro_switch",
                        onCheckedChange = onShowIntroChange
                    )

                    OutlinedButton(
                        onClick = onOpenTutorialNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_tutorial_from_settings_button")
                    ) {
                        Icon(imageVector = Icons.Default.HelpOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exibir tutorial de gestos agora")
                    }
                }

                // Card 3: Aparência e Tema
                SettingsGroupCard(title = stringResource(R.string.pref_theme)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                            label = { Text(stringResource(R.string.theme_system)) },
                            modifier = Modifier.testTag("theme_chip_system")
                        )
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                            label = { Text(stringResource(R.string.theme_light)) },
                            modifier = Modifier.testTag("theme_chip_light")
                        )
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChange(ThemeMode.DARK) },
                            label = { Text(stringResource(R.string.theme_dark)) },
                            modifier = Modifier.testTag("theme_chip_dark")
                        )
                    }
                }

                // Card 4: Restauração e Informações do Aplicativo
                SettingsGroupCard(title = stringResource(R.string.pref_about)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Floating Timer v1.0",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Motor de alta precisão baseado em SystemClock.elapsedRealtime() • WindowManager Overlay • Sem anúncios ou rastreadores.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showRestoreConfirmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_defaults_button")
                    ) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.pref_restore_defaults))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = { Text(stringResource(R.string.pref_restore_defaults)) },
            text = {
                Text("Isso restaurará os 5 cronômetros flutuantes, os 5 temporizadores de notificação e todas as preferências para os valores iniciais.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreDefaults()
                        showRestoreConfirmDialog = false
                    },
                    modifier = Modifier.testTag("confirm_restore_defaults_button")
                ) {
                    Text(stringResource(R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsGroupCard(
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
private fun PreferenceSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.testTag(testTag)
        )
    }
}
