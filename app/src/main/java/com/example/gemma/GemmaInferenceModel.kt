package com.example.gemma

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class GemmaInferenceModel private constructor(context: Context) {

    private var llmInference: LlmInference

    private val modelPath = "/data/local/tmp/llm/model.bin"

    init {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .build()                     // ← no setTopK, no setTemperature, no setRandomSeed

        llmInference = LlmInference.createFromOptions(context, options)
    }

    fun generateResponseAsync(prompt: String): Flow<String> = callbackFlow {
        val formattedPrompt = "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"

        llmInference.generateResponseAsync(formattedPrompt) { partialResult, done ->
            partialResult?.let { trySend(it) }
            if (done) close()
        }
        awaitClose()
    }

    companion object {
        @Volatile
        private var instance: GemmaInferenceModel? = null

        fun getInstance(context: Context): GemmaInferenceModel {
            return instance ?: synchronized(this) {
                instance ?: GemmaInferenceModel(context).also { instance = it }
            }
        }
    }
}