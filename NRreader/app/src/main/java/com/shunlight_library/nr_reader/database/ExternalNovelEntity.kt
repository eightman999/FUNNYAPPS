package com.shunlight_library.nr_reader.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 外部DBの小説概要情報テーブル (novels_descs)
 * エラーメッセージに基づいて修正：n_codeカラムをncodeフィールドにマッピング
 */
@Entity(
    tableName = "novels_descs",
    indices = [
        Index(name = "idx_novels_last_update", value = ["last_update_date"]),
        Index(
            name = "idx_novels_update_check",
            value = ["n_code", "rating", "total_ep", "general_all_no", "updated_at"]
        )
    ]
)
data class ExternalNovelEntity(
    @ColumnInfo(name = "n_code")
    @PrimaryKey
    val ncode: String,  // これは変更しない (内部表現と外部表現のマッピング)

    @ColumnInfo(name = "title")
    val title: String,  // notNull=true に変更

    @ColumnInfo(name = "author")
    val author: String,  // notNull=true に変更

    @ColumnInfo(name = "Synopsis")
    val Synopsis: String?,  // 大文字始まりはそのまま

    @ColumnInfo(name = "main_tag")
    val main_tag: String?,

    @ColumnInfo(name = "sub_tag")
    val sub_tag: String?,

    @ColumnInfo(name = "rating")
    val rating: Int?,

    @ColumnInfo(name = "last_update_date")
    val last_update_date: String?,

    @ColumnInfo(name = "total_ep")
    val total_ep: Int?,

    @ColumnInfo(name = "general_all_no")
    val general_all_no: Int?,

    @ColumnInfo(name = "updated_at")
    val updated_at: String?
)

/**
 * 外部DBのエピソード情報テーブル (episodes)
 */
@Entity(
    tableName = "episodes",
    primaryKeys = ["ncode", "episode_no"],
    indices = [
        Index(name = "idx_episodes_ncode", value = ["ncode", "episode_no"])
    ]
)
data class EpisodeEntity(
    val ncode: String,
    val episode_no: String, // 文字列型 (DBスキーマに合わせる)
    val body: String?,
    val e_title: String?,
    val update_time: String?
)

/**
 * 外部DBの最終読込情報テーブル (rast_read_novel)
 */
@Entity(
    tableName = "rast_read_novel",
    indices = [
        Index(name = "idx_last_read", value = ["ncode", "date"])
    ]
)
data class LastReadNovelEntity(
    @PrimaryKey
    val ncode: String,
    val date: String?,
    val episode_no: Int?
)

/**
 * 内部表現用の小説データモデル
 * (外部DBと内部データモデルの橋渡し役)
 */
data class ExternalNovel(
    val title: String,
    val ncode: String,
    val author: String,
    val synopsis: String?,
    val mainTags: List<String>,
    val subTags: List<String>,
    val rating: Int,
    val lastUpdateDate: String?,
    val totalEpisodes: Int,
    val generalAllNo: Int,
    val lastReadEpisode: Int = 1,
    val unreadCount: Int = 0
)