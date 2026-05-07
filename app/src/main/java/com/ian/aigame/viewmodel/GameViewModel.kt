package com.ian.aigame.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ian.aigame.engine.LocalInferenceEngine
import com.ian.aigame.model.GameState
import com.ian.aigame.model.StoryOption
import com.ian.aigame.model.StoryPreview
import com.ian.aigame.model.StoryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {

    val inferenceEngine = LocalInferenceEngine(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    fun selectTheme(theme: StoryTheme) {
        _uiState.update { it.copy(selectedTheme = theme) }
    }

    fun selectGameLength(length: GameLength) {
        _uiState.update { it.copy(gameLength = length) }
    }

    fun downloadAndLoadModel() {
        _uiState.update { it.copy(isDownloading = true, downloadError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val downloadResult = inferenceEngine.downloadModel()
            if (downloadResult.isFailure) {
                _uiState.update { it.copy(isDownloading = false, downloadError = downloadResult.exceptionOrNull()?.message) }
                return@launch
            }
            val loadResult = inferenceEngine.loadModel()
            if (loadResult.isSuccess) {
                _uiState.update { it.copy(isDownloading = false, modelReady = true) }
            } else {
                _uiState.update { it.copy(isDownloading = false, downloadError = loadResult.exceptionOrNull()?.message) }
            }
        }
    }

    fun rollDice() {
        if (!_uiState.value.modelReady) {
            _uiState.update { it.copy(downloadError = "請先下載並載入模型") }
            return
        }
        _uiState.update { it.copy(isRolling = true, storyPreview = null) }

        viewModelScope.launch(Dispatchers.Default) {
            val theme = _uiState.value.selectedTheme
            _uiState.update { it.copy(isRolling = false, isGeneratingPreview = true) }
            val preview = inferenceEngine.generateStoryPreview(theme)
            _uiState.update { it.copy(storyPreview = preview, isGeneratingPreview = false) }
        }
    }

    fun startGame() {
        val preview = _uiState.value.storyPreview ?: return
        val maxRounds = _uiState.value.gameLength.rounds
        val settings = preview.settings.copy(playerName = _uiState.value.playerName.ifBlank { "冒險者" })

        _gameState.update { GameState(settings = settings, maxRounds = maxRounds, isGenerating = true) }
        _uiState.update { it.copy(currentScreen = Screen.GAME, isGeneratingSegment = true) }

        viewModelScope.launch(Dispatchers.Default) {
            val firstSegment = inferenceEngine.generateNextSegment(settings.theme, emptyList(), 1, maxRounds)
            _gameState.update { it.copy(currentSegment = firstSegment, isGenerating = false, isPlaying = true, round = 1) }
            _uiState.update { it.copy(isGeneratingSegment = false) }
        }
    }

    fun makeChoice(option: StoryOption) {
        val current = _gameState.value
        if (current.isGenerating || current.gameEnded) return
        val currentSegment = current.currentSegment ?: return
        val newRound = current.round + 1
        val isEnding = newRound > current.maxRounds || option.isEnding
        val updatedStory = current.storySoFar + currentSegment

        _gameState.update { it.copy(storySoFar = updatedStory, isGenerating = true, currentSegment = null) }
        viewModelScope.launch(Dispatchers.Default) {
            delay(500)
            val nextSegment = inferenceEngine.generateNextSegment(current.settings.theme, updatedStory, newRound, current.maxRounds)
            _gameState.update { it.copy(currentSegment = nextSegment, isGenerating = false, round = newRound, gameEnded = isEnding) }
        }
    }

    fun goToSetup() {
        _uiState.update { it.copy(currentScreen = Screen.SETUP) }
    }

    fun goToTitle() {
        _gameState.update { it.copy(isPlaying = false, gameEnded = false) }
        _uiState.update { it.copy(currentScreen = Screen.TITLE) }
    }

    override fun onCleared() {
        super.onCleared()
        inferenceEngine.close()
    }

    data class UiState(
        val currentScreen: Screen = Screen.TITLE,
        val selectedTheme: StoryTheme = StoryTheme.FANTASY,
        val storyPreview: StoryPreview? = null,
        val isRolling: Boolean = false,
        val isGeneratingPreview: Boolean = false,
        val isGeneratingSegment: Boolean = false,
        val isDownloading: Boolean = false,
        val modelReady: Boolean = false,
        val downloadError: String? = null,
        val playerName: String = "冒險者",
        val gameLength: GameLength = GameLength.MEDIUM
    )

    enum class GameLength(val rounds: Int, val label: String) {
        SHORT(40, "短篇 (~1 小時)"), MEDIUM(120, "中篇 (~3 小時)"), LONG(200, "長篇 (~5 小時)")
    }
    enum class Screen { TITLE, SETUP, GAME }
}