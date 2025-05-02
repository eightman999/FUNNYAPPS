package com.shunlight_library.novel_reader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    // State variables for settings with initial values from DataStore
    var themeMode by remember { mutableStateOf("System") }
    var fontFamily by remember { mutableStateOf("Gothic") }
    var fontSize by remember { mutableStateOf(16) }
    var backgroundColor by remember { mutableStateOf("White") }
    var selfServerAccess by remember { mutableStateOf(false) }
    var textOrientation by remember { mutableStateOf("Horizontal") }
    var selfServerPath by remember { mutableStateOf("") }
// ダイアログ表示のための状態変数
    var showDBWriteDialog by remember { mutableStateOf(false) }
// 選択されたURIを一時的に保持する変数
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    // Load saved preferences when the screen is created
    LaunchedEffect(key1 = true) {
        themeMode = settingsStore.themeMode.first()
        fontFamily = settingsStore.fontFamily.first()
        fontSize = settingsStore.fontSize.first()
        backgroundColor = settingsStore.backgroundColor.first()
        selfServerAccess = settingsStore.selfServerAccess.first()
        textOrientation = settingsStore.textOrientation.first()
        selfServerPath = settingsStore.selfServerPath.first() // 追加
    }

    // ファイル選択のランチャー
    // ファイル選択時に永続的な権限を取得するよう修正
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // URIからパスを取得
                val path = uri.toString()
                selfServerPath = path

                // 永続的な権限を取得
                val contentResolver = context.contentResolver
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                try {
                    // 永続的な権限を付与
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    Log.d("SettingsScreen", "取得した永続的なアクセス権限: $path")

                    // 権限が正しく取得できたか確認
                    val hasPermission = contentResolver.persistedUriPermissions.any {
                        it.uri == uri && it.isReadPermission && it.isWritePermission
                    }

                    if (hasPermission) {
                        // 成功メッセージをトーストで表示
                        Toast.makeText(context, "ディレクトリへのアクセス権限を取得しました", Toast.LENGTH_SHORT).show()

                        // 選択されたURIを保存
                        selectedUri = uri

                        // 内部DBへの書き込み確認ダイアログを表示
                        showDBWriteDialog = true
                    } else {
                        // 権限取得失敗の場合
                        Toast.makeText(context, "アクセス権限の取得に失敗しました", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("SettingsScreen", "権限取得エラー: ${e.message}", e)
                    Toast.makeText(context, "アクセス権限の取得中にエラーが発生しました", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    if (showDBWriteDialog && selectedUri != null) {
        AlertDialog(
            onDismissRequest = {
                showDBWriteDialog = false
            },
            title = {
                Text("内部DBへの書き込み")
            },
            text = {
                Text("選択したディレクトリの情報を内部DBに書き込みますか？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Yesが選択された場合の処理
                        // TODO: 内部DBへの書き込み処理（後で実装）
                        Toast.makeText(context, "内部DBに書き込みました", Toast.LENGTH_SHORT).show()
                        showDBWriteDialog = false
                    }
                ) {
                    Text("はい")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // Noが選択された場合の処理
                        Toast.makeText(context, "内部DBへの書き込みをキャンセルしました", Toast.LENGTH_SHORT).show()
                        showDBWriteDialog = false
                    }
                ) {
                    Text("いいえ")
                }
            }
        )
    }
    // Background color options
    val backgroundOptions = listOf("White", "Cream", "Light Gray", "Light Blue")
    val backgroundColors = mapOf(
        "White" to Color.White,
        "Cream" to Color(0xFFF5F5DC),
        "Light Gray" to Color(0xFFEEEEEE),
        "Light Blue" to Color(0xFFE6F2FF)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
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
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme Mode Setting
            SettingSection(title = "表示モード") {
                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    RadioButtonOption(
                        text = "システム設定に従う",
                        selected = themeMode == "System",
                        onClick = { themeMode = "System" }
                    )
                    RadioButtonOption(
                        text = "ライトモード",
                        selected = themeMode == "Light",
                        onClick = { themeMode = "Light" }
                    )
                    RadioButtonOption(
                        text = "ダークモード",
                        selected = themeMode == "Dark",
                        onClick = { themeMode = "Dark" }
                    )
                }
            }

            HorizontalDivider()

            // Font Family Setting
            SettingSection(title = "フォント") {
                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    RadioButtonOption(
                        text = "ゴシック体",
                        selected = fontFamily == "Gothic",
                        onClick = { fontFamily = "Gothic" }
                    )
                    RadioButtonOption(
                        text = "明朝体",
                        selected = fontFamily == "Mincho",
                        onClick = { fontFamily = "Mincho" }
                    )
                }
            }

            HorizontalDivider()

            // Font Size Setting
            SettingSection(title = "フォントサイズ (${fontSize}sp)") {
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { fontSize = it.toInt() },
                    valueRange = 12f..24f,
                    steps = 6,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("小", fontSize = 12.sp)
                    Text("中", fontSize = 16.sp)
                    Text("大", fontSize = 24.sp)
                }
            }

            HorizontalDivider()

            // Background Color Setting
            SettingSection(title = "背景色") {
                backgroundOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = backgroundColor == option,
                                onClick = { backgroundColor = option }
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = backgroundColor == option,
                            onClick = null // null because we're handling click on the row
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(option)
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = backgroundColors[option] ?: Color.White
                                )
                        )
                    }
                }
            }

            HorizontalDivider()

            // Self-Server Access Setting
            SettingSection(title = "自己サーバーアクセス") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自己サーバーへの接続")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = selfServerAccess,
                        onCheckedChange = { selfServerAccess = it }
                    )
                }
            }

            HorizontalDivider()

            // Text Orientation Setting
            SettingSection(title = "テキスト表示の向き") {
                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    RadioButtonOption(
                        text = "横書き",
                        selected = textOrientation == "Horizontal",
                        onClick = { textOrientation = "Horizontal" }
                    )
                    RadioButtonOption(
                        text = "縦書き",
                        selected = textOrientation == "Vertical",
                        onClick = { textOrientation = "Vertical" }
                    )
                }
            }

            // 自己サーバーアクセスがONの場合のみディレクトリ選択ボタンを表示
            if (selfServerAccess) {
                HorizontalDivider()

                SettingSection(title = "自己サーバーのディレクトリ設定") {
                    // 選択されたパスがある場合は表示
                    if (selfServerPath.isNotEmpty()) {
                        Text(
                            text = "選択されたディレクトリ: $selfServerPath",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    Text(
                        text = "小説サーバーのrootディレクトリを選択してください。\nindex.htmlファイルおよびnovelsディレクトリが含まれるフォルダを選んでください。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    Button(
                        onClick = {
                            // ファイル選択インテントを起動
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                // ディレクトリ選択に変更（ACTION_OPEN_DOCUMENT_TREEを使用）
                                // ドキュメントツリー全体への永続的な権限を要求
                                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION) // プレフィックスURIへの権限も取得
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            }
                            filePickerLauncher.launch(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text("ディレクトリを選択")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Save Button
            Button(
                onClick = {
                    scope.launch {
                        try {
                            // 設定を保存
                            settingsStore.saveAllSettings(
                                themeMode = themeMode,
                                fontFamily = fontFamily,
                                fontSize = fontSize,
                                backgroundColor = backgroundColor,
                                selfServerAccess = selfServerAccess,
                                textOrientation = textOrientation,
                                selfServerPath = selfServerPath
                            )

                            // 保存確認ログ
                            Log.d("SettingsScreen", "設定を保存しました: selfServerAccess=$selfServerAccess, selfServerPath=$selfServerPath")

                            // 保存したことをユーザーに通知
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "設定を保存しました", Toast.LENGTH_SHORT).show()
                            }

                            // キャッシュをクリア


                            onBack()
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "設定保存エラー: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "設定の保存に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("設定を保存")
            }
        }
    }
}

@Composable
fun SettingSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
fun RadioButtonOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null // null because we're handling click on the row
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text)
    }
}