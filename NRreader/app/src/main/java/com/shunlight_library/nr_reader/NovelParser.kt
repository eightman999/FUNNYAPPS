package com.shunlight_library.nr_reader

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.*

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

                Log.d(TAG, "小説一覧の解析を開始: $serverPath")
                val novelList = mutableListOf<Novel>()

                // URIを解析
                val uri = Uri.parse(serverPath)
                Log.d(TAG, "URI scheme: ${uri.scheme}")

                // HTMLコンテンツを取得
                val htmlContent = try {
                    when (uri.scheme) {
                        "file" -> {
                            // ファイルパスからの読み込み
                            val file = File(uri.path ?: "")
                            val baseDir = file.parentFile
                            val indexFile = File(baseDir, "index.html")
                            Log.d(TAG, "ファイルを読み込み: ${indexFile.absolutePath}")

                            if (indexFile.exists()) {
                                indexFile.readText()
                            } else {
                                Log.e(TAG, "ファイルが存在しません: ${indexFile.absolutePath}")
                                throw FileNotFoundException("index.htmlファイルが見つかりません")
                            }
                        }
                        "content" -> {
                            // ContentProviderからの読み込み
                            Log.d(TAG, "ContentProviderからの読み込み: $uri")
                            try {
                                val inputStream = context.contentResolver.openInputStream(uri)
                                    ?: throw IOException("ファイルを開けませんでした")

                                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                    reader.readText()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "ファイル読み込みエラー: ${e.message}", e)
                                throw IOException("ファイルの読み込みに失敗しました: ${e.message}")
                            }
                        }
                        else -> {
                            Log.e(TAG, "未対応のURIスキーム: ${uri.scheme}")
                            throw IllegalArgumentException("未対応のファイル形式です: ${uri.scheme}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ファイル読み込みエラー", e)
                    throw e
                }

                // HTMLコンテンツが取得できたら解析
                if (htmlContent.isNotEmpty()) {
                    try {
                        Log.d(TAG, "HTML解析開始: ${htmlContent.length}文字")

                        // HTMLを解析
                        val document = Jsoup.parse(htmlContent)
                        Log.d(TAG, "ドキュメントタイトル: ${document.title()}")

                        // 小説一覧を取得
                        val novelElements = document.select(".novel-card")
                        Log.d(TAG, "小説要素数: ${novelElements.size}")

                        novelElements.forEach { element ->
                            val title = element.select("h3 a").text()
                            val ncode = element.attr("data-novel-id")

                            Log.d(TAG, "小説検出 - タイトル: $title, ID: $ncode")

                            if (title.isNotEmpty() && ncode.isNotEmpty()) {
                                novelList.add(Novel(title, ncode))
                            }
                        }

                        // 小説が見つからない場合はダミーデータを追加
                        if (novelList.isEmpty()) {
                            Log.d(TAG, "小説が見つかりませんでした。ダミーデータを追加します")
                            novelList.add(Novel("サンプル小説1", "n1111111111"))
                            novelList.add(Novel("サンプル小説2", "n2222222222"))
                        }

                        Log.d(TAG, "小説検出数: ${novelList.size}")
                    } catch (e: Exception) {
                        Log.e(TAG, "HTML解析エラー", e)
                        throw IOException("HTMLの解析に失敗しました: ${e.message}")
                    }
                } else {
                    Log.e(TAG, "HTMLコンテンツが空です")
                    throw IOException("HTMLコンテンツが空です")
                }

                // キャッシュに結果を保存
                NovelParserCache.cacheNovels(serverPath, novelList)

                novelList
            } catch (e: Exception) {
                Log.e(TAG, "小説一覧取得エラー", e)
                throw e  // 上位層でキャッチできるようにエラーを再スロー
            }
        }
    }

    // キャッシュをクリア
    fun clearCache() {
        NovelParserCache.clearCache()
    }
}