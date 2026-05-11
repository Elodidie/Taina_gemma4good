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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GemmaInferenceModel private constructor(private val context: Context) {

    // ─── 🔀 SWITCH HERE ──────────────────────────────────────────────────────
    //
    //  true  → Ollama on your dev machine (emulator / hackathon demo)
    //  false → on-device LiteRT inference (real device)
    //
    private val useOllama = true
    //
    // ─────────────────────────────────────────────────────────────────────────

    // Ollama settings — only used when useOllama = true
    // 10.0.2.2 is the Android emulator's alias for your host machine's localhost
    private val ollamaBaseUrl = "http://10.0.2.2:11434"
    private val ollamaModel   = "gemma3:4b"  // swap to "gemma4" when ready

    // LiteRT engine — only initialised when useOllama = false
    private val engine: Engine?
    private var conversation: com.google.ai.edge.litertlm.Conversation?

    private fun getModelPath(): String {
        val modelFile = File("/data/local/tmp/llm/gemma-4-E2B-it.litertlm")
        if (!modelFile.exists()) {
            throw IllegalStateException("Model not found at ${modelFile.absolutePath}")
        }
        return modelFile.absolutePath
    }

    init {
        if (!useOllama) {
            val engineConfig = EngineConfig(
                modelPath = getModelPath(),
                backend   = Backend.CPU()
            )
            engine = Engine(engineConfig)
            engine.initialize()
            conversation = createNewConversation()
            Log.d(TAG, "LiteRT engine initialised")
        } else {
            engine       = null
            conversation = null
            Log.d(TAG, "Ollama mode — skipping LiteRT init")
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Send a user message with the full conversation history.
     * [history] is a list of (userMessage, assistantMessage) pairs for all
     * previous turns. Gemma has no memory between calls, so we replay the
     * full context every time.
     *
     * Returns a Flow<String> — the complete assistant response emitted as
     * a single token (non-streaming). ChatViewModel's typing indicator covers
     * the wait time.
     */
    fun sendMessage(
        history: List<Pair<String, String>>,
        userMessage: String
    ): Flow<String> =
        if (useOllama) sendViaOllama(history, userMessage)
        else           sendViaLiteRT(history, userMessage)

    fun close() {
        try {
            conversation?.close()
            engine?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing engine", e)
        }
    }

    // ─── LiteRT path ─────────────────────────────────────────────────────────

    private fun createNewConversation(): com.google.ai.edge.litertlm.Conversation? {
        return engine?.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(SYSTEM_PROMPT),
                samplerConfig     = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.7)
            )
        )
    }

    /**
     * LiteRT conversations are stateful per session but have no persistence.
     * We reset and replay history before each call so the model always
     * sees full context — same pattern as the Ollama path.
     */
    private fun sendViaLiteRT(
        history: List<Pair<String, String>>,
        userMessage: String
    ): Flow<String> = flow {
        try { conversation?.close() } catch (_: Exception) {}
        conversation = createNewConversation()

        // Replay past turns so the model has full context
        for ((pastUser, _) in history) {
            conversation?.sendMessageAsync(pastUser)?.collect {}
        }

        // Send current message and stream response
        val responseFlow: Flow<Message> = conversation!!.sendMessageAsync(userMessage)
        val sb = StringBuilder()
        responseFlow.collect { message ->
            val text = message.contents?.toString().orEmpty()
            if (text.isNotBlank()) {
                sb.append(text)
                emit(text)
            }
        }
    }.flowOn(Dispatchers.IO)

    // ─── Ollama path ──────────────────────────────────────────────────────────

    /**
     * Uses Ollama's /api/chat endpoint (not /api/generate) which natively
     * supports a messages array — cleaner than injecting history into a prompt string.
     *
     * AndroidManifest.xml needs:
     *   <uses-permission android:name="android.permission.INTERNET" />
     *   android:usesCleartextTraffic="true"  ← on <application> tag, dev only
     */
    private fun sendViaOllama(
        history: List<Pair<String, String>>,
        userMessage: String
    ): Flow<String> = flow {
        val url  = URL("$ollamaBaseUrl/api/chat")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod  = "POST"
            connectTimeout = 5_000
            readTimeout    = 60_000
            doOutput       = true
            setRequestProperty("Content-Type", "application/json")
        }

        // Build messages array: system prompt + interleaved history + new user turn
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role",    "system")
                put("content", SYSTEM_PROMPT)
            })
            for ((pastUser, pastAssistant) in history) {
                put(JSONObject().apply {
                    put("role",    "user")
                    put("content", pastUser)
                })
                put(JSONObject().apply {
                    put("role",    "assistant")
                    put("content", pastAssistant)
                })
            }
            put(JSONObject().apply {
                put("role",    "user")
                put("content", userMessage)
            })
        }

        val body = JSONObject().apply {
            put("model",    ollamaModel)
            put("messages", messages)
            put("stream",   false)
        }.toString()

        conn.outputStream.use { it.write(body.toByteArray()) }

        val raw      = conn.inputStream.bufferedReader().readText()
        val response = JSONObject(raw)
            .optJSONObject("message")
            ?.optString("content", "")
            ?.trim()
            .orEmpty()

        conn.disconnect()
        Log.d(TAG, "Ollama response: $response")
        if (response.isNotEmpty()) emit(response)

    }.flowOn(Dispatchers.IO)

    // ─── Companion ────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "GemmaModel"

        /**
         * Taina's full conversational system prompt.
         * Gemma now owns the conversation — asking one question at a time,
         * validating answers naturally, and outputting a sentinel JSON block
         * when all fields are confirmed. ChatViewModel watches for that JSON
         * to trigger the save flow.
         */
        val SYSTEM_PROMPT = """
            You are Taina 🌿, a warm and enthusiastic nature assistant helping citizen scientists record biodiversity observations.

            Your job is to collect ALL of the following fields through natural conversation:
            - commonName: the common name of the species (required)
            - scientificName: the scientific name (optional — ask once, accept "don't know")
            - count: number of individuals observed (required — must be a positive integer)
            - locality: place name or location description (required)
            - habitat: habitat type, e.g. forest, wetland, grassland, urban, coastal (required)
            - notes: any extra observations (optional — accept "none" or "no")

            Rules:
            - Ask ONE question at a time. Keep each question warm and natural, under 15 words.
            - If an answer clearly doesn't match the field (e.g. a greeting instead of a species name, a non-number for count), gently point it out and re-ask that same field.
            - Accept approximate counts ("about 3", "a few" → store as 3).
            - Once you have all fields, show a brief friendly summary and ask for confirmation.
            - When the user confirms (yes / sure / ok / save), output ONLY the following JSON and absolutely nothing else — no explanation, no text before or after:
            {"commonName":"...","scientificName":"...","count":1,"locality":"...","habitat":"...","notes":"..."}
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