package com.shunlight_library.novel_reader.data.sync

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.repository.NovelRepository
import com.shunlight_library.novel_reader.data.sync.DatabaseSyncUtils.getColumnIndexSafely
import com.shunlight_library.novel_reader.data.sync.DatabaseSyncUtils.getIntSafely
import com.shunlight_library.novel_reader.data.sync.DatabaseSyncUtils.getStringSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 内部RoomデータベースとSDカード上のSQLiteデータベースを同期するための改良版マネージャクラス
 */
class ImprovedDatabaseSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "ImprovedDbSyncManager"
    }

    private val repository: NovelRepository = NovelReaderApplication.getRepository()
    private val sqliteHelper = ExternalSQLiteHelper(context)
    private var syncCallback: SyncProgressCallback? = null
    /**
     * 同期プロセスの進行状況を表すデータクラス
     */
    data class SyncProgress(
        val step: SyncStep,
        val message: String,
        val progress: Float, // 0.0f から 1.0f の範囲
        val currentNcode: String = "", // 現在処理中のncode
        val currentTitle: String = "", // 現在処理中のタイトル
        val currentCount: Int = 0,    // 現在の処理済み数
        val totalCount: Int = 0       // 処理対象の総数
    )

    /**
     * 同期ステップを表す列挙型
     */
    enum class SyncStep {
        PREPARING,
        CHECKING_COMPATIBILITY,
        SYNCING_NOVEL_DESCS,
        SYNCING_EPISODES,
        SYNCING_LAST_READ,
        COMPLETED,
        ERROR
    }

    /**
     * 同期の結果を表すデータクラス
     */
    data class SyncResult(
        val success: Boolean,
        val novelDescsCount: Int,
        val episodesCount: Int,
        val lastReadCount: Int,
        val errorMessage: String? = null
    )

    /**
     * 同期の進行状況を通知するコールバック
     */
    interface SyncProgressCallback {
        fun onProgressUpdate(progress: SyncProgress)
        fun onComplete(result: SyncResult)
    }

    /**
     * 指定されたURIのSQLiteデータベースから内部Roomデータベースへデータを同期します
     * @param uri 外部データベースのURI
     * @param callback 進行状況を受け取るコールバック（省略可）
     * @return 同期の結果
     */
    suspend fun syncFromExternalDb(
        uri: Uri,
        callback: SyncProgressCallback? = null
    ): SyncResult {
        return withContext(Dispatchers.IO) {
            var db: SQLiteDatabase? = null
            var tempFile: File? = null
            syncCallback = callback
            try {
                // 進行状況の更新: 準備中
                updateProgress(callback, SyncProgress(
                    step = SyncStep.PREPARING,
                    message = "外部データベースをロード中...",
                    progress = 0.1f
                ))

                // 外部DBを開く
                val dbAndFile = sqliteHelper.openExternalDatabase(uri)
                if (dbAndFile == null) {
                    val errorMsg = "外部データベースを開けませんでした"
                    updateProgress(callback, SyncProgress(
                        step = SyncStep.ERROR,
                        message = errorMsg,
                        progress = 0f
                    ))
                    return@withContext SyncResult(
                        success = false,
                        novelDescsCount = 0,
                        episodesCount = 0,
                        lastReadCount = 0,
                        errorMessage = errorMsg
                    )
                }

                db = dbAndFile.first
                tempFile = dbAndFile.second

                // データベースの互換性チェック
                updateProgress(callback, SyncProgress(
                    step = SyncStep.CHECKING_COMPATIBILITY,
                    message = "データベースの互換性をチェック中...",
                    progress = 0.2f
                ))

                // 必要なテーブルの存在確認
                val requiredTables = listOf("novels_descs", "episodes", "last_read_novel")
                for (table in requiredTables) {
                    if (!sqliteHelper.isTableExists(db, table)) {
                        val errorMsg = "必要なテーブルがありません: $table"
                        updateProgress(callback, SyncProgress(
                            step = SyncStep.ERROR,
                            message = errorMsg,
                            progress = 0f
                        ))
                        return@withContext SyncResult(
                            success = false,
                            novelDescsCount = 0,
                            episodesCount = 0,
                            lastReadCount = 0,
                            errorMessage = errorMsg
                        )
                    }
                }

                // データ同期を実行
                // 小説説明の同期
                updateProgress(callback, SyncProgress(
                    step = SyncStep.SYNCING_NOVEL_DESCS,
                    message = "小説情報を同期中...",
                    progress = 0.3f
                ))
                val novelDescsCount = syncNovelDescs(db)

                // エピソードの同期
                updateProgress(callback, SyncProgress(
                    step = SyncStep.SYNCING_EPISODES,
                    message = "エピソードを同期中...",
                    progress = 0.6f
                ))
                val episodesCount = syncEpisodes(db)

                // 最後に読んだ記録の同期
                updateProgress(callback, SyncProgress(
                    step = SyncStep.SYNCING_LAST_READ,
                    message = "読書履歴を同期中...",
                    progress = 0.9f
                ))
                val lastReadCount = syncLastReadNovels(db)

                // 完了通知
                val result = SyncResult(
                    success = true,
                    novelDescsCount = novelDescsCount,
                    episodesCount = episodesCount,
                    lastReadCount = lastReadCount
                )

                updateProgress(callback, SyncProgress(
                    step = SyncStep.COMPLETED,
                    message = "同期が完了しました: 小説${novelDescsCount}件、エピソード${episodesCount}件、履歴${lastReadCount}件",
                    progress = 1.0f
                ))

                callback?.onComplete(result)
                return@withContext result

            } catch (e: Exception) {
                val errorMsg = "データベース同期中にエラーが発生しました: ${e.message}"
                Log.e(TAG, errorMsg, e)

                updateProgress(callback, SyncProgress(
                    step = SyncStep.ERROR,
                    message = errorMsg,
                    progress = 0f
                ))

                val result = SyncResult(
                    success = false,
                    novelDescsCount = 0,
                    episodesCount = 0,
                    lastReadCount = 0,
                    errorMessage = errorMsg
                )

                callback?.onComplete(result)
                return@withContext result
            } finally {
                // リソースのクリーンアップ
                sqliteHelper.closeAndCleanup(db, tempFile)
            }
        }
    }

    /**
     * 進行状況をコールバックに通知します
     */
    private fun updateProgress(callback: SyncProgressCallback?, progress: SyncProgress) {
        syncCallback?.onProgressUpdate(progress)

        // ログに小説情報も表示
        val novelInfo = if (progress.currentNcode.isNotEmpty() && progress.currentTitle.isNotEmpty())
            "[${progress.currentNcode}] ${progress.currentTitle}"
        else ""

        Log.d(TAG, "[${progress.step}] ${progress.message} $novelInfo (${progress.progress * 100}%)")

    }

    /**
     * 小説説明データを同期します
     * @return 同期された小説数
     */
    private suspend fun syncNovelDescs(externalDb: SQLiteDatabase): Int {

        var cursor: Cursor? = null
        var count = 0
        var totalCount = 0

        try {
            // 総レコード数を取得
            totalCount = sqliteHelper.getTableCount(externalDb, "novels_descs")

            // 外部DBから全ての小説説明を取得
            cursor = externalDb.query(
                "novels_descs",
                null,
                null,
                null,
                null,
                null,
                null
            )

            // カラム名の取得とマッピング
            val columnNcode = if (cursor.getColumnIndex("ncode") >= 0) {
                cursor.getColumnIndexOrThrow("ncode")
            } else {
                cursor.getColumnIndexOrThrow("n_code")
            }
            val columnTitle = cursor.getColumnIndexOrThrow("title")
            val columnAuthor = cursor.getColumnIndexOrThrow("author")

            // SQLiteDBではカラム名が大文字小文字を区別しない可能性がある
            val columnSynopsis = getColumnIndexSafely(cursor, "Synopsis") ?:
            getColumnIndexSafely(cursor, "synopsis")

            val columnMainTag = getColumnIndexSafely(cursor, "main_tag")
            val columnSubTag = getColumnIndexSafely(cursor, "sub_tag")
            val columnRating = getColumnIndexSafely(cursor, "rating")
            val columnLastUpdateDate = getColumnIndexSafely(cursor, "last_update_date")
            val columnTotalEp = getColumnIndexSafely(cursor, "total_ep")
            val columnGeneralAllNo = getColumnIndexSafely(cursor, "general_all_no")
            val columnUpdatedAt = getColumnIndexSafely(cursor, "updated_at")

            val batchSize = 50
            val novels = mutableListOf<NovelDescEntity>()

            // データの読み取りとバッチ処理
            while (cursor.moveToNext()) {
                val novel = NovelDescEntity(
                    ncode = getStringSafely(cursor, columnNcode),
                    title = getStringSafely(cursor, columnTitle),
                    author = getStringSafely(cursor, columnAuthor),
                    Synopsis = getStringSafely(cursor, columnSynopsis),
                    main_tag = getStringSafely(cursor, columnMainTag),
                    sub_tag = getStringSafely(cursor, columnSubTag),
                    rating = getIntSafely(cursor, columnRating),
                    last_update_date = getStringSafely(cursor, columnLastUpdateDate,
                        DatabaseSyncUtils.getCurrentDateTimeString()),
                    total_ep = getIntSafely(cursor, columnTotalEp),
                    general_all_no = getIntSafely(cursor, columnGeneralAllNo),
                    updated_at = getStringSafely(cursor, columnUpdatedAt,
                        DatabaseSyncUtils.getCurrentDateTimeString())
                )

                novels.add(novel)
                count++
                val progressValue = if (totalCount > 0) count.toFloat() / totalCount else 0f
                val progressPercent = (progressValue * 100).toInt()

                updateProgress(syncCallback, SyncProgress(
                    step = SyncStep.SYNCING_NOVEL_DESCS,
                    message = "小説情報を同期中 ($count/$totalCount - $progressPercent%)",
                    progress = 0.3f + (0.3f * progressValue),
                    currentNcode = columnNcode.toString(),
                    currentTitle = columnTitle.toString(),
                    currentCount = count,
                    totalCount = totalCount
                ))
                // バッチサイズに達したら保存
                if (novels.size >= batchSize) {
                    repository.insertNovels(novels)
                    novels.clear()
                }
            }

            // 残りのデータを保存
            if (novels.isNotEmpty()) {
                repository.insertNovels(novels)
            }

            Log.d(TAG, "小説説明の同期が完了しました: $count 件")
            return count
        } catch (e: Exception) {
            Log.e(TAG, "小説説明の同期中にエラーが発生しました", e)
            throw e
        } finally {
            cursor?.close()
        }
    }

    /**
     * エピソードデータを同期します
     * @return 同期されたエピソード数
     */
    private suspend fun syncEpisodes(externalDb: SQLiteDatabase): Int {
        var cursor: Cursor? = null
        var count = 0
        var totalCount = 0

        try {
            // 総レコード数を取得
            totalCount = sqliteHelper.getTableCount(externalDb, "episodes")

            // 進捗初期化の報告
            updateProgress(syncCallback, SyncProgress(
                step = SyncStep.SYNCING_EPISODES,
                message = "エピソードデータの同期を開始 (0/$totalCount - 0%)",
                progress = 0.6f,
                currentCount = 0,
                totalCount = totalCount
            ))

            // 外部DBからエピソードを取得
            cursor = externalDb.query(
                "episodes",
                null,
                null,
                null,
                null,
                null,
                null
            )

            // カラム名の取得とマッピング
            val columnNcode = if (cursor.getColumnIndex("ncode") >= 0) {
                cursor.getColumnIndexOrThrow("ncode")
            } else {
                cursor.getColumnIndexOrThrow("n_code")
            }
            val columnEpisodeNo = cursor.getColumnIndexOrThrow("episode_no")
            val columnBody = cursor.getColumnIndexOrThrow("body")
            val columnETitle = getColumnIndexSafely(cursor, "e_title")
            val columnUpdateTime = getColumnIndexSafely(cursor, "update_time")

            val batchSize = 20
            val episodes = mutableListOf<EpisodeEntity>()

            // データの読み取りとバッチ処理
            while (cursor.moveToNext()) {
                val episode = EpisodeEntity(
                    ncode = getStringSafely(cursor, columnNcode),
                    episode_no = getStringSafely(cursor, columnEpisodeNo),
                    body = getStringSafely(cursor, columnBody),
                    e_title = getStringSafely(cursor, columnETitle),
                    update_time = getStringSafely(cursor, columnUpdateTime,
                        DatabaseSyncUtils.getCurrentDateTimeString())
                )

                episodes.add(episode)
                count++
                val progressValue = if (totalCount > 0) count.toFloat() / totalCount else 0f
                val progressPercent = (progressValue * 100).toInt()

                updateProgress(syncCallback, SyncProgress(
                    step = SyncStep.SYNCING_EPISODES,
                    message = "エピソードを同期中 ($count/$totalCount - $progressPercent%)",
                    progress = 0.6f + (0.3f * progressValue),
                    currentNcode = getStringSafely(cursor, columnNcode),
                    currentTitle = "第${getStringSafely(cursor, columnEpisodeNo)}話",
                    currentCount = count,
                    totalCount = totalCount
                ))

                // バッチサイズに達したら保存
                if (episodes.size >= batchSize) {
                    repository.insertEpisodes(episodes)
                    episodes.clear()
                }
            }

            // 残りのデータを保存
            if (episodes.isNotEmpty()) {
                repository.insertEpisodes(episodes)
            }

            Log.d(TAG, "エピソードの同期が完了しました: $count 件")
            return count
        } catch (e: Exception) {
            Log.e(TAG, "エピソードの同期中にエラーが発生しました", e)
            throw e
        } finally {
            cursor?.close()
        }
    }

    /**
     * 最後に読んだ小説のデータを同期します
     * @return 同期された記録数
     */
    private suspend fun syncLastReadNovels(externalDb: SQLiteDatabase): Int {
        var cursor: Cursor? = null
        var count = 0
        var totalCount = 0

        try {
            // 総レコード数を取得
            totalCount = sqliteHelper.getTableCount(externalDb, "last_read_novel")

            // 進捗初期化の報告
            updateProgress(syncCallback, SyncProgress(
                step = SyncStep.SYNCING_LAST_READ,
                message = "読書履歴データの同期を開始 (0/$totalCount - 0%)",
                progress = 0.9f,
                currentCount = 0,
                totalCount = totalCount
            ))

            // 外部DBから最終読書記録を取得
            cursor = externalDb.query(
                "last_read_novel",
                null,
                null,
                null,
                null,
                null,
                null
            )

            // カラム名の取得とマッピング
            val columnNcode = if (cursor.getColumnIndex("ncode") >= 0) {
                cursor.getColumnIndexOrThrow("ncode")
            } else {
                cursor.getColumnIndexOrThrow("n_code")
            }
            val columnDate = cursor.getColumnIndexOrThrow("date")
            val columnEpisodeNo = cursor.getColumnIndexOrThrow("episode_no")

            // データの読み取りと処理
            while (cursor.moveToNext()) {
                val ncode = getStringSafely(cursor, columnNcode)
                val episodeNo = getIntSafely(cursor, columnEpisodeNo)

                // 既存のデータがあれば更新、なければ新規挿入
                repository.updateLastRead(ncode, episodeNo)
                count++
                val progressValue = if (totalCount > 0) count.toFloat() / totalCount else 0f
                val progressPercent = (progressValue * 100).toInt()

                updateProgress(syncCallback, SyncProgress(
                    step = SyncStep.SYNCING_LAST_READ,
                    message = "読書履歴を同期中 ($count/$totalCount - $progressPercent%)",
                    progress = 0.9f + (0.1f * progressValue),
                    currentNcode = ncode,
                    currentTitle = "最終話: ${episodeNo}話",
                    currentCount = count,
                    totalCount = totalCount
                ))
            }

            Log.d(TAG, "最終読書記録の同期が完了しました: $count 件")
            return count
        } catch (e: Exception) {
            Log.e(TAG, "最終読書記録の同期中にエラーが発生しました", e)
            throw e
        } finally {
            cursor?.close()
        }
    }
}