package com.shunlight_library.novel_reader

import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import kotlinx.coroutines.*
import org.yaml.snakeyaml.Yaml
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateInfoScreen(
    onBack: () -> Unit,
    onNovelClick: (String) -> Unit
) {
    val repository = NovelReaderApplication.getRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 状態変数
    var updateQueue by remember { mutableStateOf<List<UpdateQueueEntity>>(emptyList()) }
    var novels by remember { mutableStateOf<Map<String, NovelDescEntity>>(emptyMap()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // 進捗表示用の変数
    var isSyncing by remember { mutableStateOf(false) }
    var syncProgress by remember { mutableStateOf(0f) }
    var syncStep by remember { mutableStateOf("") }
    var syncMessage by remember { mutableStateOf("") }
    var currentCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }

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
                        // 更新チェック処理
                        showConfirmDialog = false
                        isRefreshing = true
                        isSyncing = true  // 同期状態をONに
                        syncProgress = 0f
                        syncStep = "更新確認"
                        syncMessage = "小説の更新をチェック中..."
                        currentCount = 0
                        totalCount = 0

                        scope.launch {
                            try {
                                // 更新対象の小説を取得
                                var novels = repository.getNovelsForUpdate()
                                totalCount = novels.size  // 総数を設定
                                var processedNovels = 0
                                var newCount = 0
                                var updatedCount = 0

                                // 進捗状態の更新関数
                                val updateProgress = { count: Int, message: String ->
                                    val progress = if (totalCount > 0) count.toFloat() / totalCount else 0f
                                    syncProgress = progress
                                    currentCount = count
                                    syncMessage = message
                                }

                                // 初期プログレスを設定
                                updateProgress(0, "小説の更新を確認中...")

                                // 各小説を処理
                                novels.forEach { novel ->
                                    processedNovels++

                                    try {
                                        // 進捗状態を更新
                                        val progressPercent = (processedNovels.toFloat() / totalCount * 100).toInt()
                                        updateProgress(
                                            processedNovels,
                                            "「${novel.title}」の更新を確認中 ($processedNovels/$totalCount - $progressPercent%)"
                                        )

                                        // APIエンドポイントを選択
                                        val apiUrl = if (novel.rating == 1) {
                                            "https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode=${novel.ncode}&gzip=5&json"
                                        } else {
                                            "https://api.syosetu.com/novelapi/api/?of=t-w-ga-s-ua&ncode=${novel.ncode}&gzip=5&json"
                                        }

                                        // APIからデータを取得
                                        val result = withContext(Dispatchers.IO) {
                                            try {
                                                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                                                connection.requestMethod = "GET"

                                                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                                                    // gzipファイルを解凍
                                                    val inputStream = GZIPInputStream(connection.inputStream)
                                                    val reader = BufferedReader(InputStreamReader(inputStream))
                                                    val content = StringBuilder()
                                                    var line: String?

                                                    while (reader.readLine().also { line = it } != null) {
                                                        content.append(line).append("\n")
                                                    }

                                                    // YAMLデータを解析
                                                    val yaml = Yaml()
                                                    val yamlData = yaml.load<List<Map<String, Any>>>(content.toString())

                                                    if (yamlData.size >= 2) {
                                                        val novelData = yamlData[1]
                                                        val newGeneralAllNo = novelData["general_all_no"] as Int
                                                        val newUpdatedAt = novelData["updated_at"] as String

                                                        // データベースを更新
                                                        if (newGeneralAllNo > novel.general_all_no) {
                                                            // 小説情報を更新
                                                            val updatedNovel = novel.copy(
                                                                general_all_no = newGeneralAllNo,
                                                                updated_at = newUpdatedAt
                                                            )
                                                            repository.updateNovel(updatedNovel)

                                                            // 更新キューに追加
                                                            val updateQueue = UpdateQueueEntity(
                                                                ncode = novel.ncode,
                                                                total_ep = novel.total_ep,
                                                                general_all_no = newGeneralAllNo,
                                                                update_time = newUpdatedAt
                                                            )
                                                            repository.insertUpdateQueue(updateQueue)

                                                            // 新規追加か更新かをカウント
                                                            if (novel.general_all_no == 0) {
                                                                newCount++
                                                            } else {
                                                                updatedCount++
                                                            }

                                                            true // 更新あり
                                                        } else {
                                                            false // 更新なし
                                                        }
                                                    } else {
                                                        false // データなし
                                                    }
                                                } else {
                                                    false // HTTP通信失敗
                                                }
                                            } catch (e: Exception) {
                                                Log.e("UpdateCheck", "エラー: ${novel.ncode} - ${e.message}")
                                                false // エラー発生
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("UpdateCheck", "小説処理エラー: ${novel.ncode} - ${e.message}")
                                    }

                                    // 処理の遅延（サーバーに負荷をかけないため）
                                    delay(500)  // 500ミリ秒待機
                                }

                                // 完了メッセージを表示
                                // 更新情報を再取得する部分の修正
// collect は別の方法で使用する必要があります

// 完了メッセージを表示
                                withContext(Dispatchers.Main) {
                                    updateProgress(totalCount, "更新チェック完了")

                                    // 結果を表示
                                    val resultMessage = if (newCount > 0 || updatedCount > 0) {
                                        "新着${newCount}件・更新あり${updatedCount}件の小説が見つかりました"
                                    } else {
                                        "更新された小説はありませんでした"
                                    }

                                    Toast.makeText(context, resultMessage, Toast.LENGTH_SHORT).show()

                                    // 更新情報を再取得（Flow の collect の使用方法を修正）
                                    try {
                                        // collect は suspend 関数内で直接呼び出す必要がある
                                        // 単一の値を取得するには first() を使うか、単発の collect を使う
                                        val latestQueueList = repository.getAllUpdateQueue()
                                        updateQueue = latestQueueList

                                        // 関連する小説情報も取得
                                        val novelMap = mutableMapOf<String, NovelDescEntity>()
                                        latestQueueList.forEach { queue ->
                                            repository.getNovelByNcode(queue.ncode)?.let { novel ->
                                                novelMap[queue.ncode] = novel
                                            }
                                        }
                                        novels = novelMap as List<NovelDescEntity>
                                    } catch (e: Exception) {
                                        Log.e("UpdateCheck", "更新情報再取得エラー: ${e.message}")
                                    }

                                    // 処理完了
                                    isRefreshing = false
                                    isSyncing = false  // 同期状態をOFFに
                                }
                            } catch (e: Exception) {
                                Log.e("UpdateCheck", "更新チェックエラー: ${e.message}")

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "更新チェック中にエラーが発生しました: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    isRefreshing = false
                                    isSyncing = false  // 同期状態をOFFに
                                }
                            }
                        }
                    },
                    enabled = !isSyncing
                ) {
                    Text(if (isSyncing) "同期中..." else "確認する")
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
                            enabled = !isRefreshing && !isSyncing
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
                            enabled = !isRefreshing && !isSyncing && updateQueue.isNotEmpty()
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

                    if (isRefreshing || isSyncing) {
                        Spacer(modifier = Modifier.height(16.dp))

                        if (isSyncing) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    progress = syncProgress,
                                    modifier = Modifier.size(64.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = syncStep,
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                Text(
                                    text = syncMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )

                                // テーブルごとの進捗バーを追加（totalCountが0より大きい場合のみ）
                                if (totalCount > 0) {
                                    Spacer(modifier = Modifier.height(16.dp))

                                    // N/n（X%）形式の進捗表示
                                    val progressPercent = (currentCount.toFloat() / totalCount * 100).toInt()
                                    Text(
                                        text = "取得プログレス: $currentCount/$totalCount ($progressPercent%)",
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    LinearProgressIndicator(
                                        progress = currentCount.toFloat() / totalCount,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                    )
                                }
                            }
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "更新をチェック中...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // 更新キューリスト
            if (updateQueue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRefreshing || isSyncing) {
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
                                    // キューから削除する処理
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

                // N/n（X%）形式の表示を追加（未読エピソード数）
                val unreadEpisodeCount = queueItem.general_all_no - queueItem.total_ep
                val episodeText = if (unreadEpisodeCount > 0) {
                    "全${queueItem.total_ep}話 (未読${unreadEpisodeCount}話)"
                } else {
                    "全${queueItem.total_ep}話"
                }

                Text(
                    text = episodeText,
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