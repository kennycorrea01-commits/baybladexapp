package com.bxtracker.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.bxtracker.app.R
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.bxtracker.app.data.BeyType
import com.bxtracker.app.data.ThemeMode

/** True black dark theme. Surfaces sit just above #000 so cards still read as separate. */
private val OledColors = darkColorScheme(
    primary = Color(0xFF179CD8),
    onPrimary = Color(0xFF0B1E2B),
    primaryContainer = Color(0xFF0F729F),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF74C6EA),
    onSecondary = Color(0xFF0B1E2B),
    secondaryContainer = Color(0xFF0E3448),
    onSecondaryContainer = Color(0xFF74C6EA),
    tertiary = Color(0xFF74C6EA),
    onTertiary = Color(0xFF0B1E2B),
    background = Color.Black,
    onBackground = Color(0xFFF3F7FB),
    surface = Color.Black,
    onSurface = Color(0xFFF3F7FB),
    surfaceVariant = Color(0xFF161B22),
    onSurfaceVariant = Color(0xFFA9B8C7),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080B0F),
    surfaceContainer = Color(0xFF0D1117),
    surfaceContainerHigh = Color(0xFF161B22),
    surfaceContainerHighest = Color(0xFF1C222A),
    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF21262D),
    error = Color(0xFFFF8A8D),
    onError = Color(0xFF2B0A0B),
    errorContainer = Color(0xFF3A1416),
    onErrorContainer = Color(0xFFFFB3B5),
    inverseSurface = Color(0xFFF3F7FB),
    inverseOnSurface = Color(0xFF0B1E2B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F729F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEFF9),
    onPrimaryContainer = Color(0xFF0B1E2B),
    secondary = Color(0xFF0F729F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEFF9),
    onSecondaryContainer = Color(0xFF0F729F),
    tertiary = Color(0xFF0F729F),
    onTertiary = Color.White,
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF0B1E2B),
    surface = Color(0xFFF7F9FB),
    onSurface = Color(0xFF0B1E2B),
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = Color(0xFF4A5666),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEEF2F6),
    surfaceContainerHighest = Color(0xFFE1E7EE),
    outline = Color(0xFFCBD4DE),
    outlineVariant = Color(0xFFE1E7EE),
    error = Color(0xFFC4282D),
    onError = Color.White,
    errorContainer = Color(0xFFFDE3E4),
    onErrorContainer = Color(0xFF7D2A2E),
    inverseSurface = Color(0xFF0B1E2B),
    inverseOnSurface = Color(0xFFF3F7FB),
)

/** Montserrat (SIL Open Font License, see assets/licenses). One variable font file covers every weight. */
@OptIn(ExperimentalTextApi::class)
val Montserrat = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { w ->
        Font(R.font.montserrat, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    }
)

private val BxTypography = Typography().let { t ->
    fun TextStyle.m() = copy(fontFamily = Montserrat)
    Typography(
        displayLarge = t.displayLarge.m(), displayMedium = t.displayMedium.m(), displaySmall = t.displaySmall.m(),
        headlineLarge = t.headlineLarge.m(), headlineMedium = t.headlineMedium.m(), headlineSmall = t.headlineSmall.m(),
        titleLarge = t.titleLarge.m(), titleMedium = t.titleMedium.m(), titleSmall = t.titleSmall.m(),
        bodyLarge = t.bodyLarge.m(), bodyMedium = t.bodyMedium.m(), bodySmall = t.bodySmall.m(),
        labelLarge = t.labelLarge.m(), labelMedium = t.labelMedium.m(), labelSmall = t.labelSmall.m(),
    )
}

val LocalIsDark = staticCompositionLocalOf { true }

data class Tint(val fg: Color, val bg: Color)

/** Colors for Attack / Defense / Stamina / Balance tags, tuned per theme for contrast. */
@Composable
fun typeTint(type: BeyType?): Tint {
    val dark = LocalIsDark.current
    return when (type) {
        BeyType.ATTACK -> if (dark) Tint(Color(0xFFFF8A8D), Color(0x29E5484D)) else Tint(Color(0xFFC4282D), Color(0x1FE5484D))
        BeyType.DEFENSE -> if (dark) Tint(Color(0xFF74C6EA), Color(0x2E179CD8)) else Tint(Color(0xFF0F729F), Color(0x24179CD8))
        BeyType.STAMINA -> if (dark) Tint(Color(0xFF4FD3A0), Color(0x2919B47A)) else Tint(Color(0xFF0E8A5C), Color(0x2419B47A))
        BeyType.BALANCE -> if (dark) Tint(Color(0xFFF7BE5C), Color(0x29F5A623)) else Tint(Color(0xFF9A5B00), Color(0x2EF5A623))
        null -> Tint(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceContainerHigh)
    }
}

@Composable fun winColor(): Color = if (LocalIsDark.current) Color(0xFF4FD3A0) else Color(0xFF0E8A5C)
@Composable fun lossColor(): Color = MaterialTheme.colorScheme.error
@Composable fun warnColor(): Color = if (LocalIsDark.current) Color(0xFFF7BE5C) else Color(0xFF9A5B00)

@Composable
fun BxTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.OLED -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
            window.decorView.setBackgroundColor(if (dark) android.graphics.Color.BLACK else 0xFFF7F9FB.toInt())
        }
    }
    CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) OledColors else LightColors,
            typography = BxTypography,
            content = content,
        )
    }
}
