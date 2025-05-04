package com.shunlight_library.novel_reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateInfoScreen(
    onBack: () -> Unit,
    onNovelClick: (String) -> Unit
) {
    val repository = NovelReaderApplication.getRepository()
    val scope = rememberCoroutineScope()

    // 状態変数
    var updateQueue by remember { mutableStateOf<List<UpdateQueueEntity>>(emptyList()) }
    var novels by remember { mutableStateOf<Map<String, NovelDescEntity>>(emptyMap()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // データ取得
    LaunchedEffect(key1 = Unit) {
        repository.allUpdateQueue.collect { queueList ->
            updateQueue = queueList

            // 関連する小説情報も取得
            val novelMap = mutableMapOf<String, NovelDescEntity>()
            queueList.forEach { queue ->
                repository.getNovelByNcode(queue.ncode)?.let { novel ->
                    novelMap[queue.ncode] = novel
                }
            }
            novels = novelMap
        }
    }

    // 更新確認ダイアログ
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("更新確認") },
            text = { Text("すべての小説の更新をチェックしますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // TODO: 更新チェック処理
                        showConfirmDialog = false
                        isRefreshing = true

                        scope.launch {
                            // 更新チェックの実装（ダミー）
                            kotlinx.coroutines.delay(2000)
                            isRefreshing = false
                        }
                    }
                ) {
                    Text("確認する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新着・更新情報") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 更新ボタンエリア
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "小説の更新",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { showConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = !isRefreshing
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "更新確認",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("更新確認")
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Button(
                            onClick = {
                                // TODO: 一括更新処理
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isRefreshing && updateQueue.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "一括更新",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("一括更新")
                        }
                    }

                    if (isRefreshing) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "更新をチェック中...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // 更新キューリスト
            if (updateQueue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRefreshing) {
                        // 更新中なので何も表示しない
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "更新情報はありません",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "「更新確認」を押して最新情報を取得してください",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "更新キュー (${updateQueue.size}件)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Divider()

                    LazyColumn {
                        items(updateQueue) { queueItem ->
                            val novel = novels[queueItem.ncode]
                            UpdateQueueItem(
                                queueItem = queueItem,
                                novel = novel,
                                onClick = {
                                    // 小説がnullでなければクリックを処理
                                    novel?.let { onNovelClick(queueItem.ncode) }
                                },
                                onRemove = {
                                    // TODO: キューから削除する処理
                                    scope.launch {
                                        repository.deleteUpdateQueueByNcode(queueItem.ncode)
                                    }
                                }
                            )
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateQueueItem(
    queueItem: UpdateQueueEntity,
    novel: NovelDescEntity?,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            if (novel != null) {
                Text(
                    text = novel.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "作者: ${novel.author}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "小説情報がありません",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )

                Text(
                    text = "Nコード: ${queueItem.ncode}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Update,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatDate(queueItem.update_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "全${queueItem.total_ep}話",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = "削除",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

// 日付表示フォーマット
private fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        dateString
    }
}