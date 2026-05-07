package com.ian.aigame.model

data class StorySegment(
    val id: String,
    val narrative: String,
    val dialogue: String?,
    val speaker: String?,
    val options: List<StoryOption> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class StoryOption(
    val id: String,
    val text: String,
    val isEnding: Boolean = false
)

enum class StoryTheme(val key: String, val displayName: String) {
    FANTASY("fantasy", "奇幻魔法"),
    SCIFI("scifi", "星際科幻"),
    WUXIA("wuxia", "武俠江湖"),
    HORROR("horror", "詭異驚悚"),
    ROMANCE("romance", "穿越愛情"),
    MYSTERY("mystery", "懸疑推理"),
    POSTAPOCALYPTIC("postapocalyptic", "末世求生"),
    DUNGEON("dungeon", "迷宮探險")
}

data class GameSettings(
    val theme: StoryTheme = StoryTheme.FANTASY,
    val playerName: String = "冒險者"
)

data class GameState(
    val storySoFar: List<StorySegment> = emptyList(),
    val currentSegment: StorySegment? = null,
    val settings: GameSettings = GameSettings(),
    val isPlaying: Boolean = false,
    val isGenerating: Boolean = false,
    val round: Int = 0,
    val maxRounds: Int = 120,
    val gameEnded: Boolean = false
)

data class StoryPreview(
    val title: String,
    val opening: String,
    val theme: StoryTheme,
    val settings: GameSettings
)