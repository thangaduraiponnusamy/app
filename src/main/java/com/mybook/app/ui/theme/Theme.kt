package com.mybook.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val BrandViolet = Color(0xFF6D5DFC)
val BrandPink = Color(0xFFF25F9C)
val IncomeGreen = Color(0xFF059669)
val ExpenseRed = Color(0xFFDC2626)

val BrandGradient = Brush.linearGradient(listOf(BrandViolet, BrandPink))
val GreenGradient = Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF059669)))

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
    error = ExpenseRed,
    background = Color(0xFFF7F5FF),
    onBackground = Color(0xFF1B1B2F),
    surface = Color(0xFFF7F5FF),
    onSurface = Color(0xFF1B1B2F),
    surfaceVariant = Color(0xFFEDE9FA),
    onSurfaceVariant = Color(0xFF6B6880),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAFF),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEDE9FA),
    outline = Color(0xFFCBC6E0),
    outlineVariant = Color(0xFFE4E0F2),
)

@Composable
fun MyBookTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
