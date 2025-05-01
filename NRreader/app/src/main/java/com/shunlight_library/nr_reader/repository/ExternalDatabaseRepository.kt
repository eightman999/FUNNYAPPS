// ExternalDatabaseRepository.kt の修正
package com.shunlight_library.nr_reader.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.shunlight_library.nr_reader.database.*
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

    // shouldCopyToInternal引数を削除（常にコピーするため）
    suspend fun synchronizeWithExternalDatabase(externalDbUri: Uri): Boolean {
        _progressMessage.value = "データベースの同期を準備しています..."
        _processedCount.set(0)
        _totalCount = 0

        try {
            // 常に内部コピーを行う
            _progressMessage.value = "データベースを内部ストレージにコピーしています..."
            val copySuccess = dbHandler.copyExternalDatabaseToInternal(externalDbUri)
            if (!copySuccess) {
                _progressMessage.value = "データベースのコピーに失敗しました"
                return false
            }

            _progressMessage.value = "コピーされたデータベースからデータを読み込んでいます..."
            val externalNovels: List<ExternalNovel> = dbHandler.readNovelsFromCopiedDatabase()
            _totalCount = externalNovels.size

            _progressMessage.value = "アプリの内部データベースにデータを同期しています... (0/${_totalCount})"

            // トランザクションを使用して一括処理
            withContext(Dispatchers.IO) {
                appDatabase.withTransaction {
                    externalNovels.forEachIndexed { index, novel ->
                        // 小説概要テーブルに保存
                        appDatabase.novelDescDao().insert(novel.toNovelDescEntity())

                        // 最終読取情報を保存
                        appDatabase.lastReadDao().insert(novel.toLastReadEntity())

                        val processed = _processedCount.incrementAndGet()
                        if (processed % 10 == 0 || processed == _totalCount) {
                            _progressMessage.value = "アプリの内部データベースにデータを同期しています... ($processed/${_totalCount})"
                        }
                    }
                }
            }

            _progressMessage.value = "データベースの同期が完了しました"
            Log.d(TAG, "アプリの内部データベースへの同期完了: ${externalNovels.size}件の小説を同期")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "データベース同期中にエラーが発生しました", e)
            _progressMessage.value = "エラー: ${e.message}"
            return false
        }
    }
}