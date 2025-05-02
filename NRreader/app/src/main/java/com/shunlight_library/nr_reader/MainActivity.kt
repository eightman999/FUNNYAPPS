

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
// MainActivity.kt の先頭付近でのインポート部分を修正
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack  // AutoMirrored バージョンをインポート
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import com.shunlight_library.nr_reader.*
import com.shunlight_library.nr_reader.ui.components.DetailedProgressBar
import com.shunlight_library.nr_reader.ui.components.LoadingDialog
import com.shunlight_library.nr_reader.ui.theme.LocalAppSettings
import com.shunlight_library.nr_reader.ui.theme.NRreaderTheme
import com.shunlight_library.nr_reader.ui.theme.backgroundColorValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.*
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : ComponentActivity() {
    // MainActivity.kt の onCreate メソッドに追加
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide the status bar completely
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Make the app content go edge-to-edge
        enableEdgeToEdge()

        // Hide the system bars and make the content draw under them
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 永続的な権限を確認
        val settingsStore = SettingsStore(this)

        // 必要に応じて初期化処理

        setContent {
            NRreaderTheme {
                NovelReaderApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelReaderApp() {
    val lightBlue = Color(0xFF80C8FF)
    var showWebView by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showNovelList by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var currentNovel by remember { mutableStateOf<Novel?>(null) }

    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val parser = remember { NovelParser(context) }

    // 設定情報を取得
    var selfServerPath by remember { mutableStateOf("") }
    var selfServerAccess by remember { mutableStateOf(false) }
    var hasValidPermission by remember { mutableStateOf(false) }
    var lastReadNovel by remember { mutableStateOf<Novel?>(null) }
    var newNovelCount by remember { mutableStateOf(0) }
    var updatedNovelCount by remember { mutableStateOf(0) }

    // 最後に読んだ小説と新着・更新情報を読み込む関数
    fun loadLastReadAndUpdatedInfo() {
        scope.launch {
            try {
                // 最後に読んだ小説情報を取得
                val prefs = context.getSharedPreferences("novel_reader_prefs", ComponentActivity.MODE_PRIVATE)
                val lastReadNcode = prefs.getString("last_read_ncode", null)
                val lastReadTitle = prefs.getString("last_read_title", null)
                val lastReadEpisode = prefs.getInt("last_read_episode", 1)

                if (lastReadNcode != null && lastReadTitle != null) {
                    lastReadNovel = Novel(
                        title = lastReadTitle,
                        ncode = lastReadNcode,
                        lastReadEpisode = lastReadEpisode,
                        totalEpisodes = 0,
                        unreadCount = 0
                    )
                }

                // 新着・更新情報を取得（実際のDB状態から）
                // この例では簡略化のために仮の値を使用
                newNovelCount = 1  // 実際はDBから取得
                updatedNovelCount = 0  // 実際はDBから取得
            } catch (e: Exception) {
                Log.e("NovelReaderApp", "最後に読んだ小説情報の取得エラー: ${e.message}", e)
            }
        }
    }

    // 重要：設定をちゃんと読み込む
    LaunchedEffect(key1 = Unit) {
        try {
            // 現在の設定値を取得
            selfServerAccess = settingsStore.selfServerAccess.first()
            selfServerPath = settingsStore.selfServerPath.first()

            // 読み込み確認用ログ
            Log.d("NovelReaderApp", "設定読み込み: selfServerAccess=$selfServerAccess, " +
                    "selfServerPath=$selfServerPath")

            // 保存されている権限の有効性を確認
            if (selfServerPath.isNotEmpty()) {
                hasValidPermission = settingsStore.hasPersistedPermission(selfServerPath)
                Log.d("NovelReaderApp", "保存されているパスの権限確認: $hasValidPermission")
            }

            // 最後に読んだ小説情報と新着・更新情報を取得
            loadLastReadAndUpdatedInfo()
        } catch (e: Exception) {
            Log.e("NovelReaderApp", "設定読み込みエラー: ${e.message}", e)
        }
    }

    // DisposableEffect を追加
    DisposableEffect(key1 = Unit) {
        onDispose {
            // クリーンアップ処理
            Log.d("NovelReaderApp", "メイン画面が破棄されました")
        }
    }

    // アプリ起動時に設定情報を一度だけ読み込む
    LaunchedEffect(key1 = Unit) {
        try {
            // 保存されている権限の有効性を確認
            settingsStore.validatePersistedPermissions()

            // サーバーパスの権限がある場合は確認
            if (selfServerPath.isNotEmpty()) {
                hasValidPermission = settingsStore.hasPersistedPermission(selfServerPath)
                Log.d("NovelReaderApp", "保存されているパスの権限確認: $hasValidPermission")

                // 権限がない場合はトーストでユーザーに通知
                if (!hasValidPermission && selfServerAccess) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "自己サーバーへのアクセス権限がありません。設定画面で再設定してください。",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NovelReaderApp", "権限検証エラー: ${e.message}", e)
        }
    }
    // selfServerAccess の設定を別の LaunchedEffect で読み込む
    LaunchedEffect(key1 = Unit) {
        try {
            settingsStore.selfServerAccess.collect { enabled ->
                selfServerAccess = enabled
                Log.d("NovelReaderApp", "サーバーアクセス設定: $enabled")
            }
        } catch (e: Exception) {
            Log.e("NovelReaderApp", "設定読み込みエラー: ${e.message}")
        }
    }

    // 設定画面の表示
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    }
    // 小説一覧の表示
    else if (showNovelList) {
        NovelListScreen(
            selfServerPath = selfServerPath,
            selfServerAccess = selfServerAccess,
            onNovelSelected = { novel ->
                if (selfServerAccess && selfServerPath.isNotEmpty()) {
                    // 自己サーバーモードの場合、常にローカルファイルを参照する
                    val uri = Uri.parse(selfServerPath)
                    val novelUrl = when (uri.scheme) {
                        "file" -> {
                            // ファイルパスの場合
                            val baseDir = uri.path?.let { File(it).parentFile?.path } ?: ""
                            "file://$baseDir/novels/${novel.ncode}/index.html"
                        }
                        "content" -> {
                            // DocumentsProviderからのアクセスパスを構築
                            // ※ここではURIを文字列として組み立て
                            val docId = DocumentsContract.getTreeDocumentId(uri)
                            val childPath = "$docId/novels/${novel.ncode}/index.html"
                            val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, childPath)
                            documentUri.toString()
                        }
                        else -> {
                            // サポートされていないスキームの場合はエラーメッセージを表示
                            Toast.makeText(context, "サポートされていないファイル形式です", Toast.LENGTH_SHORT).show()
                            return@NovelListScreen
                        }
                    }

                    currentTitle = novel.title
                    currentUrl = novelUrl
                    showWebView = true
                    showNovelList = false
                    currentNovel = novel

                    Log.d("NovelReaderApp", "ローカルファイルのURLを構築: $novelUrl")
                } else {
                    // 自己サーバーモードが無効の場合はオンラインモード
                    // この部分も自己サーバーを強制する場合は変更
                    Toast.makeText(context, "自己サーバーモードが無効です。設定を確認してください。", Toast.LENGTH_SHORT).show()
                    // アクセスをブロック
                    return@NovelListScreen
                }
            },
            onBack = { showNovelList = false }
        )
    }
    // WebViewの表示
    else if (showWebView) {
        var currentNovel by remember { mutableStateOf<Novel?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = currentTitle) },
                    navigationIcon = {
                        IconButton(onClick = { showWebView = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* 新しいタブを追加する処理 */ }) {
                            Icon(Icons.Default.Add, contentDescription = "新しいタブを追加")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                WebViewScreen(url = currentUrl, novel = currentNovel)
            }
        }
    }
    // メイン画面の表示
    else {
        // Get the current app settings
        val appSettings = LocalAppSettings.current

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(appSettings.backgroundColorValue)
        ) {
            // 上部セクション - 新着・更新情報
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(lightBlue)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "新着・更新情報",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "新着${newNovelCount}件・更新あり${updatedNovelCount}件",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "最後に開いていた小説",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lastReadNovel?.let { "${it.title} ${it.lastReadEpisode}話" }
                            ?: "まだ小説を読んでいません",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 「小説をさがす」セクション
            item {
                SectionHeader(title = "小説をさがす")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NavButton(
                        title = "ランキング",
                        icon = "🏆",
                        onClick = {
                            currentTitle = "ランキング"
                            currentUrl = "https://yomou.syosetu.com/rank/top/"
                            showWebView = true
                        }
                    )
                    NavButton(
                        title = "PickUp!",
                        icon = "📢",
                        onClick = {
                            currentTitle = "PickUp!"
                            currentUrl = "https://syosetu.com/pickup/list/"
                            showWebView = true
                        }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NavButton(
                        title = "キーワード検索",
                        icon = "🔍",
                        onClick = {
                            currentTitle = "キーワード検索"
                            currentUrl = "https://yomou.syosetu.com/search.php"
                            showWebView = true
                        }
                    )
                    NavButton(
                        title = "詳細検索",
                        icon = "▶",
                        onClick = {
                            currentTitle = "詳細検索"
                            currentUrl = "https://yomou.syosetu.com/search.php"
                            showWebView = true
                        }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NavButton(
                        title = "ノクターンノベルズ",
                        icon = "👑",
                        onClick = {
                            currentTitle = "ノクターンノベルズ"
                            currentUrl = "https://noc.syosetu.com/top/top/"
                            showWebView = true
                        }
                    )
                    NavButton(
                        title = "ムーンライトノベルズ",
                        icon = "👑",
                        onClick = {
                            currentTitle = "ムーンライトノベルズ"
                            currentUrl = "https://mnlt.syosetu.com/top/top/"
                            showWebView = true
                        }
                    )
                }
                // ミッドナイトノベルズの追加
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    NavButton(
                        title = "ミッドナイトノベルズ",
                        icon = "👑",
                        onClick = {
                            currentTitle = "ミッドナイトノベルズ"
                            currentUrl = "https://mid.syosetu.com/top/top/"
                            showWebView = true
                        }
                    )
                }
            }

            // 「小説を読む」セクション
            item {
                SectionHeader(title = "小説を読む")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NavButton(
                        title = "小説一覧",
                        icon = "📚",
                        onClick = {
                            showNovelList = true
                        }
                    )
                    NavButton(title = "最近更新された小説", icon = "▶")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NavButton(title = "最近読んだ小説", icon = "▶")
                    NavButton(title = "作者別・シリーズ別", icon = "▶")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NavButton(title = "タグ検索", icon = "▶")
                    Spacer(modifier = Modifier.width(160.dp))
                }
            }

            // 「オプション」セクション
            item {
                SectionHeader(title = "オプション")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NavButton(title = "ダウンロード状況", icon = "⬇")
                    Spacer(modifier = Modifier.width(160.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NavButton(
                        title = "設定",
                        icon = "⚙",
                        onClick = { showSettings = true }
                    )
                    Spacer(modifier = Modifier.width(160.dp))
                }

                // スペース追加
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun WebViewScreen(url: String, novel: Novel? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val repository = remember { NovelRepository(context) }

    // 設定情報を取得
    var selfServerPath by remember { mutableStateOf("") }

    // 設定情報を読み込む
    LaunchedEffect(key1 = Unit) {
        selfServerPath = settingsStore.selfServerPath.first()
    }

    // URLからエピソード番号を抽出する関数
    fun extractEpisodeNumber(url: String): Int {
        // episode_XXX.html の形式を想定
        val regex = "episode_(\\d+)\\.html".toRegex()
        val matchResult = regex.find(url)
        return matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    // URLをインターセプトして書き換え
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val requestUrl = request.url.toString()

                        // novel オブジェクトがnullでなければ処理を行う
                        if (novel != null) {
                            Log.d("WebViewScreen", "リクエストURL: $requestUrl")

                            // エピソードへのリンクかどうかをチェック（小説サイト内の移動を検出）
                            if (requestUrl.contains("ncode.syosetu.com") ||
                                requestUrl.contains("episode_") ||
                                requestUrl.contains("index.html")) {

                                // エピソード番号またはindex.htmlへの参照を抽出
                                if (requestUrl.contains("episode_")) {
                                    // エピソード番号を抽出
                                    val episodeNum = extractEpisodeNumber(requestUrl)

                                    // ローカルのエピソードファイルURLを構築
                                    val uri = Uri.parse(selfServerPath)
                                    val newUrl = when (uri.scheme) {
                                        "file" -> {
                                            val baseDir = uri.path?.let { File(it).parentFile?.path } ?: ""
                                            "file://$baseDir/novels/${novel.ncode}/episode_$episodeNum.html"
                                        }
                                        "content" -> {
                                            val docId = DocumentsContract.getTreeDocumentId(uri)
                                            val childPath = "$docId/novels/${novel.ncode}/episode_$episodeNum.html"
                                            DocumentsContract.buildDocumentUriUsingTree(uri, childPath).toString()
                                        }
                                        else -> return false
                                    }

                                    Log.d("WebViewScreen", "エピソードリンクを書き換え: $newUrl")
                                    view.loadUrl(newUrl)
                                    return true
                                }
                                // index.htmlへの参照を検出
                                else if (requestUrl.contains("index.html") ||
                                    (requestUrl.contains("ncode.syosetu.com") && !requestUrl.contains("/\\d+/"))) {

                                    // 小説のインデックスページURLを構築
                                    val uri = Uri.parse(selfServerPath)
                                    val newUrl = when (uri.scheme) {
                                        "file" -> {
                                            val baseDir = uri.path?.let { File(it).parentFile?.path } ?: ""
                                            "file://$baseDir/novels/${novel.ncode}/index.html"
                                        }
                                        "content" -> {
                                            val docId = DocumentsContract.getTreeDocumentId(uri)
                                            val childPath = "$docId/novels/${novel.ncode}/index.html"
                                            DocumentsContract.buildDocumentUriUsingTree(uri, childPath).toString()
                                        }
                                        else -> return false
                                    }

                                    Log.d("WebViewScreen", "インデックスリンクを書き換え: $newUrl")
                                    view.loadUrl(newUrl)
                                    return true
                                }
                                // 小説本編ページの番号形式 (ncode.syosetu.com/nXXXXX/YY/)
                                else if (requestUrl.matches("https://ncode.syosetu.com/${novel.ncode}/(\\d+)/?.*".toRegex())) {
                                    // エピソード番号を抽出
                                    val episodeRegex = "https://ncode.syosetu.com/${novel.ncode}/(\\d+)/?.*".toRegex()
                                    val episodeMatch = episodeRegex.find(requestUrl)
                                    val episodeNum = episodeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

                                    // ローカルのエピソードファイルURLを構築
                                    val uri = Uri.parse(selfServerPath)
                                    val newUrl = when (uri.scheme) {
                                        "file" -> {
                                            val baseDir = uri.path?.let { File(it).parentFile?.path } ?: ""
                                            "file://$baseDir/novels/${novel.ncode}/episode_$episodeNum.html"
                                        }
                                        "content" -> {
                                            val docId = DocumentsContract.getTreeDocumentId(uri)
                                            val childPath = "$docId/novels/${novel.ncode}/episode_$episodeNum.html"
                                            DocumentsContract.buildDocumentUriUsingTree(uri, childPath).toString()
                                        }
                                        else -> return false
                                    }

                                    Log.d("WebViewScreen", "小説サイトのリンクをローカルに書き換え: $newUrl")
                                    view.loadUrl(newUrl)
                                    return true
                                }
                            }
                        }

                        // リンク書き換えを行わない場合
                        return false
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)

                        // novelがnullでなく、ncodeが正しい場合のみ更新
                        if (novel != null && novel.ncode.isNotEmpty()) {
                            // エピソード番号を抽出して保存
                            val episodeNum = extractEpisodeNumber(url)
                            repository.saveLastReadEpisode(novel.ncode, episodeNum)

                            // SharedPreferencesに最後に読んだ小説の情報を保存
                            val prefs = context.getSharedPreferences("novel_reader_prefs", ComponentActivity.MODE_PRIVATE)
                            prefs.edit()
                                .putString("last_read_ncode", novel.ncode)
                                .putString("last_read_title", novel.title)
                                .putInt("last_read_episode", episodeNum)
                                .apply()

                            Log.d("WebViewScreen", "最後に読んだエピソードを更新: ${novel.ncode} - エピソード $episodeNum")
                        }
                    }
                }
                settings.javaScriptEnabled = true

                // キャッシュをクリアする
                clearCache(true)
                settings.cacheMode = WebSettings.LOAD_NO_CACHE

                // ファイルアクセスを有効化
                settings.allowFileAccess = true
                settings.allowContentAccess = true

                // 拡大縮小コントロールを有効化
                settings.builtInZoomControls = true
                settings.displayZoomControls = false

                // URLを読み込む
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun SectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = Color.Gray,
            fontSize = 18.sp
        )
    }
}

@Composable
fun NavButton(
    title: String,
    icon: String,
    onClick: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = icon,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 16.sp
        )
    }
}
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
}