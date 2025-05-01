package com.shunlight_library.nr_reader.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 小説データベースへの統合アクセス用DAO
 */
// UnifiedNovelDao.kt の修正箇所

@Dao
interface UnifiedNovelDao {
    // 全小説を取得（Flow版）
    @Query("SELECT * FROM novels ORDER BY last_updated DESC")
    fun getAllNovels(): Flow<List<UnifiedNovelEntity>>

    // 全小説を取得（同期版）
    @Query("SELECT * FROM novels ORDER BY last_updated DESC")
    suspend fun getAllNovelsSync(): List<UnifiedNovelEntity>

    // 小説の総数を取得
    @Query("SELECT COUNT(*) FROM novels")
    suspend fun getNovelCount(): Int

    // 特定の小説を取得
    @Query("SELECT * FROM novels WHERE ncode = :ncode")
    suspend fun getNovelByNcode(ncode: String): UnifiedNovelEntity?

    // 小説を挿入
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(novel: UnifiedNovelEntity)

    // 複数の小説を一括挿入
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(novels: List<UnifiedNovelEntity>)

    // 小説を削除
    @Query("DELETE FROM novels WHERE ncode = :ncode")
    suspend fun deleteNovelByNcode(ncode: String)

    // 最後に読んだエピソード番号を更新
    @Query("UPDATE novels SET last_read_episode = :episodeNum WHERE ncode = :ncode")
    suspend fun updateLastReadEpisode(ncode: String, episodeNum: Int)

    // 総エピソード数を更新
    @Query("UPDATE novels SET total_episodes = :totalCount WHERE ncode = :ncode")
    suspend fun updateTotalEpisodes(ncode: String, totalCount: Int)

    // ExternalNovel型から変換したUnifiedNovelEntityを挿入または更新
    @Transaction
    suspend fun insertOrUpdateNovel(novel: ExternalNovel) {
        val entity = UnifiedNovelEntity(
            ncode = novel.ncode,
            title = novel.title,
            author = novel.author,
            synopsis = novel.synopsis,
            mainTags = novel.mainTags.joinToString(","),
            subTags = novel.subTags.joinToString(","),
            rating = novel.rating,
            totalEpisodes = novel.totalEpisodes,
            lastReadEpisode = novel.lastReadEpisode,
            lastUpdateDate = novel.lastUpdateDate,
            generalAllNo = novel.generalAllNo,
            lastUpdated = System.currentTimeMillis(),
            lastSynced = System.currentTimeMillis()
        )
        insert(entity)
    }
    @Transaction
    suspend fun insertOrUpdateNovelsInBatch(novels: List<ExternalNovel>) {
        novels.forEach { novel ->
            insertOrUpdateNovel(novel)
        }
    }
}