package com.rork.gpssimulator.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.MockReadiness
import java.util.Locale

/**
 * Result of attempting to start or push a system-level mock location.
 *
 * The UI must only show "MOCK LOCATION ACTIVE" when [Success] is returned —
 * a marker moving on the map is NOT the same as the system location changing.
 */
sealed interface MockResult {
    data object Success : MockResult
    data class Failure(val reason: MockFailure, val message: String) : MockResult
}

enum class MockFailure {
    /** The app is not selected in Developer Options › Select mock location app. */
    NOT_SELECTED_AS_MOCK_APP,

    /** ACCESS_FINE_LOCATION has not been granted. */
    MISSING_PERMISSION,

    /** LocationManager rejected provider creation for another reason. */
    PROVIDER_ERROR,
}

/**
 * Real Android system-level mock location integration.
 *
 * Uses [LocationManager.addTestProvider] / [LocationManager.setTestProviderLocation],
 * which is the supported mechanism gated behind
 * Developer Options › "Select mock location app". When this app is not the selected
 * mock app, Android throws SecurityException and we surface a precise failure instead
 * of pretending the session started.
 */
class MockLocationController(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    /** Providers we push test locations into, so any app reading the system location sees them. */
    private val targetProviders: List<String> = buildList {
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
    }

    private val installedProviders = mutableSetOf<String>()

    @Volatile
    var isRunning: Boolean = false
        private set

    /**
     * Probes whether system mock location can currently be started, without
     * leaving any provider installed.
     */
    fun checkReadiness(hasLocationPermission: Boolean): MockReadiness {
        val manager = locationManager ?: return MockReadiness.SETUP_REQUIRED
        if (!hasLocationPermission) return MockReadiness.PERMISSION_REQUIRED
        if (isRunning) return MockReadiness.READY

        val probe = LocationManager.GPS_PROVIDER
        return try {
            addTestProvider(manager, probe)
            manager.setTestProviderEnabled(probe, true)
            removeTestProviderQuietly(manager, probe)
            MockReadiness.READY
        } catch (e: SecurityException) {
            Log.i(TAG, "Mock location not permitted: app is not the selected mock location app")
            MockReadiness.DEV_OPTIONS_REQUIRED
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Mock provider probe rejected by the platform")
            MockReadiness.SETUP_REQUIRED
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected mock readiness failure: ${e.javaClass.simpleName}")
            MockReadiness.SETUP_REQUIRED
        }
    }

    /**
     * Starts system mock location and immediately pushes the first fix.
     * Returns [MockResult.Failure] when the platform refuses — the caller must
     * not enter the MOCK_ACTIVE state in that case.
     */
    fun start(point: LatLng, accuracy: Float, altitude: Double, speed: Float, bearing: Float): MockResult {
        val manager = locationManager
            ?: return MockResult.Failure(MockFailure.PROVIDER_ERROR, "LocationManager unavailable")

        return try {
            installedProviders.clear()
            targetProviders.forEach { provider ->
                try {
                    addTestProvider(manager, provider)
                    manager.setTestProviderEnabled(provider, true)
                    installedProviders.add(provider)
                } catch (e: IllegalArgumentException) {
                    // Provider not supported on this device; skip it but keep the others.
                    Log.i(TAG, "Provider $provider unavailable for mocking")
                }
            }

            if (installedProviders.isEmpty()) {
                return MockResult.Failure(
                    MockFailure.PROVIDER_ERROR,
                    "No location provider accepted a test provider",
                )
            }

            isRunning = true
            when (val push = push(point, accuracy, altitude, speed, bearing)) {
                is MockResult.Failure -> {
                    stop()
                    push
                }
                MockResult.Success -> MockResult.Success
            }
        } catch (e: SecurityException) {
            isRunning = false
            removeAllQuietly()
            showMockSetupToast()
            MockResult.Failure(
                MockFailure.NOT_SELECTED_AS_MOCK_APP,
                "App is not selected as the mock location app",
            )
        } catch (e: Exception) {
            isRunning = false
            removeAllQuietly()
            Log.e(TAG, "Failed to start mock location: ${e.javaClass.simpleName}")
            MockResult.Failure(MockFailure.PROVIDER_ERROR, "Could not start mock location")
        }
    }

    /** Pushes an updated test location into every installed provider. */
    fun push(point: LatLng, accuracy: Float, altitude: Double, speed: Float, bearing: Float): MockResult {
        val manager = locationManager
            ?: return MockResult.Failure(MockFailure.PROVIDER_ERROR, "LocationManager unavailable")
        if (!isRunning || installedProviders.isEmpty()) {
            return MockResult.Failure(MockFailure.PROVIDER_ERROR, "Mock session is not running")
        }

        return try {
            installedProviders.forEach { provider ->
                manager.setTestProviderLocation(
                    provider,
                    buildLocation(provider, point, accuracy, altitude, speed, bearing),
                )
            }
            MockResult.Success
        } catch (e: SecurityException) {
            showMockSetupToast()
            MockResult.Failure(
                MockFailure.NOT_SELECTED_AS_MOCK_APP,
                "Mock location permission was revoked",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push mock location: ${e.javaClass.simpleName}")
            MockResult.Failure(MockFailure.PROVIDER_ERROR, "Could not update mock location")
        }
    }

    /** Stops the session and removes every installed test provider. */
    fun stop() {
        val manager = locationManager ?: return
        installedProviders.forEach { provider ->
            try {
                manager.setTestProviderEnabled(provider, false)
            } catch (e: Exception) {
                Log.d(TAG, "Provider $provider already disabled")
            }
            removeTestProviderQuietly(manager, provider)
        }
        installedProviders.clear()
        isRunning = false
    }

    private fun showMockSetupToast() {
        val message = if (Locale.getDefault().language == "ar") {
            "يرجى تفعيل خيارات المطور ثم اختيار GPS Simulator كتطبيق الموقع الوهمي"
        } else {
            "Enable Developer Options, then select GPS Simulator as the mock location app"
        }
        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
    }

    private fun addTestProvider(manager: LocationManager, provider: String) {
        // Remove any stale provider left behind by a previous process.
        removeTestProviderQuietly(manager, provider)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.addTestProvider(
                provider,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE,
            )
        } else {
            @Suppress("DEPRECATION")
            manager.addTestProvider(
                provider,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                1, // POWER_REQUIREMENT_LOW
                1, // ACCURACY_FINE
            )
        }
    }

    private fun removeTestProviderQuietly(manager: LocationManager, provider: String) {
        try {
            manager.removeTestProvider(provider)
        } catch (e: Exception) {
            // Provider was not installed — expected on the first run.
        }
    }

    private fun removeAllQuietly() {
        val manager = locationManager ?: return
        targetProviders.forEach { removeTestProviderQuietly(manager, it) }
        installedProviders.clear()
    }

    @SuppressLint("NewApi")
    private fun buildLocation(
        provider: String,
        point: LatLng,
        accuracy: Float,
        altitude: Double,
        speed: Float,
        bearing: Float,
    ): Location = Location(provider).apply {
        latitude = point.lat
        longitude = point.lng
        this.accuracy = accuracy
        this.altitude = altitude
        this.speed = speed
        this.bearing = bearing
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verticalAccuracyMeters = 3f
            speedAccuracyMetersPerSecond = 1f
            bearingAccuracyDegrees = 5f
        }
    }

    companion object {
        private const val TAG = "MockLocationController"
    }
}
