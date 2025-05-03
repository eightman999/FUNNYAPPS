package com.shunlight_library.novel_reader

import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListScreen(
    ncode: String,
    onBack: () -> Unit,
    onEpisodeClick: (String, String) -> Unit // ncode, episodeNo
) {
    val repository = NovelReaderApplication.getRepository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 状態変数
    var novel by remember { mutableStateOf<NovelDescEntity?>(null) }
    var episodes by remember { mutableStateOf<List<EpisodeEntity>>(emptyList()) }
    var lastRead by remember { mutableStateOf<LastReadNovelEntity?>(null) }

    // 折りたたみ状態の追加
    var isDescriptionExpanded by remember { mutableStateOf(true) }

    // タグ編集用の状態変数
    var showTagEditDialog by remember { mutableStateOf(false) }
    var mainTag by remember { mutableStateOf("") }
    var subTag by remember { mutableStateOf("") }

    // データの取得
    LaunchedEffect(ncode) {
        // 小説情報の取得
        novel = repository.getNovelByNcode(ncode)

        // 最後に読んだ情報の取得
        lastRead = repository.getLastReadByNcode(ncode)

        // 初期タグ値の設定
        novel?.let {
            mainTag = it.main_tag
            subTag = it.sub_tag
        }
    }

    LaunchedEffect(ncode) {
        // エピソード一覧の取得（Flow型なのでLaunchedEffectで直接collect可能）
        repository.getEpisodesByNcode(ncode).collect { episodeList ->
            // エピソードリストを数値順にソート
            episodes = episodeList.sortedWith(compareBy {
                it.episode_no.toIntOrNull() ?: Int.MAX_VALUE
            })
        }
    }

    // タグ編集ダイアログ
    if (showTagEditDialog) {
        AlertDialog(
            onDismissRequest = { showTagEditDialog = false },
            title = { Text("タグを編集") },
            text = {
                Column {
                    OutlinedTextField(
                        value = mainTag,
                        onValueChange = { mainTag = it },
                        label = { Text("メインタグ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = subTag,
                        onValueChange = { subTag = it },
                        label = { Text("サブタグ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // タグを更新する処理
                        novel?.let { currentNovel ->
                            val updatedNovel = currentNovel.copy(main_tag = mainTag, sub_tag = subTag)
                            scope.launch {
                                try {
                                    repository.updateNovel(updatedNovel)
                                    novel = updatedNovel
                                    Toast.makeText(context, "タグを更新しました", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Log.e("EpisodeListScreen", "タグ更新エラー: ${e.message}")
                                    Toast.makeText(context, "タグの更新に失敗しました", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        showTagEditDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTagEditDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(novel?.title ?: "小説詳細")
                        novel?.let {
                            Text(
                                "作者: ${it.author}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // しおりから読む
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = lastRead != null) {
                                if (lastRead != null) {
                                    onEpisodeClick(ncode, lastRead!!.episode_no.toString())
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "しおりから読む",
                            tint = if (lastRead != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            "しおりから読む",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (lastRead != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // しおりを削除
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = lastRead != null) {
                                if (lastRead != null) {
                                    scope.launch {
                                        try {
                                            repository.deleteLastRead(ncode)
                                            lastRead = null
                                            Toast.makeText(context, "しおりを削除しました", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Log.e("EpisodeListScreen", "しおり削除エラー: ${e.message}")
                                            Toast.makeText(context, "しおりの削除に失敗しました", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.BookmarkRemove,
                            contentDescription = "しおりを削除",
                            tint = if (lastRead != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            "しおりを削除",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (lastRead != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // 小説を更新
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                Toast.makeText(context, "小説更新機能は後で実装予定です", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "小説を更新")
                        Text("小説を更新", style = MaterialTheme.typography.labelSmall)
                    }

                    // タグを編集
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                showTagEditDialog = true
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "タグを編集")
                        Text("タグを編集", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    ) { innerPadding ->
        // 小説の基本情報表示
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 小説情報のヘッダー部分 - 折りたたみ機能追加
            novel?.let { novel ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable { isDescriptionExpanded = !isDescriptionExpanded },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // 折りたたみボタンとタイトル
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "あらすじとタグ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (isDescriptionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isDescriptionExpanded) "折りたたむ" else "展開する"
                            )
                        }

                        // 折りたたみ部分の内容
                        if (isDescriptionExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // あらすじ
                            Text(
                                text = "あらすじ",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = novel.Synopsis,
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // タグ
                            if (novel.main_tag.isNotEmpty() || novel.sub_tag.isNotEmpty()) {
                                Text(
                                    text = "タグ",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = buildString {
                                        append(novel.main_tag)
                                        if (novel.sub_tag.isNotEmpty()) {
                                            if (novel.main_tag.isNotEmpty()) {
                                                append(", ")
                                            }
                                            append(novel.sub_tag)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // 最終更新日と総話数（常に表示）
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "最終更新: ${novel.last_update_date}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "全${novel.total_ep}話",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // 最後に読んだ情報（常に表示）
                        lastRead?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "しおり: ${it.episode_no}話 (${it.date})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // エピソード一覧のヘッダー
            Text(
                text = "エピソード一覧",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // エピソード一覧
            if (episodes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(episodes) { episode ->
                        val isRead = lastRead != null &&
                                episode.episode_no.toIntOrNull()?.let { it <= lastRead!!.episode_no } ?: false

                        EpisodeItem(
                            episode = episode,
                            isRead = isRead,
                            onClick = { onEpisodeClick(ncode, episode.episode_no) }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeItem(
    episode: EpisodeEntity,
    isRead: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 既読/未読アイコン
        Icon(
            imageVector = if (isRead) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (isRead) "既読" else "未読",
            tint = if (isRead) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // エピソード情報
        Column {
            Text(
                text = "${episode.episode_no}. ${episode.e_title}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isRead) FontWeight.Normal else FontWeight.Bold
            )

            if (episode.update_time.isNotEmpty()) {
                Text(
                    text = "更新: ${episode.update_time}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}