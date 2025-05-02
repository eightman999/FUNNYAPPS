package com.shunlight_library.novel_reader.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Define a data class to hold our app-specific settings
data class AppSettings(
    val themeMode: String = "System",
    val fontFamily: String = "Gothic",
    val fontSize: Int = 16,
    val backgroundColor: String = "White",
    val selfServerAccess: Boolean = false,
    val textOrientation: String = "Horizontal"
)

// Create a composition local for the app settings
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

// Map of background colors
val BackgroundColors = mapOf(
    "White" to Color.White,
    "Cream" to Color(0xFFF5F5DC),
    "Light Gray" to Color(0xFFEEEEEE),
    "Light Blue" to Color(0xFFE6F2FF)
)

// Extension property to get the actual background color from the settings
val AppSettings.backgroundColorValue: Color
    @Composable
    @ReadOnlyComposable
    get() = BackgroundColors[backgroundColor] ?: Color.White

// Extension property to get the actual font family from the settings
val AppSettings.fontFamilyValue: FontFamily
    @Composable
    @ReadOnlyComposable
    get() = when (fontFamily) {
        "Mincho" -> FontFamily.Serif // Using Serif as the closest to Mincho
        else -> FontFamily.Default // Default is Sans-serif (Gothic)
    }

// Create a composable function that wraps the app's theme
@Composable
fun AppTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    // Determine if dark theme should be used
    val darkTheme = when (settings.themeMode) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }

    // Dynamic color is available on Android 12+
    val dynamicColor = settings.themeMode == "System" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(LocalContext.current)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Create a custom typography with the selected font family and size
    val typography = Typography.copy(
        bodyLarge = Typography.bodyLarge.copy(
            fontFamily = settings.fontFamilyValue,
            fontSize = settings.fontSize.sp
        ),
        bodyMedium = Typography.bodyMedium.copy(
            fontFamily = settings.fontFamilyValue,
            fontSize = (settings.fontSize - 2).sp
        ),
        bodySmall = Typography.bodySmall.copy(
            fontFamily = settings.fontFamilyValue,
            fontSize = (settings.fontSize - 4).sp
        ),
        titleLarge = Typography.titleLarge.copy(
            fontFamily = settings.fontFamilyValue
        ),
        titleMedium = Typography.titleMedium.copy(
            fontFamily = settings.fontFamilyValue
        ),
        titleSmall = Typography.titleSmall.copy(
            fontFamily = settings.fontFamilyValue
        )
    )

    // Apply the status bar color
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // Provide the app settings to the composition
    CompositionLocalProvider(LocalAppSettings provides settings) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

// Extension function to use the app settings in composables
@Composable
fun TextStyle.withAppSettings(settings: AppSettings = LocalAppSettings.current): TextStyle {
    return this.copy(
        fontFamily = settings.fontFamilyValue,
        fontSize = settings.fontSize.sp
    )
}