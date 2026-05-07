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
            if (!modelFile.exists() || modelFile.length() < 100_000_000) return@withContext Result.failure(Exception("模型尚未下載"))
            val ptr = NativeLLM.init(modelFile.absolutePath)
            if (ptr == 0L) return@withContext Result.failure(Exception("模型載入失敗"))
            nativePtr = ptr
            _state = _state.copy(ready = true)
            Result.success(Unit)
        } catch (e: Exception) {
            _state = _state.copy(error = e.message); Result.failure(e)
        }
    }

    suspend fun generateStoryPreview(theme: StoryTheme): StoryPreview = withContext(Dispatchers.Default) {
        if (nativePtr == 0L) return@withContext StoryPreview(title = "${theme.displayName}冒險", opening = "引擎未初始化", theme = theme, settings = com.ian.aigame.model.GameSettings(theme = theme))
        try {
            val prompt = promptBuilder.buildPreviewPrompt(theme)
            val response = NativeLLM.generate(nativePtr, prompt, 256)
            Log.i("AIGAME", "Raw preview: $response")
            promptBuilder.parsePreviewResponse(response, theme)
        } catch (e: Exception) {
            Log.e("AIGAME", "Preview error", e)
            StoryPreview(title = "${theme.displayName}傳說", opening = "生成失敗", theme = theme, settings = com.ian.aigame.model.GameSettings(theme = theme))
        }
    }

    suspend fun generateNextSegment(theme: StoryTheme, storySoFar: List<StorySegment>, round: Int, maxRounds: Int): StorySegment = withContext(Dispatchers.Default) {
        if (nativePtr == 0L) return@withContext segFallback(round)
        try {
            val prompt = promptBuilder.buildSegmentPrompt(theme, storySoFar, round, maxRounds)
            val response = NativeLLM.generate(nativePtr, prompt, 512)
            promptBuilder.parseSegmentResponse(response, round)
        } catch (e: Exception) {
            segFallback(round)
        }
    }

    private fun segFallback(round: Int) = StorySegment(
        id = "seg_${round}_${System.currentTimeMillis()}", narrative = "故事繼續展開……",
        dialogue = null, speaker = null,
        options = listOf(
            StoryOption("opt_${round}_0", "繼續冒險"), StoryOption("opt_${round}_1", "仔細觀察四周"), StoryOption("opt_${round}_2", "謹慎地前進")
        )
    )

    fun close() {
        if (nativePtr != 0L) { try { NativeLLM.close(nativePtr) } catch (_: Exception) {}; nativePtr = 0L }
    }
}

class PromptBuilder {
    fun buildPreviewPrompt(theme: StoryTheme): String {
        return "請為一個${theme.displayName}題材的互動小說寫一個開場。請先寫一個吸引人的故事標題（用【標題】標示），然後寫一段150到200字的繁體中文開場描述（用【開場描述】標示）。"
    }

    fun buildSegmentPrompt(theme: StoryTheme, storySoFar: List<StorySegment>, round: Int, maxRounds: Int): String {
        val recap = storySoFar.mapIndexed { i, seg -> "第${i+1}章：${seg.narrative.take(120)}" }.joinToString("\n")
        val ending = if (round >= maxRounds) "\n注意：這是最後一章，請寫個精彩結局。" else ""
        return "這是${theme.displayName}互動小說的第${round}章（共$maxRounds 章）。$ending\n\n目前經過的劇情：\n$recap\n\n請生成下一段劇情，輸出格式：\n【敘述】（150到250字的繁體中文情境描述）\n【對話】（角色名：「對話」，可省略）\n【選項】\n1. 第一個選項\n2. 第二個選項\n3. 第三個選項"
    }

    fun parsePreviewResponse(response: String?, theme: StoryTheme): StoryPreview {
        if (response == null || response.isBlank()) return StoryPreview(title = "${theme.displayName}冒險", opening = "故事即將開始……", theme = theme, settings = com.ian.aigame.model.GameSettings(theme = theme))
        val raw = response.trim()
        // Try to extract title from 【標題】 markers
        var title = raw.substringAfter("【標題】", "").substringBefore("【開場描述】").trim()
        var opening = raw.substringAfter("【開場描述】", "").trim()
        if (opening.isBlank()) opening = raw.substringAfter("開場描述：", "").trim()
        // Truncate at next 【 marker (model sometimes generates multiple blocks)
        val nextBlock = opening.indexOf("【", 1)
        if (nextBlock > 0) opening = opening.substring(0, nextBlock).trim()
        if (title.isBlank()) title = theme.displayName + "傳說"
        if (opening.isBlank()) opening = raw.take(500)
        return StoryPreview(title = title, opening = opening, theme = theme, settings = com.ian.aigame.model.GameSettings(theme = theme))
    }

    fun parseSegmentResponse(response: String?, round: Int): StorySegment {
        if (response == null) return StorySegment(id = "seg_${round}_${System.currentTimeMillis()}", narrative = "故事暫時沉默……", dialogue = null, speaker = null, options = defaultOpts(round))
        val narrative = response.substringAfter("【敘述】", "").substringBefore("【對話】").trim().let { if (it.isBlank()) response.take(200) else it }
        val dialogueSection = response.substringAfter("【對話】", "").substringBefore("【選項】").trim()
        val speaker: String?; val dialogue: String?
        if (dialogueSection.isNotEmpty() && dialogueSection.contains("：「")) {
            val parts = dialogueSection.split("：「", limit = 2); speaker = parts[0].trim(); dialogue = parts[1].trim().removeSuffix("」")
        } else { speaker = null; dialogue = if (dialogueSection.isNotEmpty()) dialogueSection else null }
        return StorySegment(id = "seg_${round}_${System.currentTimeMillis()}", narrative = narrative, dialogue = dialogue, speaker = speaker, options = parseOptions(response, round))
    }

    private fun parseOptions(response: String, round: Int): List<StoryOption> {
        val text = response.substringAfter("【選項】", "").trim()
        if (text.isEmpty()) return defaultOpts(round)
        val parsed = text.lines().filter { it.isNotBlank() }.mapNotNull { opt ->
            val cleaned = opt.trimStart('1', '2', '3', '.', '、', ' ', ')', '）')
            if (cleaned.isNotBlank()) StoryOption("opt_${round}_${text.indexOf(opt)}", cleaned) else null
        }.take(3)
        return if (parsed.isNotEmpty()) parsed else defaultOpts(round)
    }

    private fun defaultOpts(round: Int) = listOf(
        StoryOption("opt_${round}_0", "繼續冒險"), StoryOption("opt_${round}_1", "仔細觀察四周"), StoryOption("opt_${round}_2", "謹慎地前進")
    )
}