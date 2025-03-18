package com.shunlight_library.microwaveconverter

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shunlight_library.microwaveconverter.ui.theme.MicrowaveConverterTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize settings manager
        settingsManager = SettingsManager(this)
        val initialSettings = settingsManager.loadSettings()

        // Apply saved locale
        applyLocale(initialSettings.locale)

        enableEdgeToEdge()
        setContent {
            var settings by remember { mutableStateOf(initialSettings) }

            // Update settings when they change
            DisposableEffect(settings) {
                // Save settings whenever they change
                settingsManager.saveSettings(settings)

                // Apply locale settings
                applyLocale(settings.locale)

                onDispose { }
            }

            MicrowaveConverterTheme(
                darkTheme = settings.isDarkMode
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MicrowaveConverterApp(
                        settings = settings,
                        onToggleDarkMode = { isDarkMode ->
                            settings = settings.copy(isDarkMode = isDarkMode)
                        },
                        onChangeLanguage = { languageCode ->
                            val newLocale = settingsManager.changeLanguage(languageCode)
                            settings = settings.copy(locale = newLocale)
                            applyLocale(newLocale)
                            recreate() // アクティビティを再作成
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    // Apply locale to the app context
    private fun applyLocale(locale: Locale) {
        val resources = resources
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(locale)
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }
}

@Composable
fun MicrowaveConverterApp(
    settings: AppSettings,
    onToggleDarkMode: (Boolean) -> Unit,
    onChangeLanguage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Show settings state
        var showSettings by remember { mutableStateOf(false) }

        // コンバーターロジックのインスタンス
        val converter = remember { MicrowaveConverter() }

        // UI状態の管理
        var sourceWattage by remember { mutableStateOf(converter.sourceWattage.toString()) }
        var sourceMinutes by remember { mutableStateOf(converter.sourceMinutes.toString()) }
        var sourceSeconds by remember { mutableStateOf(converter.sourceSeconds.toString()) }
        var targetWattage by remember { mutableStateOf(converter.targetWattage.toString()) }

        // 結果状態の管理
        var resultSourceWattage by remember { mutableStateOf(converter.sourceWattage) }
        var resultTargetWattage by remember { mutableStateOf(converter.targetWattage) }
        var resultSourceMinutes by remember { mutableStateOf(converter.sourceMinutes) }
        var resultSourceSeconds by remember { mutableStateOf(converter.sourceSeconds) }
        var resultTargetMinutes by remember { mutableStateOf(0) }
        var resultTargetSeconds by remember { mutableStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App header with title and settings button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { showSettings = !showSettings }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Show settings panel if toggled
            if (showSettings) {
                SettingsPanel(
                    settings = settings,
                    onDarkModeToggle = onToggleDarkMode,
                    onLanguageChange = onChangeLanguage,
                    onCloseSettings = { showSettings = false }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 入力フォーム
            InputForm(
                sourceWattage = sourceWattage,
                sourceMinutes = sourceMinutes,
                sourceSeconds = sourceSeconds,
                targetWattage = targetWattage,
                onSourceWattageChange = { sourceWattage = it },
                onSourceMinutesChange = { sourceMinutes = it },
                onSourceSecondsChange = { sourceSeconds = it },
                onTargetWattageChange = { targetWattage = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 変換ボタン
            Button(
                onClick = {
                    // 入力値をコンバーターに設定
                    converter.sourceWattage = converter.safeParseInt(sourceWattage, 500)
                    converter.targetWattage = converter.safeParseInt(targetWattage, 600)
                    converter.sourceMinutes = converter.safeParseInt(sourceMinutes, 2)
                    converter.sourceSeconds = converter.safeParseInt(sourceSeconds, 0)

                    // 変換実行
                    val result = converter.convert()

                    // 結果を更新
                    resultSourceWattage = converter.sourceWattage
                    resultTargetWattage = converter.targetWattage
                    resultSourceMinutes = converter.sourceMinutes
                    resultSourceSeconds = converter.sourceSeconds
                    resultTargetMinutes = result.first
                    resultTargetSeconds = result.second

                    // 入力フィールドの値を更新（バリデーション後の値）
                    sourceWattage = converter.sourceWattage.toString()
                    sourceMinutes = converter.sourceMinutes.toString()
                    sourceSeconds = converter.sourceSeconds.toString()
                    targetWattage = converter.targetWattage.toString()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.convert_button), fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 結果表示
            ResultCard(
                sourceWattage = resultSourceWattage,
                targetWattage = resultTargetWattage,
                sourceMinutes = resultSourceMinutes,
                sourceSeconds = resultSourceSeconds,
                targetMinutes = resultTargetMinutes,
                targetSeconds = resultTargetSeconds
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputForm(
    sourceWattage: String,
    sourceMinutes: String,
    sourceSeconds: String,
    targetWattage: String,
    onSourceWattageChange: (String) -> Unit,
    onSourceMinutesChange: (String) -> Unit,
    onSourceSecondsChange: (String) -> Unit,
    onTargetWattageChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 変換元ワット数
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.source_wattage_label),
                modifier = Modifier.width(120.dp)
            )
            OutlinedTextField(
                value = sourceWattage,
                onValueChange = onSourceWattageChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = stringResource(R.string.watt_unit))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 加熱時間
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.cooking_time_label),
                modifier = Modifier.width(120.dp)
            )
            OutlinedTextField(
                value = sourceMinutes,
                onValueChange = onSourceMinutesChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(70.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = stringResource(R.string.minutes_unit))
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = sourceSeconds,
                onValueChange = onSourceSecondsChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(70.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = stringResource(R.string.seconds_unit))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 変換先ワット数
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.target_wattage_label),
                modifier = Modifier.width(120.dp)
            )
            OutlinedTextField(
                value = targetWattage,
                onValueChange = onTargetWattageChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = stringResource(R.string.watt_unit))
        }
    }
}

@Composable
fun ResultCard(
    sourceWattage: Int,
    targetWattage: Int,
    sourceMinutes: Int,
    sourceSeconds: Int,
    targetMinutes: Int,
    targetSeconds: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.result_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.result_format_source,
                    sourceWattage,
                    sourceMinutes,
                    sourceSeconds
                )
            )

            Text(
                text = stringResource(
                    R.string.result_format_target,
                    targetWattage
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.result_format_time,
                    targetMinutes,
                    targetSeconds
                ),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MicrowaveConverterPreview() {
    val settings = AppSettings(isDarkMode = false, locale = Locale.getDefault())

    MicrowaveConverterTheme {
        MicrowaveConverterApp(
            settings = settings,
            onToggleDarkMode = {},
            onChangeLanguage = {}
        )
    }
}