// NovelParser.kt の修正部分
package com.shunlight_library.nr_reader

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.*
import java.util.concurrent.atomic.AtomicInteger

class NovelParser(private val context: Context) {

    private val TAG = "NovelParser"

    // 進行状況を追跡するための変数
    private val _processedCount = AtomicInteger(0)
    private var _totalCount = 0

    // 進行状況を取得するプロパティ
    val processedCount: Int get() = _processedCount.get()
    val totalCount: Int get() = _totalCount
    val progress: Float get() = if (_totalCount > 0) processedCount.toFloat() / _totalCount else 0f

    // 進行状況をリセット
    fun resetProgress() {
        _processedCount.set(0)
        _totalCount = 0
    }

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

                // 直接novelsディレクトリをスキャンする方法を優先
                when (uri.scheme) {
                    "file" -> {
                        // ファイルパスからの読み込み
                        val file = File(uri.path ?: "")
                        val baseDir = file.parentFile
                        val novelsDir = File(baseDir, "novels")

                        if (novelsDir.exists() && novelsDir.isDirectory) {
                            Log.d(TAG, "novelsディレクトリを検出: ${novelsDir.absolutePath}")
                            val novelDirs = novelsDir.listFiles { file -> file.isDirectory }

                            // 総数を設定
                            _totalCount = novelDirs?.size ?: 0
                            _processedCount.set(0)

                            novelDirs?.forEach { dir ->
                                try {
                                    val ncode = dir.name
                                    Log.d(TAG, "小説ディレクトリを検出: $ncode")

                                    // index.htmlからタイトルを取得
                                    val indexFile = File(dir, "index.html")
                                    if (indexFile.exists()) {
                                        val novelHtml = indexFile.readText()
                                        val doc = Jsoup.parse(novelHtml)

                                        // 2つの方法でタイトルを探す
                                        var title = doc.title()

                                        // タイトルが見つからない場合はh1タグを探す
                                        if (title.isBlank() || title == "Document") {
                                            title = doc.select("h1").firstOrNull()?.text() ?: "無題の小説"
                                        }

                                        // エピソードファイルの数をカウント
                                        val episodeFiles = dir.listFiles { file ->
                                            file.name.startsWith("episode_") && file.name.endsWith(".html")
                                        }
                                        val episodeCount = episodeFiles?.size ?: 0

                                        Log.d(TAG, "小説情報: タイトル=$title, エピソード数=$episodeCount")
                                        novelList.add(Novel(
                                            title = title,
                                            ncode = ncode,
                                            totalEpisodes = episodeCount
                                        ))
                                    } else {
                                        Log.d(TAG, "index.htmlが見つかりません: ${indexFile.absolutePath}")
                                    }

                                    // 進行状況を更新
                                    _processedCount.incrementAndGet()
                                } catch (e: Exception) {
                                    Log.e(TAG, "小説情報の解析エラー: ${dir.name}", e)
                                    // エラーがあっても進行状況は更新
                                    _processedCount.incrementAndGet()
                                }
                            }
                        } else {
                            Log.e(TAG, "novelsディレクトリが見つかりません: ${novelsDir.absolutePath}")
                            throw FileNotFoundException("novelsディレクトリが見つかりません")
                        }
                    }
                    "content" -> {
                        // ContentProviderからの読み込み
                        Log.d(TAG, "ContentProviderからの読み込み: $uri")

                        val documentFile = DocumentFile.fromTreeUri(context, uri)
                        if (documentFile == null || !documentFile.exists()) {
                            throw IOException("指定されたディレクトリにアクセスできません")
                        }

                        // novelsディレクトリを探す
                        val novelsDir = documentFile.findFile("novels")
                        if (novelsDir != null && novelsDir.exists()) {
                            Log.d(TAG, "novelsディレクトリを検出")
                            val novelDirs = novelsDir.listFiles()

                            // 総数を設定
                            _totalCount = novelDirs.size
                            _processedCount.set(0)

                            novelDirs.forEach { dir ->
                                if (dir.isDirectory) {
                                    try {
                                        val ncode = dir.name
                                        Log.d(TAG, "小説ディレクトリを検出: $ncode")

                                        // index.htmlからタイトルを取得
                                        val indexFile = dir.findFile("index.html")
                                        if (indexFile != null && indexFile.exists()) {
                                            val inputStream = context.contentResolver.openInputStream(indexFile.uri)
                                            val novelHtml = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
                                            val doc = Jsoup.parse(novelHtml)

                                            // 2つの方法でタイトルを探す
                                            var title = doc.title()

                                            // タイトルが見つからない場合はh1タグを探す
                                            if (title.isBlank() || title == "Document") {
                                                title = doc.select("h1").firstOrNull()?.text() ?: "無題の小説"
                                            }

                                            // エピソードファイルの数をカウント
                                            val episodeFiles = dir.listFiles().filter { file ->
                                                val fileName = file.name
                                                fileName!!.startsWith("episode_") && fileName!!.endsWith(".html")
                                            }
                                            val episodeCount = episodeFiles.size

                                            Log.d(TAG, "小説情報: タイトル=$title, エピソード数=$episodeCount")
                                            novelList.add(Novel(
                                                title = title,
                                                ncode = ncode.toString(),
                                                totalEpisodes = episodeCount
                                            ))
                                        } else {
                                            Log.d(TAG, "index.htmlが見つかりません: $ncode")
                                        }

                                        // 進行状況を更新
                                        _processedCount.incrementAndGet()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "小説情報の解析エラー: ${dir.name}", e)
                                        // エラーがあっても進行状況は更新
                                        _processedCount.incrementAndGet()
                                    }
                                }
                            }
                        } else {
                            Log.e(TAG, "novelsディレクトリが見つかりません")
                            throw FileNotFoundException("novelsディレクトリが見つかりません")
                        }
                    }
                    else -> {
                        Log.e(TAG, "未対応のURIスキーム: ${uri.scheme}")
                        throw IllegalArgumentException("未対応のファイル形式です: ${uri.scheme}")
                    }
                }

                // 小説リストが空の場合
                if (novelList.isEmpty()) {
                    Log.d(TAG, "小説が見つかりませんでした")
                    throw FileNotFoundException("小説が見つかりませんでした")
                }

                // キャッシュに結果を保存
                NovelParserCache.cacheNovels(serverPath, novelList)
                Log.d(TAG, "小説リストをキャッシュしました: ${novelList.size}件")

                novelList
            } catch (e: Exception) {
                Log.e(TAG, "小説一覧取得エラー", e)
                throw e
            }
        }
    }

    // キャッシュをクリア
    fun clearCache() {
        NovelParserCache.clearCache()
    }
    // 特定の小説の総エピソード数を取得
    suspend fun getNovelEpisodeCount(serverPath: String, ncode: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(serverPath)
                val baseDir = File(uri.path ?: "").parentFile
                val novelDir = File(baseDir, "novels/$ncode")

                if (!novelDir.exists() || !novelDir.isDirectory) {
                    return@withContext 0
                }

                // episode_XXXX.htmlのファイル数をカウント
                novelDir.listFiles { file ->
                    file.name.startsWith("episode_") && file.name.endsWith(".html")
                }?.size ?: 0
            } catch (e: Exception) {
                Log.e(TAG, "エピソード数取得エラー: $ncode", e)
                0
            }
        }
    }

    // 小説の未読エピソード数を計算
    suspend fun calculateUnreadCount(serverPath: String, novel: Novel): Novel {
        val totalEpisodes = getNovelEpisodeCount(serverPath, novel.ncode)
        val unreadCount = (totalEpisodes - novel.lastReadEpisode).coerceAtLeast(0)

        return novel.copy(
            totalEpisodes = totalEpisodes,
            unreadCount = unreadCount
        )
    }

    // 小説一覧取得時に未読情報も含める
    suspend fun parseNovelListFromServerPathWithUnreadInfo(serverPath: String): List<Novel> {
        val novels = parseNovelListFromServerPath(serverPath)

        return novels.map { novel ->
            calculateUnreadCount(serverPath, novel)
        }
    }
    private suspend fun findIndexHtmlInTree(context: Context, treeUri: Uri): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                // ツリールートのドキュメントIDを取得
                val docId = DocumentsContract.getTreeDocumentId(treeUri)

                // ルートディレクトリのURIを構築
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                // ルートディレクトリ内のファイル一覧を取得
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val childDocId = cursor.getString(0)
                        val displayName = cursor.getString(1)

                        // index.htmlを探す
                        if (displayName == "index.html") {
                            return@withContext DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        }
                    }
                }

                // 見つからなかった場合
                null

            } catch (e: Exception) {
                Log.e("NovelParser", "ディレクトリ探索エラー: ${e.message}")
                null
            }
        }
    }
}


// 3. 未読情報の永続化(SharedPreferences or Room DBを使用)
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