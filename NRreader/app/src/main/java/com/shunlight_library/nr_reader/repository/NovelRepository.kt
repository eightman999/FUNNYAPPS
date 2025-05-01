package com.shunlight_library.nr_reader.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.shunlight_library.nr_reader.Novel
import com.shunlight_library.nr_reader.database.AppDatabase
import com.shunlight_library.nr_reader.database.UnifiedNovelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class NovelRepository(private val database: AppDatabase, private val context: Context) {
    private val novelDao = database.UnifiedNovelDao()

    private val _processedCount = AtomicInteger(0)
    private var _totalCount = 0

    val processedCount: Int get() = _processedCount.get()
    val totalCount: Int get() = _totalCount
    val progress: Float get() = if (_totalCount > 0) processedCount.toFloat() / _totalCount else 0f

    fun resetProgress() {
        _processedCount.set(0)
        _totalCount = 0
    }

    fun getAllNovels(): Flow<List<Novel>> {
        return novelDao.getAllNovels().map { entities ->
            entities.map { entity ->
                Novel(
                    title = entity.title,
                    ncode = entity.ncode,
                    lastReadEpisode = entity.lastReadEpisode,
                    totalEpisodes = entity.totalEpisodes,
                    unreadCount = (entity.totalEpisodes - entity.lastReadEpisode).coerceAtLeast(0)
                )
            }
        }
    }

    suspend fun hasAnyNovels(): Boolean {
        return withContext(Dispatchers.IO) {
            novelDao.getNovelCount() > 0
        }
    }

    suspend fun getNovelCount(): Int {
        return withContext(Dispatchers.IO) {
            novelDao.getNovelCount()
        }
    }

    suspend fun saveNovel(novel: Novel) {
        val entity = UnifiedNovelEntity(
            ncode = novel.ncode,
            title = novel.title,
            author = "", // 必要に応じて適切な値を設定
            synopsis = null,
            mainTags = null,
            subTags = null,
            rating = 0,
            totalEpisodes = novel.totalEpisodes,
            lastReadEpisode = novel.lastReadEpisode,
            lastUpdateDate = null,
            generalAllNo = 0
        )
        novelDao.insert(entity)
    }

    suspend fun saveNovels(novels: List<Novel>) {
        novels.forEach { novel ->
            saveNovel(novel)
        }
    }

    suspend fun saveNovelsInBatch(novels: List<Novel>) {
        withContext(Dispatchers.IO) {
            val entities = novels.map { novel ->
                UnifiedNovelEntity(
                    ncode = novel.ncode,
                    title = novel.title,
                    author = "", // 必要に応じて適切な値を設定
                    synopsis = null,
                    mainTags = null,
                    subTags = null,
                    rating = 0,
                    totalEpisodes = novel.totalEpisodes,
                    lastReadEpisode = novel.lastReadEpisode,
                    lastUpdateDate = null,
                    generalAllNo = 0
                )
            }
            novelDao.insertAll(entities)
        }
    }

    suspend fun updateLastReadEpisode(ncode: String, episodeNum: Int) {
        novelDao.updateLastReadEpisode(ncode, episodeNum)
    }

    suspend fun updateTotalEpisodes(ncode: String, totalCount: Int) {
        novelDao.updateTotalEpisodes(ncode, totalCount)
    }

    suspend fun getUpdatedNovels(serverPath: String): List<Novel> {
        return withContext(Dispatchers.IO) {
            try {
                val allNovels = novelDao.getAllNovelsSync()
                val updatedNovels = mutableListOf<Novel>()

                _totalCount = allNovels.size
                _processedCount.set(0)

                for (entity in allNovels) {
                    val currentEpisodeCount = countEpisodesFromFileSystem(serverPath, entity.ncode)

                    if (currentEpisodeCount > entity.totalEpisodes) {
                        val novel = Novel(
                            title = entity.title,
                            ncode = entity.ncode,
                            lastReadEpisode = entity.lastReadEpisode,
                            totalEpisodes = currentEpisodeCount,
                            unreadCount = (currentEpisodeCount - entity.lastReadEpisode).coerceAtLeast(0)
                        )
                        updatedNovels.add(novel)

                        novelDao.updateTotalEpisodes(entity.ncode, currentEpisodeCount)
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

    suspend fun countEpisodesFromFileSystem(serverPath: String, ncode: String): Int {
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
    private val PREFS_NAME = "novel_reader_prefs"
    private val KEY_LAST_UPDATE = "last_update_timestamp"

    // 小説データの更新が必要かどうかを判断するメソッド
    suspend fun needsUpdate(): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastUpdate = sharedPrefs.getLong(KEY_LAST_UPDATE, 0L)
        val currentTime = System.currentTimeMillis()

        // 24時間（86400000ミリ秒）経過したら更新が必要
        // または lastUpdate が 0 の場合（初回実行時）
        return currentTime - lastUpdate > 86400000L || lastUpdate == 0L
    }

    // 最終更新タイムスタンプを保存するメソッド
    fun saveLastUpdateTimestamp(timestamp: Long) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putLong(KEY_LAST_UPDATE, timestamp).apply()
    }
}