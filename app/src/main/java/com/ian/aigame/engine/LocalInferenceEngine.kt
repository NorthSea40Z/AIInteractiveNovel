package com.ian.aigame.engine

import android.content.Context
import android.util.Log
import com.ian.aigame.model.StoryOption
import com.ian.aigame.model.StoryPreview
import com.ian.aigame.model.StorySegment
import com.ian.aigame.model.StoryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class LocalInferenceEngine(private val appContext: Context) {

    private val promptBuilder = PromptBuilder()
    var nativePtr: Long = 0

    data class ModelState(
        val ready: Boolean = false,
        val downloading: Boolean = false,
        val progress: Float = 0f,
        val error: String? = null
    )

    private var _state = ModelState()
    val state get() = _state

    private val modelsDir get() = File(appContext.getExternalFilesDir(null), "models")
    val modelFile get() = File(modelsDir, "qwen2.5-1.5b-instruct-q4_k_m.gguf")

    companion object {
        private const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
    }

    suspend fun downloadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!NativeLLM.isAvailable) return@withContext Result.failure(Exception("Native LLM not available"))
        if (modelFile.exists() && modelFile.length() > 100_000_000) return@withContext Result.success(Unit)
        _state = _state.copy(downloading = true, progress = 0f, error = null)
        try {
            modelsDir.mkdirs()
            val url = URL(MODEL_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 600000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val contentLength = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        total += read
                        if (contentLength > 0) _state = _state.copy(progress = total.toFloat() / contentLength)
                    }
                }
            }
            _state = _state.copy(downloading = false, progress = 1f)
            Result.success(Unit)
        } catch (e: Exception) {
            _state = _state.copy(downloading = false, error = e.message)
            modelFile.delete()
            Result.failure(e)
        }
    }

    suspend fun loadModel(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            if (!NativeLLM.isAvailable) return@withContext Result.failure(Exception("Native LLM unavailable"))
            if (!modelFile.exists() || modelFile.length() < 100_000_000) return@withContext Result.failure(Exception("model not downloaded"))
            val ptr = NativeLLM.init(modelFile.absolutePath)
            if (ptr == 0L) return@withContext Result.failure(Exception("model load failed"))
            nativePtr = ptr
            _state = _state.copy(ready = true)
            Result.success(Unit)
        } catch (e: Exception) {
            _state = _state.copy(error = e.message); Result.failure(e)
        }
    }

    suspend fun generateStoryPreview(theme: StoryTheme): StoryPreview = withContext(Dispatchers.Default) {
        if (nativePtr == 0L) return@withContext StoryPreview(title = "${theme.displayName} Adventure", opening = "Engine not ready", theme = theme, settings = com.ian.aigame.model.GameSettings(theme = theme))
        NativeLLM.resetContext(nativePtr)
        try {
            val response = NativeLLM.generate(nativePtr, "一段${theme.displayName}互動小說的故事：\n", 256)
            Log.i("AIGAME", "Raw preview: $response")
            val raw = response ?: ""
            val cleaned = raw.trim()
            val lines = cleaned.split('\n').filter { it.isNotBlank() }
            val title = lines.firstOrNull()?.take(30) ?: "${theme.displayName}傳說"
            var opening = lines.drop(1).joinToString("\n").take(500).ifBlank { cleaned.take(200) }
            val sentenceEndings = listOf('.', '!', '?', '\u3002', '\u3001', '\uFF01', '\uFF1F')
            if (opening.length >= 490 && sentenceEndings.none { opening.endsWith(it.toString()) }) {
            val idx = opening.indexOfLast { it in sentenceEndings }
                if (idx > 0) opening = opening.substring(0, idx + 1)
            }

            StoryPreview(title = title, opening = opening, theme = theme, settings = com.ian.aigame.model.GameSettings(theme = theme))
        } catch (e: Exception) {
            Log.e("AIGAME", "Preview error", e)
            StoryPreview(title = "${theme.displayName} Story", opening = "Generation failed", theme = theme, settings = com.ian.aigame.model.GameSettings(theme = theme))
        }
    }

    suspend fun generateNextSegment(theme: StoryTheme, storySoFar: List<StorySegment>, round: Int, maxRounds: Int): StorySegment = withContext(Dispatchers.Default) {
        if (nativePtr == 0L) return@withContext segFallback(round)
        NativeLLM.resetContext(nativePtr)
        try {
            val context = buildStoryContext(theme, storySoFar, round, maxRounds)
            Log.i("AIGAME", "Generating segment $round, context: ${context.take(100)}...")

            val narrative = NativeLLM.generate(nativePtr, "$context\n\n故事繼續：", 128) ?: ""
            Log.i("AIGAME", "Narrative($round): $narrative")

            val opt1 = NativeLLM.generate(nativePtr, "$context\n$narrative\n\n玩家選擇：", 32) ?: ""
            val opt2 = NativeLLM.generate(nativePtr, "$context\n$narrative\n\n另一個選擇：", 32) ?: ""
            val opt3 = NativeLLM.generate(nativePtr, "$context\n$narrative\n\n第三個選擇：", 32) ?: ""
            Log.i("AIGAME", "Options($round): 1=$opt1 2=$opt2 3=$opt3")

            val opts = listOfNotNull(
                if (opt1.isNotBlank()) StoryOption("opt_${round}_0", opt1.take(60)) else null,
                if (opt2.isNotBlank()) StoryOption("opt_${round}_1", opt2.take(60)) else null,
                if (opt3.isNotBlank()) StoryOption("opt_${round}_2", opt3.take(60)) else null
            )

            StorySegment(id = "seg_${round}_${System.currentTimeMillis()}",
                narrative = narrative.ifBlank { "The story continues..." },
                dialogue = null, speaker = null,
                options = if (opts.isNotEmpty()) opts else segFallback(round).options
            )
        } catch (e: Exception) {
            Log.e("AIGAME", "Segment error", e)
            segFallback(round)
        }
    }

    private fun buildStoryContext(theme: StoryTheme, storySoFar: List<StorySegment>, round: Int, maxRounds: Int): String {
        val recap = storySoFar.mapIndexed { i, seg ->
            "第${i+1}章：${seg.narrative.take(300)}"
        }.joinToString("\n")
        val ending = if (round >= maxRounds) " 這是最後一章。" else ""
        return "一個${theme.displayName}故事，第${round}章。$ending\n$recap"
    }

    private fun segFallback(round: Int) = StorySegment(
        id = "seg_${round}_${System.currentTimeMillis()}", narrative = "Story continues...",
        dialogue = null, speaker = null,
        options = listOf(
            StoryOption("opt_${round}_0", "Continue"),
            StoryOption("opt_${round}_1", "Look around"),
            StoryOption("opt_${round}_2", "Move forward")
        )
    )

    fun close() {
        if (nativePtr != 0L) { try { NativeLLM.close(nativePtr) } catch (_: Exception) {}; nativePtr = 0L }
    }
}

class PromptBuilder {
    fun buildPreviewPrompt(theme: StoryTheme): String {
        return "Write an opening for a ${theme.displayName} interactive novel. Use format:\nTitle: [title]\nOpening: [narrative in Chinese, 150-200 chars]"
    }

    fun buildSegmentPrompt(theme: StoryTheme, storySoFar: List<StorySegment>, round: Int, maxRounds: Int): String {
        val recap = storySoFar.mapIndexed { i, seg -> "Ch${i+1}: ${seg.narrative.take(120)}" }.joinToString("\n")
        val ending = if (round >= maxRounds) "This is the final chapter." else ""
        return "Chapter $round of a $theme interactive novel. $ending\n\nPrevious: $recap\n\nWrite in Chinese. Format:\n[Narrative]\n[Dialogue] Character: \"words\"\n[Options]\n1.\n2.\n3."
    }

    fun parsePreviewResponse(response: String?, theme: StoryTheme): StoryPreview {
        val r = response ?: return StoryPreview(title = "${theme.displayName}", opening = "Once upon a time...", theme = theme, settings = com.ian.aigame.model.GameSettings(theme = theme))
        if (r.isBlank()) return StoryPreview(title = "${theme.displayName}", opening = "Once upon a time...", theme = theme, settings = com.ian.aigame.model.GameSettings(theme = theme))
        val raw = r.trim()
        var title = raw.substringAfter("Title:", "").substringBefore("Opening:").trim()
        var opening = raw.substringAfter("Opening:", "").trim()
        if (title.isBlank()) title = theme.displayName + " Story"
        if (opening.isBlank()) opening = raw.take(500)
        return StoryPreview(title = title, opening = opening, theme = theme, settings = com.ian.aigame.model.GameSettings(theme = theme))
    }

    fun parseSegmentResponse(response: String?, round: Int): StorySegment {
        val r = response ?: return StorySegment(id = "seg_${round}_${System.currentTimeMillis()}", narrative = "", dialogue = null, speaker = null, options = emptyList())
        if (r.isBlank()) return StorySegment(id = "seg_${round}_${System.currentTimeMillis()}", narrative = "", dialogue = null, speaker = null, options = emptyList())
        var raw = r.trim()
        val narrativeStart = raw.indexOf("[Narrative]")
        if (narrativeStart >= 0) raw = raw.substring(narrativeStart + 11).trim()
        val narrative = raw.substringBefore("[Dialogue]").trim().let { if (it.isBlank() || it.length < 5) null else it }
        val dialogueSection = raw.substringAfter("[Dialogue]", "").substringBefore("[Options]").trim()
        val optionsText = raw.substringAfter("[Options]", "").trim()
        val speaker: String?; val dialogue: String?
        if (dialogueSection.isNotEmpty() && dialogueSection.contains("\":")) {
            val parts = dialogueSection.split("\":", limit = 2)
            speaker = parts[0].trim(); dialogue = parts[1].trim().removeSurrounding("\"").removeSurrounding("\u201c").removeSurrounding("\u201d")
        } else { speaker = null; dialogue = if (dialogueSection.isNotEmpty()) dialogueSection else null }
        val options = if (optionsText.isNotEmpty()) {
            optionsText.lines().filter { it.isNotBlank() }.mapNotNull { opt ->
                val cleaned = opt.trimStart('1', '2', '3', '.', '\u3001', ' ')
                if (cleaned.isNotBlank()) StoryOption("opt_${round}_${raw.indexOf(opt)}", cleaned) else null
            }.take(3)
        } else emptyList()
        val finalNarrative = narrative ?: raw.take(500)
        val finalOptions = if (options.isEmpty()) {
            listOf(StoryOption("opt_${round}_0", "Move forward"), StoryOption("opt_${round}_1", "Look around"), StoryOption("opt_${round}_2", "Talk to someone"))
        } else options
        return StorySegment(id = "seg_${round}_${System.currentTimeMillis()}", narrative = finalNarrative, dialogue = dialogue, speaker = speaker, options = finalOptions)
    }
}