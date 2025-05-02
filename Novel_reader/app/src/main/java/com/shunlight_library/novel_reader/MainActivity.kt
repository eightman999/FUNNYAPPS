package com.shunlight_library.novel_reader

import android.os.Bundle
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
// MainActivityの表示を更新
@Composable
fun NovelReaderApp() {
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

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

    // 設定画面の表示
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    } else {
        // メイン画面
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
                            onClick = {}
                        )
                        MenuButton(
                            icon = "📢",
                            text = "PickUp!",
                            onClick = {}
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
                            onClick = {}
                        )
                        MenuButton(
                            icon = ">",
                            text = "詳細検索",
                            onClick = {}
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
                            onClick = {}
                        )

                        MenuButton(
                            icon = "<",
                            text = "R18",
                            onClick = {}
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