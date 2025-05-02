package com.shunlight_library.nr_reader

/**
 * 外部DBと内部DBの間のデータ変換に使用するモデルクラス
 */
data class ExternalNovel(
    val title: String,
    val ncode: String,
    val author: String,
    val synopsis: String?,
    val mainTags: List<String>,
    val subTags: List<String>,
    val rating: Int = 0,
    val lastUpdateDate: String?,
    val totalEpisodes: Int = 0,
    val generalAllNo: Int = 0,
    val lastReadEpisode: Int = 1,
    val unreadCount: Int = 0
)
