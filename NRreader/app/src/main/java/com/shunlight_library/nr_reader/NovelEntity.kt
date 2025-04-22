// NovelEntity.kt
package com.shunlight_library.nr_reader.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey val ncode: String,
    val title: String,
    val lastReadEpisode: Int = 1,
    val totalEpisodes: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)