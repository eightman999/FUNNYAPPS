package com.shunlight_library.nr_reader.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.*

/**
 * 外部DBファイルハンドラー
 * 外部のSQLiteデータベースファイルをコピーする機能を提供します
 */
class ExternalDatabaseHandler(private val context: Context) {

    // 処理進捗状況の追跡用
    private val _progressFlow = MutableStateFlow(0f)
    val progressFlow: Flow<Float> = _progressFlow

    private val _progressMessage = MutableStateFlow("")
    val progressMessage: Flow<String> = _progressMessage

    private var _processedBytes = 0L
    private var _totalBytes = 0L

    companion object {
        private const val TAG = "ExternalDatabaseHandler"
        const val INTERNAL_DB_NAME = "external_novels_database.db"

        // コピー中かどうかのフラグ
        private var isCopying = false
    }

    /**
     * 外部DBファイルを本体ストレージにコピーする
     * @param sourceUri コピー元の外部DBファイルURI
     * @return コピーに成功したかどうか
     */
    suspend fun copyExternalDatabaseToInternal(sourceUri: Uri): Boolean {
        if (isCopying) {
            Log.d(TAG, "すでにファイルコピー処理が実行中です")
            return false
        }

        isCopying = true
        _progressMessage.value = "データベースファイルをコピーしています..."
        _progressFlow.value = 0f
        _processedBytes = 0

        return try {
            withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: throw IOException("ファイルを開けませんでした")

                // ファイルサイズの取得
                _totalBytes = context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex("_size")
                        if (sizeIndex != -1) {
                            cursor.getLong(sizeIndex)
                        } else {
                            // サイズが不明の場合は-1を返す
                            -1L
                        }
                    } else {
                        -1L
                    }
                } ?: -1L

                Log.d(TAG, "コピー開始: ファイルサイズ = $_totalBytes バイト")

                // 内部ストレージのDBファイルを作成
                val outputFile = context.getDatabasePath(INTERNAL_DB_NAME)

                // ディレクトリの作成を確実にする
                val parentDir = outputFile.parentFile
                if (parentDir != null) {
                    if (!parentDir.exists()) {
                        val dirCreated = parentDir.mkdirs()
                        if (!dirCreated) {
                            Log.e(TAG, "ディレクトリ作成失敗: ${parentDir.absolutePath}")
                            throw IOException("データベースディレクトリを作成できませんでした")
                        }
                    }
                } else {
                    Log.e(TAG, "親ディレクトリがnullです")
                    throw IOException("親ディレクトリがnullです")
                }

                // ファイルが既に存在する場合は削除
                if (outputFile.exists()) {
                    Log.d(TAG, "既存のファイルを削除します: ${outputFile.absolutePath}")
                    outputFile.delete()
                }

                val outputStream = FileOutputStream(outputFile)
                val buffer = ByteArray(1024 * 1024) // 1MBバッファ
                var bytesRead: Int

                // バッファサイズごとにファイルをコピー
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    _processedBytes += bytesRead

                    // 進捗状況の更新
                    if (_totalBytes > 0) {
                        _progressFlow.value = (_processedBytes.toFloat() / _totalBytes).coerceIn(0f, 1f)
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                Log.d(TAG, "コピー完了: $_processedBytes/$_totalBytes バイト")
                _progressFlow.value = 1f
                _progressMessage.value = "コピー完了 (${formatFileSize(_processedBytes)})"

                // ファイルが実際に存在するか確認
                if (!outputFile.exists()) {
                    Log.e(TAG, "コピー後にファイルが存在しません: ${outputFile.absolutePath}")
                    throw IOException("コピー後にデータベースファイルが存在しません")
                }

                // ファイルサイズを確認
                if (outputFile.length() == 0L) {
                    Log.e(TAG, "コピーされたファイルが空です: ${outputFile.absolutePath}")
                    throw IOException("コピーされたデータベースファイルが空です")
                }

                // アクセス権限を確認
                if (!outputFile.canRead()) {
                    Log.e(TAG, "ファイルの読み取り権限がありません: ${outputFile.absolutePath}")
                    throw IOException("データベースファイルに読み取り権限がありません")
                }

                Log.d(TAG, "コピーしたデータベースのサイズ: ${formatFileSize(outputFile.length())}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "データベースのコピー中にエラーが発生しました", e)
            _progressMessage.value = "エラー: ${e.message}"
            false
        } finally {
            isCopying = false
        }
    }

    /**
     * ファイルサイズを見やすい形式に変換
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
    // ExternalDatabaseHandler.kt に追加するメソッド
    suspend fun getAllNovelsFromExternalDB(): List<ExternalNovel> {
        val copiedDbFile = context.getDatabasePath(INTERNAL_DB_NAME)
        if (!copiedDbFile.exists()) {
            throw FileNotFoundException("コピーされたデータベースファイルが見つかりません")
        }

        // 外部DBに接続
        val externalDb = SQLiteDatabase.openDatabase(
            copiedDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )

        val novels = mutableListOf<ExternalNovel>()

        try {
            // 小説情報を取得
            val cursor = externalDb.query(
                "novels_descs",
                null, // 全カラム
                null, // WHERE句なし
                null, // WHERE句の引数なし
                null, // GROUP BY
                null, // HAVING
                "last_update_date DESC" // ORDER BY
            )

            cursor.use {
                if (cursor.moveToFirst()) {
                    do {
                        val ncode = cursor.getString(cursor.getColumnIndexOrThrow("n_code"))
                        val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
                        val author = cursor.getString(cursor.getColumnIndexOrThrow("author"))
                        val synopsis = cursor.getString(cursor.getColumnIndexOrThrow("Synopsis"))
                        val mainTag = cursor.getString(cursor.getColumnIndexOrThrow("main_tag"))
                        val subTag = cursor.getString(cursor.getColumnIndexOrThrow("sub_tag"))
                        val rating = cursor.getInt(cursor.getColumnIndexOrThrow("rating"))
                        val lastUpdateDate = cursor.getString(cursor.getColumnIndexOrThrow("last_update_date"))
                        val totalEp = cursor.getInt(cursor.getColumnIndexOrThrow("total_ep"))
                        val generalAllNo = cursor.getInt(cursor.getColumnIndexOrThrow("general_all_no"))

                        // 最後に読んだエピソード情報を取得
                        val lastReadEpisode = getLastReadEpisode(externalDb, ncode)

                        // タグをリストに変換
                        val mainTags = mainTag?.split(",")?.map { it.trim() } ?: emptyList()
                        val subTags = subTag?.split(",")?.map { it.trim() } ?: emptyList()

                        val novel = ExternalNovel(
                            title = title ?: "タイトルなし",
                            ncode = ncode,
                            author = author ?: "作者不明",
                            synopsis = synopsis,
                            mainTags = mainTags,
                            subTags = subTags,
                            rating = rating ?: 0,
                            lastUpdateDate = lastUpdateDate,
                            totalEpisodes = totalEp ?: 0,
                            generalAllNo = generalAllNo ?: 0,
                            lastReadEpisode = lastReadEpisode
                        )

                        novels.add(novel)
                    } while (cursor.moveToNext())
                }
            }
        } finally {
            externalDb.close()
        }

        return novels
    }

    // 特定の小説の最後に読んだエピソード番号を取得
    private fun getLastReadEpisode(db: SQLiteDatabase, ncode: String): Int {
        var lastReadEpisode = 1

        try {
            val cursor = db.query(
                "rast_read_novel",
                arrayOf("episode_no"),
                "ncode = ?",
                arrayOf(ncode),
                null,
                null,
                "date DESC",
                "1" // 最新の1件
            )

            cursor.use {
                if (it.moveToFirst()) {
                    val epIndex = it.getColumnIndex("episode_no")
                    if (epIndex >= 0) {
                        lastReadEpisode = it.getInt(epIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "最後に読んだエピソード番号の取得に失敗しました: $ncode", e)
            // デフォルト値の1を使用
        }

        return lastReadEpisode
    }
}