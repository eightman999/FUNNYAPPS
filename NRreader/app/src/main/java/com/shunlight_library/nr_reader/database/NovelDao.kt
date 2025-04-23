package com.shunlight_library.nr_reader.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query("SELECT * FROM novels ORDER BY lastUpdated DESC")
    fun getAllNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels ORDER BY lastUpdated DESC")
    suspend fun getAllNovelsSync(): List<NovelEntity>

    @Query("SELECT COUNT(*) FROM novels")
    suspend fun getNovelCount(): Int

    @Query("SELECT * FROM novels WHERE ncode = :ncode")
    suspend fun getNovelByNcode(ncode: String): NovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(novel: NovelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(novels: List<NovelEntity>)

    @Update
    suspend fun update(novel: NovelEntity)

    @Query("UPDATE novels SET lastReadEpisode = :episodeNum, lastUpdated = :timestamp WHERE ncode = :ncode")
    suspend fun updateLastReadEpisode(ncode: String, episodeNum: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE novels SET totalEpisodes = :totalCount WHERE ncode = :ncode")
    suspend fun updateTotalEpisodes(ncode: String, totalCount: Int)

    @Delete
    suspend fun delete(novel: NovelEntity)

    @Query("DELETE FROM novels")
    suspend fun deleteAll()

    // 新規追加：最近読んだ小説のリスト取得（Flow版）
    @Query("SELECT * FROM novels ORDER BY lastUpdated DESC LIMIT :limit")
    fun getRecentNovels(limit: Int = 20): Flow<List<NovelEntity>>

    // 新規追加：最近読んだ小説のリスト取得（非Flow版）
    @Query("SELECT * FROM novels ORDER BY lastUpdated DESC LIMIT :limit")
    suspend fun getRecentNovelsSync(limit: Int = 20): List<NovelEntity>

    // 新規追加：総エピソード数と最後に読んだエピソードの差が1以上の小説（未読あり）
    @Query("SELECT * FROM novels WHERE (totalEpisodes - lastReadEpisode) > 0 ORDER BY lastUpdated DESC")
    suspend fun getNovelsWithUnread(): List<NovelEntity>

    // 新規追加：検索機能
    @Query("SELECT * FROM novels WHERE title LIKE '%' || :keyword || '%'")
    suspend fun searchNovelsByTitle(keyword: String): List<NovelEntity>
}