package com.shunlight_library.nr_reader

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
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
                    val uri = Uri.parse(selfServerPath)
                    val baseDir = uri.path?.let { File(it).parentFile?.path } ?: ""
                    val novelUrl = "file://$baseDir/novels/${novel.ncode}/index.html"
                    currentTitle = novel.title
                    currentUrl = novelUrl
                    showWebView = true
                    showNovelList = false

                    // 選択された小説オブジェクトも保存
                    currentNovel = novel
                } else {
                    // 小説をオンラインで開く
                    currentTitle = novel.title
                    currentUrl = "https://ncode.syosetu.com/${novel.ncode}/"
                    showWebView = true
                    showNovelList = false

                    // 選択された小説オブジェクトも保存
                    currentNovel = novel
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
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)

                        // novelがnullでなく、ncodeが正しい場合のみ更新
                        if (novel != null && novel.ncode.isNotEmpty()) {
                            // エピソード番号を抽出して保存
                            val episodeNum = extractEpisodeNumber(url)
                            scope.launch {
                                repository.updateLastReadEpisode(novel.ncode, episodeNum)
                            }
                        }
                    }
                }
                settings.javaScriptEnabled = true
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
    val scope = rememberCoroutineScope() // このスコープを使う
    val settingsStore = remember { SettingsStore(context) }
    val parser = remember { NovelParser(context) }
    var novels by remember { mutableStateOf<List<Novel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasValidPermission by remember { mutableStateOf(false) }
    val application = context.applicationContext as NovelReaderApplication
    val repository = application.repository
    // コンポジションのライフサイクルに紐づけてクリーンアップする
    DisposableEffect(key1 = Unit) {
        onDispose {
            // クリーンアップ処理をここに追加
            Log.d("NovelListScreen", "画面が破棄されました")
        }
    }

    // 権限の確認は一度だけ行う
    LaunchedEffect(key1 = Unit) {
        try {
            hasValidPermission = settingsStore.hasPersistedPermission(selfServerPath)
            Log.d("NovelListScreen", "権限の確認: $selfServerPath - $hasValidPermission")
        } catch (e: Exception) {
            Log.e("NovelListScreen", "権限確認エラー: ${e.message}")
            hasValidPermission = false
        }
    }
    // 小説リポジトリの準備
    val novelRepository = remember { NovelRepository(context) }
    // データ読み込みはボタンクリックなどのアクションで行う方が安全
    // 自動読み込みの場合は以下のようにする
    // Roomデータベースから小説情報を取得
    LaunchedEffect(key1 = Unit) {
        try {
            // Flowからデータを収集
            repository.getAllNovels().collect { novelList ->
                novels = novelList
                isLoading = false
            }
        } catch (e: Exception) {
            errorMessage = "データの読み込みに失敗しました: ${e.message}"
            isLoading = false
        }
    }

    // 自己サーバーからの小説情報更新
    LaunchedEffect(key1 = selfServerPath, key2 = selfServerAccess) {
        if (selfServerAccess && selfServerPath.isNotEmpty()) {
            try {
                // NovelParserから小説リストを取得
                val parser = NovelParser(context)
                val parsedNovels = parser.parseNovelListFromServerPath(selfServerPath)

                // 各小説のエピソード数を取得して保存
                parsedNovels.forEach { novel ->
                    val episodeCount = repository.countEpisodesFromFileSystem(selfServerPath, novel.ncode)

                    // 新しい小説情報をデータベースに保存
                    repository.saveNovel(
                        Novel(
                            title = novel.title,
                            ncode = novel.ncode,
                            totalEpisodes = episodeCount,
                            // 既存の小説ならlastReadEpisodeを保持、新規なら1に設定
                            lastReadEpisode = 1
                        )
                    )
                }
            } catch (e: Exception) {
                errorMessage = "小説情報の更新に失敗しました: ${e.message}"
            }
        }
    }

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
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(novels) { novel ->
                        NovelItem(
                            novel = novel,
                            onClick = { onNovelSelected(novel) }
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