package com.shunlight_library.nr_reader.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.shunlight_library.nr_reader.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun NRreaderTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }

    // Collect all settings as state
    val themeMode by settingsStore.themeMode.collectAsState(initial = settingsStore.defaultThemeMode)
    val fontFamily by settingsStore.fontFamily.collectAsState(initial = settingsStore.defaultFontFamily)
    val fontSize by settingsStore.fontSize.collectAsState(initial = settingsStore.defaultFontSize)
    val backgroundColor by settingsStore.backgroundColor.collectAsState(initial = settingsStore.defaultBackgroundColor)
    val selfServerAccess by settingsStore.selfServerAccess.collectAsState(initial = settingsStore.defaultSelfServerAccess)
    val textOrientation by settingsStore.textOrientation.collectAsState(initial = settingsStore.defaultTextOrientation)

    // Create settings object
    val appSettings = AppSettings(
        themeMode = themeMode,
        fontFamily = fontFamily,
        fontSize = fontSize,
        backgroundColor = backgroundColor,
        selfServerAccess = selfServerAccess,
        textOrientation = textOrientation
    )

    // Apply the app theme with our settings
    AppTheme(
        settings = appSettings,
        content = content
    )
}