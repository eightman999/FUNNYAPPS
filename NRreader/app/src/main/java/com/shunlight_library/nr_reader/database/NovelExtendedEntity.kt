package com.shunlight_library.nr_reader.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 小説の拡張情報を保存するためのエンティティ
 * 外部DBの情報を内部DBに反映する際に使用
 */
@Entity(tableName = "novel_extended_info")
data class NovelExtendedEntity(
    @PrimaryKey
    val ncode: String,
    val author: String,
    val synopsis: String,
    val mainTags: String,  // カンマ区切りの文字列
    val subTags: String,   // カンマ区切りの文字列
    val rating: Int,
    val lastUpdateDate: String,
    val generalAllNo: Int,
    val lastSynced: Long = System.currentTimeMillis()
)