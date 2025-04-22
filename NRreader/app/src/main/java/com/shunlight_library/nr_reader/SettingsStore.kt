package com.shunlight_library.nr_reader

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Create a DataStore instance at the top level
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * A class that handles saving and retrieving user preferences
 */
class SettingsStore(private val context: Context) {

    // Define preference keys
    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val FONT_SIZE = intPreferencesKey("font_size")
        val BACKGROUND_COLOR = stringPreferencesKey("background_color")
        val SELF_SERVER_ACCESS = booleanPreferencesKey("self_server_access")
        val TEXT_ORIENTATION = stringPreferencesKey("text_orientation")
        val SELF_SERVER_PATH_KEY = stringPreferencesKey("self_server_path")
    }

    // Default values
    val defaultThemeMode = "System"
    val defaultFontFamily = "Gothic"
    val defaultFontSize = 16
    val defaultBackgroundColor = "White"
    val defaultSelfServerAccess = false
    val defaultTextOrientation = "Horizontal"
    val defaultSelfServerPath = ""

    // Get the theme mode preference as a Flow
    val themeMode: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[THEME_MODE] ?: defaultThemeMode
        }

    // Get the font family preference as a Flow
    val fontFamily: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[FONT_FAMILY] ?: defaultFontFamily
        }

    // Get the font size preference as a Flow
    val fontSize: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[FONT_SIZE] ?: defaultFontSize
        }

    // Get the background color preference as a Flow
    val backgroundColor: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[BACKGROUND_COLOR] ?: defaultBackgroundColor
        }

    // Get the self server access preference as a Flow
    val selfServerAccess: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SELF_SERVER_ACCESS] ?: defaultSelfServerAccess
        }

    // Get the text orientation preference as a Flow
    val textOrientation: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[TEXT_ORIENTATION] ?: defaultTextOrientation
        }

    // Save the theme mode preference
    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    // Save the font family preference
    suspend fun saveFontFamily(family: String) {
        context.dataStore.edit { preferences ->
            preferences[FONT_FAMILY] = family
        }
    }

    // Save the font size preference
    suspend fun saveFontSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[FONT_SIZE] = size
        }
    }

    // Save the background color preference
    suspend fun saveBackgroundColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[BACKGROUND_COLOR] = color
        }
    }

    // Save the self server access preference
    suspend fun saveSelfServerAccess(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SELF_SERVER_ACCESS] = enabled
        }
    }

    // Save the text orientation preference
    suspend fun saveTextOrientation(orientation: String) {
        context.dataStore.edit { preferences ->
            preferences[TEXT_ORIENTATION] = orientation
        }
    }

    val selfServerPath = context.dataStore.data.map { preferences ->
        preferences[SELF_SERVER_PATH_KEY] ?: ""
    }

    suspend fun saveSelfServerPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[SELF_SERVER_PATH_KEY] = path
        }
    }
    // SettingsStore.kt に追加
// 永続的なアクセス権限を持つURIを取得
    fun getPersistedUriPermissions(): List<Uri> {
        return context.contentResolver.persistedUriPermissions.map { it.uri }
    }

    // 指定されたURIが永続的なアクセス権限を持っているか確認
    fun hasPersistedPermission(uriString: String): Boolean {
        if (uriString.isEmpty()) return false

        val targetUri = Uri.parse(uriString)
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == targetUri && it.isReadPermission
        }
    }

    // Helper function to save all settings at once
// これを次のように変更します：
    suspend fun saveAllSettings(
        themeMode: String,
        fontFamily: String,
        fontSize: Int,
        backgroundColor: String,
        selfServerAccess: Boolean,
        textOrientation: String,
        selfServerPath: String = "" // デフォルト値を追加
    ) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = themeMode
            preferences[FONT_FAMILY] = fontFamily
            preferences[FONT_SIZE] = fontSize
            preferences[BACKGROUND_COLOR] = backgroundColor
            preferences[SELF_SERVER_ACCESS] = selfServerAccess
            preferences[TEXT_ORIENTATION] = textOrientation
            preferences[SELF_SERVER_PATH_KEY] = selfServerPath // 新しいパラメータの保存
        }
    }
}