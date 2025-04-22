package com.shunlight_library.nr_reader

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File

class NovelParser(private val context: Context) {

    private val TAG = "NovelParser"

    suspend fun parseNovelListFromServerPath(serverPath: String): List<Novel> {
        return withContext(Dispatchers.IO) {
            try {
                // キャッシュをチェック
                if (NovelParserCache.hasCachedNovels(serverPath)) {
                    Log.d(TAG, "キャッシュから小説リストを返します")
                    return@withContext NovelParserCache.getCachedNovels()
                }

                Log.d(TAG, "Parsing novel list from: $serverPath")
                val novelList = mutableListOf<Novel>()

                // URIを解析
                val uri = Uri.parse(serverPath)
                Log.d(TAG, "URI scheme: ${uri.scheme}")

                // HTMLコンテンツを取得
                val htmlContent = when (uri.scheme) {
                    "file" -> {
                        // ファイルパスからの読み込み
                        val file = File(uri.path ?: "")
                        val baseDir = file.parentFile
                        val indexFile = File(baseDir, "index.html")
                        Log.d(TAG, "Reading from file: ${indexFile.absolutePath}")

                        if (indexFile.exists()) {
                            indexFile.readText()
                        } else {
                            Log.e(TAG, "Index file does not exist: ${indexFile.absolutePath}")
                            return@withContext emptyList<Novel>()
                        }
                    }
                    "content" -> {
                        // ContentProviderからの読み込み
                        Log.d(TAG, "Reading from content URI")
                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                reader.readText()
                            }
                        } else {
                            Log.e(TAG, "Failed to open input stream from URI")
                            return@withContext emptyList<Novel>()
                        }
                    }
                    else -> {
                        Log.e(TAG, "Unsupported URI scheme: ${uri.scheme}")
                        return@withContext emptyList<Novel>()
                    }
                }

                // 親ディレクトリからindex.htmlを読み込む
                if (htmlContent.isNotEmpty()) {
                    Log.d(TAG, "HTML content length: ${htmlContent.length}")

                    // HTMLを解析
                    val document = Jsoup.parse(htmlContent)
                    Log.d(TAG, "Document title: ${document.title()}")

                    // 小説一覧を取得
                    val novelElements = document.select(".novel-card")
                    Log.d(TAG, "Found ${novelElements.size} novel elements")

                    novelElements.forEach { element ->
                        val title = element.select("h3 a").text()
                        val ncode = element.attr("data-novel-id")

                        Log.d(TAG, "Novel found - Title: $title, ID: $ncode")

                        if (title.isNotEmpty() && ncode.isNotEmpty()) {
                            novelList.add(Novel(title, ncode))
                        }
                    }

                    Log.d(TAG, "Total novels added: ${novelList.size}")
                }

                // キャッシュに結果を保存
                NovelParserCache.cacheNovels(serverPath, novelList)

                novelList
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing novel list", e)
                emptyList()
            }
        }
    }

    // キャッシュをクリア
    fun clearCache() {
        NovelParserCache.clearCache()
    }
}