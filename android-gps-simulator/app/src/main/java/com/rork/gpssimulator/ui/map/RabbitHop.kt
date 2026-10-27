package com.rork.gpssimulator.ui.map

import com.rork.gpssimulator.data.model.LatLng
import kotlin.math.sin

internal data class RabbitHopFrame(val point: LatLng, val liftDp: Float, val scaleX: Float, val scaleY: Float)
data class RabbitHopRequest(val id: Long, val from: LatLng, val to: LatLng)

/** Visual coordinates only. Never passed to the mock location service. */
internal fun rabbitHopFrame(from: LatLng, to: LatLng, fraction: Float, initialLiftDp: Float = 0f): RabbitHopFrame {
    val ms = fraction.coerceIn(0f, 1f) * 700f
    fun ease(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    val travel = ease((ms - 200f) / 300f)
    val longitudeDelta = ((to.lng - from.lng + 540.0) % 360.0) - 180.0
    val point = when {
        travel == 0f -> from
        travel == 1f -> to
        else -> LatLng(from.lat + (to.lat - from.lat) * travel,
            ((from.lng + longitudeDelta * travel + 540.0) % 360.0) - 180.0)
    }
    val landing = ((ms - 500f) / 200f).coerceIn(0f, 1f)
    val lift = when {
        ms < 200f -> initialLiftDp + (36f - initialLiftDp) * ease(ms / 200f)
        ms < 500f -> 36f
        landing < 0.65f -> 36f * (1f - ease(landing / 0.65f))
        else -> (sin(((landing - 0.65f) / 0.35f) * Math.PI) * 4f).toFloat().coerceAtLeast(0f)
    }
    val squash = if (landing > 0.5f) sin((landing - 0.5f) * 2f * Math.PI).toFloat() * 0.04f else 0f
    return RabbitHopFrame(point, if (fraction >= 1f) 0f else lift, 1f + squash, 1f - squash)
}
