package com.example.gemma

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class LatLon(val lat: Double, val lon: Double)

class LocationHelper(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LatLon? = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()

        fun requestFreshFix() {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (!cont.isCompleted) cont.resume(loc?.let { LatLon(it.latitude, it.longitude) })
                }
                .addOnFailureListener {
                    if (!cont.isCompleted) cont.resume(null)
                }
        }

        // Fast path: return the cached last-known location immediately if available.
        // Falls back to a fresh GPS fix when no cache exists (e.g. first cold start).
        client.lastLocation
            .addOnSuccessListener { lastLoc ->
                when {
                    cont.isCompleted -> return@addOnSuccessListener
                    lastLoc != null  -> cont.resume(LatLon(lastLoc.latitude, lastLoc.longitude))
                    else             -> requestFreshFix()
                }
            }
            .addOnFailureListener {
                if (!cont.isCompleted) requestFreshFix()
            }

        cont.invokeOnCancellation { cts.cancel() }
    }
}