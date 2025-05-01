package com.shunlight_library.nr_reader.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.shunlight_library.nr_reader.Novel
import com.shunlight_library.nr_reader.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class NovelRepository(private val database: AppDatabase, private val context: Context) {
    private val novelDescDao = database.novelDescDao()
    private val episodeDao = database.episodeDao()
    private val lastReadDao = database.lastReadDao()

    private val _processedCount = AtomicInteger(0)
    private var _totalCount = 0

    val processedCount: Int get() = _processedCount.get()
    val totalCount: Int get() = _totalCount
    val progress: Float get() = if (_totalCount > 0) processedCount.toFloat() / _totalCount else 0f

    fun resetProgress() {
        _processedCount.set(0)
        _totalCount = 0
    }

    // 小説一覧をフローとして取得
    fun getAllNovels(): Flow<List<Novel>> {
        return novelDescDao.getAllNovels().map { entities ->
            entities.map { entity ->
                // 各小説の最終読み取り位置を取得
                val lastReadEntity = lastReadDao.getLastReadByNcode(entity.ncode)
                val lastReadEpisode = lastReadEntity?.episode_no ?: 1

                Novel(
                    title = entity.title,
                    ncode = entity.ncode,
                    lastReadEpisode = lastReadEpisode,
                    totalEpisodes = entity.total_ep ?: 0,
                    unreadCount = ((entity.total_ep ?: 0) - lastReadEpisode).coerceAtLeast(0)
                )
            }
        }
    }

    suspend fun hasAnyNovels(): Boolean {
        return withContext(Dispatchers.IO) {
            novelDescDao.getNovelCount() > 0
        }
    }

    suspend fun getNovelCount(): Int {
        return withContext(Dispatchers.IO) {
            novelDescDao.getNovelCount()
        }
    }

    // 単一の小説を保存
    suspend fun saveNovel(novel: Novel) {
        // 小説情報を保存
        val entity = NovelDescEntity(
            ncode = novel.ncode,
            title = novel.title,
            author = "", // 必要に応じて設定
            Synopsis = null,
            main_tag = null,
            sub_tag = null,
            rating = 0,
            total_ep = novel.totalEpisodes,
            last_update_date = null,
            general_all_no = 0,
            updated_at = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        )
        novelDescDao.insert(entity)

        // 最終読み取り位置を保存
        val lastReadEntity = InternalLastReadEntity(
            ncode = novel.ncode,
            date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
            episode_no = novel.lastReadEpisode
        )
        lastReadDao.insert(lastReadEntity)
    }

    // 複数の小説を保存
    suspend fun saveNovels(novels: List<Novel>) {
        novels.forEach { novel ->
            saveNovel(novel)
        }
    }

    // 複数の小説をバッチで保存
    suspend fun saveNovelsInBatch(novels: List<Novel>) {
        withContext(Dispatchers.IO) {
            // 小説情報のリスト
            val novelEntities = novels.map { novel ->
                NovelDescEntity(
                    ncode = novel.ncode,
                    title = novel.title,
                    author = "", // 必要に応じて設定
                    Synopsis = null,
                    main_tag = null,
                    sub_tag = null,
                    rating = 0,
                    total_ep = novel.totalEpisodes,
                    last_update_date = null,
                    general_all_no = 0,
                    updated_at = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                )
            }

            // 最終読み取り位置のリスト
            val lastReadEntities = novels.map { novel ->
                InternalLastReadEntity(
                    ncode = novel.ncode,
                    date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                    episode_no = novel.lastReadEpisode
                )
            }

            // バッチで保存
            novelDescDao.insertAll(novelEntities)
            lastReadEntities.forEach { lastReadDao.insert(it) }
        }
    }

    // 最終読み取り位置を更新
    suspend fun updateLastReadEpisode(ncode: String, episodeNum: Int) {
        val lastReadEntity = InternalLastReadEntity(
            ncode = ncode,
            date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
            episode_no = episodeNum
        )
        lastReadDao.insert(lastReadEntity)
    }

    // 総エピソード数を更新
    suspend fun updateTotalEpisodes(ncode: String, totalCount: Int) {
        novelDescDao.updateTotalEpisodes(ncode, totalCount)
    }

    // 更新された小説を取得
    suspend fun getUpdatedNovels(serverPath: String): List<Novel> {
        return withContext(Dispatchers.IO) {
            try {
                val allNovels = novelDescDao.getAllNovelsSync()
                val updatedNovels = mutableListOf<Novel>()

                _totalCount = allNovels.size
                _processedCount.set(0)

                for (entity in allNovels) {
                    val currentEpisodeCount = countEpisodesFromFileSystem(serverPath, entity.ncode)

                    // 最終読み取り位置を取得
                    val lastReadEntity = lastReadDao.getLastReadByNcode(entity.ncode)
                    val lastReadEpisode = lastReadEntity?.episode_no ?: 1

                    if (currentEpisodeCount > (entity.total_ep ?: 0)) {
                        val novel = Novel(
                            title = entity.title,
                            ncode = entity.ncode,
                            lastReadEpisode = lastReadEpisode,
                            totalEpisodes = currentEpisodeCount,
                            unreadCount = (currentEpisodeCount - lastReadEpisode).coerceAtLeast(0)
                        )
                        updatedNovels.add(novel)

                        novelDescDao.updateTotalEpisodes(entity.ncode, currentEpisodeCount)
                    }

                    _processedCount.incrementAndGet()
                }

                updatedNovels
            } catch (e: Exception) {
                Log.e("NovelRepository", "更新された小説の取得エラー: ${e.message}")
                emptyList()
            }
        }
    }

    // ファイルシステムからエピソード数を数える
    suspend fun countEpisodesFromFileSystem(serverPath: String, ncode: String): Int {
        // 既存のコードを再利用
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(serverPath)
                when (uri.scheme) {
                    "file" -> {
                        val baseDir = File(uri.path ?: "").parentFile
                        val novelDir = File(baseDir, "novels/$ncode")

                        if (!novelDir.exists() || !novelDir.isDirectory) {
                            return@withContext 0
                        }

                        novelDir.listFiles { file ->
                            file.name.startsWith("episode_") && file.name.endsWith(".html")
                        }?.size ?: 0
                    }
                    "content" -> {
                        val documentFile = DocumentFile.fromTreeUri(context, uri)
                        if (documentFile == null || !documentFile.exists()) {
                            return@withContext 0
                        }

                        val novelsDir = documentFile.findFile("novels")
                        if (novelsDir == null || !novelsDir.exists()) {
                            return@withContext 0
                        }

                        val novelDir = novelsDir.listFiles().find { it.name == ncode }
                        if (novelDir == null || !novelDir.exists() || !novelDir.isDirectory) {
                            return@withContext 0
                        }

                        novelDir.listFiles().count { file ->
                            val fileName = file.name
                            fileName!!.startsWith("episode_") && fileName.endsWith(".html")
                        }
                    }
                    else -> 0
                }
            } catch (e: Exception) {
                Log.e("NovelRepository", "エピソード数カウントエラー: $ncode - ${e.message}")
                0
            }
        }
    }

    // 以下は既存のコードを保持
    private val PREFS_NAME = "novel_reader_prefs"
    private val KEY_LAST_UPDATE = "last_update_timestamp"

    suspend fun needsUpdate(): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastUpdate = sharedPrefs.getLong(KEY_LAST_UPDATE, 0L)
        val currentTime = System.currentTimeMillis()
        return currentTime - lastUpdate > 86400000L || lastUpdate == 0L
    }

    fun saveLastUpdateTimestamp(timestamp: Long) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putLong(KEY_LAST_UPDATE, timestamp).apply()
    }
}