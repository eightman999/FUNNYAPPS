package com.shunlight_library.nr_reader

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import com.shunlight_library.nr_reader.ui.theme.LocalAppSettings
import com.shunlight_library.nr_reader.ui.theme.NRreaderTheme
import com.shunlight_library.nr_reader.ui.theme.backgroundColorValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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


    // 重要：設定をちゃんと読み込む
    LaunchedEffect(key1 = Unit) {
        try {
            // 現在の設定値を取得
            selfServerAccess = settingsStore.selfServerAccess.first()
            selfServerPath = settingsStore.selfServerPath.first()

            // 読み込み確認用ログ
            Log.d("NovelReaderApp", "設定読み込み: selfServerAccess=$selfServerAccess, selfServerPath=$selfServerPath")

            // 保存されている権限の有効性を確認
            if (selfServerPath.isNotEmpty()) {
                hasValidPermission = settingsStore.hasPersistedPermission(selfServerPath)
                Log.d("NovelReaderApp", "保存されているパスの権限確認: $hasValidPermission")
            }
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
    // WebViewの表示
    else if (showWebView) {
        var currentNovel by remember { mutableStateOf<Novel?>(null) }

        // URLからnovelを検索する関数
        LaunchedEffect(key1 = currentUrl) {
            // 小説を表示する場合は、novelオブジェクトを取得
            if (currentUrl.contains("/novels/") && currentUrl.contains("/episode_")) {
                // URLからncodeを抽出
                val ncodeRegex = "/novels/([^/]+)/".toRegex()
                val ncodeMatch = ncodeRegex.find(currentUrl)
                val ncode = ncodeMatch?.groupValues?.get(1) ?: ""

                if (ncode.isNotEmpty()) {
                    // リポジトリから該当する小説情報を取得
                    val application = context.applicationContext as NovelReaderApplication
                    val repository = application.repository
                    repository.getAllNovels().collect { allNovels ->
                        currentNovel = allNovels.find { it.ncode == ncode }
                        // 1回だけ収集したら終了
                        return@collect
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = currentTitle) },
                    navigationIcon = {
                        IconButton(onClick = { showWebView = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
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
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "新着1件・更新あり0件",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "最後に開いていた小説",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Re：ゼロから始める異世界生活 1話",
                        color = Color.White,
                        fontSize = 24.sp,
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
                    NavButton(
                        title = "設定",
                        icon = "⚙",
                        onClick = { showSettings = true }
                    )
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
    val application = context.applicationContext as NovelReaderApplication
    val repository = application.repository
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }

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
                            scope.launch {
                                repository.updateLastReadEpisode(novel.ncode, episodeNum)
                                Log.d("WebViewScreen", "最後に読んだエピソードを更新: ${novel.ncode} - エピソード $episodeNum")
                            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelListScreen(
    selfServerPath: String,
    selfServerAccess: Boolean,
    onNovelSelected: (Novel) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val parser = remember { NovelParser(context) }
    val application = context.applicationContext as NovelReaderApplication
    val repository = application.repository

    var novels by remember { mutableStateOf<List<Novel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isInitialLoading by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasValidPermission by remember { mutableStateOf(false) }

    // 進行状況を追跡する変数
    var progress by remember { mutableStateOf(0f) }
    var processedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var progressMessage by remember { mutableStateOf("小説データを読み込んでいます...") }

    // 権限のチェックとデータ読み込みを分離
    LaunchedEffect(key1 = selfServerPath, key2 = selfServerAccess) {
        Log.d("NovelListScreen", "LaunchedEffect開始 - selfServerAccess=$selfServerAccess, selfServerPath=$selfServerPath")

        try {
            // 自己サーバーの有効性チェック
            if (!selfServerAccess || selfServerPath.isEmpty()) {
                Log.d("NovelListScreen", "自己サーバーモードが無効またはパスが空")
                errorMessage = "自己サーバーモードが無効です。設定画面で有効にしてください。"
                isLoading = false
                return@LaunchedEffect
            }

            // 権限のチェック
            hasValidPermission = settingsStore.hasPersistedPermission(selfServerPath)
            Log.d("NovelListScreen", "権限チェック: $hasValidPermission")

            if (!hasValidPermission) {
                errorMessage = "自己サーバーへのアクセス権限がありません。設定画面で再設定してください。"
                isLoading = false
                return@LaunchedEffect
            }

            // ここまで来たら、自己サーバーモードは有効で権限もある
            Log.d("NovelListScreen", "自己サーバーモードは有効で権限もあります。小説データを読み込みます。")

            // DBに小説が存在するか確認
            val hasNovels = repository.hasAnyNovels()
            if (hasNovels) {
                // DBから小説を読み込み
                Log.d("NovelListScreen", "DBから小説データを読み込みます")
                progressMessage = "保存済みの小説データを読み込んでいます..."
                repository.getAllNovels().collect { allNovels ->
                    novels = allNovels
                    isLoading = false

                    // バックグラウンドで更新チェック
                    if (repository.needsUpdate()) {
                        isUpdating = true
                        Log.d("NovelListScreen", "バックグラウンドで更新チェックを開始")
                        progressMessage = "小説の更新をチェックしています..."

                        // バックグラウンドでアップデートを実行
                        scope.launch {
                            try {
                                progress = 0f
                                val updatedNovels = repository.getUpdatedNovels(selfServerPath)

                                // 更新中の進行状況を監視
                                while (isUpdating) {
                                    progress = repository.progress
                                    processedCount = repository.processedCount
                                    totalCount = repository.totalCount

                                    // 完了したら終了
                                    if (progress >= 1f) {
                                        isUpdating = false
                                    }

                                    //delay(100) // 少し待機
                                }

                                // タイムスタンプを更新
                                repository.saveLastUpdateTimestamp(System.currentTimeMillis())

                                // 更新がある場合のみトースト表示
                                if (updatedNovels.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "${updatedNovels.size}作品に更新がありました",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("NovelListScreen", "更新チェック中にエラー: ${e.message}", e)
                                isUpdating = false
                            }
                        }
                    }
                }
            } else {
                // 初回読み込み：ファイルシステムからスキャンしてDBに保存
                Log.d("NovelListScreen", "初回読み込み: ファイルシステムから小説データをスキャン")
                isInitialLoading = true
                progressMessage = "小説データを初めて読み込んでいます..."

                // 小説解析の進行状況を監視
                scope.launch {
                    while (isInitialLoading) {
                        progress = parser.progress
                        processedCount = parser.processedCount
                        totalCount = parser.totalCount
                        //delay(100) // 少し待機
                    }
                }

                try {
                    // NovelParserから小説リストを取得
                    val parsedNovels = parser.parseNovelListFromServerPath(selfServerPath)
                    Log.d("NovelListScreen", "取得した小説数: ${parsedNovels.size}")

                    if (parsedNovels.isNotEmpty()) {
                        // スキャンが終わったら、DBへの保存処理に進む
                        progressMessage = "小説データをデータベースに保存しています..."
                        progress = 0f // プログレスバーをリセット
                        processedCount = 0
                        totalCount = parsedNovels.size

                        // 一括でDBに保存（書き込み中の進捗表示）
                        withContext(Dispatchers.Default) {
                            val novelCount = parsedNovels.size
                            val batchSize = 50 // バッチサイズを調整
                            val batches = parsedNovels.chunked(batchSize)

                            batches.forEachIndexed { index, batch ->
                                // 各小説のエピソード数を確認
                                val updatedBatch = batch.map { novel ->
                                    val episodeCount = repository.countEpisodesFromFileSystem(selfServerPath, novel.ncode)
                                    novel.copy(totalEpisodes = episodeCount)
                                }

                                // バッチ単位でDBに保存
                                repository.saveNovelsInBatch(updatedBatch)

                                // 進行状況を更新
                                processedCount = (index + 1) * batchSize
                                if (processedCount > novelCount) processedCount = novelCount
                                progress = processedCount.toFloat() / novelCount

                                // 少し待機して進行状況を表示
                                //delay(10)
                            }
                        }

                        // タイムスタンプを更新
                        repository.saveLastUpdateTimestamp(System.currentTimeMillis())

                        // すべて完了したらFlowから小説データを読み込む
                        progressMessage = "完了しました！"
                        progress = 1f

                        // 若干遅延を入れてユーザーに完了を認識させる
                        //delay(500)

                        // 読み込んだデータを表示
                        repository.getAllNovels().collect { allNovels ->
                            novels = allNovels
                            isLoading = false
                            isInitialLoading = false
                            break // 1回だけ収集
                        }
                    } else {
                        Log.d("NovelListScreen", "取得した小説がありません")
                        errorMessage = "小説が見つかりませんでした。指定されたディレクトリに小説データが存在するか確認してください。"
                        isLoading = false
                        isInitialLoading = false
                    }
                } catch (e: Exception) {
                    Log.e("NovelListScreen", "小説情報の更新に失敗しました: ${e.message}", e)
                    errorMessage = "小説情報の更新に失敗しました: ${e.message}"
                    isLoading = false
                    isInitialLoading = false
                }
            }
        } catch (e: Exception) {
            Log.e("NovelListScreen", "LaunchedEffect内のエラー: ${e.message}", e)
            errorMessage = "エラーが発生しました: ${e.message}"
            isLoading = false
            isInitialLoading = false
            isUpdating = false
        }
    }

    // UIの構築
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("小説一覧") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading || isInitialLoading) {
                // 詳細なローディング表示
                LoadingDialog(
                    message = progressMessage,
                    progress = progress,
                    processedCount = processedCount,
                    totalCount = totalCount
                )
            } else if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // アクセス権限がない場合、設定画面へ移動するボタンを表示
                    if (!hasValidPermission && selfServerPath.isNotEmpty()) {
                        Button(onClick = {
                            // 設定画面へ戻る処理
                            onBack()
                        }) {
                            Text("設定画面へ戻る")
                        }
                    } else {
                        Button(onClick = onBack) {
                            Text("戻る")
                        }
                    }
                }
            } else if (novels.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("小説が見つかりませんでした")
                    Spacer(modifier = Modifier.height(16.dp))
                    if (selfServerAccess) {
                        Text(
                            text = "自己サーバーのパス: $selfServerPath",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            // デバッグ用に現在のパスを表示
                            Log.d("NovelListScreen", "Current server path: $selfServerPath")
                        }) {
                            Text("パス情報を確認")
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 小説リスト
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(novels) { novel ->
                            NovelItem(
                                novel = novel,
                                onClick = { onNovelSelected(novel) }
                            )
                        }
                    }

                    // バックグラウンド更新の進行状況表示
                    if (isUpdating) {
                        DetailedProgressBar(
                            progress = progress,
                            message = progressMessage,
                            processedCount = processedCount,
                            totalCount = totalCount,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun NovelItem(
    novel: Novel,
    onClick: () -> Unit
) {
    // 未読数に基づいた透明度の設定
    val alpha = if (novel.unreadCount == 0) 0.5f else 1.0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
            .alpha(alpha)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = novel.title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "最後に読んだ: ${novel.lastReadEpisode}話",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "未読: ${novel.unreadCount}/${novel.totalEpisodes}話",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (novel.unreadCount > 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }

}
@Preview(showBackground = true)
@Composable
fun NovelReaderAppPreview() {
    NRreaderTheme {
        NovelReaderApp()
    }
}