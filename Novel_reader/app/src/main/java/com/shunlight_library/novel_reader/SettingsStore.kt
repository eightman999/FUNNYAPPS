package com.shunlight_library.novel_reader

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// DataStoreのインスタンスをトップレベルで定義
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val FONT_SIZE = intPreferencesKey("font_size")
        val BACKGROUND_COLOR = stringPreferencesKey("background_color")
        val SELF_SERVER_ACCESS = booleanPreferencesKey("self_server_access")
        val TEXT_ORIENTATION = stringPreferencesKey("text_orientation")
        val SELF_SERVER_PATH_KEY = stringPreferencesKey("self_server_path")
    }

    val defaultThemeMode = "System"
    val defaultFontFamily = "Gothic"
    val defaultFontSize = 16
    val defaultBackgroundColor = "White"
    val defaultSelfServerAccess = false
    val defaultTextOrientation = "Horizontal"
    val defaultSelfServerPath = ""

    val themeMode: Flow<String> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[THEME_MODE] ?: defaultThemeMode
        }

    val fontFamily: Flow<String> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[FONT_FAMILY] ?: defaultFontFamily
        }

    val fontSize: Flow<Int> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[FONT_SIZE] ?: defaultFontSize
        }

    val backgroundColor: Flow<String> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[BACKGROUND_COLOR] ?: defaultBackgroundColor
        }

    val selfServerAccess: Flow<Boolean> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[SELF_SERVER_ACCESS] ?: defaultSelfServerAccess
        }

    val textOrientation: Flow<String> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[TEXT_ORIENTATION] ?: defaultTextOrientation
        }

    val selfServerPath = context.dataStore.data.map { preferences: Preferences ->
        preferences[SELF_SERVER_PATH_KEY] ?: ""
    }

    // すべての設定を保存するためのメソッド
    suspend fun saveAllSettings(
        themeMode: String,
        fontFamily: String,
        fontSize: Int,
        backgroundColor: String,
        selfServerAccess: Boolean,
        textOrientation: String,
        selfServerPath: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = themeMode
            preferences[FONT_FAMILY] = fontFamily
            preferences[FONT_SIZE] = fontSize
            preferences[BACKGROUND_COLOR] = backgroundColor
            preferences[SELF_SERVER_ACCESS] = selfServerAccess
            preferences[TEXT_ORIENTATION] = textOrientation
            preferences[SELF_SERVER_PATH_KEY] = selfServerPath
        }
    }

    // 個別の設定を保存するメソッド（既存のメソッド）
    suspend fun saveSelfServerPath(path: String) {
        context.dataStore.edit { preferences: MutablePreferences ->
            preferences[SELF_SERVER_PATH_KEY] = path
        }
    }
}