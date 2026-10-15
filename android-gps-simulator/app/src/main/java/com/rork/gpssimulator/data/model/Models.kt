package com.rork.gpssimulator.data.model

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class LatLng(
    val lat: Double,
    val lng: Double,
) {
    fun isValid(): Boolean = lat in -90.0..90.0 && lng in -180.0..180.0

    /** Great-circle distance in meters. */
    fun distanceTo(other: LatLng): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(other.lat - lat)
        val dLng = Math.toRadians(other.lng - lng)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) *
            sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * asin(minOf(1.0, sqrt(a)))
    }

    /** Initial bearing in degrees (0-360) towards [other]. */
    fun bearingTo(other: LatLng): Float {
        val lat1 = Math.toRadians(lat)
        val lat2 = Math.toRadians(other.lat)
        val dLng = Math.toRadians(other.lng - lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val deg = Math.toDegrees(atan2(y, x))
        return ((deg + 360.0) % 360.0).toFloat()
    }

    /** Offset this point by [meters] along [bearingDeg]. */
    fun offset(meters: Double, bearingDeg: Double): LatLng {
        val r = 6371000.0
        val angular = meters / r
        val brg = bearingDeg * PI / 180.0
        val lat1 = Math.toRadians(lat)
        val lng1 = Math.toRadians(lng)
        val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(brg))
        val lng2 = lng1 + atan2(
            sin(brg) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )
        return LatLng(Math.toDegrees(lat2), ((Math.toDegrees(lng2) + 540) % 360) - 180)
    }

    /** Linear interpolation towards [other] by fraction [t]. */
    fun lerp(other: LatLng, t: Double): LatLng =
        LatLng(lat + (other.lat - lat) * t, lng + (other.lng - lng) * t)
}

enum class SavedKind { FAVORITE, PRESET }

@Serializable
data class SavedLocation(
    val id: String,
    val name: String,
    val address: String = "",
    val point: LatLng,
    val kind: SavedKind = SavedKind.FAVORITE,
    val iconKey: String = "pin",
    val notes: String = "",
    val createdAt: Long = 0L,
)

enum class SpeedProfile(val kmh: Double) {
    WALKING(5.0),
    CYCLING(18.0),
    DRIVING(60.0),
    RUNNING(10.0),
    CUSTOM(30.0),
}

@Serializable
data class SimRoute(
    val id: String,
    val name: String,
    val points: List<LatLng>,
    val profile: SpeedProfile = SpeedProfile.WALKING,
    val customSpeedKmh: Double = 30.0,
    val loop: Boolean = false,
    val createdAt: Long = 0L,
) {
    /** Total route length in meters. */
    val distanceMeters: Double
        get() = points.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }

    val speedKmh: Double
        get() = if (profile == SpeedProfile.CUSTOM) customSpeedKmh else profile.kmh

    /** Estimated duration in seconds at the configured speed. */
    val durationSeconds: Double
        get() = if (speedKmh <= 0) 0.0 else distanceMeters / (speedKmh / 3.6)
}

enum class SessionKind { STATIC, ROUTE, JOYSTICK }

/**
 * Durable snapshot of the mock session owned by [com.rork.gpssimulator.location.MockLocationService].
 *
 * Persisted so the last known state survives process death; the service's own
 * in-memory state (exposed live via MockSessionBus) is always the source of
 * truth while the service is actually running.
 */
@Serializable
data class MockSessionRecord(
    val active: Boolean = false,
    val lat: Double = Double.NaN,
    val lng: Double = Double.NaN,
    val altitude: Double = 0.0,
    val accuracy: Float = 5f,
    val speedKmh: Double = 0.0,
    val bearing: Float = 0f,
    val kind: SessionKind = SessionKind.STATIC,
    val isPaused: Boolean = false,
    val sessionStartedAt: Long = 0L,
    val placeName: String = "",
    val placeAddress: String = "",
)

@Serializable
data class HistoryEntry(
    val id: String,
    val name: String,
    val address: String = "",
    val point: LatLng,
    val startedAt: Long,
    val durationMs: Long,
    val kind: SessionKind = SessionKind.STATIC,
)

@Serializable
data class Geofence(
    val id: String,
    val name: String,
    val center: LatLng,
    val radiusMeters: Double,
    val createdAt: Long = 0L,
)

enum class GeofenceState { INSIDE, OUTSIDE }

@Serializable
data class GeofenceEvent(
    val geofenceId: String,
    val geofenceName: String,
    val entered: Boolean,
    val timestamp: Long,
)

/** The app's three-state mock workflow. */
enum class MockState { REAL_GPS, SELECTED, MOCK_ACTIVE }

enum class MapType { STANDARD, SATELLITE, TERRAIN, DETAILED_STREETS }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class UnitSystem { AUTOMATIC, METRIC, IMPERIAL }

/** Readiness of the Android system-level mock location mechanism. */
enum class MockReadiness { READY, SETUP_REQUIRED, PERMISSION_REQUIRED, DEV_OPTIONS_REQUIRED }

/** A live location sample shown across the app. */
data class LocationSample(
    val point: LatLng,
    val accuracy: Float,
    val altitude: Double,
    val speed: Float,
    val bearing: Float,
    val provider: String,
    val timestamp: Long,
)
