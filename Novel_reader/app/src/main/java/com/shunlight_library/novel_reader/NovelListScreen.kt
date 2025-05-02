package com.shunlight_library.novel_reader

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.ui.theme.LightOrange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelListScreen(
    onBack: () -> Unit,
    onNovelClick: (String) -> Unit // 小説をクリックした時の処理（ncode渡し）
) {
    val repository = NovelReaderApplication.getRepository()
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    // 表示設定の読み込み
    var showTitle by remember { mutableStateOf(true) }
    var showAuthor by remember { mutableStateOf(true) }
    var showSynopsis by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(true) }
    var showRating by remember { mutableStateOf(false) }
    var showUpdateDate by remember { mutableStateOf(true) }
    var showEpisodeCount by remember { mutableStateOf(true) }

    // 小説リストの取得
    var novels by remember { mutableStateOf<List<NovelDescEntity>>(emptyList()) }

    // 設定の読み込み
    LaunchedEffect(key1 = true) {
        // 表示設定の取得
        val displaySettings = settingsStore.getDisplaySettings()
        showTitle = displaySettings.showTitle
        showAuthor = displaySettings.showAuthor
        showSynopsis = displaySettings.showSynopsis
        showTags = displaySettings.showTags
        showRating = displaySettings.showRating
        showUpdateDate = displaySettings.showUpdateDate
        showEpisodeCount = displaySettings.showEpisodeCount

        // 小説データの取得
        repository.allNovels.collect {
            novels = it
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("小説一覧") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (novels.isEmpty()) {
            // 小説がない場合
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("小説が登録されていません")
            }
        } else {
            // 小説リストの表示
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(novels) { novel ->
                    NovelListItem(
                        novel = novel,
                        showTitle = showTitle,
                        showAuthor = showAuthor,
                        showSynopsis = showSynopsis,
                        showTags = showTags,
                        showRating = showRating,
                        showUpdateDate = showUpdateDate,
                        showEpisodeCount = showEpisodeCount,
                        onClick = { onNovelClick(novel.ncode) }
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
fun NovelListItem(
    novel: NovelDescEntity,
    showTitle: Boolean,
    showAuthor: Boolean,
    showSynopsis: Boolean,
    showTags: Boolean,
    showRating: Boolean,
    showUpdateDate: Boolean,
    showEpisodeCount: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (showTitle) {
                Text(
                    text = novel.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (showAuthor) {
                Text(
                    text = "作者: ${novel.author}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (showSynopsis) {
                Text(
                    text = novel.Synopsis,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (showTags) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "タグ: ${novel.main_tag}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (novel.sub_tag.isNotEmpty()) {
                        Text(
                            text = ", ${novel.sub_tag}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (showRating) {
                    Text(
                        text = "評価: ${novel.rating}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (showUpdateDate) {
                    Text(
                        text = "更新: ${novel.last_update_date}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (showEpisodeCount) {
                    Text(
                        text = "全${novel.total_ep}話",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}