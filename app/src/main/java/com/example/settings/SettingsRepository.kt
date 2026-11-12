package com.example.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "floating_timer_settings")

data class AppSettings(
    val lockPosition: Boolean = false,
    val keepScreenOn: Boolean = false,
    val showIntroOnFirstActivation: Boolean = true,
    val hasShownIntro: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val confirmBeforeReset: Boolean = false,
    val autoStartOnActivate: Boolean = false,
    val longPressOpensControls: Boolean = true
)

interface SettingsDataSource {
    val settingsFlow: Flow<AppSettings>
    suspend fun setLockPosition(locked: Boolean)
    suspend fun setKeepScreenOn(keepOn: Boolean)
    suspend fun setShowIntroOnFirstActivation(show: Boolean)
    suspend fun setHasShownIntro(shown: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setConfirmBeforeReset(confirm: Boolean)
    suspend fun setAutoStartOnActivate(autoStart: Boolean)
    suspend fun setLongPressOpensControls(enabled: Boolean)
    suspend fun restoreDefaults()
}

class SettingsRepository(private val context: Context) : SettingsDataSource {

    private object Keys {
        val LOCK_POSITION = booleanPreferencesKey("lock_position")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SHOW_INTRO = booleanPreferencesKey("show_intro_on_first_activation")
        val HAS_SHOWN_INTRO = booleanPreferencesKey("has_shown_intro")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CONFIRM_BEFORE_RESET = booleanPreferencesKey("confirm_before_reset")
        val AUTO_START_ON_ACTIVATE = booleanPreferencesKey("auto_start_on_activate")
        val LONG_PRESS_CONTROLS = booleanPreferencesKey("long_press_controls")
    }

    override val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        AppSettings(
            lockPosition = prefs[Keys.LOCK_POSITION] ?: false,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: false,
            showIntroOnFirstActivation = prefs[Keys.SHOW_INTRO] ?: true,
            hasShownIntro = prefs[Keys.HAS_SHOWN_INTRO] ?: false,
            themeMode = themeMode,
            confirmBeforeReset = prefs[Keys.CONFIRM_BEFORE_RESET] ?: false,
            autoStartOnActivate = prefs[Keys.AUTO_START_ON_ACTIVATE] ?: false,
            longPressOpensControls = prefs[Keys.LONG_PRESS_CONTROLS] ?: true
        )
    }

    override suspend fun setLockPosition(locked: Boolean) {
        context.dataStore.edit { it[Keys.LOCK_POSITION] = locked }
    }

    override suspend fun setKeepScreenOn(keepOn: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = keepOn }
    }

    override suspend fun setShowIntroOnFirstActivation(show: Boolean) {
        context.dataStore.edit {
            it[Keys.SHOW_INTRO] = show
            if (show) {
                it[Keys.HAS_SHOWN_INTRO] = false
            }
        }
    }

    override suspend fun setHasShownIntro(shown: Boolean) {
        context.dataStore.edit { it[Keys.HAS_SHOWN_INTRO] = shown }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setConfirmBeforeReset(confirm: Boolean) {
        context.dataStore.edit { it[Keys.CONFIRM_BEFORE_RESET] = confirm }
    }

    override suspend fun setAutoStartOnActivate(autoStart: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_START_ON_ACTIVATE] = autoStart }
    }

    override suspend fun setLongPressOpensControls(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LONG_PRESS_CONTROLS] = enabled }
    }

    override suspend fun restoreDefaults() {
        context.dataStore.edit { it.clear() }
    }
}
