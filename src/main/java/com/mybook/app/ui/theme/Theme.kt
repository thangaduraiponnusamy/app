package com.mybook.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val BrandViolet = Color(0xFF6D5DFC)
val BrandPink = Color(0xFFF25F9C)
val BrandGradient = Brush.linearGradient(listOf(BrandViolet, BrandPink))

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E0FF),
    onPrimaryContainer = Color(0xFF1A0B6B),
    secondary = Color(0xFFD9437F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E6),
    onSecondaryContainer = Color(0xFF3E0020),
    tertiary = Color(0xFF00897B),
    background = Color(0xFFF7F5FF),
    surface = Color(0xFFF7F5FF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC7BFFF),
    onPrimary = Color(0xFF2A1A8C),
    primaryContainer = Color(0xFF4335B8),
    onPrimaryContainer = Color(0xFFE6E0FF),
    secondary = Color(0xFFFFB0CD),
    onSecondary = Color(0xFF5E1135),
    tertiary = Color(0xFF4FD8C4),
)

@Composable
fun MyBookTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
