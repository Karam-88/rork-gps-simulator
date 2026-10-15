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

    private var listener: LocationListener? = null

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

    fun isPlayServicesProviderAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            locationManager?.allProviders?.contains(LocationManager.FUSED_PROVIDER) == true
        } catch (e: Exception) {
            false
        }
    }

    fun activeProviderName(preferPlayServices: Boolean): String = when {
        preferPlayServices && isPlayServicesProviderAvailable() -> "Google Play Services (fused)"
        locationManager?.allProviders?.contains(LocationManager.NETWORK_PROVIDER) == true -> "Network + GPS"
        isGpsProviderEnabled() -> "GPS"
        else -> "Unavailable"
    }

    private fun providerOrder(preferPlayServices: Boolean): List<String> = buildList {
        if (preferPlayServices && isPlayServicesProviderAvailable()) add(LocationManager.FUSED_PROVIDER)
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
                .filterNot { it.isMockFix() }
                .maxByOrNull { it.time }
                ?.toSample()
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission revoked while reading last known fix")
            null
        }
    }

    /**
     * Starts continuous real-location updates from all enabled providers.
     * Safe to call repeatedly; the previous listener is removed first.
     */
    fun startUpdates(preferPlayServices: Boolean, intervalMs: Long, onSample: (LocationSample) -> Unit) {
        if (!hasPermission()) return
        val manager = locationManager ?: return
        stopUpdates()

        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!location.isMockFix()) onSample(location.toSample())
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            @Deprecated("Required for API < 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }

        var registered = false
        providerOrder(preferPlayServices).forEach { provider ->
            val enabled = runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            if (!enabled) return@forEach
            try {
                manager.requestLocationUpdates(
                    provider,
                    intervalMs,
                    0f,
                    newListener,
                    Looper.getMainLooper(),
                )
                registered = true
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot request location updates without permission")
            } catch (e: Exception) {
                Log.d(TAG, "Provider $provider unavailable for real updates")
            }
        }

        if (registered) {
            listener = newListener
            // Show a valid real marker immediately while a fresher fix is acquired.
            lastKnown(preferPlayServices)?.let(onSample)
        }
    }

    fun stopUpdates() {
        val manager = locationManager ?: return
        listener?.let { runCatching { manager.removeUpdates(it) } }
        listener = null
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
