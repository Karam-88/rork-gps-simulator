package com.rork.gpssimulator.util

import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.UnitSystem
import java.util.Locale
import java.util.concurrent.TimeUnit

object Format {

    private val root: Locale = Locale.US

    fun coord(value: Double): String = String.format(root, "%.6f", value)

    fun coordPair(point: LatLng): String =
        "${coord(point.lat)}, ${coord(point.lng)}"

    /** Resolves AUTOMATIC to metric/imperial using the device locale. */
    fun resolveImperial(units: UnitSystem): Boolean = when (units) {
        UnitSystem.METRIC -> false
        UnitSystem.IMPERIAL -> true
        UnitSystem.AUTOMATIC -> {
            val country = Locale.getDefault().country.uppercase(root)
            country in setOf("US", "LR", "MM", "GB")
        }
    }

    /** Distance with unit suffix, switching between small and large units. */
    fun distance(meters: Double, units: UnitSystem): String {
        val imperial = resolveImperial(units)
        return if (imperial) {
            val feet = meters * 3.28084
            if (feet < 1000) String.format(root, "%.0f ft", feet)
            else String.format(root, "%.2f mi", meters / 1609.344)
        } else {
            if (meters < 1000) String.format(root, "%.0f m", meters)
            else String.format(root, "%.2f km", meters / 1000.0)
        }
    }

    /** Short elevation/accuracy readout, always in whole units. */
    fun shortDistance(meters: Double, units: UnitSystem): String {
        val imperial = resolveImperial(units)
        return if (imperial) String.format(root, "%.0f ft", meters * 3.28084)
        else String.format(root, "%.0f m", meters)
    }

    fun speed(kmh: Double, units: UnitSystem): String {
        val imperial = resolveImperial(units)
        return if (imperial) String.format(root, "%.0f mph", kmh * 0.621371)
        else String.format(root, "%.0f km/h", kmh)
    }

    fun speedFromMetersPerSecond(mps: Float, units: UnitSystem): String =
        speed(mps * 3.6, units)

    fun bearing(deg: Float): String = String.format(root, "%.0f°", deg)

    /** HH:MM:SS elapsed timer. */
    fun elapsed(millis: Long): String {
        val safe = millis.coerceAtLeast(0)
        val hours = TimeUnit.MILLISECONDS.toHours(safe)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return String.format(root, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /** Compact duration such as "22 min" or "1 h 05 min". */
    fun duration(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        return if (hours > 0) String.format(root, "%d h %02d min", hours, minutes)
        else String.format(root, "%d min", maxOf(1, minutes))
    }

    fun timestamp(millis: Long): String {
        val formatter = java.text.SimpleDateFormat("d MMM yyyy · HH:mm", root)
        return formatter.format(java.util.Date(millis))
    }

    fun clockTime(millis: Long): String {
        val formatter = java.text.SimpleDateFormat("HH:mm", root)
        return formatter.format(java.util.Date(millis))
    }
}
