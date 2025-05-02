package com.shunlight_library.nr_reader

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

/**
 * ユーザー設定の保存と取得を扱うクラス
 */
class SettingsStore(private val context: Context) {

    // 設定キーの定義
    companion object {
        // 既存の設定キー
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val FONT_SIZE = intPreferencesKey("font_size")
        val BACKGROUND_COLOR = stringPreferencesKey("background_color")
        val SELF_SERVER_ACCESS = booleanPreferencesKey("self_server_access")
        val TEXT_ORIENTATION = stringPreferencesKey("text_orientation")
        val SELF_SERVER_PATH_KEY = stringPreferencesKey("self_server_path")

    }

    // デフォルト値の定義
    val defaultThemeMode = "System"
    val defaultFontFamily = "Gothic"
    val defaultFontSize = 16
    val defaultBackgroundColor = "White"
    val defaultSelfServerAccess = false
    val defaultTextOrientation = "Horizontal"
    val defaultSelfServerPath = ""

    // テーマモード設定をFlowとして取得
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

    // フォントファミリー設定をFlowとして取得
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

    // フォントサイズ設定をFlowとして取得
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

    // 背景色設定をFlowとして取得
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

    // 自己サーバーアクセス設定をFlowとして取得
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

    // テキスト表示方向設定をFlowとして取得
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



    // テーマモード設定の保存
    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    // フォントファミリー設定の保存
    suspend fun saveFontFamily(family: String) {
        context.dataStore.edit { preferences ->
            preferences[FONT_FAMILY] = family
        }
    }

    // フォントサイズ設定の保存
    suspend fun saveFontSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[FONT_SIZE] = size
        }
    }

    // 背景色設定の保存
    suspend fun saveBackgroundColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[BACKGROUND_COLOR] = color
        }
    }

    // 自己サーバーアクセス設定の保存
    suspend fun saveSelfServerAccess(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SELF_SERVER_ACCESS] = enabled
        }
    }

    // テキスト表示方向設定の保存
    suspend fun saveTextOrientation(orientation: String) {
        context.dataStore.edit { preferences ->
            preferences[TEXT_ORIENTATION] = orientation
        }
    }

    // 自己サーバーパス設定を取得
    val selfServerPath = context.dataStore.data.map { preferences ->
        preferences[SELF_SERVER_PATH_KEY] ?: ""
    }

    // 自己サーバーパス設定の保存
    suspend fun saveSelfServerPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[SELF_SERVER_PATH_KEY] = path
        }
    }




    // 永続的なアクセス権限を持つURIを取得
    fun getPersistedUriPermissions(): List<Uri> {
        return context.contentResolver.persistedUriPermissions.map { it.uri }
    }

    // 自己サーバーアクセス設定の確認
    suspend fun isSelfServerAccessEnabled(): Boolean {
        return selfServerAccess.first()
    }

    // 自己サーバーパスの確認
    suspend fun getSelfServerPath(): String {
        return selfServerPath.first()
    }

    // 指定されたURIが永続的なアクセス権限を持っているか確認
    fun hasPersistedPermission(uriString: String): Boolean {
        if (uriString.isEmpty()) return false

        try {
            // 権限チェックログ
            Log.d("SettingsStore", "URIの権限をチェック: $uriString")

            // "file://"スキームの場合は常にtrueを返す（通常のファイルシステムアクセス）
            if (uriString.startsWith("file://")) {
                Log.d("SettingsStore", "fileスキームなので権限はあります")
                return true
            }

            val targetUri = Uri.parse(uriString)
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == targetUri && it.isReadPermission
            }

            Log.d("SettingsStore", "権限チェック結果: $hasPermission")
            return hasPermission

        } catch (e: Exception) {
            Log.e("SettingsStore", "権限確認エラー: ${e.message}", e)
            return false
        }
    }

    // 永続的な権限を持つURIが有効期限切れでないか確認
    fun validatePersistedPermissions() {
        val invalidPermissions = mutableListOf<Uri>()

        // 現在保持している永続的権限のリストを取得
        val permissions = context.contentResolver.persistedUriPermissions

        for (permission in permissions) {
            try {
                // URIが有効かチェック（ファイルが存在するか確認）
                val uri = permission.uri
                val canAccess = try {
                    context.contentResolver.getType(uri) != null
                } catch (e: Exception) {
                    false
                }

                if (!canAccess) {
                    invalidPermissions.add(uri)
                    Log.d("SettingsStore", "無効な権限を検出: $uri")
                }
            } catch (e: Exception) {
                Log.e("SettingsStore", "権限検証エラー: ${e.message}", e)
            }
        }

        // 無効な権限があれば記録
        if (invalidPermissions.isNotEmpty()) {
            Log.d("SettingsStore", "${invalidPermissions.size}個の無効な権限が見つかりました")
        }
    }

    // 設定を一括で保存するメソッド
    suspend fun saveAllSettings(
        themeMode: String,
        fontFamily: String,
        fontSize: Int,
        backgroundColor: String,
        selfServerAccess: Boolean,
        textOrientation: String,
        selfServerPath: String = ""
    ) {
        try {
            context.dataStore.edit { preferences ->
                preferences[THEME_MODE] = themeMode
                preferences[FONT_FAMILY] = fontFamily
                preferences[FONT_SIZE] = fontSize
                preferences[BACKGROUND_COLOR] = backgroundColor
                preferences[SELF_SERVER_ACCESS] = selfServerAccess
                preferences[TEXT_ORIENTATION] = textOrientation
                preferences[SELF_SERVER_PATH_KEY] = selfServerPath

                // 保存ログ
                Log.d("SettingsStore", "設定を保存しました: selfServerAccess=$selfServerAccess, selfServerPath=$selfServerPath")
            }
        } catch (e: Exception) {
            Log.e("SettingsStore", "設定保存エラー: ${e.message}", e)
            throw e  // エラーを上位に伝播させる
        }
    }
}