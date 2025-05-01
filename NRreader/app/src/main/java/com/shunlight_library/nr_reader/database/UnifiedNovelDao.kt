package com.shunlight_library.nr_reader.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDescDao {
    @Query("SELECT * FROM novels_descs ORDER BY last_update_date DESC")
    fun getAllNovels(): Flow<List<NovelDescEntity>>

    @Query("SELECT * FROM novels_descs ORDER BY last_update_date DESC")
    suspend fun getAllNovelsSync(): List<NovelDescEntity>

    @Query("SELECT COUNT(*) FROM novels_descs")
    suspend fun getNovelCount(): Int

    @Query("SELECT * FROM novels_descs WHERE ncode = :ncode")
    suspend fun getNovelByNcode(ncode: String): NovelDescEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(novel: NovelDescEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(novels: List<NovelDescEntity>)

    @Query("DELETE FROM novels_descs WHERE ncode = :ncode")
    suspend fun deleteNovelByNcode(ncode: String)

    @Query("UPDATE novels_descs SET total_ep = :totalCount WHERE ncode = :ncode")
    suspend fun updateTotalEpisodes(ncode: String, totalCount: Int)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE ncode = :ncode ORDER BY episode_no ASC")
    fun getEpisodesByNcode(ncode: String): Flow<List<InternalEpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE ncode = :ncode AND episode_no = :episodeNo")
    suspend fun getEpisode(ncode: String, episodeNo: String): InternalEpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(episode: InternalEpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<InternalEpisodeEntity>)
}

@Dao
interface LastReadDao {
    @Query("SELECT * FROM rast_read_novel WHERE ncode = :ncode ORDER BY date DESC LIMIT 1")
    suspend fun getLastReadByNcode(ncode: String): InternalLastReadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lastRead: InternalLastReadEntity)

    @Query("DELETE FROM rast_read_novel WHERE ncode = :ncode")
    suspend fun deleteByNcode(ncode: String)
}