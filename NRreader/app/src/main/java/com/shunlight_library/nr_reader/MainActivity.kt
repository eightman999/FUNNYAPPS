package com.shunlight_library.nr_reader

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
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

    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val parser = remember { NovelParser(context) }

    // 設定情報を取得
    var selfServerPath by remember { mutableStateOf("") }
    var selfServerAccess by remember { mutableStateOf(false) }

    // アプリ起動時に設定情報を読み込む
    LaunchedEffect(key1 = true) {
        settingsStore.selfServerPath.collect { path ->
            if (path != selfServerPath) {
                selfServerPath = path
                // パスが変更された場合はキャッシュをクリア
                parser.clearCache()
                Log.d("NovelReaderApp", "サーバーパスを更新: $path")
            }
        }
    }

    // selfServerAccessの設定も監視
    LaunchedEffect(key1 = true) {
        settingsStore.selfServerAccess.collect { enabled ->
            selfServerAccess = enabled
            Log.d("NovelReaderApp", "サーバーアクセス設定を更新: $enabled")
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
                } else {
                    // 小説をオンラインで開く
                    currentTitle = novel.title
                    currentUrl = "https://ncode.syosetu.com/${novel.ncode}/"
                    showWebView = true
                    showNovelList = false
                }
            },
            onBack = { showNovelList = false }
        )
    }
    // WebViewの表示
    else if (showWebView) {
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
                WebViewScreen(url = currentUrl)
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
fun WebViewScreen(url: String) {
    val context = LocalContext.current

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
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
    val scope = rememberCoroutineScope()
    val parser = remember { NovelParser(context) }
    var novels by remember { mutableStateOf<List<Novel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // キャッシュチェックとデータ読み込み
    LaunchedEffect(key1 = selfServerPath, key2 = selfServerAccess) {
        isLoading = true
        errorMessage = null

        if (selfServerAccess && selfServerPath.isNotEmpty()) {
            try {
                Log.d("NovelListScreen", "Loading novels from server path: $selfServerPath")

                // 既にキャッシュがあればそれを使用
                if (NovelParserCache.hasCachedNovels(selfServerPath)) {
                    Log.d("NovelListScreen", "キャッシュから小説リストを読み込みます")
                    novels = NovelParserCache.getCachedNovels()
                } else {
                    Log.d("NovelListScreen", "サーバーから小説リストを読み込みます")
                    val result = parser.parseNovelListFromServerPath(selfServerPath)
                    Log.d("NovelListScreen", "Loaded ${result.size} novels from server")
                    novels = result
                }
            } catch (e: Exception) {
                Log.e("NovelListScreen", "Error loading novels", e)
                errorMessage = "小説一覧の取得に失敗しました: ${e.message}"
            }
        } else {
            // テスト用のダミーデータ
            Log.d("NovelListScreen", "Using dummy novel data")
            novels = listOf(
                Novel("Re:ゼロから始める異世界生活", "n9876543210"),
                Novel("転生したらスライムだった件", "n1234567890"),
                Novel("オーバーロード", "n0987654321")
            )
        }

        isLoading = false

    }

    // UI部分（変更なし）
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
                    Button(onClick = onBack) {
                        Text("戻る")
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
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
            Text(
                text = "最後に読んだ: ${novel.lastReadEpisode}話",
                style = MaterialTheme.typography.bodySmall
            )
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