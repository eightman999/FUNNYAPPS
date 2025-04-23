package com.shunlight_library.nr_reader.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 外部DBの小説概要情報テーブル (novels_descs)
 */
@Entity(
    tableName = "novels_descs",
    indices = [
        Index(name = "idx_novels_last_update", value = ["last_update_date"]),
        Index(
            name = "idx_novels_update_check",
            value = ["ncode", "rating", "total_ep", "general_all_no", "updated_at"]
        )
    ]
)
data class ExternalNovelEntity(
    @PrimaryKey
    val ncode: String,
    val title: String,
    val author: String,
    val Synopsis: String?, // 注: DBスキーマに合わせて大文字始まり
    val main_tag: String?,
    val sub_tag: String?,
    val rating: Int?,
    val last_update_date: String?,
    val total_ep: Int?,
    val general_all_no: Int?,
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