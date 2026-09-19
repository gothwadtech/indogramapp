package com.gothwad.grixchat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity

private val DarkColorScheme = darkColorScheme(
    primary = GrixPrimary,
    secondary = GrixSecondary,
    tertiary = GrixAccent,
    background = GrixDarkBackground,
    surface = GrixSurfaceDark,
    surfaceVariant = GrixSurfaceCard,
    onPrimary = GrixDarkBackground,
    onSecondary = GrixDarkBackground,
    onBackground = GrixLightBackground,
    onSurface = GrixLightBackground
)

private val LightColorScheme = lightColorScheme(
    primary = GrixPrimaryLight,
    secondary = GrixSecondaryLight,
    tertiary = GrixAccent,
    background = GrixLightBackground,
    surface = GrixSurfaceLight,
    surfaceVariant = GrixSurfaceCardLight,
    onPrimary = GrixSurfaceLight,
    onSecondary = GrixSurfaceLight,
    onBackground = GrixDarkBackground,
    onSurface = GrixDarkBackground
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Allow dynamic colors on API 31+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            SideEffect {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
