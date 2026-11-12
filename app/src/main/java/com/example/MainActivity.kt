package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.FloatingTimerService
import com.example.ui.TimerViewModel
import com.example.ui.components.TutorialDialog
import com.example.ui.screens.EditTimerScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.FloatingTimerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TimerViewModel by viewModels {
        TimerViewModel.provideFactory(application as FloatingTimerApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val timers by viewModel.timers.collectAsStateWithLifecycle()
            val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
            val uiTickMillis by viewModel.uiTickMillis.collectAsStateWithLifecycle()
            val showTutorial by viewModel.showTutorialDialog.collectAsStateWithLifecycle()

            // Aplica ou remove FLAG_KEEP_SCREEN_ON na janela da Activity conforme preferência
            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // Garante que o serviço coordenador esteja ativo se houver algum cronômetro habilitado
            LaunchedEffect(timers) {
                FloatingTimerService.ensureServiceRunningIfNeeded(this@MainActivity, timers)
            }

            FloatingTimerTheme(themeMode = settings.themeMode) {
                FloatingTimerAppContent(
                    viewModel = viewModel,
                    timers = timers,
                    settings = settings,
                    selectedTab = selectedTab,
                    uiTickMillis = uiTickMillis,
                    showTutorial = showTutorial
                )
            }
        }
    }
}

@Composable
fun FloatingTimerAppContent(
    viewModel: TimerViewModel,
    timers: List<com.example.domain.TimerConfig>,
    settings: com.example.settings.AppSettings,
    selectedTab: com.example.domain.TimerCategory,
    uiTickMillis: Long,
    showTutorial: Boolean
) {
    var currentScreen by rememberSaveable { mutableStateOf("main") }
    var editingTimerId by rememberSaveable { mutableStateOf<Int?>(null) }

    when (currentScreen) {
        "edit" -> {
            val targetTimer = timers.firstOrNull { it.id == editingTimerId }
            if (targetTimer != null) {
                EditTimerScreen(
                    initialTimer = targetTimer,
                    onSave = { updated -> viewModel.saveEditedTimer(updated) },
                    onNavigateBack = { currentScreen = "main" }
                )
            } else {
                currentScreen = "main"
            }
        }

        "settings" -> {
            SettingsScreen(
                settings = settings,
                onLockPositionChange = viewModel::setLockPosition,
                onKeepScreenOnChange = viewModel::setKeepScreenOn,
                onAutoStartChange = viewModel::setAutoStartOnActivate,
                onConfirmBeforeResetChange = viewModel::setConfirmBeforeReset,
                onLongPressControlsChange = viewModel::setLongPressOpensControls,
                onShowIntroChange = viewModel::setShowIntroOnFirstActivation,
                onOpenTutorialNow = viewModel::openTutorial,
                onThemeModeChange = viewModel::setThemeMode,
                onRestoreDefaults = viewModel::restoreAllDefaults,
                onNavigateBack = { currentScreen = "main" }
            )
        }

        else -> {
            MainScreen(
                timers = timers,
                settings = settings,
                selectedTab = selectedTab,
                uiTickMillis = uiTickMillis,
                formatTimer = viewModel::formatTimerNow,
                onSelectTab = viewModel::selectTab,
                onToggleEnabled = viewModel::toggleTimerEnabled,
                onToggleStartPause = viewModel::toggleStartPause,
                onResetTimer = viewModel::resetTimer,
                onUpdatePosition = viewModel::updateWindowPosition,
                onEditTimer = { id ->
                    editingTimerId = id
                    currentScreen = "edit"
                },
                onOpenSettings = { currentScreen = "settings" },
                onOpenTutorial = viewModel::openTutorial,
                onPauseAll = viewModel::pauseAllInCurrentTab,
                onResetAll = viewModel::resetAllInCurrentTab,
                onDisableAll = viewModel::disableAllOverlays,
                onRestoreDefaults = viewModel::restoreAllDefaults
            )
        }
    }

    if (showTutorial) {
        TutorialDialog(onDismiss = viewModel::dismissTutorial)
    }
}
