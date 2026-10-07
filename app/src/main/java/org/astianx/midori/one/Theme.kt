package org.astianx.midori.one

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    primary = Color(0xFF236B56), onPrimary = Color.White,
    primaryContainer = Color(0xFFDAEDDF), onPrimaryContainer = Color(0xFF193C2F),
    secondary = Color(0xFF51685D), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EAE2), onSecondaryContainer = Color(0xFF283F33),
    tertiary = Color(0xFF805600), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8B8), onTertiaryContainer = Color(0xFF4F3500),
    background = Color(0xFFF7F8F2), onBackground = Color(0xFF202B25),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF202B25),
    surfaceVariant = Color(0xFFEDF0E8), onSurfaceVariant = Color(0xFF5E6B62),
    outline = Color(0xFF77867B), outlineVariant = Color(0xFFDEE5DB),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
)

private val MidoriDarkColors = darkColorScheme(
    primary = Color(0xFFA3D8B8), onPrimary = Color(0xFF123B29),
    primaryContainer = Color(0xFF294B3A), onPrimaryContainer = Color(0xFFDAEDDF),
    secondary = Color(0xFFBCD0C1), onSecondary = Color(0xFF25392C),
    secondaryContainer = Color(0xFF344B3C), onSecondaryContainer = Color(0xFFE2EAE2),
    tertiary = Color(0xFFFFCC80), onTertiary = Color(0xFF452F00),
    tertiaryContainer = Color(0xFF574219), onTertiaryContainer = Color(0xFFFFE8B8),
    background = Color(0xFF141C18), onBackground = Color(0xFFE5ECE3),
    surface = Color(0xFF1E2922), onSurface = Color(0xFFE5ECE3),
    surfaceVariant = Color(0xFF2A352D), onSurfaceVariant = Color(0xFFB6C4B9),
    outline = Color(0xFF85978B), outlineVariant = Color(0xFF3C4B40),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

// Bundled OFL font: no provider requests or network dependency at runtime.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val MidoriFont = FontFamily(
    Font(R.font.manrope, FontWeight.Normal, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(400))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(700)))
)
private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
    TextStyle(fontFamily = MidoriFont, fontSize = size.sp, lineHeight = height.sp,
        fontWeight = weight, letterSpacing = tracking.sp, fontFeatureSettings = "tnum")
private val MidoriTypography = Typography(
    displayLarge = type(48, 56, FontWeight.Bold, -1.6),
    displayMedium = type(40, 48, FontWeight.Bold, -1.2),
    displaySmall = type(34, 42, FontWeight.Bold, -1.0),
    headlineLarge = type(30, 38, FontWeight.Bold, -0.8),
    headlineMedium = type(26, 34, FontWeight.Bold, -0.6),
    headlineSmall = type(23, 30, FontWeight.SemiBold, -0.4),
    titleLarge = type(21, 28, FontWeight.SemiBold, -0.3),
    titleMedium = type(16, 24, FontWeight.SemiBold),
    titleSmall = type(14, 20, FontWeight.SemiBold),
    bodyLarge = type(16, 24), bodyMedium = type(14, 21), bodySmall = type(12, 18),
    labelLarge = type(14, 20, FontWeight.SemiBold),
    labelMedium = type(12, 18, FontWeight.SemiBold),
    labelSmall = type(11, 16, FontWeight.Medium)
)

@Composable
fun MidoriTheme(darkTheme: Boolean = isSystemInDarkTheme(), style: AppStyle = AppStyle(), content: @Composable () -> Unit) {
    val base = if (darkTheme) MidoriDarkColors else MidoriLightColors
    val accent = Color(style.accent.color)
    val primary = if (darkTheme) androidx.compose.ui.graphics.lerp(accent, Color.White, .62f) else accent
    val colors = if (style.accent == AccentTone.JADE) base else base.copy(primary = primary,
        onPrimary = if (darkTheme) Color(0xFF14201A) else Color.White,
        primaryContainer = androidx.compose.ui.graphics.lerp(base.surface, accent, if (darkTheme) .38f else .15f),
        onPrimaryContainer = base.onSurface)
    val family = when (style.typeface) { AppTypeface.MANROPE -> MidoriFont; AppTypeface.SYSTEM -> FontFamily.Default; AppTypeface.SERIF -> FontFamily.Serif }
    val typography = MidoriTypography.run { copy(
        displayLarge = displayLarge.copy(fontFamily = family), displayMedium = displayMedium.copy(fontFamily = family), displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family), headlineMedium = headlineMedium.copy(fontFamily = family), headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family), titleMedium = titleMedium.copy(fontFamily = family), titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family), bodyMedium = bodyMedium.copy(fontFamily = family), bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family), labelMedium = labelMedium.copy(fontFamily = family), labelSmall = labelSmall.copy(fontFamily = family)) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    CompositionLocalProvider(LocalDarkTheme provides darkTheme, LocalAppStyle provides style,
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, density.fontScale * style.textScale / 100f)) {
        MaterialTheme(colorScheme = if (darkTheme && style.pureBlack) colors.copy(background = Color.Black) else colors,
            typography = typography,
            shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp))) {
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                content = content)
        }
    }
}
