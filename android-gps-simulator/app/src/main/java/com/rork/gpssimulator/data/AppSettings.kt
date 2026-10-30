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
    val theme: ThemeMode = ThemeMode.LIGHT,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val followSystemLanguage: Boolean = true,
    val mapType: MapType = MapType.STANDARD,
    val mapTheme: ThemeMode = ThemeMode.LIGHT,
    val units: UnitSystem = UnitSystem.AUTOMATIC,

    // Appearance only: never used by the mock-location engine.
    val crosshairShape: CrosshairShape = CrosshairShape.PRECISION,
    val crosshairColor: CrosshairColor = CrosshairColor.GREEN,
    val crosshairSizeDp: Float = 60f,
    val crosshairThicknessDp: Float = 3f,
    val crosshairOpacity: Float = 0.9f,
    val mockMarkerColor: MockMarkerColor = MockMarkerColor.RED,

    // Map behavior
    val directTapSelection: Boolean = false,
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


/**
 * Previous releases followed the device's appearance by default. Convert that
 * legacy automatic default to daylight once when upgrading, without overwriting
 * explicitly selected light/dark themes. Future selections of SYSTEM persist.
 */
internal fun AppSettings.withDaylightStartupDefault(alreadyApplied: Boolean): AppSettings =
    if (alreadyApplied || theme != ThemeMode.SYSTEM) this
    else copy(
        theme = ThemeMode.LIGHT,
        mapTheme = if (mapTheme == ThemeMode.SYSTEM) ThemeMode.LIGHT else mapTheme,
    )
