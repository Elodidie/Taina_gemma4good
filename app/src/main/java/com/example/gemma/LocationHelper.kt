package com.example.gemma

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class LatLon(val lat: Double, val lon: Double)

class LocationHelper(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    private fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        Log.d("LocationHelper", "Permission — FINE: $fine, COARSE: $coarse")
        return fine || coarse
    }

    /**
     * Returns the best available location:
     * 1. Cached last-known fix (instant, fully offline).
     * 2. Fresh HIGH_ACCURACY satellite fix (offline, takes a few seconds outdoors).
     *
     * PRIORITY_HIGH_ACCURACY uses the device's GPS radio — no internet required.
     * Callers should wrap this with withTimeoutOrNull() for a hard deadline.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LatLon? {
        if (!hasPermission()) {
            Log.w("LocationHelper", "No location permission — returning null")
            return null
        }

        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()

            fun requestFreshFix() {
                // HIGH_ACCURACY = GPS satellite, works fully offline in the field.
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        Log.d("LocationHelper", "Fresh GPS fix: $loc")
                        if (!cont.isCompleted) cont.resume(loc?.let { LatLon(it.latitude, it.longitude) })
                    }
                    .addOnFailureListener { e ->
                        Log.w("LocationHelper", "Fresh fix failed: ${e.message}")
                        if (!cont.isCompleted) cont.resume(null)
                    }
            }

            // Try cached location first — immediate and works offline.
            client.lastLocation
                .addOnSuccessListener { lastLoc ->
                    Log.d("LocationHelper", "Last known location: $lastLoc")
                    when {
                        cont.isCompleted -> return@addOnSuccessListener
                        lastLoc != null  -> cont.resume(LatLon(lastLoc.latitude, lastLoc.longitude))
                        else             -> requestFreshFix()
                    }
                }
                .addOnFailureListener { e ->
                    Log.w("LocationHelper", "lastLocation failed: ${e.message}")
                    if (!cont.isCompleted) requestFreshFix()
                }

            cont.invokeOnCancellation { cts.cancel() }
        }
    }

    /**
     * Starts a continuous GPS fix in the background and delivers the first result
     * to [onLocation]. Call this at app start so a fix is ready by the time the
     * user saves a record. Fully offline — uses satellite GPS only.
     * Returns a stop function; call it when no longer needed.
     */
    @SuppressLint("MissingPermission")
    fun startBackgroundFix(onLocation: (LatLon) -> Unit): () -> Unit {
        if (!hasPermission()) return {}

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .setMaxUpdates(5)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    Log.d("LocationHelper", "Background fix: ${loc.latitude}, ${loc.longitude}")
                    onLocation(LatLon(loc.latitude, loc.longitude))
                }
            }
        }

        client.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())
        return { client.removeLocationUpdates(callback) }
    }
}
