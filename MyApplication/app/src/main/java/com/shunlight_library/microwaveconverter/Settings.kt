package com.shunlight_library.microwaveconverter

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// Settings data class to hold user preferences
data class AppSettings(
    val isDarkMode: Boolean,
    val locale: Locale
)

// Settings manager to save and load preferences
class SettingsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // Default settings
    val defaultSettings = AppSettings(
        isDarkMode = false,
        locale = Locale.getDefault()
    )

    // Get current settings
    fun loadSettings(): AppSettings {
        val isDarkMode = prefs.getBoolean("dark_mode", defaultSettings.isDarkMode)
        val languageCode = prefs.getString("language_code", defaultSettings.locale.language) ?: defaultSettings.locale.language
        val locale = when (languageCode) {
            "en" -> Locale.ENGLISH
            "fr" -> Locale.FRENCH
            "de" -> Locale.GERMAN
            "es" -> Locale("es")
            "it" -> Locale.ITALIAN
            "ja" -> Locale.JAPANESE
            "ru" -> Locale("ru")
            "zh" -> Locale.CHINESE
            "pt" -> Locale("pt")
            "el" -> Locale("el")
            else -> Locale.getDefault()
        }

        return AppSettings(isDarkMode, locale)
    }

    // Save settings
    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putBoolean("dark_mode", settings.isDarkMode)
            .putString("language_code", settings.locale.language)
            .apply()
    }

    // Toggle dark mode
    fun toggleDarkMode(current: Boolean): Boolean {
        val newSetting = !current
        prefs.edit().putBoolean("dark_mode", newSetting).apply()
        return newSetting
    }

    // Change language
    fun changeLanguage(languageCode: String): Locale {
        val locale = when (languageCode) {
            "en" -> Locale.ENGLISH
            "fr" -> Locale.FRENCH
            "de" -> Locale.GERMAN
            "es" -> Locale("es")
            "it" -> Locale.ITALIAN
            "ja" -> Locale.JAPANESE
            "ru" -> Locale("ru")
            "zh" -> Locale.CHINESE
            "pt" -> Locale("pt")
            "el" -> Locale("el")
            else -> Locale.getDefault()
        }
        prefs.edit().putString("language_code", languageCode).apply()
        return locale
    }
}

// Data class for language options
data class LanguageOption(
    val code: String,
    val displayName: String,
    val nativeName: String
)

// List of supported languages
val supportedLanguages = listOf(
    LanguageOption("en", "English", "English"),
    LanguageOption("fr", "French", "Français"),
    LanguageOption("de", "German", "Deutsch"),
    LanguageOption("es", "Spanish", "Español"),
    LanguageOption("it", "Italian", "Italiano"),
    LanguageOption("ja", "Japanese", "日本語"),
    LanguageOption("ru", "Russian", "Русский"),
    LanguageOption("zh", "Chinese", "中文"),
    LanguageOption("pt", "Portuguese", "Português"),
    LanguageOption("el", "Greek", "Ελληνικά")
)

// Settings panel UI
@Composable
fun SettingsPanel(
    settings: AppSettings,
    onDarkModeToggle: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onCloseSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()

            // Dark mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (settings.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = "Theme Mode",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Dark mode")
                }
                Switch(
                    checked = settings.isDarkMode,
                    onCheckedChange = onDarkModeToggle
                )
            }

            Divider()

            // Language selection
            var languageMenuExpanded by remember { mutableStateOf(false) }
            val currentLanguage = supportedLanguages.find { it.code == settings.locale.language }
                ?: supportedLanguages.first()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clickable { languageMenuExpanded = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Language",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Language")
                }

                Box {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "${currentLanguage.nativeName} (${currentLanguage.displayName})")
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select language"
                        )
                    }

                    DropdownMenu(
                        expanded = languageMenuExpanded,
                        onDismissRequest = { languageMenuExpanded = false }
                    ) {
                        supportedLanguages.forEach { language ->
                            DropdownMenuItem(
                                text = { Text("${language.nativeName} (${language.displayName})") },
                                onClick = {
                                    onLanguageChange(language.code)
                                    languageMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}