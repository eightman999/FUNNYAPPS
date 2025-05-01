package com.shunlight_library.nr_reader.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 小説データベースへの統合アクセス用DAO
 */
@Dao
interface UnifiedNovelDao {

    // **小説関連の操作**

    // 全小説を取得（Flow版）
    @Query("SELECT * FROM novels_descs ORDER BY last_update_date DESC")
    fun getAllNovels(): Flow<List<ExternalNovelEntity>>

    // 全小説を取得（同期版）
    @Query("SELECT * FROM novels_descs ORDER BY last_update_date DESC")
    suspend fun getAllNovelsSync(): List<ExternalNovelEntity>

    // 小説の総数を取得
    @Query("SELECT COUNT(*) FROM novels_descs")
    suspend fun getNovelCount(): Int

    // 特定の小説を取得
    @Query("SELECT * FROM novels_descs WHERE n_code = :ncode")
    suspend fun getNovelByNcode(ncode: String): ExternalNovelEntity?

    // 小説を挿入または更新
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNovel(novel: ExternalNovelEntity)

    // 小説を削除
    @Query("DELETE FROM novels_descs WHERE n_code = :ncode")
    suspend fun deleteNovelByNcode(ncode: String)

    // **エピソード関連の操作**

    // 特定の小説のエピソード一覧を取得
    @Query("SELECT * FROM episodes WHERE ncode = :ncode ORDER BY CAST(episode_no AS INTEGER)")
    suspend fun getEpisodesForNovel(ncode: String): List<EpisodeEntity>

    // 特定のエピソードを取得
    @Query("SELECT * FROM episodes WHERE ncode = :ncode AND episode_no = :episodeNo")
    suspend fun getEpisode(ncode: String, episodeNo: String): EpisodeEntity?

    // **検索機能**

    // タグで小説を検索
    @Query("SELECT * FROM novels_descs WHERE main_tag LIKE '%' || :tag || '%' OR sub_tag LIKE '%' || :tag || '%'")
    suspend fun searchNovelsByTag(tag: String): List<ExternalNovelEntity>

    // 作者で小説を検索
    @Query("SELECT * FROM novels_descs WHERE author = :author")
    suspend fun getNovelsByAuthor(author: String): List<ExternalNovelEntity>

    // 評価順に小説を取得
    @Query("SELECT * FROM novels_descs ORDER BY rating DESC LIMIT :limit")
    suspend fun getTopRatedNovels(limit: Int = 20): List<ExternalNovelEntity>

    // 最後に読んだエピソード番号を更新
    @Query("UPDATE novels_descs SET last_read_episode = :episodeNum WHERE n_code = :ncode")
    suspend fun updateLastReadEpisode(ncode: String, episodeNum: Int)

    // 総エピソード数を更新
    @Query("UPDATE novels_descs SET total_episodes = :totalCount WHERE n_code = :ncode")
    suspend fun updateTotalEpisodes(ncode: String, totalCount: Int)

    // 複数の小説を一括挿入
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(novels: List<NovelEntity>)
    // **統合トランザクション**

    // 外部データベースと内部データベースの同期
    @Transaction
    suspend fun syncNovelWithExternalDB(
        Novel:UnifiedNovelEntity
    ) {
        // 必要に応じて内部DBにデータを同期
    }

    abstract fun insertOrUpdateNovel(novel: ExternalNovel)
}