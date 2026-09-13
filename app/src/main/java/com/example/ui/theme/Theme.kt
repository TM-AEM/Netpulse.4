package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.data.preferences.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = NetPulsePrimaryDark,
    onPrimary = NetPulseOnPrimaryDark,
    primaryContainer = NetPulsePrimaryContainerDark,
    onPrimaryContainer = NetPulseOnPrimaryContainerDark,
    secondary = NetPulseSecondaryDark,
    onSecondary = NetPulseOnSecondaryDark,
    secondaryContainer = NetPulseSecondaryContainerDark,
    onSecondaryContainer = NetPulseOnSecondaryContainerDark,
    background = NetPulseBackgroundDark,
    onBackground = NetPulseOnSurfaceDark,
    surface = NetPulseSurfaceDark,
    onSurface = NetPulseOnSurfaceDark,
    surfaceVariant = NetPulseSurfaceVariantDark,
    onSurfaceVariant = NetPulseOnSurfaceVariantDark,
    surfaceContainer = NetPulseSurfaceContainerDark,
    surfaceContainerHigh = NetPulseSurfaceContainerHighDark,
    surfaceContainerHighest = NetPulseSurfaceContainerHighestDark,
    outline = NetPulseOutlineDark,
    outlineVariant = NetPulseOutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = NetPulsePrimaryLight,
    onPrimary = NetPulseOnPrimaryLight,
    primaryContainer = NetPulsePrimaryContainerLight,
    onPrimaryContainer = NetPulseOnPrimaryContainerLight,
    secondary = NetPulseSecondaryLight,
    onSecondary = NetPulseOnSecondaryLight,
    secondaryContainer = NetPulseSecondaryContainerLight,
    onSecondaryContainer = NetPulseOnSecondaryContainerLight,
    background = NetPulseBackgroundLight,
    onBackground = NetPulseOnSurfaceLight,
    surface = NetPulseSurfaceLight,
    onSurface = NetPulseOnSurfaceLight,
    surfaceVariant = NetPulseSurfaceVariantLight,
    onSurfaceVariant = NetPulseOnSurfaceVariantLight,
    surfaceContainer = NetPulseSurfaceContainerLight,
    surfaceContainerHigh = NetPulseSurfaceContainerHighLight,
    surfaceContainerHighest = NetPulseSurfaceContainerHighestLight,
    outline = NetPulseOutlineLight,
    outlineVariant = NetPulseOutlineVariantLight
)

@Composable
fun NetPulseTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> systemInDark
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    NetPulseTheme(
        themeMode = if (darkTheme) AppThemeMode.DARK else AppThemeMode.LIGHT,
        dynamicColor = dynamicColor,
        content = content
    )
}
