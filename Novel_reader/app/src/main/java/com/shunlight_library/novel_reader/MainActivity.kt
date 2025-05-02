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
import com.shunlight_library.novel_reader.ui.theme.Novel_readerTheme
import com.shunlight_library.novel_reader.ui.theme.backgroundColorValue
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
    val lightBlue = Color(0xFF80C8FF)
    var showSettings by remember { mutableStateOf(false) }

    // 設定画面の表示
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    } else {
        // メイン画面
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("小説リーダー") },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "設定")
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
                            text = "小説リーダーアプリへようこそ",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "設定アイコンから表示設定を変更できます",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }

                // 使い方案内
                item {
                    SectionHeader(title = "使い方")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("1. 設定画面で表示設定を調整")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("2. 自己サーバーアクセスを有効にして小説サーバーの場所を指定")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("3. 小説一覧から読みたい小説を選択")
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
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = Color.Gray,
            fontSize = 18.sp
        )
    }
}