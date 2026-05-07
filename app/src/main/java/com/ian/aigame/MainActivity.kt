package com.ian.aigame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ian.aigame.ui.screen.GameScreen
import com.ian.aigame.ui.screen.SetupScreen
import com.ian.aigame.ui.screen.TitleScreen
import com.ian.aigame.ui.theme.AIInteractiveNovelTheme
import com.ian.aigame.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIInteractiveNovelTheme {
                val uiState by vm.uiState.collectAsState()
                val gameState by vm.gameState.collectAsState()
                when (uiState.currentScreen) {
                    GameViewModel.Screen.TITLE -> TitleScreen(selectedTheme = uiState.selectedTheme, onThemeSelected = { vm.selectTheme(it) }, onStartAdventure = { vm.goToSetup() })
                    GameViewModel.Screen.SETUP -> SetupScreen(
                        storyPreview = uiState.storyPreview, isRolling = uiState.isRolling, isGeneratingPreview = uiState.isGeneratingPreview,
                        isDownloading = uiState.isDownloading, modelReady = uiState.modelReady, downloadError = uiState.downloadError,
                        gameLengthText = uiState.gameLength.label,
                        onRollDice = { vm.rollDice() }, onStartGame = { vm.startGame() },
                        onDownloadModel = { vm.downloadAndLoadModel() }, onBack = { vm.goToTitle() })
                    GameViewModel.Screen.GAME -> GameScreen(
                        currentSegment = gameState.currentSegment, storySoFar = gameState.storySoFar,
                        isGenerating = gameState.isGenerating, gameEnded = gameState.gameEnded,
                        round = gameState.round, maxRounds = gameState.maxRounds,
                        themeName = gameState.settings.theme.displayName,
                        onChoiceSelected = { vm.makeChoice(it) }, onBack = { vm.goToTitle() })
                }
            }
        }
    }
}