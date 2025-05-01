package com.shunlight_library.nr_reader.database

import androidx.annotation.NonNull
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 内部DB用の小説概要情報テーブル (novels_descs)
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
data class NovelDescEntity(
    @PrimaryKey
    @ColumnInfo(name = "ncode")
    val ncode: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "author")
    val author: String,

    @ColumnInfo(name = "Synopsis")
    val Synopsis: String?,

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
 * 内部DB用のエピソード情報テーブル (episodes)
 */
@Entity(
    tableName = "episodes",
    primaryKeys = ["ncode", "episode_no"],
    indices = [
        Index(name = "idx_episodes_ncode", value = ["ncode", "episode_no"])
    ]
)
data class InternalEpisodeEntity(
    val ncode: String,
    val episode_no: String,
    val body: String?,
    val e_title: String?,
    val update_time: String?
)

/**
 * 内部DB用の最終読込情報テーブル (rast_read_novel)
 */
@Entity(
    tableName = "rast_read_novel",
    primaryKeys = ["ncode", "date"],
    indices = [Index(value = ["ncode", "date"], name = "idx_last_read")]
)
data class InternalLastReadEntity(
    @NonNull
    @ColumnInfo(name = "ncode")
    val ncode: String,

    @NonNull
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "episode_no")
    val episode_no: Int?
)