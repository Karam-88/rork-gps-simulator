package com.rork.gpssimulator.data

import com.rork.gpssimulator.data.model.MapType
import com.rork.gpssimulator.data.model.SpeedProfile
import com.rork.gpssimulator.data.model.ThemeMode
import com.rork.gpssimulator.data.model.UnitSystem
import com.rork.gpssimulator.i18n.AppLanguage
import kotlinx.serialization.Serializable

/** Every persisted user preference. Defaults double as the "reset" values. */
@Serializable
data class AppSettings(
    // General
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val followSystemLanguage: Boolean = true,
    val mapType: MapType = MapType.STANDARD,
    val units: UnitSystem = UnitSystem.AUTOMATIC,

    // Map behavior
    val animateMap: Boolean = true,
    val rememberLastPosition: Boolean = true,
    val returnToRealOnStop: Boolean = true,
    val defaultZoom: Float = 14f,

    // Mock location behavior
    val stopOnAppClose: Boolean = false,
    val restoreLastLocation: Boolean = true,
    val startConfirmation: Boolean = true,
    val usePlayServices: Boolean = true,

    // Randomization
    val randomizeEnabled: Boolean = false,
    val randomizationRadiusM: Float = 10f,

    // Simulated values
    val accuracyM: Float = 5f,
    val altitudeM: Float = 250f,
    val speedProfile: SpeedProfile = SpeedProfile.WALKING,
    val customSpeedKmh: Float = 30f,

    // Advanced parameters
    val bearingDeg: Float = 0f,
    val updateIntervalMs: Int = 1000,
    val coordinateVariation: Boolean = false,
    val routeInterpolation: Boolean = true,

    // Tools
    val joystickEnabled: Boolean = false,

    // Data & privacy
    val saveHistory: Boolean = true,

    // Persisted camera
    val lastCameraLat: Double = 37.7749,
    val lastCameraLng: Double = -122.4194,
    val lastCameraZoom: Float = 14f,

    // Last simulated coordinate
    val lastMockLat: Double = Double.NaN,
    val lastMockLng: Double = Double.NaN,
) {
    val speedKmh: Double
        get() = if (speedProfile == SpeedProfile.CUSTOM) customSpeedKmh.toDouble() else speedProfile.kmh
}
