package com.shunlight_library.nr_reader

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shunlight_library.nr_reader.ui.components.LoadingDialog
import com.shunlight_library.nr_reader.ui.theme.backgroundColorValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // 状態変数
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var novels by remember { mutableStateOf<List<Novel>>(emptyList()) }
    var progress by remember { mutableStateOf(0f) }
    var processedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }

    // 小説リストを読み込む関数
    fun loadNovels() {
        if (!selfServerAccess) {
            errorMessage = "自己サーバーモードが無効です。設定で有効にしてください。"
            return
        }

        if (selfServerPath.isEmpty()) {
            errorMessage = "サーバーパスが設定されていません。設定で指定してください。"
            return
        }

        isLoading = true
        errorMessage = null

        scope.launch {
            try {
                // 小説一覧を取得
                parser.resetProgress() // 進捗状況をリセット

                // 進捗状況を監視するコルーチン
                launch {
                    while (isLoading) {
                        progress = parser.progress
                        processedCount = parser.processedCount
                        totalCount = parser.totalCount
                        kotlinx.coroutines.delay(200) // 0.2秒ごとに更新
                    }
                }

                // 別スレッドで小説一覧を取得
                val novelList = withContext(Dispatchers.IO) {
                    parser.parseNovelListFromServerPath(selfServerPath)
                }

                // 未読情報を計算
                val novelListWithUnread = novelList.map { novel ->
                    // 小説リポジトリから最後に読んだエピソード番号を取得
                    val repository = NovelRepository(context)
                    val lastReadEpisode = repository.getLastReadEpisode(novel.ncode)

                    // 未読数を計算
                    val unreadCount = (novel.totalEpisodes - lastReadEpisode).coerceAtLeast(0)

                    // 未読情報を含む小説オブジェクトを返す
                    novel.copy(
                        lastReadEpisode = lastReadEpisode,
                        unreadCount = unreadCount
                    )
                }

                novels = novelListWithUnread
                isLoading = false

                Log.d("NovelListScreen", "小説一覧を取得完了: ${novels.size}件")

            } catch (e: Exception) {
                Log.e("NovelListScreen", "小説一覧取得エラー", e)
                errorMessage = "小説一覧の取得に失敗しました: ${e.message}"
                isLoading = false
            }
        }
    }

    // 画面表示時に小説一覧を自動で読み込む
    LaunchedEffect(key1 = selfServerPath) {
        loadNovels()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("小説一覧") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ローディング表示
            if (isLoading) {
                LoadingDialog(
                    message = "小説一覧を読み込んでいます...",
                    progress = progress,
                    processedCount = processedCount,
                    totalCount = totalCount
                )
            }

            // エラーメッセージ表示
            errorMessage?.let { error ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { loadNovels() }) {
                        Text("再試行")
                    }
                }
            }

            // 小説一覧表示
            if (!isLoading && errorMessage == null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(novels) { novel ->
                        NovelItem(
                            novel = novel,
                            onClick = { onNovelSelected(novel) }
                        )
                        Divider()
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 小説情報（タイトル、話数など）
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = novel.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "全${novel.totalEpisodes}話・最終読了${novel.lastReadEpisode}話",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        // 未読数表示
        if (novel.unreadCount > 0) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${novel.unreadCount}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}