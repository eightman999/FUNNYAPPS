// NovelDao.kt
package com.shunlight_library.nr_reader.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query("SELECT * FROM novels ORDER BY lastUpdated DESC")
    fun getAllNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE ncode = :ncode")
    suspend fun getNovelByNcode(ncode: String): NovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(novel: NovelEntity)

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
}