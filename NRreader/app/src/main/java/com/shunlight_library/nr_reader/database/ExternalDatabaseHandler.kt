package com.shunlight_library.nr_reader.database

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*

/**
 * 外部DBファイルハンドラー
 * 外部のSQLiteデータベースファイルを読み込む機能を提供します
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
        private const val INTERNAL_DB_NAME = "external_novels_database.db"

        // シングルトンインスタンス
        @Volatile
        private var INSTANCE: ExternalNovelsDatabase? = null

        // 外部DBファイルをコピー中かどうかのフラグ
        private var isCopying = false

        // 外部DBファイルのURI
        private var externalDbUri: Uri? = null
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
                if (!outputFile.parentFile?.exists()!!) {
                    outputFile.parentFile?.mkdirs()
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

                // コピーに成功したら、内部DBのURIを設定
                externalDbUri = Uri.fromFile(outputFile)

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
     * SAF (Storage Access Framework) 経由で外部DBにアクセスするための設定
     * @param uri 外部DBファイルのURI
     */
    fun setExternalDatabaseUri(uri: Uri) {
        Log.d(TAG, "外部データベースURIを設定: $uri")
        externalDbUri = uri

        // 既存のデータベース接続をクローズ
        closeDatabase()
    }

    /**
     * 外部DBへのアクセスを閉じる
     */
    fun closeDatabase() {
        Log.d(TAG, "データベース接続をクローズします")
        INSTANCE?.let {
            if (it.isOpen) {
                it.close()
            }
            INSTANCE = null
        }
    }

    /**
     * 外部DBインスタンスを取得
     * @return 外部DBインスタンス
     */
    fun getDatabase(): ExternalNovelsDatabase? {
        if (externalDbUri == null) {
            Log.e(TAG, "外部データベースURIが設定されていません")
            return null
        }

        Log.d(TAG, "データベースインスタンスを取得します: $externalDbUri")

        return INSTANCE ?: synchronized(this) {
            val dbPath = if (externalDbUri?.scheme == "file") {
                // ファイルスキームの場合はパスを使用
                externalDbUri?.path
            } else {
                // 内部コピーを使用
                context.getDatabasePath(INTERNAL_DB_NAME).absolutePath
            }

            if (dbPath == null) {
                Log.e(TAG, "データベースパスがnullです")
                return null
            }

            try {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExternalNovelsDatabase::class.java,
                    INTERNAL_DB_NAME
                )
                    .createFromFile(File(dbPath))
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            Log.d(TAG, "データベースを開きました: $dbPath")

                            // データベースを開いた後の初期化処理
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = INSTANCE?.externalNovelDao()
                                val count = dao?.getNovelCount() ?: 0
                                Log.d(TAG, "データベース内の小説数: $count")
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            } catch (e: Exception) {
                Log.e(TAG, "データベースのオープン中にエラーが発生しました: ${e.message}", e)
                null
            }
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

    /**
     * 外部DBが設定されているかどうかを確認
     */
    fun hasExternalDatabase(): Boolean {
        return externalDbUri != null
    }

    /**
     * 外部DBのURIを取得
     */
    fun getExternalDatabaseUri(): Uri? {
        return externalDbUri
    }
}

/**
 * 外部小説データベース定義
 */
@Database(
    entities = [
        ExternalNovelEntity::class,
        EpisodeEntity::class,
        LastReadNovelEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ExternalNovelsDatabase : RoomDatabase() {
    abstract fun externalNovelDao(): ExternalNovelDao

    // データベースが開いているかどうかを確認
    override val isOpen: Boolean
        get() = try {
            openHelper.writableDatabase.isOpen
        } catch (e: Exception) {
            false
        }
}