package com.rork.gpssimulator.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.rork.gpssimulator.data.AppRepository
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.MockReadiness
import com.rork.gpssimulator.i18n.AppLanguage
import com.rork.gpssimulator.BuildConfig
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices

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
class MockLocationController(
    context: Context,
    private val onAsyncFailure: (MockResult.Failure) -> Unit = {},
) {

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
    private var lastPushElapsedMs = 0L
    private var lastHeartbeatElapsedMs = 0L
    private var pushCount = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(appContext) }
    private var useFused = false
    private var sessionGeneration = 0L
    private val fusedPump = MockFusedPump<Location>(
        setMode = { enabled, done ->
            fusedClient.setMockMode(enabled).addOnCompleteListener { task ->
                diagnostic("fused_mode=$enabled success=${task.isSuccessful}")
                done(if (task.isSuccessful) null else task.exception ?: IllegalStateException("Fused mode cancelled"))
            }
        },
        send = { location, done ->
            fusedClient.setMockLocation(location).addOnCompleteListener { task ->
                done(if (task.isSuccessful) null else task.exception ?: IllegalStateException("Fused update cancelled"))
            }
        },
        onFailure = { error ->
            diagnostic("fused_failed error=${error.javaClass.simpleName}")
            val failedGeneration = sessionGeneration
            // Avoid re-entering a start/push before its synchronous work finishes.
            mainHandler.post {
                if (isRunning && sessionGeneration == failedGeneration) onAsyncFailure(MockResult.Failure(
                    if (error is SecurityException) MockFailure.NOT_SELECTED_AS_MOCK_APP else MockFailure.PROVIDER_ERROR,
                    "Could not update Google Play Services mock location",
                ))
            }
        },
    )

    val isStopping: Boolean get() = fusedPump.isStopping

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

        // Never replace the real GPS provider merely to check the mock app-op.
        val probe = "gps_simulator_readiness_probe"
        return try {
            addTestProvider(manager, probe)
            manager.setTestProviderEnabled(probe, true)
            manager.removeTestProvider(probe)
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
        } finally {
            removeTestProviderQuietly(manager, probe)
        }
    }

    /**
     * Starts system mock location and immediately pushes the first fix.
     * Returns [MockResult.Failure] when the platform refuses — the caller must
     * not enter the MOCK_ACTIVE state in that case.
     */
    fun start(point: LatLng, accuracy: Float, altitude: Double, speed: Float, bearing: Float): MockResult {
        if (isStopping) return MockResult.Failure(MockFailure.PROVIDER_ERROR, "Mock location cleanup is still pending")
        sessionGeneration++
        val manager = locationManager
            ?: return MockResult.Failure(MockFailure.PROVIDER_ERROR, "LocationManager unavailable")

        return try {
            // Replacing a session must not remove/re-add working providers: that
            // briefly exposes the underlying real source between sessions.
            targetProviders.filterNot { it in installedProviders }.forEach { provider ->
                try {
                    addTestProvider(manager, provider)
                    installedProviders.add(provider)
                    manager.setTestProviderEnabled(provider, true)
                } catch (e: IllegalArgumentException) {
                    // Provider not supported on this device; skip it but keep the others.
                    installedProviders.remove(provider)
                    removeTestProviderQuietly(manager, provider)
                    Log.i(TAG, "Provider $provider unavailable for mocking")
                }
            }

            if (LocationManager.GPS_PROVIDER !in installedProviders ||
                LocationManager.NETWORK_PROVIDER !in installedProviders) {
                stop()
                return MockResult.Failure(
                    MockFailure.PROVIDER_ERROR,
                    "GPS and network mock providers are required",
                )
            }

            isRunning = true
            useFused = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS
            lastPushElapsedMs = 0L
            lastHeartbeatElapsedMs = 0L
            pushCount = 0L
            diagnostic("session_started providers=${installedProviders.joinToString(",")} play_services=$useFused")
            when (val push = push(point, accuracy, altitude, speed, bearing)) {
                is MockResult.Failure -> {
                    stop()
                    push
                }
                MockResult.Success -> MockResult.Success
            }
        } catch (e: SecurityException) {
            isRunning = false
            stop()
            showMockSetupToast()
            MockResult.Failure(
                MockFailure.NOT_SELECTED_AS_MOCK_APP,
                "App is not selected as the mock location app",
            )
        } catch (e: Exception) {
            isRunning = false
            stop()
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

        val nowMs = SystemClock.elapsedRealtime()
        val gapMs = if (lastPushElapsedMs == 0L) 0L else nowMs - lastPushElapsedMs
        if (gapMs > 3_000L) diagnostic("update_gap_ms=$gapMs")
        lastPushElapsedMs = nowMs
        var firstFailure: Exception? = null
        var failedProvider: String? = null
        // Identical position and timestamps for every source in this update.
        val fix = buildLocation(LocationManager.GPS_PROVIDER, point, accuracy, altitude, speed, bearing)
        installedProviders.forEach { provider ->
            try {
                manager.setTestProviderLocation(
                    provider,
                    Location(fix).apply { this.provider = provider },
                )
            } catch (e: Exception) {
                if (firstFailure == null) {
                    firstFailure = e
                    failedProvider = provider
                }
                diagnostic("provider_push_failed provider=$provider error=${e.javaClass.simpleName}")
            }
        }
        if (firstFailure == null && useFused) {
            fusedPump.submit(Location(fix).apply { provider = "fused" })
        }
        pushCount++
        if (nowMs - lastHeartbeatElapsedMs >= 5_000L) {
            diagnostic("heartbeat pushes=$pushCount gap_ms=$gapMs providers=${installedProviders.joinToString(",")}")
            lastHeartbeatElapsedMs = nowMs
        }
        return when (firstFailure) {
            null -> MockResult.Success
            is SecurityException -> {
                showMockSetupToast()
                MockResult.Failure(MockFailure.NOT_SELECTED_AS_MOCK_APP, "Mock location permission was revoked ($failedProvider)")
            }
            else -> MockResult.Failure(MockFailure.PROVIDER_ERROR, "Could not update mock location ($failedProvider)")
        }
    }

    /** Stops the session and removes every installed test provider. */
    fun stop(completed: (Boolean) -> Unit = {}): Boolean {
        // Injection is disarmed before any provider is removed.
        sessionGeneration++
        isRunning = false
        val manager = locationManager
        installedProviders.toList().forEach { provider ->
            try {
                // Removing the test provider restores the underlying real provider directly.
                manager?.removeTestProvider(provider)
                installedProviders.remove(provider)
                Log.i(TAG, "REAL_RECOVERY mock_provider_removed provider=$provider")
            } catch (e: IllegalArgumentException) {
                // Older Android versions throw if it has already been removed.
                installedProviders.remove(provider)
                Log.i(TAG, "REAL_RECOVERY mock_provider_already_removed provider=$provider")
            } catch (e: Exception) {
                Log.e(TAG, "REAL_RECOVERY mock_provider_removal_failed provider=$provider", e)
            }
        }
        Log.i(TAG, "REAL_RECOVERY injection_stopped cleanup_ok=${installedProviders.isEmpty()}")
        val platformClean = installedProviders.isEmpty()
        fusedPump.stop { fusedClean -> completed(platformClean && fusedClean) }
        return installedProviders.isEmpty()
    }

    private fun showMockSetupToast() {
        val language = AppRepository.getInstance(appContext).settings.value.language
        val message = if (language == AppLanguage.ARABIC) {
            "يرجى تفعيل خيارات المطور ثم اختيار GPS Simulator كتطبيق الموقع الوهمي"
        } else {
            "Enable Developer Options, then select GPS Simulator as the mock location app"
        }
        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
    }

    private fun addTestProvider(manager: LocationManager, provider: String) {
        // addTestProvider atomically replaces an existing provider with this name.
        // Do not remove first and expose a real fix in the registration gap.
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

    private fun diagnostic(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "MOCK_DIAG $message")
    }
}

