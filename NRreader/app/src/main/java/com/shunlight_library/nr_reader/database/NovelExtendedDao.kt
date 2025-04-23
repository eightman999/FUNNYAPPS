package com.shunlight_library.nr_reader.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 小説の拡張情報にアクセスするためのDAO
 */
@Dao
interface NovelExtendedDao {
    @Query("SELECT * FROM novel_extended_info WHERE ncode = :ncode")
    suspend fun getNovelExtendedInfo(ncode: String): NovelExtendedEntity?

    @Query("SELECT * FROM novel_extended_info WHERE ncode = :ncode")
    fun getNovelExtendedInfoFlow(ncode: String): Flow<NovelExtendedEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(novelExtended: NovelExtendedEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(novelExtendedList: List<NovelExtendedEntity>)

    @Query("SELECT * FROM novel_extended_info WHERE author = :author")
    suspend fun getNovelsByAuthor(author: String): List<NovelExtendedEntity>

    @Query("SELECT * FROM novel_extended_info WHERE mainTags LIKE '%' || :tag || '%' OR subTags LIKE '%' || :tag || '%'")
    suspend fun getNovelsByTag(tag: String): List<NovelExtendedEntity>

    @Query("SELECT * FROM novel_extended_info ORDER BY rating DESC LIMIT :limit")
    suspend fun getTopRatedNovels(limit: Int = 20): List<NovelExtendedEntity>

    @Query("SELECT * FROM novel_extended_info ORDER BY lastUpdateDate DESC LIMIT :limit")
    suspend fun getRecentlyUpdatedNovels(limit: Int = 20): List<NovelExtendedEntity>

    @Query("DELETE FROM novel_extended_info WHERE ncode = :ncode")
    suspend fun deleteByNcode(ncode: String)

    @Query("DELETE FROM novel_extended_info")
    suspend fun deleteAll()
}