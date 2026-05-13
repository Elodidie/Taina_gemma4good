package com.example.gemma

import android.app.Application
import android.media.ExifInterface
import android.media.MediaPlayer
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ─── Data ─────────────────────────────────────────────────────────────────────

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false,
    val photoPath: String? = null   // absolute path to the internal copy; never a content URI
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages    = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading   = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _modelLoaded = MutableStateFlow(false)
    val modelLoaded: StateFlow<Boolean> = _modelLoaded.asStateFlow()

    private val _recordSaved = MutableStateFlow<String?>(null)
    val recordSaved: StateFlow<String?> = _recordSaved.asStateFlow()

    private val _currentLocation = MutableStateFlow<LatLon?>(null)
    val currentLocation: StateFlow<LatLon?> = _currentLocation.asStateFlow()

    private var model: GemmaInferenceModel? = null

    /**
     * Full conversation history as (userMessage, assistantMessage) pairs.
     * Sent to Gemma on every call so it has full context — Gemma has no memory.
     */
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    private var currentPhotoPath = ""
    private var currentLatLon: LatLon? = null
    // True when currentLatLon came from photo EXIF — background device GPS must not override it.
    private var gpsFromExif = false

    private val dao            = DarwinDatabase.getInstance(application).darwinDao()
    private val locationHelper = LocationHelper(application)

    // Keeps the background GPS running and caches each fix into currentLatLon.
    // Started at init, stopped when the ViewModel is cleared.
    private var stopBackgroundFix: (() -> Unit)? = null

    init {
        loadModel()
        startLocationUpdates()
    }

    // ─── Init ──────────────────────────────────────────────────────────────────

    private fun startLocationUpdates() {
        stopBackgroundFix = locationHelper.startBackgroundFix { latLon ->
            // Never overwrite a precise EXIF fix with the device's current position.
            if (!gpsFromExif) {
                currentLatLon = latLon
                _currentLocation.value = latLon
                Log.d("ChatViewModel", "GPS updated from device: $latLon")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopBackgroundFix?.invoke()
    }

    private fun loadModel() {
        if (_modelLoaded.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                model = GemmaInferenceModel.getInstance(getApplication())
                _modelLoaded.value = true

                if (_messages.value.isEmpty()) {
                    appendMessage(ChatMessage(
                        text    = "Hi! I'm Taina 🌿 I can help you with:\n\n🐦 Recording a species observation\n🎵 Setting up an AudioMoth bioacoustic recorder\n\nWhat would you like to do?",
                        isUser  = false
                    ))
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading model", e)
                appendMessage(ChatMessage(
                    text   = "❌ Failed to load model:\n${e.message}",
                    isUser = false
                ))
            }
        }
    }

    /** Called after the user grants location permission — (re)starts background GPS. */
    fun refreshLocation() {
        stopBackgroundFix?.invoke()
        startLocationUpdates()
    }

    // ─── Public actions ────────────────────────────────────────────────────────

    /**
     * Called when the user picks or captures a photo.
     * Saves the file, shows the thumbnail bubble, then lets Gemma open
     * the observation dialogue naturally.
     */
    fun onPhotoSelected(uri: Uri) {
        if (_isLoading.value) return
        // Lock immediately so a second photo tap during the file-copy / GPS fetch
        // window cannot start a concurrent observation flow.
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(
                    getApplication<Application>().filesDir,
                    "obs_${System.currentTimeMillis()}.jpg"
                )

                val bytesCopied = getApplication<Application>()
                    .contentResolver
                    .openInputStream(uri)
                    ?.use { input -> file.outputStream().use { input.copyTo(it) } }

                if (bytesCopied == null || bytesCopied == 0L) {
                    Log.e("ChatViewModel", "Photo copy failed — stream was null or empty for $uri")
                    appendMessage(ChatMessage(text = "⚠️ Could not read the photo. Try selecting it again.", isUser = false))
                    _isLoading.value = false
                    return@launch
                }

                Log.d("ChatViewModel", "Photo copied: ${file.absolutePath} ($bytesCopied bytes)")
                currentPhotoPath = file.absolutePath

                // Read EXIF GPS immediately — it's a fast local file op.
                // Device GPS is resolved later in parseAndSave() (with timeout +
                // geocode fallback) so the upload never blocks waiting for a fix.
                val exifLocation = readExifLocation(file.absolutePath)
                if (exifLocation != null) {
                    Log.d("ChatViewModel", "GPS from photo EXIF: $exifLocation")
                    currentLatLon = exifLocation
                    gpsFromExif = true
                } else {
                    gpsFromExif = false
                }

                appendMessage(ChatMessage(
                    text      = "📷 Photo attached",
                    isUser    = true,
                    photoPath = file.absolutePath
                ))

                // Reset conversation for a fresh observation
                // (_isLoading stays true — startNewObservation keeps it set until Gemma responds)
                startNewObservation()

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Photo handling failed", e)
                appendMessage(ChatMessage(text = "Failed to process image.", isUser = false))
                _isLoading.value = false
            }
        }
    }

    /**
     * Called on every user text message.
     * Passes the full history + new message to Gemma and handles the response:
     * - If the response is a JSON sentinel → parse and save the record.
     * - Otherwise → show as a normal chat bubble and add to history.
     */
    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isLoading.value) return

        appendMessage(ChatMessage(text = userText, isUser = true))
        appendLoadingBubble()
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            var accumulated = ""

            model?.sendMessage(
                history     = conversationHistory.toList(),
                userMessage = userText
            )
                ?.catch { e ->
                    Log.e("ChatViewModel", "Gemma error", e)
                    updateLastBotMessage("Sorry, something went wrong. Please try again.")
                    _isLoading.value = false
                }
                ?.onCompletion {
                    _isLoading.value = false
                    val trimmed = accumulated.trim()

                    // Extract the first well-formed JSON block from the response
                    val jsonBlock = extractJson(trimmed)
                    when {
                        jsonBlock != null && isAudioMothJson(jsonBlock) -> {
                            // Show Gemma's closing sentence (before the JSON), then generate
                            val preJson = trimmed.substringBefore("{").trim()
                            updateLastBotMessage(preJson.ifBlank { "🎵 Generating your AudioMoth chime…" })
                            playChimeFromJson(jsonBlock)
                        }
                        jsonBlock != null && isJsonRecord(jsonBlock) -> {
                            // Show Gemma's closing sentence which already contains the follow-up question
                            val preJson = trimmed.substringBefore("{").trim()
                            updateLastBotMessage(preJson.ifBlank { "✅ Observation saved! It will be published to the open biodiversity ledger on ATProto when connected." })
                            parseAndSave(jsonBlock, userText)
                        }
                        else -> {
                            updateLastBotMessage(trimmed)
                            conversationHistory.add(Pair(userText, trimmed))
                        }
                    }
                }
                ?.collect { token ->
                    // Accumulate silently — bubble is set once in onCompletion
                    // This avoids any partial JSON flashing in the UI
                    accumulated += token
                }
        }
    }

    fun acknowledgeRecordSaved() {
        _recordSaved.value = null
    }

    // ─── Conversation management ───────────────────────────────────────────────

    /**
     * Resets history and asks Gemma to open a fresh observation dialogue.
     * Called after a photo is attached or when starting over.
     * NOTE: intentionally does NOT clear currentPhotoPath — that is only
     * cleared inside parseAndSave() after the record is persisted.
     */
    private suspend fun startNewObservation() {
        conversationHistory.clear()
        // GPS is resolved in parseAndSave() — not here — so Gemma starts immediately.

        // [photo attached] tells Gemma to skip the photo offer — one is already provided
        val trigger = "[photo attached] I just spotted a species and want to record it."
        appendMessage(ChatMessage(text = trigger, isUser = true))
        appendLoadingBubble()
        _isLoading.value = true

        var accumulated = ""

        model?.sendMessage(
            history     = emptyList(),
            userMessage = trigger
        )
            ?.catch { e ->
                Log.e("ChatViewModel", "Gemma error on start", e)
                updateLastBotMessage("What species did you spot? 🌿")
                _isLoading.value = false
            }
            ?.onCompletion {
                _isLoading.value = false
                val trimmed = accumulated.trim()
                updateLastBotMessage(trimmed)
                conversationHistory.add(Pair(trigger, trimmed))
            }
            ?.collect { token ->
                accumulated += token
                updateLastBotMessage(accumulated)
            }
    }

    // ─── JSON detection & record saving ───────────────────────────────────────

    /**
     * Extracts the first well-formed JSON object from a string by counting braces.
     * Handles nested objects and ignores any prose that surrounds or follows the JSON block.
     */
    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            when (c) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun isAudioMothJson(text: String): Boolean {
        val stripped = text.trim()
        if (!stripped.startsWith("{")) return false
        return try {
            JSONObject(stripped).optBoolean("audiomoth", false)
        } catch (_: Exception) { false }
    }

    /**
     * Called when Gemma emits the AudioMoth sentinel JSON.
     * Resolves "device" lat/lng to the current GPS fix and "random" to a fresh hex ID,
     * then generates and plays the chime WAV.
     */
    private suspend fun playChimeFromJson(json: String) {
        try {
            val obj = JSONObject(json)

            val latRaw = obj.opt("lat")
            val lat = if (latRaw is Number) latRaw.toDouble()
                      else currentLatLon?.lat ?: 0.0

            val lngRaw = obj.opt("lng")
            val lng = if (lngRaw is Number) lngRaw.toDouble()
                      else currentLatLon?.lon ?: 0.0

            val depRaw = obj.optString("deploymentId", "random")
            val deploymentHex = if (depRaw == "random" || depRaw.length != 16) randomHex()
                                else depRaw.lowercase()

            val timestamp = System.currentTimeMillis() / 1000L
            val samples = AudioMothChimeGenerator.generate(timestamp, lat, lng, deploymentHex)
            val wavFile = File(getApplication<Application>().cacheDir, "audiomoth_chime.wav")
            AudioMothChimeGenerator.writeWav(wavFile, samples)

            withContext(Dispatchers.Main) {
                val mp = MediaPlayer()
                mp.setDataSource(wavFile.absolutePath)
                mp.prepare()
                mp.start()
                mp.setOnCompletionListener { it.release() }
            }

            val utcFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
            appendMessage(ChatMessage(
                text = "✅ Chime playing! Hold your AudioMoth close to the speaker.\n\n" +
                       "• Time set to: ${utcFmt.format(Date(timestamp * 1000))} UTC\n" +
                       "• GPS: ${"%.5f".format(lat)}°, ${"%.5f".format(lng)}°\n" +
                       "• Deployment ID: $deploymentHex\n\n" +
                       "Would you like to record a species observation, or configure another device? 🌿",
                isUser = false
            ))
            conversationHistory.clear()

        } catch (e: Exception) {
            Log.e("AudioMoth", "Chime playback failed", e)
            appendMessage(ChatMessage(text = "❌ Chime failed: ${e.message}", isUser = false))
        }
    }

    private fun randomHex(length: Int = 16): String {
        val chars = "0123456789abcdef"
        return (1..length).map { chars.random() }.joinToString("")
    }

    private fun isJsonRecord(text: String): Boolean {
        val stripped = text.trim()
        if (!stripped.startsWith("{")) return false
        return try {
            val json = JSONObject(stripped)
            json.has("commonName") && json.has("count") && json.has("locality")
        } catch (_: Exception) { false }
    }

    private suspend fun parseAndSave(json: String, lastUserMessage: String) {
        try {
            val obj = JSONObject(json)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // GPS resolution — priority order:
            //   1. EXIF from photo (already in currentLatLon, gpsFromExif = true) — most precise
            //   2. Locality stated by user → geocode (they explicitly named the place)
            //   3. Device GPS — background fix or fresh satellite fix (works offline)
            var gpsSource = "EXIF"
            if (!gpsFromExif) {
                var geocodedSuccessfully = false
                val locality = obj.optString("locality", "")
                if (locality.isNotBlank()) {
                    val geocoded = geocodeLocality(locality)
                    if (geocoded != null) {
                        currentLatLon = geocoded
                        geocodedSuccessfully = true
                        gpsSource = "geocoded locality"
                        Log.d("TainaRecord", "GPS from geocoding '$locality': $geocoded")
                    } else {
                        Log.w("TainaRecord", "Geocoding '$locality' returned null — falling back to device GPS")
                    }
                }
                if (!geocodedSuccessfully) {
                    // Always fetch a fresh fix at save time so a changed device location
                    // is reflected immediately. Fall back to the background-cached value
                    // only if the fresh fetch times out (e.g. indoors with no signal).
                    val freshFix = withTimeoutOrNull(5_000) { locationHelper.getCurrentLocation() }
                    if (freshFix != null) currentLatLon = freshFix
                    gpsSource = "device GPS"
                    Log.d("TainaRecord", "GPS from device (fresh=$freshFix, used=$currentLatLon)")
                }
            }

            val commonName = obj.optString("commonName", "")
            val rawScientific = obj.optString("scientificName", "").trim()
            val scientificName = when {
                rawScientific.isNotBlank()
                    && !rawScientific.equals("unknown", ignoreCase = true)
                    && !rawScientific.equals("don't know", ignoreCase = true)
                    && !rawScientific.equals("n/a", ignoreCase = true) -> rawScientific
                else -> SpeciesLookup.lookup(commonName) ?: "no data"
            }

            val record = DarwinRecord(
                scientificName   = scientificName,
                vernacularName   = commonName,
                individualCount  = obj.optInt("count", 1),
                locality         = obj.optString("locality", ""),
                habitat          = obj.optString("habitat", ""),
                notes            = obj.optString("notes", ""),
                eventDate        = today,
                decimalLatitude  = currentLatLon?.lat,
                decimalLongitude = currentLatLon?.lon,
                photoPath        = currentPhotoPath
            )

            // ── Logcat verification ──────────────────────────────────────
            // Filter by tag "TainaRecord" in Logcat to verify every save.
            // Example Logcat query: tag:TainaRecord
            Log.d("TainaRecord", "────────────────────────────────────────")
            Log.d("TainaRecord", "commonName     : ${record.vernacularName}")
            Log.d("TainaRecord", "scientificName : ${record.scientificName}")
            Log.d("TainaRecord", "count          : ${record.individualCount}")
            Log.d("TainaRecord", "locality       : ${record.locality}")
            Log.d("TainaRecord", "habitat        : ${record.habitat}")
            Log.d("TainaRecord", "notes          : ${record.notes}")
            Log.d("TainaRecord", "date           : ${record.eventDate}")
            Log.d("TainaRecord", "lat/lon        : ${record.decimalLatitude}, ${record.decimalLongitude}")
            Log.d("TainaRecord", "gpsSource      : $gpsSource")
            Log.d("TainaRecord", "occurrenceID   : ${record.occurrenceID}")
            Log.d("TainaRecord", "────────────────────────────────────────")

            dao.insert(record)
            SyncWorker.schedule(getApplication())
            _recordSaved.value = record.occurrenceID

            // Reset for next observation
            conversationHistory.clear()
            currentPhotoPath = ""
            gpsFromExif      = false

        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to parse or save record", e)
            appendMessage(ChatMessage(
                text   = "❌ Failed to save: ${e.message}",
                isUser = false
            ))
        }
    }

    // ─── EXIF GPS ─────────────────────────────────────────────────────────────

    /**
     * Reads GPS coordinates embedded in a JPEG by the camera app at capture time.
     * Returns null if the file has no EXIF location data (e.g. gallery photos
     * downloaded from the web, or screenshots).
     *
     * Why prefer EXIF over device GPS?
     * A user may attach a photo taken at the observation site but record it later
     * from a different location. EXIF carries the coordinates of *when and where
     * the shutter was pressed*, which is exactly what we want for biodiversity data.
     */
    private fun readExifLocation(filePath: String): LatLon? {
        return try {
            val exif    = ExifInterface(filePath)
            val latLon  = FloatArray(2)
            if (exif.getLatLong(latLon)) LatLon(latLon[0].toDouble(), latLon[1].toDouble())
            else null
        } catch (e: Exception) {
            Log.d("ChatViewModel", "No EXIF GPS in $filePath: ${e.message}")
            null
        }
    }

    /**
     * Converts a place name to GPS coordinates using the device's offline Geocoder.
     * Works without internet on devices that have cached geocoding data (most Android
     * phones with Google Play Services). Returns null on emulators or if the place
     * name is too ambiguous — device GPS is the primary source for field use.
     */
    private fun geocodeLocality(locality: String): LatLon? {
        if (!android.location.Geocoder.isPresent()) {
            Log.w("ChatViewModel", "Geocoder not available on this device")
            return null
        }
        return try {
            val geocoder = android.location.Geocoder(getApplication(), Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocationName(locality, 1)
            if (!results.isNullOrEmpty()) {
                Log.d("ChatViewModel", "Geocoder: '$locality' → ${results[0].latitude}, ${results[0].longitude}")
                LatLon(results[0].latitude, results[0].longitude)
            } else {
                Log.w("ChatViewModel", "Geocoder returned no results for '$locality'")
                null
            }
        } catch (e: Exception) {
            Log.w("ChatViewModel", "Geocoder failed for '$locality': ${e.message}")
            null
        }
    }

    /**
     * Embeds GPS coordinates into a JPEG's EXIF. Used for camera photos where
     * the camera app didn't write location data (common when the camera app
     * doesn't have location permission).
     */
    private fun writeExifLocation(filePath: String, latLon: LatLon) {
        try {
            val exif = ExifInterface(filePath)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (latLon.lat >= 0) "N" else "S")
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, decimalToDms(Math.abs(latLon.lat)))
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (latLon.lon >= 0) "E" else "W")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, decimalToDms(Math.abs(latLon.lon)))
            exif.saveAttributes()
            Log.d("ChatViewModel", "GPS written to EXIF: ${latLon.lat}, ${latLon.lon}")
        } catch (e: Exception) {
            Log.w("ChatViewModel", "Failed to write GPS EXIF: ${e.message}")
        }
    }

    /** Converts a decimal degree value to the DMS rational string format EXIF expects. */
    private fun decimalToDms(decimal: Double): String {
        val degrees = decimal.toInt()
        val minutesF = (decimal - degrees) * 60
        val minutes = minutesF.toInt()
        val secondsF = (minutesF - minutes) * 60
        val secondsNum = (secondsF * 1000).toLong()
        return "$degrees/1,$minutes/1,$secondsNum/1000"
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