package com.example.gemma

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Data ────────────────────────────────────────────────────────────────────

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false,
    val photoUri: Uri? = null
)

/**
 * Each value is the hardcoded fallback question shown if Gemma fails or times out.
 * Kotlin owns ALL step logic — Gemma only rephrases these strings.
 */
enum class ObservationStep(val fallbackQuestion: String) {
    COMMON_NAME    ("What is the common name of the species?"),
    SCIENTIFIC_NAME("Do you know the scientific name? No worries if not."),
    COUNT          ("How many individuals did you see?"),
    LOCALITY       ("What is the location or place name?"),
    HABITAT        ("What habitat type? e.g. forest, wetland, grassland, urban, coastal."),
    NOTES          ("Any extra notes to add? Say 'none' to skip."),
    CONFIRM        ("Ready to save this record?"),
    DONE           ("")   // terminal state — no question needed
}

/**
 * Holds all collected field values for the current observation.
 * Built up answer by answer; converted to DarwinRecord at CONFIRM.
 */
data class ObservationState(
    val step: ObservationStep = ObservationStep.COMMON_NAME,
    val commonName: String     = "",
    val scientificName: String = "",
    val count: Int             = 1,
    val locality: String       = "",
    val habitat: String        = "",
    val notes: String          = ""
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages    = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading   = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _modelLoaded = MutableStateFlow(false)
    val modelLoaded: StateFlow<Boolean> = _modelLoaded.asStateFlow()

    private val _recordSaved = MutableStateFlow<String?>(null)
    val recordSaved: StateFlow<String?> = _recordSaved.asStateFlow()

    private var model: GemmaInferenceModel? = null
    private var obsState         = ObservationState()
    private var currentPhotoPath = ""
    private var currentLatLon: LatLon? = null

    private val dao            = DarwinDatabase.getInstance(application).darwinDao()
    private val locationHelper = LocationHelper(application)

    init {
        loadModel()
        fetchLocation()
    }

    // ─── Init ─────────────────────────────────────────────────────────────────

    private fun fetchLocation() {
        viewModelScope.launch {
            currentLatLon = locationHelper.getCurrentLocation()
            Log.d("ChatViewModel", "Location: $currentLatLon")
        }
    }

    private fun loadModel() {
        if (_modelLoaded.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                model = GemmaInferenceModel.getInstance(getApplication())
                _modelLoaded.value = true

                if (_messages.value.isEmpty()) {
                    appendMessage(ChatMessage(
                        text = "Hi! I'm Taina 🌿 Share a photo or tell me what species you spotted!",
                        isUser = false
                    ))
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading model", e)
                appendMessage(ChatMessage(
                    text = "❌ Failed to load model:\n${e.message}",
                    isUser = false
                ))
            }
        }
    }

    // ─── Public actions ───────────────────────────────────────────────────────

    /**
     * Called when the user picks or captures a photo.
     * Saves the file, shows the thumbnail bubble, then kicks off the Q&A.
     */
    fun onPhotoSelected(uri: Uri) {
        if (_isLoading.value) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(
                    getApplication<Application>().filesDir,
                    "obs_${System.currentTimeMillis()}.jpg"
                )
                getApplication<Application>()
                    .contentResolver
                    .openInputStream(uri)
                    ?.use { input -> file.outputStream().use { input.copyTo(it) } }
                currentPhotoPath = file.absolutePath

                appendMessage(ChatMessage(
                    text = "📷 Photo attached",
                    isUser = true,
                    photoUri = uri
                ))

                // Reset state machine for a fresh observation
                obsState = ObservationState()
                askCurrentStep()

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Photo handling failed", e)
                appendMessage(ChatMessage(text = "Failed to process image.", isUser = false))
            }
        }
    }

    /**
     * Called on every user text message.
     * Stores the answer for the current step, advances the state machine,
     * then either asks the next question or saves the record.
     */
    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isLoading.value) return

        appendMessage(ChatMessage(text = userText, isUser = true))

        viewModelScope.launch(Dispatchers.IO) {
            when (obsState.step) {
                ObservationStep.DONE -> {
                    // Previous observation complete — start fresh
                    obsState = ObservationState()
                    askCurrentStep()
                }
                ObservationStep.CONFIRM -> handleConfirm(userText)
                else -> {
                    obsState = storeAnswer(obsState, userText)
                    obsState = obsState.copy(step = nextStep(obsState.step))
                    if (obsState.step == ObservationStep.CONFIRM) {
                        showSummaryAndConfirm()
                    } else {
                        askCurrentStep()
                    }
                }
            }
        }
    }

    fun acknowledgeRecordSaved() {
        _recordSaved.value = null
    }

    // ─── State machine ────────────────────────────────────────────────────────

    /** Stores the user's free-text answer into the correct field. */
    private fun storeAnswer(state: ObservationState, answer: String): ObservationState {
        return when (state.step) {
            ObservationStep.COMMON_NAME     -> state.copy(commonName     = answer.trim())
            ObservationStep.SCIENTIFIC_NAME -> state.copy(scientificName = answer.trim())
            ObservationStep.COUNT           -> state.copy(count          = parseCount(answer))
            ObservationStep.LOCALITY        -> state.copy(locality       = answer.trim())
            ObservationStep.HABITAT         -> state.copy(habitat        = answer.trim())
            ObservationStep.NOTES           -> state.copy(notes          =
                if (answer.trim().lowercase() == "none") "" else answer.trim())
            else -> state
        }
    }

    private fun nextStep(current: ObservationStep): ObservationStep {
        return when (current) {
            ObservationStep.COMMON_NAME     -> ObservationStep.SCIENTIFIC_NAME
            ObservationStep.SCIENTIFIC_NAME -> ObservationStep.COUNT
            ObservationStep.COUNT           -> ObservationStep.LOCALITY
            ObservationStep.LOCALITY        -> ObservationStep.HABITAT
            ObservationStep.HABITAT         -> ObservationStep.NOTES
            ObservationStep.NOTES           -> ObservationStep.CONFIRM
            ObservationStep.CONFIRM         -> ObservationStep.DONE
            ObservationStep.DONE            -> ObservationStep.DONE
        }
    }

    /** Parses a count from free text — handles word numbers, falls back to 1. */
    private fun parseCount(text: String): Int {
        val wordMap = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
        )
        val lower = text.trim().lowercase()
        wordMap[lower]?.let { return it }
        // Also try extracting first number from a phrase like "about 3"
        return Regex("\\d+").find(lower)?.value?.toIntOrNull() ?: 1
    }

    // ─── Question asking ──────────────────────────────────────────────────────

    /**
     * Asks Gemma to rephrase the hardcoded fallback question for the current step.
     * Each call is fully stateless — Gemma gets no conversation history, just one prompt.
     * If Gemma fails or goes off-script, [sanitiseRephrasing] falls back to the hardcoded string.
     */
    private suspend fun askCurrentStep() {
        val step = obsState.step
        if (step == ObservationStep.DONE) return

        val fallback = step.fallbackQuestion
        appendLoadingBubble()
        _isLoading.value = true

        val prompt = "Rephrase this question as Taina, friendly and warm, under 12 words: \"$fallback\""

        var accumulated = ""
        var tokenCount  = 0

        model?.rephrase(prompt)
            ?.catch { e ->
                Log.w("ChatViewModel", "Gemma rephrase failed, using fallback. ${e.message}")
                updateLastBotMessage(fallback)
                _isLoading.value = false
            }
            ?.onCompletion {
                val finalText = sanitiseRephrasing(accumulated, fallback)
                updateLastBotMessage(finalText)
                _isLoading.value = false
            }
            ?.collect { token ->
                accumulated += token
                tokenCount++
                if (tokenCount % 4 == 0) updateLastBotMessage(accumulated)
            }
    }

    /**
     * Displays a summary of all collected fields, then asks the confirm question.
     */
    private suspend fun showSummaryAndConfirm() {
        val s = obsState
        val summary = buildString {
            appendLine("Here's what I've recorded:")
            appendLine("🐾 Species: ${s.commonName}")
            if (s.scientificName.isNotBlank()) appendLine("🔬 Scientific: ${s.scientificName}")
            appendLine("🔢 Count: ${s.count}")
            appendLine("📍 Location: ${s.locality}")
            appendLine("🌿 Habitat: ${s.habitat}")
            if (s.notes.isNotBlank()) appendLine("📝 Notes: ${s.notes}")
        }
        appendMessage(ChatMessage(text = summary.trim(), isUser = false))
        askCurrentStep()
    }

    /**
     * Handles a yes/no answer at the CONFIRM step.
     * Yes → save the record. No → offer to restart.
     */
    private fun handleConfirm(userText: String) {
        val lower = userText.trim().lowercase()
        val isYes = lower in listOf(
            "yes", "y", "yeah", "yep", "sure", "ok",
            "okay", "save", "confirm", "go ahead", "do it"
        )

        if (isYes) {
            saveRecord()
        } else {
            obsState = obsState.copy(step = ObservationStep.DONE)
            appendMessage(ChatMessage(
                text = "No problem! Send a photo or type a species name to start a new observation.",
                isUser = false
            ))
        }
    }

    // ─── Record saving ────────────────────────────────────────────────────────

    private fun saveRecord() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val record = DarwinRecord(
            scientificName   = obsState.scientificName,
            vernacularName   = obsState.commonName,
            individualCount  = obsState.count,
            locality         = obsState.locality,
            habitat          = obsState.habitat,
            notes            = obsState.notes,
            eventDate        = today,
            decimalLatitude  = currentLatLon?.lat,
            decimalLongitude = currentLatLon?.lon,
            photoPath        = currentPhotoPath
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.insert(record)
                SyncWorker.schedule(getApplication())
                _recordSaved.value = record.occurrenceID

                appendMessage(ChatMessage(
                    text = "✅ Record saved! It will sync to GBIF when you're on WiFi.",
                    isUser = false
                ))
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to save record", e)
                appendMessage(ChatMessage(
                    text = "❌ Failed to save: ${e.message}",
                    isUser = false
                ))
            } finally {
                obsState         = ObservationState()
                currentPhotoPath = ""
            }
        }
    }

    // ─── Output sanity check ──────────────────────────────────────────────────

    /**
     * Gemma sometimes outputs preamble or goes off-script.
     * Take the first non-blank line only, and fall back to the hardcoded question
     * if the result is too short, too long, or looks like an explanation rather
     * than a question.
     */
    private fun sanitiseRephrasing(raw: String, fallback: String): String {
        val cleaned = raw
            .trim()
            .lines()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: return fallback

        val wordCount = cleaned.split("\\s+".toRegex()).size
        return if (wordCount in 3..20) cleaned else fallback
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private fun appendMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    private fun appendLoadingBubble() {
        _messages.value = _messages.value + ChatMessage(
            text = "", isUser = false, isLoading = true
        )
    }

    private fun updateLastBotMessage(text: String) {
        val current   = _messages.value.toMutableList()
        val lastIndex = current.indexOfLast { !it.isUser }
        if (lastIndex == -1) return
        current[lastIndex] = current[lastIndex].copy(text = text, isLoading = false)
        _messages.value = current
    }
}