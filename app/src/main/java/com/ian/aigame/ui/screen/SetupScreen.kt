package com.ian.aigame.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ian.aigame.model.StoryPreview
import com.ian.aigame.ui.theme.CrimsonRed
import com.ian.aigame.ui.theme.GoldAccent
import com.ian.aigame.ui.theme.GoldLight
import com.ian.aigame.ui.theme.ParchmentDark
import com.ian.aigame.ui.theme.SurfaceOverlay

@Composable
fun SetupScreen(
    storyPreview: StoryPreview?,
    isRolling: Boolean,
    isGeneratingPreview: Boolean,
    isDownloading: Boolean,
    modelReady: Boolean,
    downloadError: String?,
    gameLengthText: String = "120 回合",
    onRollDice: () -> Unit,
    onStartGame: () -> Unit,
    onDownloadModel: () -> Unit,
    onBack: () -> Unit
) {
    val scaleAnim = remember { Animatable(1f) }
    var showIntro by remember(storyPreview) { mutableStateOf(false) }

    LaunchedEffect(isRolling) {
        if (isRolling) {
            repeat(8) { scaleAnim.animateTo(1.3f, tween(60)); scaleAnim.animateTo(0.9f, tween(60)) }
            scaleAnim.animateTo(1f, tween(200))
        }
    }
    LaunchedEffect(storyPreview) { showIntro = false; if (storyPreview != null) { kotlinx.coroutines.delay(400); showIntro = true } }

    Box(modifier = Modifier.fillMaxSize().background(ParchmentDark)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = GoldAccent) }
                Spacer(Modifier.width(8.dp)); Text("命運的骰子", style = MaterialTheme.typography.headlineLarge, color = GoldAccent)
            }

            Spacer(Modifier.height(24.dp))

            if (!modelReady) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceOverlay.copy(alpha = 0.6f))) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("下載 AI 模型", style = MaterialTheme.typography.titleLarge, color = GoldAccent)
                        Spacer(Modifier.height(8.dp))
                        Text("首次使用需下載 TinyLlama 1.1B 模型（約 700MB）\n下載一次即可離線使用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        if (isDownloading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = GoldAccent)
                            Spacer(Modifier.height(8.dp))
                            Text("下載中…", style = MaterialTheme.typography.labelMedium, color = GoldAccent)
                        } else {
                            Button(onClick = onDownloadModel, colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = ParchmentDark), modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Filled.Download, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("開始下載模型", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (downloadError != null) {
                            Spacer(Modifier.height(8.dp)); Text(downloadError, color = CrimsonRed, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                Text("模型已就緒", style = MaterialTheme.typography.bodyMedium, color = GoldAccent)
                Spacer(Modifier.height(32.dp))

                Box(modifier = Modifier.size(120.dp).scale(scaleAnim.value).clip(CircleShape).background(if (isRolling || isGeneratingPreview) GoldAccent.copy(alpha = 0.3f) else GoldAccent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    if (isRolling || isGeneratingPreview) {
                        CircularProgressIndicator(Modifier.size(56.dp), color = GoldAccent, strokeWidth = 3.dp)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Casino, "擲骰", Modifier.size(48.dp), tint = GoldAccent)
                            Spacer(Modifier.height(4.dp)); Text("點我", style = MaterialTheme.typography.labelSmall, color = GoldAccent)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(onClick = onRollDice, enabled = !isRolling && !isGeneratingPreview, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = GoldAccent.copy(alpha = 0.15f))) {
                    Icon(Icons.Filled.Casino, null, Modifier.size(18.dp), tint = GoldAccent); Spacer(Modifier.width(8.dp)); Text("擲骰生成故事", color = GoldAccent)
                }

                if (storyPreview != null && showIntro) {
                    Spacer(Modifier.height(24.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceOverlay.copy(alpha = 0.8f))) {
                        Column(Modifier.padding(20.dp)) {
                            Text(storyPreview.title, style = MaterialTheme.typography.headlineMedium, color = GoldLight, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("類型：${storyPreview.theme.displayName}  |  $gameLengthText", style = MaterialTheme.typography.labelMedium, color = GoldAccent.copy(alpha = 0.6f))
                            Spacer(Modifier.height(16.dp))
                            Text(storyPreview.opening, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onStartGame, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = ParchmentDark)) {
                        Icon(Icons.Filled.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("確認故事，開始遊戲", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}