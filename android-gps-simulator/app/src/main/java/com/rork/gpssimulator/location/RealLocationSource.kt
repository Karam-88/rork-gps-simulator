package com.rork.gpssimulator.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.LocationSample

/**
 * Reads the device's real location through the platform LocationManager.
 *
 * We listen to every enabled real provider (fused/network/GPS) instead of only
 * one provider. This gives a fast coarse fix indoors and lets GPS improve it
 * when available. Mock fixes are always ignored so a stopped simulation cannot
 * remain visible as the user's real position.
 */
class RealLocationSource(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    enum class Issue { PERMISSION, DISABLED, PROVIDER_ERROR }
    private var listener: LocationListener? = null
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(appContext) }
    private var fusedCallback: LocationCallback? = null
    private var generation = 0L


    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val manager = locationManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.isLocationEnabled
            } else {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isGpsProviderEnabled(): Boolean = try {
        locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    } catch (e: Exception) {
        false
    }

    fun isNetworkAvailable(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return try {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    fun isPlayServicesProviderAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS

    fun activeProviderName(preferPlayServices: Boolean): String = when {
        preferPlayServices && isPlayServicesProviderAvailable() -> "Google Play Services + GPS/network"
        locationManager?.allProviders?.contains(LocationManager.NETWORK_PROVIDER) == true -> "Network + GPS"
        isGpsProviderEnabled() -> "GPS"
        else -> "Unavailable"
    }

    private fun providerOrder(preferPlayServices: Boolean): List<String> = buildList {
        if (preferPlayServices && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
    }.distinct()

    /** Best non-mock last-known fix across all providers. */
    fun lastKnown(preferPlayServices: Boolean): LocationSample? {
        if (!hasPermission()) return null
        val manager = locationManager ?: return null
        return try {
            providerOrder(preferPlayServices)
                .mapNotNull { provider ->
                    runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                }
                .filter { RealFixPolicy.accepts(it.isMockFix(), it.elapsedRealtimeNanos,
                    SystemClock.elapsedRealtimeNanos(), MockSessionBus.lastStop?.elapsedNanos ?: 0L, generation, generation) }
                .maxByOrNull { it.elapsedRealtimeNanos }
                ?.toSample()
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission revoked while reading last known fix")
            null
        }
    }

    /** Keep a single registration through recovery; never deliver cached fixes as success. */
    fun startUpdates(
        preferPlayServices: Boolean,
        intervalMs: Long,
        freshAfterNanos: Long = 0L,
        onIssue: (Issue) -> Unit = {},
        onSample: (LocationSample) -> Unit,
    ) {
        stopUpdates()
        val epoch = generation
        if (!hasPermission()) { onIssue(Issue.PERMISSION); return }
        val manager = locationManager
        if (manager == null) { onIssue(Issue.PROVIDER_ERROR); return }
        if (!isLocationEnabled()) onIssue(Issue.DISABLED)
        var newestNanos = 0L
        fun deliver(location: Location, source: String) {
            val now = SystemClock.elapsedRealtimeNanos()
            val mock = location.isMockFix()
            val accepted = RealFixPolicy.accepts(mock, location.elapsedRealtimeNanos, now,
                freshAfterNanos, epoch, generation) && location.elapsedRealtimeNanos > newestNanos
            Log.d(TAG, "REAL_RECOVERY fix source=$source provider=${location.provider} mock=$mock " +
                "wall_ms=${location.time} elapsed_ns=${location.elapsedRealtimeNanos} " +
                "age_ms=${(now - location.elapsedRealtimeNanos) / 1_000_000} accepted=$accepted epoch=$epoch")
            if (accepted) {
                newestNanos = location.elapsedRealtimeNanos
                onSample(location.toSample())
            }
        }
        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) = deliver(location, "LocationManager")
            override fun onProviderEnabled(provider: String) {
                Log.i(TAG, "REAL_RECOVERY provider_enabled provider=$provider epoch=$epoch")
            }
            override fun onProviderDisabled(provider: String) {
                Log.w(TAG, "REAL_RECOVERY provider_disabled provider=$provider epoch=$epoch")
                if (epoch == generation && !isLocationEnabled()) onIssue(Issue.DISABLED)
            }
            @Deprecated("Required for API < 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }
        listener = newListener
        var registered = 0
        providerOrder(preferPlayServices).forEach { provider ->
            // Register even if temporarily disabled; this subscription resumes when the
            // underlying real provider becomes enabled after test-provider removal.
            if (provider !in manager.allProviders) return@forEach
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                onIssue(Issue.PERMISSION)
                return@forEach
            }
            try {
                manager.requestLocationUpdates(provider, intervalMs, 0f, newListener, Looper.getMainLooper())
                registered++
                Log.i(TAG, "REAL_RECOVERY listener_registered source=LocationManager provider=$provider epoch=$epoch")
            } catch (e: Exception) {
                Log.w(TAG, "REAL_RECOVERY listener_registration_failed provider=$provider epoch=$epoch", e)
            }
        }
        if (preferPlayServices && isPlayServicesProviderAvailable()) {
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.forEach { deliver(it, "GooglePlayServices") }
                }
            }
            fusedCallback = callback
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs)
                .setMaxUpdateAgeMillis(0L)
                .setWaitForAccurateLocation(false)
                .build()
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                onIssue(Issue.PERMISSION)
                return
            }
            try {
                fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                    .addOnSuccessListener {
                        // A removed request may finish registering asynchronously after Stop.
                        if (epoch != generation) fusedClient.removeLocationUpdates(callback)
                        else Log.i(TAG, "REAL_RECOVERY listener_registered source=GooglePlayServices epoch=$epoch")
                    }
                    .addOnFailureListener { error ->
                        Log.w(TAG, "REAL_RECOVERY gms_registration_failed epoch=$epoch", error)
                        if (epoch == generation && registered == 0)
                            onIssue(if (error is SecurityException) Issue.PERMISSION else Issue.PROVIDER_ERROR)
                    }
            } catch (e: Exception) {
                Log.w(TAG, "REAL_RECOVERY gms_registration_failed epoch=$epoch", e)
                if (registered == 0) onIssue(if (e is SecurityException) Issue.PERMISSION else Issue.PROVIDER_ERROR)
            }
        } else if (registered == 0) {
            onIssue(if (!hasPermission()) Issue.PERMISSION else Issue.PROVIDER_ERROR)
        }
    }

    fun stopUpdates() {
        generation++ // Invalidate callbacks already queued before removing subscriptions.
        listener?.let { current ->
            runCatching { locationManager?.removeUpdates(current) }
                .onFailure { Log.w(TAG, "REAL_RECOVERY platform_listener_removal_failed", it) }
        }
        listener = null
        fusedCallback?.let { callback ->
            runCatching { fusedClient.removeLocationUpdates(callback) }
                .onFailure { Log.w(TAG, "REAL_RECOVERY gms_listener_removal_failed", it) }
        }
        fusedCallback = null
        Log.i(TAG, "REAL_RECOVERY listeners_stopped epoch=$generation")
    }

    @Suppress("DEPRECATION")
    private fun Location.isMockFix(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider

    private fun Location.toSample(): LocationSample = LocationSample(
        point = LatLng(latitude, longitude),
        accuracy = if (hasAccuracy()) accuracy else 0f,
        altitude = if (hasAltitude()) altitude else 0.0,
        speed = if (hasSpeed()) speed else 0f,
        bearing = if (hasBearing()) bearing else 0f,
        provider = provider ?: "unknown",
        timestamp = time,
    )

    companion object {
        private const val TAG = "RealLocationSource"
    }
}
