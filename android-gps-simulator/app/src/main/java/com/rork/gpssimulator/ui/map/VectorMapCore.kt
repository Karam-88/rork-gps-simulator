package com.rork.gpssimulator.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.MapType

/** Observable camera shared by all map screens. */
@Stable
class MapCameraState(
    initialCenter: LatLng,
    initialZoom: Float,
) {
    var centerLat by mutableDoubleStateOf(initialCenter.lat)
        internal set
    var centerLng by mutableDoubleStateOf(initialCenter.lng)
        internal set
    var zoom by mutableFloatStateOf(initialZoom)
        internal set
    var sizePx by mutableStateOf(IntSize.Zero)
        internal set
    var isUserInteracting by mutableStateOf(false)
        internal set

    val center: LatLng get() = LatLng(centerLat, centerLng)

    internal var animationJobActive = false

    fun snapTo(point: LatLng, newZoom: Float = zoom) {
        centerLat = clampLatitude(point.lat)
        centerLng = point.lng
        zoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    suspend fun animateTo(point: LatLng, newZoom: Float = zoom, animate: Boolean = true) {
        // MapLibre already animates gesture-driven camera changes smoothly. App-driven
        // camera moves use a short interpolation here so existing callers retain the
        // same API and behaviour as before.
        if (!animate) {
            snapTo(point, newZoom)
            return
        }
        val startLat = centerLat
        val startLng = centerLng
        val startZoom = zoom
        val targetLat = clampLatitude(point.lat)
        val targetLng = point.lng
        val targetZoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val deltaLng = (((targetLng - startLng) + 540.0) % 360.0) - 180.0

        animationJobActive = true
        try {
            val steps = 18
            repeat(steps) { index ->
                val t = (index + 1f) / steps
                val eased = t * t * (3f - 2f * t)
                centerLat = startLat + (targetLat - startLat) * eased
                centerLng = (((startLng + deltaLng * eased) + 540.0) % 360.0) - 180.0
                zoom = startZoom + (targetZoom - startZoom) * eased
                kotlinx.coroutines.delay(18)
            }
        } finally {
            animationJobActive = false
        }
    }
}

@Composable
fun rememberMapCameraState(initialCenter: LatLng, initialZoom: Float): MapCameraState =
    remember { MapCameraState(initialCenter, initialZoom) }

enum class MarkerStyle {
    ACTIVE,
    CANDIDATE,
    REAL,
}

data class MapMarker(
    val point: LatLng,
    val color: Color,
    val pulsing: Boolean = false,
    val radiusMeters: Double = 0.0,
    val label: String? = null,
    val style: MarkerStyle = MarkerStyle.REAL,
)

data class MapPolyline(
    val points: List<LatLng>,
    val color: Color,
    val widthPx: Float = 8f,
)

data class MapCircle(
    val center: LatLng,
    val radiusMeters: Double,
    val strokeColor: Color,
    val fillColor: Color,
)

/**
 * Main map surface. The previous hand-written raster renderer is replaced by
 * MapLibre Native, which provides robust vector rendering, built-in double-tap,
 * pinch/fling gestures and disk/network resource caching.
 */
@Composable
fun MapView(
    camera: MapCameraState,
    mapType: MapType,
    modifier: Modifier = Modifier,
    markers: List<MapMarker> = emptyList(),
    polylines: List<MapPolyline> = emptyList(),
    circles: List<MapCircle> = emptyList(),
    pulseFraction: () -> Float = { 0f },
    onMapTap: ((LatLng) -> Unit)? = null,
    onCameraIdle: ((LatLng, Float) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    @Suppress("UNUSED_EXPRESSION")
    pulseFraction

    Box(modifier = modifier) {
        VectorMapView(
            camera = camera,
            mapType = mapType,
            markers = markers,
            polylines = polylines,
            circles = circles,
            onMapTap = onMapTap,
            onCameraIdle = onCameraIdle,
            modifier = Modifier.fillMaxSize(),
        )
        content()
    }
}
