package com.shunlight_library.novel_reader

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeViewScreen(
    ncode: String,
    episodeNo: String,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val repository = NovelReaderApplication.getRepository()
    val scope = rememberCoroutineScope()

    var episode by remember { mutableStateOf<EpisodeEntity?>(null) }
    var novel by remember { mutableStateOf<NovelDescEntity?>(null) }

    // テキスト表示サイズ（後で設定から取得できるようになると良い）
    var fontSize by remember { mutableStateOf(18) }

    LaunchedEffect(ncode, episodeNo) {
        scope.launch {
            try {
                // エピソード情報の取得
                episode = repository.getEpisode(ncode, episodeNo)
                novel = repository.getNovelByNcode(ncode)

                // 最後に読んだ情報を更新
                val episodeNumber = episodeNo.toIntOrNull() ?: 1
                repository.updateLastRead(ncode, episodeNumber)
            } catch (e: Exception) {
                Log.e("EpisodeViewScreen", "データ取得エラー: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = novel?.title ?: "小説を読む",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = episode?.e_title ?: "エピソード $episodeNo",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    // フォントサイズ調整ボタン
                    IconButton(onClick = { if (fontSize > 12) fontSize-- }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "フォントサイズを小さく")
                    }
                    IconButton(onClick = { if (fontSize < 24) fontSize++ }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "フォントサイズを大きく")
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
                    // 前のエピソード
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                enabled = episodeNo.toIntOrNull()?.let { it > 1 } ?: false,
                                onClick = onPrevious
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "前のエピソード",
                            tint = if (episodeNo.toIntOrNull()?.let { it > 1 } ?: false)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            "前のエピソード",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (episodeNo.toIntOrNull()?.let { it > 1 } ?: false)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // 目次に戻る
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onBack)
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.List, contentDescription = "目次に戻る")
                        Text("目次に戻る", style = MaterialTheme.typography.labelSmall)
                    }

                    // 次のエピソード
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                enabled = novel?.let {
                                    episodeNo.toIntOrNull()?.let { epNo ->
                                        epNo < it.total_ep
                                    } ?: false
                                } ?: false,
                                onClick = onNext
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "次のエピソード",
                            tint = if (novel?.let {
                                    episodeNo.toIntOrNull()?.let { epNo ->
                                        epNo < it.total_ep
                                    } ?: false
                                } ?: false) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            "次のエピソード",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (novel?.let {
                                    episodeNo.toIntOrNull()?.let { epNo ->
                                        epNo < it.total_ep
                                    } ?: false
                                } ?: false) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // エピソード本文の表示
        if (episode != null) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState)
            ) {
                // エピソードタイトル
                Text(
                    text = episode!!.e_title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // 本文
                Text(
                    text = episode!!.body,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp
                    ),
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
        } else {
            // ローディング表示
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}