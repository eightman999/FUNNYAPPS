package com.shunlight_library.nr_reader.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.shunlight_library.nr_reader.database.AppDatabase
import com.shunlight_library.nr_reader.database.ExternalDatabaseHandler
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 外部データベースのコピーを管理するリポジトリ
 */
class ExternalDatabaseRepository(
    private val context: Context,
    private val appDatabase: AppDatabase,
    private val dbHandler: ExternalDatabaseHandler
) {
    private val TAG = "ExternalDBRepository"

    // 進行状況を追跡するための変数
    private val _processedCount = AtomicInteger(0)
    private var _totalCount = 0

    // 進行状況を取得するプロパティ
    val processedCount: Int get() = _processedCount.get()
    val totalCount: Int get() = _totalCount
    val progress: Float get() = if (_totalCount > 0) processedCount.toFloat() / _totalCount else 0f

    // 処理メッセージ
    private val _progressMessage = MutableStateFlow("")
    val progressMessage: Flow<String> = _progressMessage

    /**
     * 外部DBファイルを内部ストレージにコピーする
     */
    suspend fun synchronizeWithExternalDatabase(shouldCopyToInternal: Boolean, externalDbUri: Uri): Boolean {
        _progressMessage.value = "外部データベースへの接続を準備しています..."
        _processedCount.set(0)
        _totalCount = 0

        try {
            // データベースをコピー
            _progressMessage.value = "データベースを内部ストレージにコピーしています..."
            val copySuccess = dbHandler.copyExternalDatabaseToInternal(externalDbUri)
            if (!copySuccess) {
                _progressMessage.value = "データベースのコピーに失敗しました"
                return false
            }

            // コピーしたファイルの存在確認
            val dbFile = context.getDatabasePath(ExternalDatabaseHandler.INTERNAL_DB_NAME)
            if (!dbFile.exists()) {
                Log.e(TAG, "コピー後のデータベースファイルが見つかりません: ${dbFile.absolutePath}")
                _progressMessage.value = "データベースファイルの確認に失敗しました"
                return false
            }

            // ファイルサイズを確認
            if (dbFile.length() == 0L) {
                Log.e(TAG, "コピーされたデータベースファイルが空です: ${dbFile.absolutePath}")
                _progressMessage.value = "コピーされたデータベースファイルが空です"
                return false
            }

            // 成功メッセージ
            _progressMessage.value = "データベースのコピーが完了しました"
            Log.d(TAG, "データベースコピー完了: ${dbFile.absolutePath}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "データベースコピー中にエラーが発生しました", e)
            _progressMessage.value = "エラー: ${e.message}"
            return false
        }
    }
}