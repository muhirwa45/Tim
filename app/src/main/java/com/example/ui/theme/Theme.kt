package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CosmicDarkColorScheme = darkColorScheme(
    primary = BrandEmerald,
    secondary = BreakBlue,
    tertiary = BreakPurple,
    background = SlateDarkBg,
    surface = GrayDarkCard,
    onBackground = TextWhite,
    onSurface = TextWhite,
    onPrimary = Color.White,
    surfaceVariant = GrayBorder,
    outline = GrayBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CosmicDarkColorScheme,
        typography = Typography,
        content = content
    )
}
