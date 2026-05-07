package com.ian.aigame.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ian.aigame.model.StoryOption
import com.ian.aigame.model.StorySegment
import com.ian.aigame.ui.theme.CrimsonRed
import com.ian.aigame.ui.theme.GoldAccent
import com.ian.aigame.ui.theme.GoldLight
import com.ian.aigame.ui.theme.ParchmentDark
import com.ian.aigame.ui.theme.ParchmentMedium
import com.ian.aigame.ui.theme.StoryTextDim
import com.ian.aigame.ui.theme.SurfaceOverlay

@Composable
fun GameScreen(
    currentSegment: StorySegment?,
    storySoFar: List<StorySegment>,
    isGenerating: Boolean,
    gameEnded: Boolean,
    round: Int,
    maxRounds: Int,
    themeName: String,
    onChoiceSelected: (StoryOption) -> Unit,
    onBack: () -> Unit
) {
    var visibleOptions by remember { mutableStateOf(false) }

    LaunchedEffect(currentSegment) {
        visibleOptions = false
        if (currentSegment != null && currentSegment.options.isNotEmpty()) {
            kotlinx.coroutines.delay(600)
            visibleOptions = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ParchmentDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ParchmentMedium)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = GoldAccent
                )
            }
            Icon(
                Icons.Filled.AutoStories,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = themeName,
                style = MaterialTheme.typography.titleMedium,
                color = GoldLight
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "$round / $maxRounds",
                style = MaterialTheme.typography.labelMedium,
                color = GoldAccent.copy(alpha = 0.6f)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            storySoFar.forEach { segment ->
                StoryBlock(segment = segment)
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (currentSegment != null) {
                StoryBlock(segment = currentSegment)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isGenerating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = GoldAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "命運正在書寫下一章……",
                            style = MaterialTheme.typography.bodySmall,
                            color = StoryTextDim
                        )
                    }
                }
            }

            if (gameEnded && currentSegment != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "— 故事終章 —",
                            style = MaterialTheme.typography.headlineMedium,
                            color = GoldAccent,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "感謝你的冒險。命運的骰子永遠為下一段旅程準備著。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StoryTextDim,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = visibleOptions && currentSegment != null && currentSegment.options.isNotEmpty() && !gameEnded,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ParchmentMedium.copy(alpha = 0.95f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                currentSegment?.options?.forEachIndexed { index, option ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    ChoiceCard(
                        option = option,
                        enabled = !isGenerating,
                        onClick = { onChoiceSelected(option) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryBlock(segment: StorySegment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceOverlay.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = segment.narrative,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
            )

            if (!segment.dialogue.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(
                    color = GoldAccent.copy(alpha = 0.15f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (segment.speaker != null) {
                    Text(
                        text = segment.speaker,
                        style = MaterialTheme.typography.labelLarge,
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = segment.dialogue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    option: StoryOption,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        animationSpec = tween(300),
        label = "choice_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = GoldAccent.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconColor = if (enabled) GoldAccent else StoryTextDim
            Icon(
                Icons.Filled.AutoStories,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = option.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else StoryTextDim,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
