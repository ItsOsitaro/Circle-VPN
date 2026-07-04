package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MidnightColorScheme = darkColorScheme(
    primary = V2Primary,
    secondary = V2Secondary,
    background = V2Background,
    surface = V2Surface,
    onBackground = V2TextPrimary,
    onSurface = V2TextPrimary,
    error = V2Error
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Force our premium theme
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MidnightColorScheme,
        typography = Typography,
        content = content
    )
}
