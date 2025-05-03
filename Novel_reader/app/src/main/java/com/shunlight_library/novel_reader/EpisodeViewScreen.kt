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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import android.text.Html
import android.os.Build
import androidx.compose.ui.unit.TextUnit


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
    val scrollState = rememberScrollState()

    // 開発者モード関連の状態
    var titleTapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var devModeEnabled by remember { mutableStateOf(false) }
    val tapTimeThreshold = 1000 // 連続タップと判定する時間間隔（ミリ秒）

    // テキスト表示サイズ（後で設定から取得できるようになると良い）
    var fontSize by remember { mutableStateOf(18) }

    fun String.removeHtmlTags(): String {
        return this.replace(Regex("<[^>]*>"), "")
    }

    LaunchedEffect(ncode, episodeNo) {
        scrollState.scrollTo(0)
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

    // タイトルをタップした時の処理関数
    fun onTitleTap() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTapTime < tapTimeThreshold) {
            // 連続タップとみなす
            titleTapCount++

            // タップ回数が5回に達したら開発者モードを切り替え
            if (titleTapCount == 5) {
                devModeEnabled = !devModeEnabled
                titleTapCount = 0 // カウントリセット

                // 開発者モード切り替えのフィードバック（必要に応じて）
                // Toast.makeText(LocalContext.current, "開発者モード: ${if(devModeEnabled) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            }
        } else {
            // 連続タップではないのでカウントリセット
            titleTapCount = 1
        }
        lastTapTime = currentTime
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
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable { onTitleTap() } // タイトルタップのイベントハンドラ追加
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
                // エピソードタイトル (タップで開発者モード切り替え機能を追加)
                Text(
                    text = episode!!.e_title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .clickable { onTitleTap() }
                )

                // 本文表示 (開発者モードに応じて表示を切り替え)
                if (devModeEnabled) {
                    // 開発者モード: 生のHTMLデータをそのまま表示
                    Text(
                        text = episode!!.body,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = fontSize.sp
                        ),
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                } else {
                    // 通常モード: Ruby処理を行った表示
                    RubyText(
                        text = episode!!.body,
                        fontSize = fontSize,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                }
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

fun processHtmlRuby(html: String): String {
    // HTMLタグを処理するための正規表現
    val rubyRegex = "<ruby>([^<]*?)<rt>([^<]*?)</rt></ruby>".toRegex()

    // rubyタグをカスタム形式に変換
    var processedText = html.replace(rubyRegex) { matchResult ->
        val base = matchResult.groupValues[1]
        val ruby = matchResult.groupValues[2]
        "｜$base《$ruby》"
    }

    // その他のHTMLタグを削除
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        processedText = Html.fromHtml(processedText, Html.FROM_HTML_MODE_LEGACY).toString()
    } else {
        @Suppress("DEPRECATION")
        processedText = Html.fromHtml(processedText).toString()
    }

    return processedText
}

fun buildRubyAnnotatedString(text: String, fontSize: TextUnit, rubyFontSize: TextUnit): AnnotatedString {
    val lines = text.split("\n")

    return buildAnnotatedString {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.contains("｜") && line.contains("《") && line.contains("》")) {
                var currentIndex = 0
                val regex = "｜([^《]*?)《([^》]*?)》".toRegex()

                val matches = regex.findAll(line)
                for (match in matches) {
                    val beforeText = line.substring(currentIndex, match.range.first)
                    append(beforeText)

                    val baseText = match.groupValues[1]
                    val rubyText = match.groupValues[2]

                    // ベーステキスト
                    append(baseText)

                    // ルビ
                    withStyle(
                        SpanStyle(
                            fontSize = rubyFontSize,
                            baselineShift = BaselineShift.Superscript,
                            fontWeight = FontWeight.Normal
                        )
                    ) {
                        append(" ")
                        append(rubyText)
                        append(" ")
                    }

                    currentIndex = match.range.last + 1
                }

                // 残りのテキスト
                if (currentIndex < line.length) {
                    append(line.substring(currentIndex))
                }

                append("\n")
                i++
            }
            // パターン1: 改行+[句読点のない文末]+改行後の（）

            // パターン2: ｜文字列《ルビ》
            else if (i < lines.size - 1 &&
                    !line.endsWith("。") && !line.endsWith("、") && !line.endsWith(".") && !line.endsWith(",") &&
                    lines[i + 1].startsWith("(") && lines[i + 1].endsWith(")")
            ) {

                val base = line
                val ruby = lines[i + 1].substring(1, lines[i + 1].length - 1)

                // ベーステキストを追加
                append(base)

                // ルビを追加
                withStyle(
                    SpanStyle(
                        fontSize = rubyFontSize,
                        baselineShift = BaselineShift.Superscript,
                        fontWeight = FontWeight.Normal
                    )
                ) {
                    append(" ")
                    append(ruby)
                    append(" ")
                }

                append("\n")
                i += 2 // 2行進める
            }

            // 通常のテキスト
            else {
                append(line)
                append("\n")
                i++
            }
        }
    }
}



@Composable
fun RubyText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 18,
    rubyFontSize: Int = 10
) {
    val processedText = processHtmlRuby(text)
    val annotatedString = buildRubyAnnotatedString(processedText, fontSize.sp, rubyFontSize.sp)
    Text(
        text = annotatedString,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = fontSize.sp
        )
    )
}