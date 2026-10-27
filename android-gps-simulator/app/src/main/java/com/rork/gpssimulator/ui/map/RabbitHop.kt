package com.rork.gpssimulator.ui.map

import com.rork.gpssimulator.data.model.LatLng
import kotlin.math.sin

internal data class RabbitHopFrame(val point: LatLng, val liftDp: Float, val scaleX: Float, val scaleY: Float)
data class RabbitHopRequest(val id: Long, val from: LatLng, val to: LatLng)

/** Visual coordinates only. Never passed to the mock location service. */
internal fun rabbitHopFrame(from: LatLng, to: LatLng, fraction: Float, initialLiftDp: Float = 0f): RabbitHopFrame {
    val ms = fraction.coerceIn(0f, 1f) * 700f
    fun ease(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    // Advance throughout the whole hop: no vertical takeoff, straight plateau or vertical landing.
    val travel = ease(ms / 700f)
    val longitudeDelta = ((to.lng - from.lng + 540.0) % 360.0) - 180.0
    val point = when {
        travel == 0f -> from
        travel == 1f -> to
        else -> LatLng(from.lat + (to.lat - from.lat) * travel,
            ((from.lng + longitudeDelta * travel + 540.0) % 360.0) - 180.0)
    }
    // One continuous semicircular arc, maximum lift exactly halfway through travel.
    // Preserve the current height when another hop interrupts a running animation.
    val progress = fraction.coerceIn(0f, 1f)
    val lift = initialLiftDp * (1f - progress) +
        (36f * sin(progress * Math.PI)).toFloat().coerceAtLeast(0f)

    return RabbitHopFrame(point, if (fraction >= 1f) 0f else lift, 1f, 1f)
}
