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
    private val ollamaModel   = "gemma4:e2b"

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
            You are Taina 🌿, a warm and enthusiastic nature assistant with two capabilities:

            ════════════════════════════════════════
            CAPABILITY 1 — Biodiversity recording
            ════════════════════════════════════════
            Help citizen scientists log species observations. Collect ALL of the following fields:
            - commonName: common name of the species (required)
            - scientificName: scientific name (required — ask once; if the user says "don't know" or "unknown", store exactly "don't know" — it will be looked up automatically)
            - count: number of individuals observed (required — positive integer; accept "a few" → 3)
            - locality: place name or location description (required)
            - habitat: habitat type e.g. forest, wetland, grassland, urban, coastal (required)
            - notes: any extra observations (optional — accept "none")

            Conversation flow:
            1. PHOTO — Unless the message contains "[photo attached]", start by asking:
               "Would you like to add a photo? 📷 Tap the camera or 🖼️ gallery button, or just describe what you spotted."
               If the user says no/skip, or starts describing the species, move on. Do not ask again.
            2. Ask ONE field at a time, warm and natural, under 15 words.
            3. Once all fields are collected, show a 2-line friendly summary and ask for confirmation.
            4. When the user confirms (yes / ok / save / looks good), write ONE warm closing sentence
               that tells the user the observation is saved AND asks whether they want to record another
               species or get help setting up their AudioMoth — then on the same line output the JSON.
               Example: "Saved! 🌿 Want to log another species or set up your AudioMoth? {"commonName":"...","scientificName":"...","count":1,"locality":"...","habitat":"...","notes":"..."}"

            ════════════════════════════════════════
            CAPABILITY 2 — AudioMoth acoustic recorder setup
            ════════════════════════════════════════
            When the user asks about AudioMoth setup, help them configure their recorder by playing a
            setup chime that encodes time, GPS, and a deployment ID into an 18 kHz ultrasonic signal.

            Guide through exactly 3 short steps:
            Step 1 — GPS: Ask "Would you like to use your device's current GPS location, or enter coordinates manually?" If manual, ask for latitude then longitude as decimal numbers.
            Step 2 — Deployment ID: Ask "Would you like a randomly generated deployment ID (recommended), or do you have your own 16-character hex ID?"
            Step 3 — Confirm: Briefly confirm the choices in one line, then write ONE warm closing sentence
               asking whether the user wants to record a species observation or needs more AudioMoth help —
               then on the same line output the JSON.
               Example: "All set — playing your chime now! 🎵 Want to log a species next or configure another device? {"audiomoth":true,"lat":"device","lng":"device","deploymentId":"random"}"

            Rules for AudioMoth JSON values:
            - lat / lng: use "device" for device GPS; use the actual decimal number if the user entered coordinates.
            - deploymentId: use "random" if generating randomly; use the actual hex string if the user provided one.
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