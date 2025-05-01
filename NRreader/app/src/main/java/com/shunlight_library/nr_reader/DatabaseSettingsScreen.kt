package com.shunlight_library.nr_reader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shunlight_library.nr_reader.ui.components.LoadingDialog
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

/**
 * データベースコピー設定画面コンポーネント
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as NovelReaderApplication
    val scope = rememberCoroutineScope()

    // 外部データベースハンドラーとリポジトリ
    val dbHandler = remember { application.externalDbHandler }
    val externalDbRepository = remember { application.externalDbRepository }

    // 設定状態
    var selectedDbUri by remember { mutableStateOf<Uri?>(null) }
    var dbFilePath by remember { mutableStateOf("") }

    // 進行状況
    var isCopying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var progressMessage by remember { mutableStateOf("") }
    var processedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }

    // 複数回表示するメッセージを定義
    val noFileSelectedMessage = "データベースファイルが選択されていません"
    val copyingMessage = "データベースをコピーしています..."

    // 設定から現在の状態を読み込む
    val settingsStore = remember { SettingsStore(context) }

    // ファイル選択のランチャー
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // URIからパスを取得
                selectedDbUri = uri
                dbFilePath = uri.toString()

                // 永続的な権限を取得
                val contentResolver = context.contentResolver
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                try {
                    // 永続的な権限を付与
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    Log.d("DatabaseSettings", "取得した永続的なアクセス権限: $dbFilePath")

                    // 成功メッセージをトーストで表示
                    Toast.makeText(context, "データベースファイルへのアクセス権限を取得しました", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("DatabaseSettings", "権限取得エラー: ${e.message}", e)
                    Toast.makeText(context, "アクセス権限の取得に失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 進行状況の監視
    LaunchedEffect(key1 = externalDbRepository) {
        externalDbRepository.progressMessage.collect { message ->
            progressMessage = message
        }
    }

    // UI構築
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("データベースコピー設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            // メイン設定UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 説明テキスト
                Text(
                    text = "外部データベースファイルのコピー",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "小説情報を含むSQLiteデータベースファイルをアプリに取り込みます。" +
                            "データベースのサイズによっては処理に時間がかかる場合があります。",
                    fontSize = 14.sp
                )

                Divider()

                // 説明テキスト（本体コピーについて）
                Text(
                    text = "データベースファイルは本体ストレージにコピーされます。これによりSDカードが取り外されても使用できるようになります。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 選択されたファイルパスの表示
                if (dbFilePath.isNotEmpty()) {
                    Text(
                        text = "選択されたファイル:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = dbFilePath,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(8.dp)
                            .fillMaxWidth()
                    )
                }

                // ファイル選択ボタン
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"  // すべてのファイルタイプを許可

                            // SQLiteデータベースファイルを表示のヒント
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                "application/x-sqlite3",
                                "application/octet-stream",
                                "application/vnd.sqlite3"
                            ))

                            // 永続的な権限を要求
                            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }
                        filePickerLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("データベースファイルを選択")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // DBコピーボタン
                Button(
                    onClick = {
                        if (selectedDbUri == null) {
                            Toast.makeText(context, noFileSelectedMessage, Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isCopying = true
                        progressMessage = copyingMessage

                        scope.launch {
                            try {
                                // DBコピー処理を実行
                                val success = externalDbRepository.synchronizeWithExternalDatabase(
                                    shouldCopyToInternal = true,
                                    externalDbUri = selectedDbUri!!
                                )

                                isCopying = false

                                if (success) {
                                    // コピー成功時にはメッセージを表示
                                    Toast.makeText(
                                        context,
                                        "データベースのコピーが完了しました",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    // 設定を保存
                                    settingsStore.saveDatabaseSettings(
                                        dbUri = selectedDbUri.toString(),
                                        copyToInternal = true,
                                        isEnabled = true
                                    )

                                    // ホーム画面に戻る
                                    onBack()
                                } else {
                                    // エラーメッセージはリポジトリ側で設定される
                                    Toast.makeText(
                                        context,
                                        "データベースのコピーに失敗しました",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                isCopying = false
                                Log.e("DatabaseSettings", "コピー中にエラーが発生しました", e)
                                Toast.makeText(
                                    context,
                                    "エラー: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("データベースをコピーする")
                }

                // 注意書き
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "注意: データベースのコピーには時間がかかる場合があります。大きなファイルの場合は特に処理に時間がかかります。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // コピー中のローディング表示
            if (isCopying) {
                LoadingDialog(
                    message = progressMessage,
                    progress = progress,
                    processedCount = processedCount,
                    totalCount = totalCount
                )
            }
        }
    }
}