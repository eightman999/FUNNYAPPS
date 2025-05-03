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
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView


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
                // エピソード本文の表示部分を修正
                // エピソード本文の表示部分を修正
                if (episode != null) {
                    if (devModeEnabled) {
                        // 開発者モード: HTMLソースを表示
                        Column {
                            Text(
                                text = "HTML Source:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = episode!!.body,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = fontSize.sp
                                ),
                                modifier = Modifier.padding(bottom = 32.dp)
                            )

                            Divider()

                            
                        }
                    } else {
                        // 通常モード: WebViewでHTML表示
                        HtmlRubyWebView(
                            htmlContent = episode!!.body,
                            fontSize = fontSize,
                            rubyFontSize = (fontSize * 0.6).toInt(),
                            modifier = Modifier.padding(bottom = 32.dp)
                        )
                    }
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

@Composable
fun HtmlRubyWebView(
    htmlContent: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 18,
    rubyFontSize: Int = 10
) {
    val context = LocalContext.current

    // HTMLを修正する関数
    fun fixRubyTags(html: String): String {
        // パターン1: <ruby>対象</rb>(ルビ) の修正
        var fixed = html.replace("<ruby>([^<]*?)</rb>\\(([^)]*?)\\)".toRegex()) {
            val base = it.groupValues[1]
            val ruby = it.groupValues[2]
            "<ruby>$base<rt>$ruby</rt></ruby>"
        }

        // パターン2: <ruby>対象(ルビ) の修正
        fixed = fixed.replace("<ruby>([^<(]*?)\\(([^)]*?)\\)".toRegex()) {
            val base = it.groupValues[1]
            val ruby = it.groupValues[2]
            "<ruby>$base<rt>$ruby</rt></ruby>"
        }

        // パターン3: 対象(ルビ) パターンをrubyタグに変換
        fixed = fixed.replace("([^<>\\s]+?)\\(([^)]+?)\\)".toRegex()) {
            val base = it.groupValues[1]
            val ruby = it.groupValues[2]
            "<ruby>$base<rt>$ruby</rt></ruby>"
        }

        return fixed
    }

    // ルビ用のCSSスタイルを定義
    val cssStyle = """
        <style>
            body {
                font-family: sans-serif;
                font-size: ${fontSize}px;
                line-height: 1.8;
                padding: 16px;
                margin: 0;
                background-color: #ffffff;
                color: #000000;
            }
            ruby {
                ruby-align: center;
                ruby-position: over;
                -webkit-ruby-position: over;
            }
            rt {
                font-size: ${rubyFontSize}px;
                text-align: center;
                line-height: 1;
                display: block;
                color: #000000;
            }
            /* タップエリアを広げる */
            p, div {
                padding: 8px 0;
            }
        </style>
    """.trimIndent()

    // JavaScriptで追加の調整
    val jsScript = """
        <script>
            // ドキュメント読み込み後にルビタグを調整
            document.addEventListener('DOMContentLoaded', function() {
                // すべてのルビ要素を取得
                var rubyElements = document.getElementsByTagName('ruby');
                for (var i = 0; i < rubyElements.length; i++) {
                    var ruby = rubyElements[i];
                    
                    // rtタグが見つからない場合は修正
                    if (ruby.getElementsByTagName('rt').length === 0) {
                        var text = ruby.textContent;
                        var match = text.match(/(.+?)\((.+?)\)/);
                        if (match) {
                            ruby.innerHTML = match[1] + '<rt>' + match[2] + '</rt>';
                        }
                    }
                }
            });
        </script>
    """.trimIndent()

    // HTMLを修正
    val fixedHtml = fixRubyTags(htmlContent)

    // HTMLコンテンツを整形
    val formattedHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            $cssStyle
            $jsScript
        </head>
        <body>
            $fixedHtml
        </body>
        </html>
    """.trimIndent()

    // WebViewでHTMLをレンダリング
    var webView: WebView? = null

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webView = this
                settings.apply {
                    javaScriptEnabled = true
                    defaultFontSize = fontSize
                    builtInZoomControls = true
                    displayZoomControls = false

                    // キャッシュを使用
                    cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

                    // 文字コードを明示
                    defaultTextEncodingName = "UTF-8"
                }

                // WebViewClientのカスタマイズ
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // ページ読み込み完了後の処理
                    }
                }

                // HTMLをロード
                loadDataWithBaseURL(null, formattedHtml, "text/html", "UTF-8", null)
            }
        },
        update = { view ->
            // コンポーネントの更新が必要な場合
            view.loadDataWithBaseURL(null, formattedHtml, "text/html", "UTF-8", null)
        },
        modifier = modifier.fillMaxSize()
    )
}