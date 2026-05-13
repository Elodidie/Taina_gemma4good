package com.example.gemma

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Publishes pending biodiversity occurrence records to an ATProto PDS
 * (Personal Data Server) as permanent, open, community-owned records.
 *
 * Why ATProto?
 * The AT Protocol (behind Bluesky) provides a decentralised, self-authenticating
 * ledger. Indigenous communities retain ownership of their data via their own DID
 * (Decentralised Identifier) while records are discoverable by researchers worldwide
 * through any ATProto-compatible client.
 *
 * Flow for each pending record:
 *   1. Authenticate → obtain a short-lived access JWT
 *   2. POST com.atproto.repo.createRecord with a custom biodiversity lexicon
 *   3. Receive the permanent at:// URI + CID (content hash) → mark as synced
 *
 * Configuration:
 *   Set [PDS_HOST], [ATP_HANDLE], and [ATP_APP_PASSWORD] to the NGO's PDS instance
 *   and account credentials before going to production.
 *   Use EncryptedSharedPreferences (Jetpack Security) for the password in production.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    // ─── ATProto configuration ────────────────────────────────────────────────

    /** Base URL of the PDS. Can be bsky.social or a self-hosted instance. */
    private val PDS_HOST = "https://bsky.social"

    /** The NGO account handle on the PDS. */
    private val ATP_HANDLE = "taina-biodiversity.bsky.social"

    /** App password generated in Bluesky Settings → App Passwords. */
    private val ATP_APP_PASSWORD = "xxxx-xxxx-xxxx-xxxx"

    /**
     * Custom ATProto lexicon for Darwin-Core-aligned biodiversity occurrence records.
     * The NGO publishes the lexicon schema at https://taina.example.org/lexicons/
     */
    private val LEXICON_TYPE = "org.taina.biodiversity.occurrence"

    // ─── Worker entry point ───────────────────────────────────────────────────

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dao = DarwinDatabase.getInstance(applicationContext).darwinDao()
        val pending = dao.getPending()

        if (pending.isEmpty()) return@withContext Result.success()
        Log.d(TAG, "Publishing ${pending.size} occurrence(s) to ATProto PDS: $PDS_HOST")

        return@withContext try {
            val (accessJwt, did) = authenticate()
            var allSucceeded = true

            for (record in pending) {
                val atUri = publishOccurrence(record, accessJwt, did)
                if (atUri != null) {
                    dao.markSynced(record.occurrenceID)
                    Log.d(TAG, "Published ${record.occurrenceID} → $atUri")
                } else {
                    allSucceeded = false
                    Log.w(TAG, "Failed to publish ${record.occurrenceID}, will retry")
                }
            }

            if (allSucceeded) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "ATProto sync error", e)
            Result.retry()
        }
    }

    // ─── ATProto: authentication ──────────────────────────────────────────────

    /**
     * POST /xrpc/com.atproto.server.createSession
     * Returns (accessJwt, did) on success.
     */
    private fun authenticate(): Pair<String, String> {
        val url = URL("$PDS_HOST/xrpc/com.atproto.server.createSession")
        val body = JSONObject().apply {
            put("identifier", ATP_HANDLE)
            put("password",   ATP_APP_PASSWORD)
        }.toString().toByteArray()

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout    = 15_000
            doOutput       = true
            setRequestProperty("Content-Type", "application/json")
        }

        conn.outputStream.use { it.write(body) }
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val json = JSONObject(response)
        val accessJwt = json.getString("accessJwt")
        val did       = json.getString("did")
        Log.d(TAG, "Authenticated as $did")
        return Pair(accessJwt, did)
    }

    // ─── ATProto: publish occurrence record ───────────────────────────────────

    /**
     * POST /xrpc/com.atproto.repo.createRecord
     *
     * Publishes one Darwin-Core occurrence record under the community's own DID.
     * The record is content-addressed (CID) and permanently retrievable via its
     * at:// URI — no central authority can delete or alter it.
     *
     * Returns the at:// URI of the published record, or null on failure.
     */
    private fun publishOccurrence(
        record: DarwinRecord,
        accessJwt: String,
        did: String,
    ): String? {
        // Build the occurrence record using the custom lexicon
        val occurrence = JSONObject().apply {
            put("\$type",           LEXICON_TYPE)
            put("occurrenceID",     record.occurrenceID)
            put("scientificName",   record.scientificName)
            put("vernacularName",   record.vernacularName)
            put("individualCount",  record.individualCount)
            put("locality",         record.locality)
            put("habitat",          record.habitat)
            put("notes",            record.notes)
            put("eventDate",        record.eventDate)
            if (record.decimalLatitude  != null) put("decimalLatitude",  record.decimalLatitude)
            if (record.decimalLongitude != null) put("decimalLongitude", record.decimalLongitude)
            put("createdAt",        isoNow()) // ISO 8601, required by ATProto
        }

        val requestBody = JSONObject().apply {
            put("repo",       did)
            put("collection", LEXICON_TYPE)
            put("record",     occurrence)
        }.toString().toByteArray()

        val url  = URL("$PDS_HOST/xrpc/com.atproto.repo.createRecord")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout    = 15_000
            doOutput       = true
            setRequestProperty("Content-Type",  "application/json")
            setRequestProperty("Authorization", "Bearer $accessJwt")
        }

        return try {
            conn.outputStream.use { it.write(requestBody) }
            if (conn.responseCode in 200..201) {
                val response = JSONObject(conn.inputStream.bufferedReader().readText())
                response.optString("uri").ifBlank { null }
            } else {
                Log.e(TAG, "createRecord HTTP ${conn.responseCode}: " +
                           conn.errorStream?.bufferedReader()?.readText())
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    // ─── Scheduling ───────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "AtProtoSync"

        private fun isoNow(): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())

        /**
         * Enqueues a one-time publish run whenever the device has any network connection.
         * ATProto records are small JSON objects so mobile data is fine.
         */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
