package com.example.gemma

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dao = DarwinDatabase.getInstance(applicationContext).darwinDao()
        val pending = dao.getPending()
        Log.d("SyncWorker", "Syncing ${pending.size} records")

        for (record in pending) {
            try {
                // Replace with your actual API endpoint
                // This example posts to a local server or GBIF-compatible endpoint
                val json = JSONObject().apply {
                    put("occurrenceID", record.occurrenceID)
                    put("scientificName", record.scientificName)
                    put("decimalLatitude", record.decimalLatitude)
                    put("decimalLongitude", record.decimalLongitude)
                    put("eventDate", record.eventDate)
                    put("individualCount", record.individualCount)
                    put("habitat", record.habitat)
                    put("locality", record.locality)
                    put("notes", record.notes)
                }

                // Uncomment and configure when you have an endpoint:
                // val url = URL("https://your-api.example.com/occurrences")
                // val conn = url.openConnection() as HttpURLConnection
                // conn.requestMethod = "POST"
                // conn.setRequestProperty("Content-Type", "application/json")
                // conn.doOutput = true
                // conn.outputStream.write(json.toString().toByteArray())
                // if (conn.responseCode == 200 || conn.responseCode == 201) {
                //     dao.markSynced(record.occurrenceID)
                // }

                // For now just mark as synced (remove this when real endpoint is ready)
                dao.markSynced(record.occurrenceID)
                Log.d("SyncWorker", "Synced ${record.occurrenceID}")
            } catch (e: Exception) {
                Log.e("SyncWorker", "Failed to sync ${record.occurrenceID}", e)
            }
        }
        Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi only
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}