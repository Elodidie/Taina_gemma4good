package com.example.gemma

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class GemmaInferenceModel private constructor(private val context: Context) {

    private val engine: Engine
    private val conversation: com.google.ai.edge.litertlm.Conversation

    /**
     * Returns the absolute path to the model file on device storage.
     * IMPORTANT: Do NOT rename or copy the model — LiteRTLM validates format.
     */
    private fun getModelPath(): String {
        val modelFile = File("/data/local/tmp/llm/gemma3-270m-it-q8.litertlm")

        if (!modelFile.exists()) {
            throw IllegalStateException(
                "Model not found at ${modelFile.absolutePath}. " +
                        "Run: adb push gemma3-1b-it-int4.litertlm /data/local/tmp/llm/"
            )
        }

        Log.d(TAG, "Using model at: ${modelFile.absolutePath} (${modelFile.length()} bytes)")
        return modelFile.absolutePath
    }

    init {
        val modelPath = getModelPath()

        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU()
        )

        engine = Engine(engineConfig)
        engine.initialize()

        conversation = engine.createConversation()

        Log.d(TAG, "LiteRTLM Engine initialized successfully")
    }

    /**
     * Streams tokens from the model response.
     */
    fun generateResponseAsync(prompt: String): Flow<String> = flow {
        val responseFlow: Flow<Message> = conversation.sendMessageAsync(prompt)

        responseFlow.collect { message ->
            val text = message.contents?.toString().orEmpty()

            if (text.isNotBlank()) {
                Log.d(TAG, "Token: $text")
                emit(text)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Clean up native resources.
     */
    fun close() {
        try {
            engine.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing engine", e)
        }
    }

    companion object {
        private const val TAG = "GemmaModel"

        @Volatile
        private var instance: GemmaInferenceModel? = null

        fun getInstance(context: Context): GemmaInferenceModel {
            return instance ?: synchronized(this) {
                instance ?: GemmaInferenceModel(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}