package com.shunlight_library.novel_reader

import android.content.Context

class NovelRepository(private val context: Context) {
    private val sharedPrefs = context.getSharedPreferences("novel_prefs", Context.MODE_PRIVATE)

    // 最後に読んだエピソード番号を保存
    fun saveLastReadEpisode(ncode: String, episodeNum: Int) {
        sharedPrefs.edit().putInt("last_read_$ncode", episodeNum).apply()
    }

    // 最後に読んだエピソード番号を取得
    fun getLastReadEpisode(ncode: String): Int {
        return sharedPrefs.getInt("last_read_$ncode", 1)
    }
}