package com.shunlight_library.nr_reader.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.shunlight_library.nr_reader.database.AppDatabase
import com.shunlight_library.nr_reader.database.ExternalDatabaseHandler
import com.shunlight_library.nr_reader.database.ExternalNovel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class ExternalDatabaseRepository(
    private val context: Context,
    private val appDatabase: AppDatabase,
    private val dbHandler: ExternalDatabaseHandler
) {
    private val TAG = "ExternalDBRepository"

    private val _processedCount = AtomicInteger(0)
    private var _totalCount = 0

    val processedCount: Int get() = _processedCount.get()
    val totalCount: Int get() = _totalCount
    val progress: Float get() = if (_totalCount > 0) processedCount.toFloat() / _totalCount else 0f

    private val _progressMessage = MutableStateFlow("")
    val progressMessage: Flow<String> = _progressMessage

    suspend fun synchronizeWithExternalDatabase(shouldCopyToInternal: Boolean, externalDbUri: Uri): Boolean {
        _progressMessage.value = "外部データベースへの接続を準備しています..."
        _processedCount.set(0)
        _totalCount = 0

        try {
            _progressMessage.value = "データベースを内部ストレージにコピーしています..."
            val copySuccess = dbHandler.copyExternalDatabaseToInternal(externalDbUri)
            if (!copySuccess) {
                _progressMessage.value = "データベースのコピーに失敗しました"
                return false
            }

            val dbFile = context.getDatabasePath(ExternalDatabaseHandler.INTERNAL_DB_NAME)
            if (!dbFile.exists() || dbFile.length() == 0L) {
                Log.e(TAG, "コピーされたデータベースファイルが無効です: ${dbFile.absolutePath}")
                _progressMessage.value = "データベースファイルの確認に失敗しました"
                return false
            }

            _progressMessage.value = "外部データベースからデータを読み込んでいます..."
            val externalNovels: List<ExternalNovel> = dbHandler.getAllNovelsFromExternalDB()
            _totalCount = externalNovels.size

            _progressMessage.value = "内部データベースにデータを同期しています..."
            withContext(Dispatchers.IO) {
                externalNovels.forEach { novel ->
                    appDatabase.UnifiedNovelDao().insertOrUpdateNovel(novel)
                    _processedCount.incrementAndGet()
                }
            }

            _progressMessage.value = "データベースの同期が完了しました"
            Log.d(TAG, "データベース同期完了: ${dbFile.absolutePath}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "データベース同期中にエラーが発生しました", e)
            _progressMessage.value = "エラー: ${e.message}"
            return false
        }
    }
}