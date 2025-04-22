package com.shunlight_library.nr_reader

data class Novel(
    val title: String,
    val ncode: String,
    val lastReadEpisode: Int = 1
)