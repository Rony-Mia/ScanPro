package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ScanProGreenDim,
    onPrimary = Color(0xFF002114),
    primaryContainer = ScanProGreenContainer,
    onPrimaryContainer = ScanProGreenOnContainer,
    secondary = Color(0xFFB9C9D3),
    onSecondary = Color(0xFF0E1D25),
    secondaryContainer = Color(0xFF3A4951),
    onSecondaryContainer = Color(0xFFD2E2ED),
    background = Color(0xFF191C1A),
    onBackground = Color(0xFFEFF1ED),
    surface = Color(0xFF191C1A),
    onSurface = Color(0xFFEFF1ED),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    outline = ScanProOutline,
    outlineVariant = Color(0xFF404943),
    error = ScanProError,
    errorContainer = Color(0xFF93000A)
)

private val LightColorScheme = lightColorScheme(
    primary = ScanProGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = ScanProGreenContainer,
    onPrimaryContainer = ScanProGreenOnContainer,
    secondary = ScanProSecondary,
    onSecondary = Color.White,
    secondaryContainer = ScanProSecondaryContainer,
    onSecondaryContainer = ScanProOnSecondaryContainer,
    background = ScanProBackground,
    onBackground = ScanProInk,
    surface = ScanProSurface,
    onSurface = ScanProInk,
    surfaceVariant = ScanProSurfaceVariant,
    onSurfaceVariant = ScanProOnSurfaceVariant,
    outline = ScanProOutline,
    outlineVariant = ScanProOutlineVariant,
    error = ScanProError,
    errorContainer = ScanProErrorContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

