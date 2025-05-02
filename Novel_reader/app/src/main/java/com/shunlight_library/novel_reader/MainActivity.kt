package com.shunlight_library.novel_reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.ui.theme.Novel_readerTheme
import com.shunlight_library.novel_reader.ui.theme.LightOrange
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ステータスバーを完全に非表示にする
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // エッジツーエッジ表示を有効化
        enableEdgeToEdge()

        // システムバーを非表示にしてコンテンツをその下に表示
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            Novel_readerTheme {
                NovelReaderApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelReaderApp() {
    var showSettings by remember { mutableStateOf(false) }
    // WebView用の状態変数を追加
    var showWebView by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // R18コンテンツ用のダイアログ表示状態
    var showR18Dialog by remember { mutableStateOf(false) }

    // URLを開くヘルパー関数を修正
    fun openUrl(url: String) {
        currentUrl = url
        showWebView = true
    }

    // リポジトリを取得
    val repository = NovelReaderApplication.getRepository()

    // 最後に読んだ小説の情報を取得
    var lastReadNovel by remember { mutableStateOf<LastReadNovelEntity?>(null) }
    var novelInfo by remember { mutableStateOf<NovelDescEntity?>(null) }

    LaunchedEffect(Unit) {
        lastReadNovel = repository.getMostRecentlyReadNovel()
        if (lastReadNovel != null) {
            novelInfo = repository.getNovelByNcode(lastReadNovel!!.ncode)
        }
    }

    // R18コンテンツ選択ダイアログ
    if (showR18Dialog) {
        AlertDialog(
            onDismissRequest = { showR18Dialog = false },
            title = { Text("R18コンテンツを選択") },
            text = { Text("閲覧したいR18サイトを選択してください") },
            confirmButton = {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            openUrl("https://noc.syosetu.com/top/top/")
                            showR18Dialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ノクターン")
                    }

                    Button(
                        onClick = {
                            openUrl("https://mid.syosetu.com/top/top/")
                            showR18Dialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ミッドナイト")
                    }

                    Button(
                        onClick = {
                            openUrl("https://mnlt.syosetu.com/top/top/")
                            showR18Dialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ムーンライト")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showR18Dialog = false }) {
                    Text("キャンセル")
                }
            }
        )
        BackHandler {
            showR18Dialog = false
        }
    }

    when {
        showWebView -> {
            WebViewScreen(
                url = currentUrl,
                onBack = { showWebView = false }
            )
        }

        showSettings -> {
            SettingsScreen(onBack = { showSettings = false })
        }

        else -> {
            // メイン画面（既存のコード）
            Scaffold { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // 新着・更新情報セクション
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LightOrange)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "新着・更新情報",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // 新着・更新情報をボタンに変更
                            Button(
                                onClick = { /* TODO: 新着・更新情報画面に遷移 */ },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = LightOrange
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "新着1件・更新あり0件",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "最後に開いていた小説",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // 最後に読んだ小説の情報をボタンに変更
                            Button(
                                onClick = { /* TODO: 最後に読んだ小説の続きを開く */ },
                                enabled = novelInfo != null, // 小説情報がある場合のみ有効化
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = if (novelInfo != null) LightOrange else Color.Gray,
                                    disabledContainerColor = Color.LightGray,
                                    disabledContentColor = Color.DarkGray
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (novelInfo != null)
                                        "${novelInfo!!.title} ${lastReadNovel!!.episode_no}話"
                                    else
                                        "まだ小説を読んでいません",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }

                    // 小説をさがすセクション
                    item {
                        SectionHeader(title = "小説をさがす")
                    }

                    // ランキングとPickup
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MenuButton(
                                icon = "⚪",
                                text = "ランキング",
                                onClick = { openUrl("https://yomou.syosetu.com/rank/top/") }
                            )
                            MenuButton(
                                icon = "📢",
                                text = "PickUp!",
                                onClick = { openUrl("https://syosetu.com/pickup/list/") }
                            )
                        }
                    }

                    // キーワード検索と詳細検索
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MenuButton(
                                icon = "🔍",
                                text = "キーワード",
                                onClick = { openUrl("https://yomou.syosetu.com/search/keyword/") }
                            )
                            MenuButton(
                                icon = ">",
                                text = "詳細検索",
                                onClick = { openUrl("https://yomou.syosetu.com/search.php") }
                            )
                        }
                    }
                    //カクヨム＆R18セクション
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MenuButton(
                                icon = ">",
                                text = "カクヨム",
                                onClick = { openUrl("https://kakuyomu.jp/") }
                            )

                            MenuButton(
                                icon = "<",
                                text = "R18",
                                onClick = { showR18Dialog = true }
                            )
                        }
                    }

                    // 小説を読むセクション
                    item {
                        SectionHeader(title = "小説を読む")
                    }

                    // 小説一覧と最近更新された小説
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MenuButton(
                                icon = "📚",
                                text = "小説一覧",
                                onClick = {}
                            )
                            MenuButton(
                                icon = ">",
                                text = "最近更新された小説",
                                onClick = {}
                            )
                        }
                    }

                    // 最近読んだ小説と作者別・シリーズ別
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MenuButton(
                                icon = ">",
                                text = "最近読んだ小説",
                                onClick = {}
                            )
                            MenuButton(
                                icon = ">",
                                text = "作者別\nシリーズ別",
                                onClick = {}
                            )
                        }
                    }

                    // タグ検索
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            MenuButton(
                                icon = ">",
                                text = "タグ検索",
                                onClick = {},
                                modifier = Modifier.width(180.dp)
                            )
                        }
                    }

                    // オプションセクション
                    item {
                        SectionHeader(title = "オプション")
                    }

                    // ダウンロード状況と設定
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MenuButton(
                                icon = "⬇",
                                text = "ダウンロード状況",
                                onClick = {}
                            )
                            MenuButton(
                                icon = "⚙",
                                text = "設定",
                                onClick = { showSettings = true }
                            )
                        }
                    }
                }
            }
            BackHandler {
                Log.d("NovelReaderApp", "Back button pressed")
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray.copy(alpha = 0.3f))
            .padding(8.dp)
    ) {
        Text(
            text = title,
            color = Color.Gray,
            fontSize = 16.sp
        )
    }
}

@Composable
fun MenuButton(
    icon: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(160.dp)
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 18.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            fontSize = 16.sp
        )
    }
}