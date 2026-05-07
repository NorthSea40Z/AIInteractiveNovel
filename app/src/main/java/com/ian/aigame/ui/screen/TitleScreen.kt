package com.ian.aigame.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ian.aigame.model.StoryTheme
import com.ian.aigame.ui.theme.GoldAccent
import com.ian.aigame.ui.theme.ParchmentDark
import com.ian.aigame.ui.theme.ParchmentMedium
import com.ian.aigame.ui.theme.CrimsonRed

@Composable
fun TitleScreen(
    selectedTheme: StoryTheme,
    onThemeSelected: (StoryTheme) -> Unit,
    onStartAdventure: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "title_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ParchmentDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Icon(
                imageVector = Icons.Filled.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = GoldAccent.copy(alpha = glowAlpha)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "幻境奇譚",
                style = MaterialTheme.typography.displayLarge,
                color = GoldAccent,
                textAlign = TextAlign.Center
            )

            Text(
                text = "AI 互動小說",
                style = MaterialTheme.typography.headlineMedium,
                color = GoldAccent.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "擲出命運的骰子，讓故事從你的選擇中誕生",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "選擇偏好的故事類型",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(StoryTheme.entries.toList()) { theme ->
                    ThemeChip(
                        theme = theme,
                        isSelected = theme == selectedTheme,
                        onClick = { onThemeSelected(theme) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = GoldAccent,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .clickable { onStartAdventure() }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "開始冒險",
                        style = MaterialTheme.typography.titleLarge,
                        color = ParchmentDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ThemeChip(
    theme: StoryTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (icon, color) = when (theme) {
        StoryTheme.FANTASY -> Icons.Filled.AutoStories to GoldAccent
        StoryTheme.SCIFI -> Icons.Filled.Science to androidx.compose.ui.graphics.Color(0xFF6EC6FF)
        StoryTheme.WUXIA -> Icons.Filled.WaterDrop to CrimsonRed
        StoryTheme.HORROR -> Icons.Filled.Warning to androidx.compose.ui.graphics.Color(0xFF9575CD)
        StoryTheme.ROMANCE -> Icons.Filled.Yard to androidx.compose.ui.graphics.Color(0xFFF48FB1)
        StoryTheme.MYSTERY -> Icons.Filled.Casino to androidx.compose.ui.graphics.Color(0xFF80CBC4)
        StoryTheme.POSTAPOCALYPTIC -> Icons.Filled.Warning to androidx.compose.ui.graphics.Color(0xFFFFB74D)
        StoryTheme.DUNGEON -> Icons.Filled.AutoStories to androidx.compose.ui.graphics.Color(0xFFA1887F)
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = theme.displayName,
                tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = theme.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
