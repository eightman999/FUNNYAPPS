// NovelRepository.kt
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

class NovelRepository(private val database: AppDatabase,private val context: Context) {
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

}