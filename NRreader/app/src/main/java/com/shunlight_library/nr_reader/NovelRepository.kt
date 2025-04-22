// NovelRepository.kt
package com.shunlight_library.nr_reader.repository

import android.net.Uri
import com.shunlight_library.nr_reader.Novel
import com.shunlight_library.nr_reader.database.AppDatabase
import com.shunlight_library.nr_reader.database.NovelEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class NovelRepository(private val database: AppDatabase) {
    private val novelDao = database.novelDao()

    // すべての小説を取得
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

    // 小説情報を保存/更新
    suspend fun saveNovel(novel: Novel) {
        val entity = NovelEntity(
            ncode = novel.ncode,
            title = novel.title,
            lastReadEpisode = novel.lastReadEpisode,
            totalEpisodes = novel.totalEpisodes
        )
        novelDao.insert(entity)
    }

    // 複数の小説情報を一括保存
    suspend fun saveNovels(novels: List<Novel>) {
        novels.forEach { novel ->
            saveNovel(novel)
        }
    }

    // 最後に読んだエピソード番号を更新
    suspend fun updateLastReadEpisode(ncode: String, episodeNum: Int) {
        novelDao.updateLastReadEpisode(ncode, episodeNum)
    }

    // 総エピソード数を更新
    suspend fun updateTotalEpisodes(ncode: String, totalCount: Int) {
        novelDao.updateTotalEpisodes(ncode, totalCount)
    }

    // エピソード数をファイルシステムから計算
    suspend fun countEpisodesFromFileSystem(serverPath: String, ncode: String): Int {
        try {
            val uri = Uri.parse(serverPath)
            val path = uri.path ?: return 0

            // ファイルシステムからエピソード数を計算
            val baseDir = File(path).parentFile
            val novelDir = File(baseDir, "novels/$ncode")

            if (!novelDir.exists() || !novelDir.isDirectory) {
                return 0
            }

            // episode_XXXX.htmlのファイル数をカウント
            return novelDir.listFiles { file ->
                file.name.startsWith("episode_") && file.name.endsWith(".html")
            }?.size ?: 0
        } catch (e: Exception) {
            return 0
        }
    }

    // 小説情報を最新化（エピソード数の更新）
    suspend fun refreshNovelInfo(serverPath: String, ncode: String) {
        val novel = novelDao.getNovelByNcode(ncode)
        if (novel != null) {
            val totalEpisodes = countEpisodesFromFileSystem(serverPath, ncode)
            if (totalEpisodes > 0) {
                novelDao.updateTotalEpisodes(ncode, totalEpisodes)
            }
        }
    }
}