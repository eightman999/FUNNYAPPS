package com.shunlight_library.nr_reader

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object NovelParserCache {
    private val TAG = "NovelParserCache"

    // キャッシュされた小説リスト
    private var cachedNovels: List<Novel>? = null

    // 最後に使用したサーバーパス
    private var lastServerPath: String? = null

    // キャッシュをクリア
    fun clearCache() {
        cachedNovels = null
        lastServerPath = null
        Log.d(TAG, "キャッシュをクリアしました")
    }

    // 小説リストが既にキャッシュされているか確認
    fun hasCachedNovels(serverPath: String): Boolean {
        return cachedNovels != null && lastServerPath == serverPath
    }

    // キャッシュから小説リストを取得
    fun getCachedNovels(): List<Novel> {
        return cachedNovels ?: emptyList()
    }

    // 小説リストをキャッシュに保存
    fun cacheNovels(serverPath: String, novels: List<Novel>) {
        cachedNovels = novels
        lastServerPath = serverPath
        Log.d(TAG, "${novels.size}件の小説をキャッシュしました - パス: $serverPath")
    }
}