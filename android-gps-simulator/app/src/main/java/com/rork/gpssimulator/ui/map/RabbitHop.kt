package com.rork.gpssimulator.ui.map

import com.rork.gpssimulator.data.model.LatLng
import kotlin.math.sin

internal data class RabbitHopFrame(val point: LatLng, val liftDp: Float, val scaleX: Float, val scaleY: Float)
data class RabbitHopRequest(val id: Long, val from: LatLng, val to: LatLng)

/** Visual coordinates only. Never passed to the mock location service. */
internal fun rabbitHopFrame(from: LatLng, to: LatLng, fraction: Float, initialLiftDp: Float = 0f): RabbitHopFrame {
    // Smooth single arc: horizontal progress and vertical lift happen together.
    // The previous phased rise/travel/landing produced a squared-off path.
    val t = fraction.coerceIn(0f, 1f)
    val longitudeDelta = ((to.lng - from.lng + 540.0) % 360.0) - 180.0
    val point = when (t) {
        0f -> from
        1f -> to
        else -> LatLng(
            from.lat + (to.lat - from.lat) * t,
            ((from.lng + longitudeDelta * t + 540.0) % 360.0) - 180.0,
        )
    }
    val arc = sin(Math.PI * t).toFloat().coerceAtLeast(0f)
    val lift = initialLiftDp * (1f - t) + 40f * arc
    val landing = ((t - 0.85f) / 0.15f).coerceIn(0f, 1f)
    val squash = sin(landing * Math.PI).toFloat() * 0.035f
    return RabbitHopFrame(point, if (t >= 1f) 0f else lift, 1f + squash, 1f - squash)
}
