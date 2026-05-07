package com.example.gemma

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class GemmaInferenceModel private constructor(private val context: Context) {

    private val engine: Engine
    private var conversation: com.google.ai.edge.litertlm.Conversation

    private fun getModelPath(): String {
        val modelFile = File("/data/local/tmp/llm/gemma3-270m-it-q8.litertlm")
        if (!modelFile.exists()) {
            throw IllegalStateException("Model not found at ${modelFile.absolutePath}")
        }
        return modelFile.absolutePath
    }

    init {
        val engineConfig = EngineConfig(
            modelPath = getModelPath(),
            backend = Backend.CPU()
        )
        engine = Engine(engineConfig)
        engine.initialize()
        conversation = createNewConversation()
        Log.d(TAG, "Gemma initialized")
    }

    private fun createNewConversation(): com.google.ai.edge.litertlm.Conversation {
        return engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(SYSTEM_PROMPT),
                samplerConfig = SamplerConfig(
                    topK = 1,       // near-greedy — most reliable for short rephrasing
                    topP = 0.9,
                    temperature = 0.3
                )
            )
        )
    }

    /**
     * Each call is fully stateless: the conversation is reset before every prompt
     * so the model never accumulates history. Gemma's only job is to rephrase
     * a single short question naturally — nothing more.
     */
    fun rephrase(prompt: String): Flow<String> = flow {
        // Always start fresh — no history needed for a rephrasing task
        resetConversation()

        val responseFlow: Flow<Message> = conversation.sendMessageAsync(prompt)
        responseFlow.collect { message ->
            val text = message.contents?.toString().orEmpty()
            if (text.isNotBlank()) emit(text)
        }
    }.flowOn(Dispatchers.IO)

    fun resetConversation() {
        try { conversation.close() } catch (_: Exception) {}
        conversation = createNewConversation()
    }

    fun close() {
        try {
            conversation.close()
            engine.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing engine", e)
        }
    }

    companion object {
        private const val TAG = "GemmaModel"

        /**
         * Minimal system prompt — the model only needs to rephrase short questions.
         * No JSON, no step logic, no conversation history. Keeping it this simple
         * is what makes the 270M model reliable.
         */
        val SYSTEM_PROMPT = """
            You are Taina, a warm and friendly nature assistant.
            Your only job is to rephrase the question you are given in a natural,
            encouraging tone. Keep your reply under 12 words. Output only the
            rephrased question — nothing else, no explanation, no preamble.
        """.trimIndent()

        @Volatile
        private var instance: GemmaInferenceModel? = null

        fun getInstance(context: Context): GemmaInferenceModel {
            return instance ?: synchronized(this) {
                instance ?: GemmaInferenceModel(context.applicationContext).also { instance = it }
            }
        }
    }
}