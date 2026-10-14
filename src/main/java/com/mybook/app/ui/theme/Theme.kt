package com.mybook.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Blue = Color(0xFF3B82F6)
val BlueLight = Color(0xFF60A5FA)
val BlueDeep = Color(0xFF2563EB)
val IncomeGreen = Color(0xFF34D399)
val ExpenseRed = Color(0xFFF87171)

val BlueGradient = Brush.linearGradient(listOf(BlueLight, BlueDeep))
val GreenGradient = Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF059669)))

private val DarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = IncomeGreen,
    onSecondary = Color(0xFF052E1C),
    tertiary = IncomeGreen,
    error = ExpenseRed,
    background = Color(0xFF0B0C10),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF0B0C10),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF1C1D24),
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainerLowest = Color(0xFF08090C),
    surfaceContainerLow = Color(0xFF121318),
    surfaceContainer = Color(0xFF15161C),
    surfaceContainerHigh = Color(0xFF1C1D24),
    surfaceContainerHighest = Color(0xFF23252D),
    outline = Color(0xFF2A2C35),
    outlineVariant = Color(0xFF23252D),
)

@Composable
fun MyBookTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
