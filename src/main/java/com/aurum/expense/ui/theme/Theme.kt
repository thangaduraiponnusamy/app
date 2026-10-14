package com.aurum.expense.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5900),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDEA0),
    onPrimaryContainer = Color(0xFF261A00),
    secondary = Color(0xFF6B5D3F),
    tertiary = Color(0xFF4A6547),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5BF48),
    onPrimary = Color(0xFF402D00),
    primaryContainer = Color(0xFF5C4200),
    onPrimaryContainer = Color(0xFFFFDEA0),
    secondary = Color(0xFFD7C4A0),
    tertiary = Color(0xFFB0CFAA),
)

@Composable
fun AurumTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
