package com.ian.aigame.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ParchmentDark = Color(0xFF1A1A2E)
val ParchmentMedium = Color(0xFF16213E)
val ParchmentLight = Color(0xFF0F3460)
val GoldAccent = Color(0xFFE2B04A)
val GoldLight = Color(0xFFF0D78C)
val CrimsonRed = Color(0xFFE94560)
val DeepPurple = Color(0xFF533483)
val StoryText = Color(0xFFE8E0D0)
val StoryTextDim = Color(0xFFA09888)
val SurfaceOverlay = Color(0xFF232340)

private val DarkColorScheme = darkColorScheme(
    primary = GoldAccent,
    onPrimary = Color.Black,
    primaryContainer = GoldLight.copy(alpha = 0.15f),
    onPrimaryContainer = GoldLight,
    secondary = CrimsonRed,
    onSecondary = Color.White,
    secondaryContainer = CrimsonRed.copy(alpha = 0.2f),
    onSecondaryContainer = Color(0xFFFFB3B3),
    tertiary = DeepPurple,
    background = ParchmentDark,
    onBackground = StoryText,
    surface = ParchmentMedium,
    onSurface = StoryText,
    surfaceVariant = SurfaceOverlay,
    onSurfaceVariant = StoryTextDim,
    outline = GoldAccent.copy(alpha = 0.3f),
    outlineVariant = GoldAccent.copy(alpha = 0.15f)
)

@Composable
fun AIInteractiveNovelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = StoryTypography,
        content = content
    )
}
