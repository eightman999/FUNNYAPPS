package com.shunlight_library.nr_reader.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Unified entity for both internal and external novel data.
 */
@Entity(
    tableName = "novels",
    indices = [
        Index(name = "idx_novels_last_update", value = ["last_update_date"]),
        Index(
            name = "idx_novels_update_check",
            value = ["ncode", "rating", "total_episodes", "general_all_no", "last_updated"]
        )
    ]
)
data class UnifiedNovelEntity(
    @PrimaryKey
    @ColumnInfo(name = "ncode")
    val ncode: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "author")
    val author: String,

    @ColumnInfo(name = "synopsis")
    val synopsis: String?,

    @ColumnInfo(name = "main_tags")
    val mainTags: String?,  // Comma-separated string

    @ColumnInfo(name = "sub_tags")
    val subTags: String?,   // Comma-separated string

    @ColumnInfo(name = "rating")
    val rating: Int = 0,

    @ColumnInfo(name = "total_episodes")
    val totalEpisodes: Int = 0,

    @ColumnInfo(name = "last_read_episode")
    val lastReadEpisode: Int = 1,

    @ColumnInfo(name = "last_update_date")
    val lastUpdateDate: String?,

    @ColumnInfo(name = "general_all_no")
    val generalAllNo: Int = 0,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_synced")
    val lastSynced: Long = System.currentTimeMillis()
)