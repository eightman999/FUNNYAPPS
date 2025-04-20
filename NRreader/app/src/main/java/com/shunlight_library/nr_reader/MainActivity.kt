package com.shunlight_library.nr_reader

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.shunlight_library.nr_reader.ui.theme.LocalAppSettings
import com.shunlight_library.nr_reader.ui.theme.NRreaderTheme
import com.shunlight_library.nr_reader.ui.theme.backgroundColorValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    val lightBlue = Color(0xFF80C8FF) // 指定された色に変更
    var showWebView by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }

    // 設定画面の表示
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
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
                    NavButton(title = "小説一覧", icon = "📚")
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

@Preview(showBackground = true)
@Composable
fun NovelReaderAppPreview() {
    NRreaderTheme {
        NovelReaderApp()
    }
}