package com.rork.gpssimulator.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.MapType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh

/** Observable camera for the map. Centre is the point under the screen centre. */
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

    /** True while the user is actively dragging or pinching. */
    var isUserInteracting by mutableStateOf(false)
        internal set

    val center: LatLng get() = LatLng(centerLat, centerLng)

    internal var animationJobActive = false

    fun snapTo(point: LatLng, newZoom: Float = zoom) {
        centerLat = clampLatitude(point.lat)
        centerLng = point.lng
        zoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /** Smoothly flies the camera to [point]; falls back to a jump when [animate] is false. */
    suspend fun animateTo(point: LatLng, newZoom: Float = zoom, animate: Boolean = true) {
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

        // Shortest longitudinal path across the antimeridian.
        val deltaLng = (((targetLng - startLng) + 540.0) % 360.0) - 180.0

        animationJobActive = true
        try {
            val animatable = Animatable(0f)
            animatable.animateTo(1f, tween(durationMillis = 620, easing = FastOutSlowInEasing)) {
                val t = value
                centerLat = startLat + (targetLat - startLat) * t
                centerLng = (((startLng + deltaLng * t) + 540.0) % 360.0) - 180.0
                zoom = startZoom + (targetZoom - startZoom) * t
            }
        } finally {
            animationJobActive = false
        }
    }
}

@Composable
fun rememberMapCameraState(initialCenter: LatLng, initialZoom: Float): MapCameraState =
    remember { MapCameraState(initialCenter, initialZoom) }

/**
 * Visual role of a marker. The active mocked position and a pending candidate
 * must never be confused with each other, so they render differently.
 */
enum class MarkerStyle {
    /** The coordinate Android is currently being mocked to. Large and loud. */
    ACTIVE,

    /** A pending candidate the user has pinned but not confirmed. Quiet outline. */
    CANDIDATE,

    /** The device's real GPS fix. */
    REAL,
}

/** A marker drawn on the map. */
data class MapMarker(
    val point: LatLng,
    val color: Color,
    val pulsing: Boolean = false,
    val radiusMeters: Double = 0.0,
    val label: String? = null,
    val style: MarkerStyle = MarkerStyle.REAL,
)

/** A polyline drawn on the map. */
data class MapPolyline(
    val points: List<LatLng>,
    val color: Color,
    val widthPx: Float = 8f,
)

/** A circular overlay (geofence) drawn on the map. */
data class MapCircle(
    val center: LatLng,
    val radiusMeters: Double,
    val strokeColor: Color,
    val fillColor: Color,
)

/**
 * Interactive raster map: pan with one finger, pinch to zoom, double-tap to zoom in.
 * Rendering is pure Compose Canvas over cached tiles.
 */
@Composable
fun MapView(
    camera: MapCameraState,
    mapType: MapType,
    modifier: Modifier = Modifier,
    markers: List<MapMarker> = emptyList(),
    polylines: List<MapPolyline> = emptyList(),
    circles: List<MapCircle> = emptyList(),
    /**
     * Supplies the 0..1 pulse phase. Read lazily inside the draw scope (and only
     * when a pulsing marker exists) so the animation invalidates drawing without
     * recomposing the map or its parent every frame.
     */
    pulseFraction: () -> Float = { 0f },
    onMapTap: ((LatLng) -> Unit)? = null,
    onCameraIdle: ((LatLng, Float) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val scope: CoroutineScope = rememberCoroutineScope()
    val tileStore = remember { TileStore(scope) }
    val density = LocalDensity.current
    val source = remember(mapType) { TileSource.of(mapType) }

    // Report idle camera position for persistence / live coordinate readout.
    LaunchedEffect(camera, onCameraIdle) {
        if (onCameraIdle == null) return@LaunchedEffect
        snapshotFlow { Triple(camera.centerLat, camera.centerLng, camera.zoom) }
            .debounce(320)
            .collect { (lat, lng, zoom) ->
                onCameraIdle(LatLng(lat, lng), zoom)
            }
    }

    Box(
        modifier = modifier
            .background(if (mapType == MapType.SATELLITE) Color(0xFF0E1620) else Color(0xFFE8EAED))
            .onSizeChanged { camera.sizePx = it }
            .pointerInput(camera, onMapTap) {
                var lastTapTime = 0L
                var lastTapPosition: Offset? = null

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var pastTouchSlop = false
                    var pointerCount = 1
                    val touchSlop = viewConfiguration.touchSlop
                    var totalMovement = 0f
                    camera.isUserInteracting = true

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        pointerCount = maxOf(pointerCount, event.changes.size)
                        val canceled = event.changes.any { it.isConsumed }
                        if (canceled) break

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (!pastTouchSlop) {
                            totalMovement += panChange.getDistance() + abs(1f - zoomChange) * 100f
                            if (totalMovement > touchSlop) pastTouchSlop = true
                        }

                        if (pastTouchSlop) {
                            applyGesture(camera, panChange, zoomChange, event.calculateCentroid(), density.density)
                            event.changes.forEach { change: PointerInputChange ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    camera.isUserInteracting = false

                    val wasTap = !pastTouchSlop && pointerCount == 1
                    if (wasTap && camera.sizePx != IntSize.Zero) {
                        val previousTap = lastTapPosition
                        val elapsedSinceTap = down.uptimeMillis - lastTapTime
                        val closeEnough = previousTap != null &&
                            (down.position - previousTap).getDistance() <= touchSlop * 2f
                        val isDoubleTap = previousTap != null &&
                            elapsedSinceTap in 1..viewConfiguration.doubleTapTimeoutMillis &&
                            closeEnough

                        if (isDoubleTap) {
                            // Match the familiar map behaviour: zoom one full level
                            // around the touched point so that point stays under the finger.
                            applyGesture(
                                camera = camera,
                                pan = Offset.Zero,
                                zoomChange = 2f,
                                centroid = down.position,
                                displayDensity = density.density,
                            )
                            lastTapTime = 0L
                            lastTapPosition = null
                        } else {
                            lastTapTime = down.uptimeMillis
                            lastTapPosition = down.position
                            if (onMapTap != null) {
                                val projector = projectorFor(camera, density.density)
                                onMapTap(projector.toLatLng(down.position))
                            }
                        }
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Touch version so new tiles trigger a redraw.
            @Suppress("UNUSED_EXPRESSION")
            tileStore.version

            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val projector = MapProjector(
                center = camera.center,
                effectiveZoom = effectiveZoom(camera.zoom, density.density),
                widthPx = size.width,
                heightPx = size.height,
            )

            drawTiles(tileStore, source, projector)
            circles.forEach { drawMapCircle(it, projector) }
            polylines.forEach { drawPolyline(it, projector) }

            val phase = if (markers.any { it.pulsing }) pulseFraction() else 0f
            // Draw order matters: the active mocked position always wins the
            // z-order over candidates, real fix, routes and tiles.
            markers.filter { it.style != MarkerStyle.ACTIVE }
                .forEach { drawMarker(it, projector, phase) }
            markers.filter { it.style == MarkerStyle.ACTIVE }
                .forEach { drawMarker(it, projector, phase) }
        }

        content()
    }
}

private fun applyGesture(
    camera: MapCameraState,
    pan: Offset,
    zoomChange: Float,
    centroid: Offset,
    displayDensity: Float,
) {
    val size = camera.sizePx
    if (size == IntSize.Zero) return

    val widthPx = size.width.toFloat()
    val heightPx = size.height.toFloat()
    val projector = MapProjector(
        camera.center,
        effectiveZoom(camera.zoom, displayDensity),
        widthPx,
        heightPx,
    )

    // Pan: move the world opposite to the finger movement.
    var originX = projector.originX - pan.x
    var originY = projector.originY - pan.y

    // Zoom about the gesture centroid so the point under the fingers stays put.
    if (zoomChange != 1f && zoomChange > 0f) {
        val requested = camera.zoom + ln(zoomChange.toDouble()) / ln(2.0)
        val newZoom = requested.coerceIn(MIN_ZOOM.toDouble(), MAX_ZOOM.toDouble())
        val scale = 2.0.pow(newZoom - camera.zoom)
        if (scale != 1.0) {
            originX = (originX + centroid.x) * scale - centroid.x
            originY = (originY + centroid.y) * scale - centroid.y
            camera.zoom = newZoom.toFloat()
        }
    }

    // Convert the updated origin back into a centre coordinate.
    val worldSize = TILE_SIZE * 2.0.pow(effectiveZoom(camera.zoom, displayDensity))
    val centerWorldX = originX + widthPx / 2.0
    val centerWorldY = (originY + heightPx / 2.0).coerceIn(0.0, worldSize)
    val lng = centerWorldX / worldSize * 360.0 - 180.0
    val n = Math.PI * (1.0 - 2.0 * centerWorldY / worldSize)
    val lat = Math.toDegrees(atan(sinh(n)))

    camera.centerLat = clampLatitude(lat)
    camera.centerLng = ((lng + 540.0) % 360.0) - 180.0
}

private fun projectorFor(camera: MapCameraState, displayDensity: Float): MapProjector =
    MapProjector(
        center = camera.center,
        effectiveZoom = effectiveZoom(camera.zoom, displayDensity),
        widthPx = camera.sizePx.width.toFloat(),
        heightPx = camera.sizePx.height.toFloat(),
    )

/** Compensates tile scale for display density so zoom levels look consistent. */
internal fun effectiveZoom(zoom: Float, displayDensity: Float): Double =
    zoom + ln(displayDensity.coerceAtLeast(1f).toDouble()) / ln(2.0)

/**
 * Subtle, neutral lift for the default OpenStreetMap palette: equal gain on all three channels
 * plus a small brightness offset (the fifth column is on the 0–255 scale), so the map gets
 * lighter without any channel being favored. Applied to [TileSource.STANDARD] tiles only; every
 * other source is drawn unfiltered.
 */
private val standardTileColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            1.03f, 0f, 0f, 0f, 8f,
            0f, 1.03f, 0f, 0f, 8f,
            0f, 0f, 1.03f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

private fun DrawScope.drawTiles(store: TileStore, source: TileSource, projector: MapProjector) {
    val z = floor(projector.effectiveZoom).toInt().coerceIn(0, source.maxZoom)
    val scale = 2.0.pow(projector.effectiveZoom - z)
    val tilePx = (TILE_SIZE * scale).toFloat()
    val tileCount = 1 shl z

    val firstCol = floor(projector.originX / (TILE_SIZE * scale)).toInt()
    val firstRow = floor(projector.originY / (TILE_SIZE * scale)).toInt()
    val cols = (size.width / tilePx).toInt() + 2
    val rows = (size.height / tilePx).toInt() + 2

    for (dx in 0..cols) {
        for (dy in 0..rows) {
            val col = firstCol + dx
            val row = firstRow + dy
            if (row < 0 || row >= tileCount) continue
            val wrappedCol = ((col % tileCount) + tileCount) % tileCount

            val left = (col * TILE_SIZE * scale - projector.originX).toFloat()
            val top = (row * TILE_SIZE * scale - projector.originY).toFloat()

            val bitmap = store.tile(source, z, wrappedCol, row)
                ?: findFallbackTile(store, source, z, wrappedCol, row)?.let { fallback ->
                    drawFallbackTile(fallback, source, left, top, tilePx)
                    null
                }

            if (bitmap != null) {
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(
                        (tilePx + 1f).roundToInt(),
                        (tilePx + 1f).roundToInt(),
                    ),
                    colorFilter = if (source == TileSource.STANDARD) standardTileColorFilter else null,
                )
            }
        }
    }
}

private class FallbackTile(
    val bitmap: androidx.compose.ui.graphics.ImageBitmap,
    val srcOffset: IntOffset,
    val srcSize: IntSize,
)

/** Finds an already-cached parent tile so zooming never shows empty space. */
private fun findFallbackTile(
    store: TileStore,
    source: TileSource,
    z: Int,
    x: Int,
    y: Int,
): FallbackTile? {
    var level = z - 1
    var steps = 1
    while (level >= 0 && steps <= 4) {
        val factor = 1 shl steps
        val parentX = x / factor
        val parentY = y / factor
        val cached = store.cached(source, level, parentX, parentY)
        if (cached != null) {
            val sub = (TILE_SIZE / factor).toInt()
            return FallbackTile(
                bitmap = cached,
                srcOffset = IntOffset((x % factor) * sub, (y % factor) * sub),
                srcSize = IntSize(sub, sub),
            )
        }
        level--
        steps++
    }
    return null
}

private fun DrawScope.drawFallbackTile(
    tile: FallbackTile,
    source: TileSource,
    left: Float,
    top: Float,
    tilePx: Float,
) {
    drawImage(
        image = tile.bitmap,
        srcOffset = tile.srcOffset,
        srcSize = tile.srcSize,
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize((tilePx + 1f).roundToInt(), (tilePx + 1f).roundToInt()),
        colorFilter = if (source == TileSource.STANDARD) standardTileColorFilter else null,
    )
}

private fun DrawScope.drawPolyline(line: MapPolyline, projector: MapProjector) {
    if (line.points.size < 2) return
    val path = androidx.compose.ui.graphics.Path()
    line.points.forEachIndexed { index, point ->
        val screen = projector.toScreen(point)
        if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
    }
    drawPath(
        path = path,
        color = line.color.copy(alpha = 0.35f),
        style = Stroke(width = line.widthPx + 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )
    drawPath(
        path = path,
        color = line.color,
        style = Stroke(width = line.widthPx, cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )
}

private fun DrawScope.drawMapCircle(circle: MapCircle, projector: MapProjector) {
    val center = projector.toScreen(circle.center)
    val radiusPx = (circle.radiusMeters / projector.metersPerPixel()).toFloat()
    if (radiusPx <= 0.5f) return
    drawCircle(color = circle.fillColor, radius = radiusPx, center = center)
    drawCircle(
        color = circle.strokeColor,
        radius = radiusPx,
        center = center,
        style = Stroke(width = 4f),
    )
}

private fun DrawScope.drawMarker(marker: MapMarker, projector: MapProjector, pulseFraction: Float) {
    val center = projector.toScreen(marker.point)
    val margin = 320f
    if (center.x < -margin || center.x > size.width + margin) return
    if (center.y < -margin || center.y > size.height + margin) return

    when (marker.style) {
        MarkerStyle.ACTIVE -> drawActiveMarker(marker, projector, center, pulseFraction)
        MarkerStyle.CANDIDATE -> drawCandidateMarker(marker, center)
        MarkerStyle.REAL -> drawRealMarker(marker, projector, center)
    }
}

/**
 * The live mocked position: translucent accuracy halo, an optional pulse, a thick
 * white ring for contrast on satellite/terrain tiles, a bold primary disc and a
 * small white core that keeps the exact coordinate unambiguous.
 */
private fun DrawScope.drawActiveMarker(
    marker: MapMarker,
    projector: MapProjector,
    center: Offset,
    pulseFraction: Float,
) {
    val discRadius = 11.dp.toPx()
    val ringRadius = 14.5.dp.toPx()

    val accuracyPx = (marker.radiusMeters / projector.metersPerPixel()).toFloat()
    val haloRadius = maxOf(accuracyPx, 30.dp.toPx())
    drawCircle(marker.color.copy(alpha = 0.16f), radius = haloRadius, center = center)
    drawCircle(
        color = marker.color.copy(alpha = 0.34f),
        radius = haloRadius,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
    )

    if (marker.pulsing && pulseFraction > 0f) {
        val pulseRadius = ringRadius + (haloRadius - ringRadius).coerceAtLeast(
            18.dp.toPx(),
        ) * pulseFraction
        drawCircle(
            color = marker.color.copy(alpha = (1f - pulseFraction) * 0.45f),
            radius = pulseRadius,
            center = center,
            style = Stroke(width = 2.5.dp.toPx()),
        )
    }

    // Drop shadow lifts the marker off busy imagery.
    drawCircle(
        color = Color.Black.copy(alpha = 0.22f),
        radius = ringRadius,
        center = Offset(center.x, center.y + 1.5.dp.toPx()),
    )
    drawCircle(Color.White, radius = ringRadius, center = center)
    drawCircle(marker.color, radius = discRadius, center = center)
    drawCircle(Color.White.copy(alpha = 0.95f), radius = 3.dp.toPx(), center = center)
}

/**
 * A pinned candidate. Deliberately an outline so it never reads as the live
 * mocked position, which is the only marker allowed to look solid and blue.
 */
private fun DrawScope.drawCandidateMarker(marker: MapMarker, center: Offset) {
    val radius = 8.dp.toPx()
    drawCircle(
        color = Color.White.copy(alpha = 0.92f),
        radius = radius,
        center = center,
        style = Stroke(width = 4.dp.toPx()),
    )
    drawCircle(
        color = marker.color,
        radius = radius,
        center = center,
        style = Stroke(width = 2.dp.toPx()),
    )
    drawCircle(marker.color, radius = 2.dp.toPx(), center = center)
}

/** The real device fix: present but visually subordinate to the mocked position. */
private fun DrawScope.drawRealMarker(
    marker: MapMarker,
    projector: MapProjector,
    center: Offset,
) {
    if (marker.radiusMeters > 0) {
        val radiusPx = (marker.radiusMeters / projector.metersPerPixel()).toFloat()
        if (radiusPx > 1f) {
            drawCircle(marker.color.copy(alpha = 0.14f), radius = radiusPx, center = center)
            drawCircle(
                color = marker.color.copy(alpha = 0.30f),
                radius = radiusPx,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
    drawCircle(Color.White, radius = 8.dp.toPx(), center = center)
    drawCircle(marker.color, radius = 6.dp.toPx(), center = center)
}
