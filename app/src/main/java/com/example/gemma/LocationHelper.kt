package com.example.gemma

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class LatLon(val lat: Double, val lon: Double)

class LocationHelper(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LatLon? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        Log.d("LocationHelper", "Permission check — FINE: $fineGranted, COARSE: $coarseGranted")

        if (!fineGranted && !coarseGranted) {
            Log.w("LocationHelper", "No location permission granted — returning null")
            return null
        }

        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()

            fun requestFreshFix() {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        Log.d("LocationHelper", "Fresh fix result: $loc")
                        if (!cont.isCompleted) cont.resume(loc?.let { LatLon(it.latitude, it.longitude) })
                    }
                    .addOnFailureListener { e ->
                        Log.w("LocationHelper", "Fresh fix failed: ${e.message}")
                        if (!cont.isCompleted) cont.resume(null)
                    }
            }

            // Fast path: cached last-known location (immediate, no satellite needed).
            // Falls back to a fresh fix when no cache exists.
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
}