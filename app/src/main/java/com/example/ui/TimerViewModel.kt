package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.FloatingTimerApplication
import com.example.domain.ThemeMode
import com.example.domain.TimeFormatted
import com.example.domain.TimerCategory
import com.example.domain.TimerConfig
import com.example.domain.TimerStatus
import com.example.service.FloatingTimerService
import com.example.settings.AppSettings
import com.example.settings.SettingsDataSource
import com.example.timer.TimerEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerViewModel(
    private val appContext: Context,
    val timerEngine: TimerEngine,
    private val settingsRepository: SettingsDataSource
) : ViewModel() {

    val timers: StateFlow<List<TimerConfig>> = timerEngine.timersState

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    private val _selectedTab = MutableStateFlow(TimerCategory.FLOATING)
    val selectedTab: StateFlow<TimerCategory> = _selectedTab.asStateFlow()

    private val _showTutorialDialog = MutableStateFlow(false)
    val showTutorialDialog: StateFlow<Boolean> = _showTutorialDialog.asStateFlow()

    // Pulso de atualização visual apenas quando há cronômetros em execução
    private val _uiTickMillis = MutableStateFlow(0L)
    val uiTickMillis: StateFlow<Long> = _uiTickMillis.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                val list = timers.value
                val anyRunning = list.any { it.status == TimerStatus.RUNNING }
                if (anyRunning) {
                    timerEngine.checkCountdownsAndEmitEvents()
                    _uiTickMillis.value = timerEngine.clockProvider.elapsedRealtime()
                    delay(33L)
                } else {
                    _uiTickMillis.value = timerEngine.clockProvider.elapsedRealtime()
                    delay(250L)
                }
            }
        }
    }

    fun selectTab(category: TimerCategory) {
        _selectedTab.value = category
    }

    fun openTutorial() {
        _showTutorialDialog.value = true
    }

    fun dismissTutorial() {
        _showTutorialDialog.value = false
        viewModelScope.launch {
            settingsRepository.setHasShownIntro(true)
        }
    }

    fun toggleTimerEnabled(timer: TimerConfig, enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = settings.value
            if (enabled && currentSettings.showIntroOnFirstActivation && !currentSettings.hasShownIntro) {
                _showTutorialDialog.value = true
                settingsRepository.setHasShownIntro(true)
            }
            timerEngine.setTimerEnabled(
                timerId = timer.id,
                enabled = enabled,
                autoStart = currentSettings.autoStartOnActivate
            )
            FloatingTimerService.ensureServiceRunningIfNeeded(appContext, timerEngine.timersState.value)
        }
    }

    fun toggleStartPause(timerId: Int) {
        viewModelScope.launch {
            timerEngine.toggleStartPause(timerId)
            FloatingTimerService.ensureServiceRunningIfNeeded(appContext, timerEngine.timersState.value)
        }
    }

    fun resetTimer(timerId: Int) {
        viewModelScope.launch {
            timerEngine.reset(timerId)
        }
    }

    fun updateWindowPosition(timerId: Int, x: Int, y: Int) {
        viewModelScope.launch {
            timerEngine.updateWindowPosition(timerId, x, y)
        }
    }

    fun saveEditedTimer(updatedConfig: TimerConfig) {
        viewModelScope.launch {
            timerEngine.updateTimerConfiguration(updatedConfig)
            FloatingTimerService.ensureServiceRunningIfNeeded(appContext, timerEngine.timersState.value)
        }
    }

    fun pauseAllInCurrentTab() {
        viewModelScope.launch {
            timerEngine.pauseAll(_selectedTab.value)
        }
    }

    fun resetAllInCurrentTab() {
        viewModelScope.launch {
            timerEngine.resetAll(_selectedTab.value)
        }
    }

    fun disableAllOverlays() {
        viewModelScope.launch {
            timerEngine.disableAll()
        }
    }

    fun setLockPosition(locked: Boolean) {
        viewModelScope.launch { settingsRepository.setLockPosition(locked) }
    }

    fun setKeepScreenOn(keepOn: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenOn(keepOn) }
    }

    fun setShowIntroOnFirstActivation(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowIntroOnFirstActivation(show) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setConfirmBeforeReset(confirm: Boolean) {
        viewModelScope.launch { settingsRepository.setConfirmBeforeReset(confirm) }
    }

    fun setAutoStartOnActivate(autoStart: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoStartOnActivate(autoStart) }
    }

    fun setLongPressOpensControls(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLongPressOpensControls(enabled) }
    }

    fun restoreAllDefaults() {
        viewModelScope.launch {
            settingsRepository.restoreDefaults()
            timerEngine.restoreAllDefaults()
        }
    }

    fun formatTimerNow(timer: TimerConfig, @Suppress("UNUSED_PARAMETER") tickAnchor: Long): TimeFormatted {
        return timerEngine.getFormattedTime(timer)
    }

    companion object {
        fun provideFactory(application: FloatingTimerApplication): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TimerViewModel(
                        appContext = application.applicationContext,
                        timerEngine = application.timerEngine,
                        settingsRepository = application.settingsRepository
                    ) as T
                }
            }
        }
    }
}
