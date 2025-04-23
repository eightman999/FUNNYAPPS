package com.shunlight_library.nr_reader.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * 外部データベースにアクセスするためのDAO（データアクセスオブジェクト）
 */
@Dao
interface ExternalNovelDao {
    // 小説一覧の取得
    @Query("SELECT * FROM novels_descs ORDER BY last_update_date DESC")
    fun getAllNovels(): Flow<List<ExternalNovelEntity>>

    // 非Flow版の小説一覧取得
    @Query("SELECT * FROM novels_descs ORDER BY last_update_date DESC")
    suspend fun getAllNovelsSync(): List<ExternalNovelEntity>

    // 小説の総数を取得
    @Query("SELECT COUNT(*) FROM novels_descs")
    suspend fun getNovelCount(): Int

    // 特定の小説情報を取得
    @Query("SELECT * FROM novels_descs WHERE ncode = :ncode")
    suspend fun getNovelByNcode(ncode: String): ExternalNovelEntity?

    // 最終更新日でフィルタリングした小説一覧を取得
    @Query("SELECT * FROM novels_descs WHERE last_update_date >= :date ORDER BY last_update_date DESC")
    suspend fun getNovelsUpdatedSince(date: String): List<ExternalNovelEntity>

    // 特定の小説のエピソード一覧を取得
    @Query("SELECT * FROM episodes WHERE ncode = :ncode ORDER BY CAST(episode_no AS INTEGER)")
    suspend fun getEpisodesForNovel(ncode: String): List<EpisodeEntity>

    // 特定の小説の特定のエピソードを取得
    @Query("SELECT * FROM episodes WHERE ncode = :ncode AND episode_no = :episodeNo")
    suspend fun getEpisode(ncode: String, episodeNo: String): EpisodeEntity?

    // 特定の小説の最終更新エピソードを取得
    @Query("SELECT * FROM episodes WHERE ncode = :ncode ORDER BY update_time DESC LIMIT 1")
    suspend fun getLatestEpisode(ncode: String): EpisodeEntity?

    // エピソードの総数を取得
    @Query("SELECT COUNT(*) FROM episodes WHERE ncode = :ncode")
    suspend fun getEpisodeCount(ncode: String): Int

    // 最後に読んだ小説情報を取得
    @Query("SELECT * FROM rast_read_novel ORDER BY date DESC LIMIT 1")
    suspend fun getLastReadNovel(): LastReadNovelEntity?

    // 特定の小説の最後に読んだ情報を取得
    @Query("SELECT * FROM rast_read_novel WHERE ncode = :ncode")
    suspend fun getLastReadForNovel(ncode: String): LastReadNovelEntity?

    // 最後に読んだ小説一覧を取得 (最新順)
    @Query("SELECT * FROM rast_read_novel ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentlyReadNovels(limit: Int = 10): List<LastReadNovelEntity>

    // タグで小説を検索
    @Query("SELECT * FROM novels_descs WHERE main_tag LIKE '%' || :tag || '%' OR sub_tag LIKE '%' || :tag || '%'")
    suspend fun searchNovelsByTag(tag: String): List<ExternalNovelEntity>

    // キーワードで小説を検索 (タイトル、作者、あらすじを対象)
    @Query("SELECT * FROM novels_descs WHERE title LIKE '%' || :keyword || '%' OR author LIKE '%' || :keyword || '%' OR Synopsis LIKE '%' || :keyword || '%'")
    suspend fun searchNovelsByKeyword(keyword: String): List<ExternalNovelEntity>

    // 評価順に小説を取得
    @Query("SELECT * FROM novels_descs ORDER BY rating DESC LIMIT :limit")
    suspend fun getTopRatedNovels(limit: Int = 20): List<ExternalNovelEntity>

    // 更新が古い順に小説を取得（更新チェック用）
    @Query("SELECT * FROM novels_descs ORDER BY updated_at ASC LIMIT :limit")
    suspend fun getNovelsForUpdateCheck(limit: Int = 50): List<ExternalNovelEntity>

    // エピソード更新日時の範囲で小説を検索
    @Query("SELECT DISTINCT n.* FROM novels_descs n JOIN episodes e ON n.ncode = e.ncode WHERE e.update_time BETWEEN :startDate AND :endDate ORDER BY e.update_time DESC")
    suspend fun getNovelsWithEpisodesUpdatedBetween(startDate: String, endDate: String): List<ExternalNovelEntity>

    // 作者で小説を検索
    @Query("SELECT * FROM novels_descs WHERE author = :author")
    suspend fun getNovelsByAuthor(author: String): List<ExternalNovelEntity>

    // 内部DBと同期するためのトランザクション
    @Transaction
    suspend fun syncNovelWithInternalDB(externalNovel: ExternalNovelEntity, lastRead: LastReadNovelEntity?) {
        // 実装はリポジトリで行う (この関数はトランザクション境界を提供)
    }
}