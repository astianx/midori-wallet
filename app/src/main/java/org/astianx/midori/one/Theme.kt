package org.astianx.midori.one

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val storageValue: String, val labelRes: Int) {
    SYSTEM("system", R.string.theme_system),
    LIGHT("light", R.string.theme_light),
    DARK("dark", R.string.theme_dark);

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorage(value: String?) = entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

/** Appearance is independent of wallet credentials and survives wallet deletion. */
internal class ThemePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("midori_appearance", Context.MODE_PRIVATE)
    fun load() = ThemeMode.fromStorage(preferences.getString("theme", null))
    fun save(mode: ThemeMode) = preferences.edit().putString("theme", mode.storageValue).apply()
}

internal val LocalDarkTheme = staticCompositionLocalOf { false }

private val MidoriLightColors = lightColorScheme(
    primary = Color(0xFF246B53), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EFE2), onPrimaryContainer = Color(0xFF12382A),
    secondary = Color(0xFF245E91), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E9FA), onSecondaryContainer = Color(0xFF17364F),
    tertiary = Color(0xFF805600), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8B8), onTertiaryContainer = Color(0xFF4F3500),
    background = Color(0xFFFAFAF8), onBackground = Color(0xFF202521),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF202521),
    surfaceVariant = Color(0xFFEEF1ED), onSurfaceVariant = Color(0xFF535E56),
    outline = Color(0xFF78847B), outlineVariant = Color(0xFFD5DCD6),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
)

private val MidoriDarkColors = darkColorScheme(
    primary = Color(0xFF8DD5B5), onPrimary = Color(0xFF103B2B),
    primaryContainer = Color(0xFF204F3B), onPrimaryContainer = Color(0xFFD5EFE2),
    secondary = Color(0xFF9CCBFA), onSecondary = Color(0xFF12334E),
    secondaryContainer = Color(0xFF214A6E), onSecondaryContainer = Color(0xFFD8E9FA),
    tertiary = Color(0xFFFFCC80), onTertiary = Color(0xFF452F00),
    tertiaryContainer = Color(0xFF574219), onTertiaryContainer = Color(0xFFFFE8B8),
    background = Color(0xFF121612), onBackground = Color(0xFFE5EBE5),
    surface = Color(0xFF1B211C), onSurface = Color(0xFFE5EBE5),
    surfaceVariant = Color(0xFF29312B), onSurfaceVariant = Color(0xFFB8C4B9),
    outline = Color(0xFF829186), outlineVariant = Color(0xFF3D4A40),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun MidoriTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = if (darkTheme) MidoriDarkColors else MidoriLightColors, content = content)
    }
}
