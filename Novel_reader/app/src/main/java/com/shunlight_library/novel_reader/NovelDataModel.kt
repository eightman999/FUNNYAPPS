package com.shunlight_library.novel_reader

data class Novel(
    val title: String,
    val ncode: String,
    val author: String,
    val mainTag: String,
    val subTag: String,
    val lastReadEpisode: Int = 1,
    val totalEpisodes: Int = 0,
    val unreadCount: Int = 0
)