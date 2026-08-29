package com.folio.viewer.data.repo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.folio.viewer.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "folio_prefs")

class PreferencesRepository(private val context: Context) {
    private val THEME = stringPreferencesKey("theme_mode")
    private val READER = booleanPreferencesKey("reader_mode")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { p: Preferences ->
        when (p[THEME]) {
            "light" -> ThemeMode.Light
            "dark" -> ThemeMode.Dark
            else -> ThemeMode.System
        }
    }

    val readerMode: Flow<Boolean> = context.dataStore.data.map { it[READER] ?: false }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { p ->
            p[THEME] = when (mode) {
                ThemeMode.System -> "system"
                ThemeMode.Light -> "light"
                ThemeMode.Dark -> "dark"
            }
        }
    }

    suspend fun setReaderMode(enabled: Boolean) {
        context.dataStore.edit { p -> p[READER] = enabled }
    }
}
