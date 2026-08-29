package com.folio.viewer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Folio palette — ink indigo + warm sand. */
val InkIndigo = Color(0xFF3B4BE0)
val InkIndigoDark = Color(0xFF1F2AAB)
val WarmSand = Color(0xFFF5EFE4)
val SlateInk = Color(0xFF12141C)
val SlateInk2 = Color(0xFF1A1D28)
val AmberHighlight = Color(0xFFFFB547)
val SuccessGreen = Color(0xFF2E9E6B)
val ErrorRed = Color(0xFFD64545)

private val LightColors = lightColorScheme(
    primary = InkIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E4FF),
    onPrimaryContainer = InkIndigoDark,
    secondary = Color(0xFF8B7B5D),
    onSecondary = Color.White,
    background = WarmSand,
    onBackground = Color(0xFF1B1A17),
    surface = Color(0xFFFDF8EF),
    onSurface = Color(0xFF1B1A17),
    surfaceVariant = Color(0xFFEAE3D2),
    onSurfaceVariant = Color(0xFF4F4A3D),
    tertiary = AmberHighlight,
    error = ErrorRed,
    outline = Color(0xFFB6AF9E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AA5FF),
    onPrimary = Color(0xFF0B1080),
    primaryContainer = InkIndigoDark,
    onPrimaryContainer = Color(0xFFE1E4FF),
    secondary = Color(0xFFCBB98F),
    onSecondary = Color(0xFF2E2712),
    background = SlateInk,
    onBackground = Color(0xFFE7E6E2),
    surface = SlateInk2,
    onSurface = Color(0xFFE7E6E2),
    surfaceVariant = Color(0xFF262A38),
    onSurfaceVariant = Color(0xFFC7C6C1),
    tertiary = AmberHighlight,
    error = Color(0xFFFF8A80),
    outline = Color(0xFF4B4F5C)
)

enum class ThemeMode { System, Light, Dark }

@Composable
fun FolioTheme(
    mode: ThemeMode = ThemeMode.System,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val colors = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = FolioTypography,
        content = content
    )
}
