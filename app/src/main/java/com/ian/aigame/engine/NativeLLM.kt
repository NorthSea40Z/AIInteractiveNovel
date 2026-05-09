package com.ian.aigame.engine

import android.util.Log

object NativeLLM {
    private const val TAG = "NativeLLM"
    private var loaded = false
    var lastError: String? = null
        private set

    init {
        try {
            System.loadLibrary("llama-jni")
            loaded = true
            Log.i(TAG, "Native LLM loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            lastError = e.message
            Log.e(TAG, "Failed to load native LLM: ${e.message}")
            loaded = false
        }
    }

    val isAvailable: Boolean get() = loaded

    external fun init(modelPath: String): Long
    external fun generate(ptr: Long, prompt: String, maxTokens: Int): String
    external fun resetContext(ptr: Long)
    external fun close(ptr: Long)
}