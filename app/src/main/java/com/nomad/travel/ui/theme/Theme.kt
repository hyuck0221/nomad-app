package com.nomad.travel.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NomadColorScheme = darkColorScheme(
    primary = NomadRoyal,
    onPrimary = NomadSilver,
    primaryContainer = NomadViolet,
    onPrimaryContainer = NomadSilver,
    secondary = NomadGlow,
    onSecondary = NomadInk,
    tertiary = NomadGlow,
    background = NomadInk,
    onBackground = NomadSilver,
    surface = NomadNight,
    onSurface = NomadSilver,
    surfaceVariant = NomadPurple,
    onSurfaceVariant = NomadMist,
    outline = NomadMuted,
    error = Color(0xFFFF6B8A),
    onError = NomadSilver
)

@Composable
fun NomadTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = NomadColorScheme,
        typography = NomadTypography
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(NomadGradient)
        ) {
            content()
        }
    }
}
