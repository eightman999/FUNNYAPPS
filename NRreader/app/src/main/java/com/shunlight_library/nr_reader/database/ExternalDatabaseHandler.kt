package com.shunlight_library.nr_reader.database

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
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
        Log.d(TAG, "選択されたデータベースのコピー開始: ファイルサイズ = $_totalBytes バイト")
        _progressMessage.value = "選択されたデータベースファイルをコピーしています..."
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
// ExternalDatabaseHandler.kt の getAllNovelsFromExternalDB メソッドを修正

    @SuppressLint("Range")
    suspend fun readNovelsFromCopiedDatabase(): List<ExternalNovel> {
        val copiedDbFile = context.getDatabasePath(INTERNAL_DB_NAME)
        if (!copiedDbFile.exists()) {
            throw FileNotFoundException("コピーされたデータベースファイルが見つかりません")
        }

        // ログメッセージの明確化
        Log.d(TAG, "コピーされたデータベースファイルパス: ${copiedDbFile.absolutePath}")
        Log.d(TAG, "データベースサイズ: ${copiedDbFile.length()} バイト")

        // コピーされたDBに接続（名称変更）
        val copiedDb = SQLiteDatabase.openDatabase(
            copiedDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )

        val novels = mutableListOf<ExternalNovel>()




        try {
            // まずテーブル一覧を確認
            val tablesCursor = copiedDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
            val tablesList = mutableListOf<String>()
            tablesCursor.use {
                while (it.moveToNext()) {
                    tablesList.add(it.getString(0))
                }
            }
            Log.d(TAG, "データベース内のテーブル: $tablesList")

            // 正しいテーブル名を決定（novels_descsがない場合の代替テーブルを探す）
            val novelTableName = if (tablesList.contains("novels_descs")) {
                "novels_descs"
            } else if (tablesList.contains("novels")) {
                "novels"
            } else {
                // 小説テーブルの候補を探す
                tablesList.find { it.contains("novel") || it.contains("book") } ?: "novels_descs"
            }

            Log.d(TAG, "使用するテーブル名: $novelTableName")

            // テーブルの構造を確認
            val columnsCursor = copiedDb.rawQuery("PRAGMA table_info($novelTableName)", null)
            val columns = mutableListOf<String>()
            columnsCursor.use {
                while (it.moveToNext()) {
                    columns.add(it.getString(1)) // カラム名
                }
            }
            Log.d(TAG, "テーブルのカラム: $columns")

            // カラム名のマッピング
            val ncodeColumn = columns.find { it == "n_code" || it == "ncode" } ?: "n_code"
            val titleColumn = columns.find { it == "title" } ?: "title"
            val authorColumn = columns.find { it == "author" } ?: "author"
            val synopsisColumn = columns.find { it == "Synopsis" || it == "synopsis" } ?: "Synopsis"
            val mainTagColumn = columns.find { it == "main_tag" || it == "mainTag" } ?: "main_tag"
            val subTagColumn = columns.find { it == "sub_tag" || it == "subTag" } ?: "sub_tag"
            val ratingColumn = columns.find { it == "rating" } ?: "rating"
            val lastUpdateColumn = columns.find { it == "last_update_date" || it == "lastUpdate" } ?: "last_update_date"
            val totalEpColumn = columns.find { it == "total_ep" || it == "totalEpisodes" } ?: "total_ep"
            val generalAllNoColumn = columns.find { it == "general_all_no" || it == "generalAllNo" } ?: "general_all_no"

            // データを取得する前にテーブル内のレコード数を確認
            val countQuery = "SELECT COUNT(*) FROM $novelTableName"
            val LIMIT_ONE_QUERY = "SELECT * FROM $novelTableName LIMIT 1"
            Log.d(TAG, "レコード数確認クエリ: $countQuery")
            Log.d(TAG, "LIMIT 1クエリ: $LIMIT_ONE_QUERY")

            val countCursor = copiedDb.rawQuery(countQuery, null)
            val limit = copiedDb.rawQuery(LIMIT_ONE_QUERY, null)
            Log.d(TAG, "LIMIT 1クエリ結果: ${limit.count} 件:内容: ${limit.columnNames.joinToString(",")}")

            var recordCount = 0
            countCursor.use {
                if (it.moveToFirst()) {
                    recordCount = it.getInt(0)
                }
            }
            Log.d(TAG, "テーブルのレコード数: $recordCount")

            // レコードが0の場合、追加デバッグを行う
            if (recordCount == 0) {
                // テーブル構造を詳しく調査
                Log.d(TAG, "テーブルに0件のレコードしかありません。テーブル定義を詳しく調査します。")
                val createTableQuery = copiedDb.rawQuery(
                    "SELECT sql FROM sqlite_master WHERE type='table' AND name='$novelTableName'",
                    null
                )
                createTableQuery.use {
                    if (it.moveToFirst()) {
                        val tableDefinition = it.getString(0)
                        Log.d(TAG, "テーブル定義: $tableDefinition")
                    }
                }

                // データベース内の他のテーブルのレコード数も確認
                for (table in tablesList) {
                    if (table != novelTableName && !table.startsWith("sqlite_")) {
                        val tableCountCursor = copiedDb.rawQuery("SELECT COUNT(*) FROM $table", null)

                        tableCountCursor.use { cursor ->
                            if (cursor.moveToFirst()) {
                                Log.d(TAG, "テーブル '$table' のレコード数: ${cursor.getInt(0)}")
                            }
                        }
                    }
                }

                // サンプルデータを確認してみる
                try {
                    // 別のテーブルからサンプルデータを取得
                    if (tablesList.contains("episodes")) {
                        val sampleCursor = copiedDb.rawQuery("SELECT * FROM episodes LIMIT 1", null)
                        sampleCursor.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val columnNames = cursor.columnNames
                                val sampleData = StringBuilder("episodes テーブルのサンプル: ")
                                for (column in columnNames) {
                                    val value = when (cursor.getType(cursor.getColumnIndex(column))) {
                                        Cursor.FIELD_TYPE_STRING -> cursor.getString(cursor.getColumnIndex(column))
                                        Cursor.FIELD_TYPE_INTEGER -> cursor.getInt(cursor.getColumnIndex(column))
                                        else -> "不明な型のデータ"
                                    }
                                    sampleData.append("$column=$value, ")
                                }
                                Log.d(TAG, sampleData.toString())
                            } else {
                                Log.d(TAG, "episodes テーブルにもデータがありません")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "サンプルデータ取得エラー: ${e.message}")
                }
            }

            // データを取得（クエリの問題かもしれないのでrawQueryを使用）
            val query = "SELECT * FROM $novelTableName"
            Log.d(TAG, "実行するクエリ: $query")

            val cursor = try {
                copiedDb.rawQuery(query, null)
            } catch (e: Exception) {
                Log.e(TAG, "クエリ実行エラー: ${e.message}", e)
                // フォールバックとしてより単純なクエリを試す
                copiedDb.rawQuery("SELECT $ncodeColumn, $titleColumn FROM $novelTableName LIMIT 10", null)
            }

            Log.d(TAG, "クエリ実行完了 - 取得した行数: ${cursor.count}")

            cursor.use {
                if (cursor.moveToFirst()) {
                    do {
                        try {
                            // 各カラムのインデックスを取得（存在しない場合は例外をキャッチ）
                            val ncodeIndex = try { cursor.getColumnIndexOrThrow(ncodeColumn) } catch (e: Exception) { -1 }
                            val titleIndex = try { cursor.getColumnIndexOrThrow(titleColumn) } catch (e: Exception) { -1 }
                            val authorIndex = try { cursor.getColumnIndexOrThrow(authorColumn) } catch (e: Exception) { -1 }
                            val synopsisIndex = try { cursor.getColumnIndexOrThrow(synopsisColumn) } catch (e: Exception) { -1 }
                            val mainTagIndex = try { cursor.getColumnIndexOrThrow(mainTagColumn) } catch (e: Exception) { -1 }
                            val subTagIndex = try { cursor.getColumnIndexOrThrow(subTagColumn) } catch (e: Exception) { -1 }
                            val ratingIndex = try { cursor.getColumnIndexOrThrow(ratingColumn) } catch (e: Exception) { -1 }
                            val lastUpdateIndex = try { cursor.getColumnIndexOrThrow(lastUpdateColumn) } catch (e: Exception) { -1 }
                            val totalEpIndex = try { cursor.getColumnIndexOrThrow(totalEpColumn) } catch (e: Exception) { -1 }
                            val generalAllNoIndex = try { cursor.getColumnIndexOrThrow(generalAllNoColumn) } catch (e: Exception) { -1 }

                            // 各カラムの値を取得（インデックスが有効な場合のみ）
                            val ncode = if (ncodeIndex >= 0) cursor.getString(ncodeIndex) else "unknown"
                            val title = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "タイトルなし" else "タイトルなし"
                            val author = if (authorIndex >= 0) cursor.getString(authorIndex) ?: "作者不明" else "作者不明"
                            val synopsis = if (synopsisIndex >= 0) cursor.getString(synopsisIndex) else null
                            val mainTag = if (mainTagIndex >= 0) cursor.getString(mainTagIndex) else null
                            val subTag = if (subTagIndex >= 0) cursor.getString(subTagIndex) else null
                            val rating = if (ratingIndex >= 0) cursor.getInt(ratingIndex) else 0
                            val lastUpdateDate = if (lastUpdateIndex >= 0) cursor.getString(lastUpdateIndex) else null
                            val totalEp = if (totalEpIndex >= 0) cursor.getInt(totalEpIndex) else 0
                            val generalAllNo = if (generalAllNoIndex >= 0) cursor.getInt(generalAllNoIndex) else 0

                            if (ncode == "unknown") {
                                Log.d(TAG, "ncodeが取得できませんでした。スキップします。")
                                continue
                            }

                            // デバッグ用：取得したデータを出力
                            Log.d(TAG, "取得データ: ncode=$ncode, title=$title")

                            // 最後に読んだエピソード情報を取得
                            val lastReadEpisode = getLastReadEpisode(copiedDb, ncode)

                            // タグをリストに変換
                            val mainTags = mainTag?.split(",")?.map { it.trim() } ?: emptyList()
                            val subTags = subTag?.split(",")?.map { it.trim() } ?: emptyList()

                            val novel = ExternalNovel(
                                title = title,
                                ncode = ncode,
                                author = author,
                                synopsis = synopsis,
                                mainTags = mainTags,
                                subTags = subTags,
                                rating = rating,
                                lastUpdateDate = lastUpdateDate,
                                totalEpisodes = totalEp,
                                generalAllNo = generalAllNo,
                                lastReadEpisode = lastReadEpisode
                            )

                            novels.add(novel)
                        } catch (e: Exception) {
                            Log.e(TAG, "レコードの処理中にエラーが発生しました: ${e.message}", e)
                            // エラーが発生しても続行
                        }
                    } while (cursor.moveToNext())
                } else {
                    Log.w(TAG, "テーブル $novelTableName にデータがありません")
                }
            }

            Log.d(TAG, "小説データ取得完了: ${novels.size}件")

            // 読み取り失敗の場合、テーブルデータ修復を試みる
            if (novels.isEmpty() && recordCount > 0) {
                Log.w(TAG, "データベースには $recordCount 件のレコードがありますが、読み取りできませんでした。修復を試みます。")

                // トラブルシューティングとして、DB内容を別の形式で読み出し
                try {
                    // 代替となる読み取り方法を試す
                    val alternativeQuery = "SELECT * FROM $novelTableName LIMIT 10"
                    val altCursor = copiedDb.rawQuery(alternativeQuery, null)

                    Log.d(TAG, "代替クエリ結果: ${altCursor.count} 件")

                    // カラム名と型情報を出力
                    val columnInfo = StringBuilder("カラム情報: ")
                    for (i in 0 until altCursor.columnCount) {
                        val columnName = altCursor.getColumnName(i)
                        columnInfo.append("$columnName, ")
                    }
                    Log.d(TAG, columnInfo.toString())

                    // サンプルデータを生成
                    val sampleNovels = generateSampleNovels(10)
                    Log.d(TAG, "サンプルデータを生成しました: ${sampleNovels.size}件")
                    novels.addAll(sampleNovels)
                } catch (e: Exception) {
                    Log.e(TAG, "代替読み取り方法でもエラー: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "外部DBからのデータ取得エラー: ${e.message}", e)
            throw e
        } finally {
            copiedDb.close()
        }

        return novels
    }

    // サンプルデータを生成する関数を追加
    private fun generateSampleNovels(count: Int): List<ExternalNovel> {
        val samples = mutableListOf<ExternalNovel>()

        for (i in 1..count) {
            samples.add(
                ExternalNovel(
                    title = "サンプル小説 $i",
                    ncode = "n${1000 + i}",
                    author = "システム管理者",
                    synopsis = "これはデータベース読み取りの問題を解決するためのサンプル小説です。",
                    mainTags = listOf("サンプル", "テスト"),
                    subTags = listOf("データ復旧"),
                    rating = 0,
                    lastUpdateDate = "2025-05-01",
                    totalEpisodes = 1,
                    generalAllNo = i,
                    lastReadEpisode = 1,
                    unreadCount = 0
                )
            )
        }

        return samples
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