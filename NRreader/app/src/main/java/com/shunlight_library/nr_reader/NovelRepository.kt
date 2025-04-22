// NovelRepository.kt の修正部分
package com.shunlight_library.nr_reader.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.shunlight_library.nr_reader.Novel
import com.shunlight_library.nr_reader.database.AppDatabase
import com.shunlight_library.nr_reader.database.NovelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class NovelRepository(private val database: AppDatabase, private val context: Context) {
    private val novelDao = database.novelDao()

    // 進行状況を追跡するための変数
    private val _processedCount = AtomicInteger(0)
    private var _totalCount = 0

    // 進行状況を取得するプロパティ
    val processedCount: Int get() = _processedCount.get()
    val totalCount: Int get() = _totalCount
    val progress: Float get() = if (_totalCount > 0) processedCount.toFloat() / _totalCount else 0f

    // 進行状況をリセット
    fun resetProgress() {
        _processedCount.set(0)
        _totalCount = 0
    }

    // すべての小説を取得（変更なし）
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

    // 新規追加: DBに小説が存在するか確認するメソッド
    suspend fun hasAnyNovels(): Boolean {
        return withContext(Dispatchers.IO) {
            novelDao.getNovelCount() > 0
        }
    }

    // 新規追加: DBの小説数を取得するメソッド
    suspend fun getNovelCount(): Int {
        return withContext(Dispatchers.IO) {
            novelDao.getNovelCount()
        }
    }

    // 小説情報を保存/更新（変更なし）
    suspend fun saveNovel(novel: Novel) {
        val entity = NovelEntity(
            ncode = novel.ncode,
            title = novel.title,
            lastReadEpisode = novel.lastReadEpisode,
            totalEpisodes = novel.totalEpisodes
        )
        novelDao.insert(entity)
    }

    // 複数の小説情報を一括保存（変更なし）
    suspend fun saveNovels(novels: List<Novel>) {
        novels.forEach { novel ->
            saveNovel(novel)
        }
    }

    // 新規追加: 小説情報を一括でRoomに保存（トランザクション利用）
    suspend fun saveNovelsInBatch(novels: List<Novel>) {
        withContext(Dispatchers.IO) {
            val entities = novels.map { novel ->
                NovelEntity(
                    ncode = novel.ncode,
                    title = novel.title,
                    lastReadEpisode = novel.lastReadEpisode,
                    totalEpisodes = novel.totalEpisodes
                )
            }
            // 一括挿入
            novelDao.insertAll(entities)
        }
    }

    // 最後に読んだエピソード番号を更新（変更なし）
    suspend fun updateLastReadEpisode(ncode: String, episodeNum: Int) {
        novelDao.updateLastReadEpisode(ncode, episodeNum)
    }

    // 総エピソード数を更新（変更なし）
    suspend fun updateTotalEpisodes(ncode: String, totalCount: Int) {
        novelDao.updateTotalEpisodes(ncode, totalCount)
    }

    // 更新された小説一覧を取得
    suspend fun getUpdatedNovels(serverPath: String): List<Novel> {
        return withContext(Dispatchers.IO) {
            try {
                val allNovels = novelDao.getAllNovelsSync()
                val updatedNovels = mutableListOf<Novel>()

                // 総数を設定
                _totalCount = allNovels.size
                _processedCount.set(0)

                for (entity in allNovels) {
                    // ファイルシステムから現在のエピソード数を取得
                    val currentEpisodeCount = countEpisodesFromFileSystem(serverPath, entity.ncode)

                    // エピソード数が増えている場合はリストに追加
                    if (currentEpisodeCount > entity.totalEpisodes) {
                        val novel = Novel(
                            title = entity.title,
                            ncode = entity.ncode,
                            lastReadEpisode = entity.lastReadEpisode,
                            totalEpisodes = currentEpisodeCount,
                            unreadCount = (currentEpisodeCount - entity.lastReadEpisode).coerceAtLeast(0)
                        )
                        updatedNovels.add(novel)

                        // DBを更新
                        novelDao.updateTotalEpisodes(entity.ncode, currentEpisodeCount)
                    }

                    // 進行状況を更新
                    _processedCount.incrementAndGet()
                }

                updatedNovels
            } catch (e: Exception) {
                Log.e("NovelRepository", "更新された小説の取得エラー: ${e.message}")
                emptyList()
            }
        }
    }

    // エピソード数をファイルシステムから計算（変更なし）
    suspend fun countEpisodesFromFileSystem(serverPath: String, ncode: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(serverPath)
                when (uri.scheme) {
                    "file" -> {
                        // ファイルシステムからエピソード数を計算
                        val baseDir = File(uri.path ?: "").parentFile
                        val novelDir = File(baseDir, "novels/$ncode")

                        if (!novelDir.exists() || !novelDir.isDirectory) {
                            return@withContext 0
                        }

                        // episode_XXXX.htmlのファイル数をカウント
                        novelDir.listFiles { file ->
                            file.name.startsWith("episode_") && file.name.endsWith(".html")
                        }?.size ?: 0
                    }
                    "content" -> {
                        // ContentProviderからエピソード数を計算
                        val documentFile = DocumentFile.fromTreeUri(context, uri)
                        if (documentFile == null || !documentFile.exists()) {
                            return@withContext 0
                        }

                        // novelsディレクトリ内の該当する小説ディレクトリを探す
                        val novelsDir = documentFile.findFile("novels")
                        if (novelsDir == null || !novelsDir.exists()) {
                            return@withContext 0
                        }

                        // 対象の小説ディレクトリを探す
                        val novelDir = novelsDir.listFiles().find { it.name == ncode }
                        if (novelDir == null || !novelDir.exists() || !novelDir.isDirectory) {
                            return@withContext 0
                        }

                        // episode_XXXX.htmlのファイル数をカウント
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

    // 小説情報を最新化（エピソード数の更新）（変更なし）
    suspend fun refreshNovelInfo(serverPath: String, ncode: String) {
        val novel = novelDao.getNovelByNcode(ncode)
        if (novel != null) {
            val totalEpisodes = countEpisodesFromFileSystem(serverPath, ncode)
            if (totalEpisodes > 0) {
                novelDao.updateTotalEpisodes(ncode, totalEpisodes)
            }
        }
    }

    // 小説情報を取得（変更なし）
    suspend fun getNovelByNcode(ncode: String): Novel? {
        val entity = novelDao.getNovelByNcode(ncode)
        return entity?.let {
            Novel(
                title = it.title,
                ncode = it.ncode,
                lastReadEpisode = it.lastReadEpisode,
                totalEpisodes = it.totalEpisodes,
                unreadCount = (it.totalEpisodes - it.lastReadEpisode).coerceAtLeast(0)
            )
        }
    }

    // 新規追加: 前回の更新時刻を保存
    suspend fun saveLastUpdateTimestamp(timestamp: Long) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("novel_sync_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_update_timestamp", timestamp).apply()
        }
    }

    // 新規追加: 前回の更新時刻を取得
    suspend fun getLastUpdateTimestamp(): Long {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("novel_sync_prefs", Context.MODE_PRIVATE)
            prefs.getLong("last_update_timestamp", 0)
        }
    }

    // 新規追加: 更新が必要かどうか判断（例: 24時間経過していたら更新）
    suspend fun needsUpdate(): Boolean {
        val lastUpdate = getLastUpdateTimestamp()
        val currentTime = System.currentTimeMillis()
        // 24時間 = 86400000ミリ秒
        return (currentTime - lastUpdate) > 86400000
    }
}