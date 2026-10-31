package com.rork.gpssimulator.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView as NativeMapView
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng as GoogleLatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.MapStyleOptions
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.MapType
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Native Google Maps renderer.
 *
 * Google Maps is intentionally used only for visual map rendering and gestures.
 * Search, reverse geocoding, routes and the mock-location engine remain on the
 * app's existing services and do not call Places, Geocoding or Routes APIs.
 */
@Composable
internal fun VectorMapView(
    camera: MapCameraState,
    mapType: MapType,
    darkMap: Boolean = false,
    modifier: Modifier = Modifier,
    markers: List<MapMarker> = emptyList(),
    polylines: List<MapPolyline> = emptyList(),
    circles: List<MapCircle> = emptyList(),
    onMapTap: ((LatLng) -> Unit)? = null,
    onMapLongPress: ((LatLng) -> Unit)? = null,
    onCameraIdle: ((LatLng, Float, Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val holder = remember {
        GoogleMapHolder(context, camera, onMapTap, onMapLongPress, onCameraIdle)
    }

    DisposableEffect(holder) {
        holder.start()
        onDispose { holder.destroy() }
    }

    LaunchedEffect(onMapTap, onMapLongPress, onCameraIdle) {
        holder.onMapTap = onMapTap
        holder.onMapLongPress = onMapLongPress
        holder.onCameraIdle = onCameraIdle
    }

    LaunchedEffect(mapType, darkMap) {
        holder.setMapType(mapType)
        holder.setDarkMap(darkMap)
    }

    LaunchedEffect(camera.centerLat, camera.centerLng, camera.zoom) {
        holder.syncCameraFromCompose(camera)
    }

    LaunchedEffect(markers, polylines, circles) {
        holder.setOverlays(markers, polylines, circles)
    }

    AndroidView(
        factory = {
            holder.mapView.apply {
                // Embedded maps can live inside vertically scrolling sheets. While the
                // user is touching the map, keep the parent from stealing pinch/pan
                // gestures; release interception as soon as the gesture ends.
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_POINTER_DOWN,
                        MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
            }
        },
        modifier = modifier,
        update = { holder.syncCameraFromCompose(camera) },
    )
}

private class GoogleMapHolder(
    context: Context,
    private val cameraState: MapCameraState,
    onMapTap: ((LatLng) -> Unit)?,
    onMapLongPress: ((LatLng) -> Unit)?,
    onCameraIdle: ((LatLng, Float, Boolean) -> Unit)?,
) {
    val mapView = NativeMapView(context)
    var onMapTap = onMapTap
    var onMapLongPress = onMapLongPress
    var onCameraIdle = onCameraIdle

    private var map: GoogleMap? = null
    private var currentMapType: MapType? = null
    private var darkMapEnabled = false
    private var pendingMarkers: List<MapMarker> = emptyList()
    private var pendingPolylines: List<MapPolyline> = emptyList()
    private var pendingCircles: List<MapCircle> = emptyList()
    // Keep native Google Maps overlay objects alive across Compose/GPS refreshes.
    // Clearing the whole map every time caused all pins to flash or disappear.
    private val nativeMarkers = mutableListOf<com.google.android.gms.maps.model.Marker?>()
    private val nativePolylines = mutableListOf<com.google.android.gms.maps.model.Polyline>()
    private val nativeCircles = mutableListOf<com.google.android.gms.maps.model.Circle>()
    private var displayedMarkers: List<MapMarker> = emptyList()
    private var displayedPolylines: List<MapPolyline> = emptyList()
    private var displayedCircles: List<MapCircle> = emptyList()
    private var started = false
    private var updatingFromCompose = false
    private var userGestureMovedCamera = false

    init {
        mapView.onCreate(null)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            cameraState.projection = { point ->
                readyMap.projection.toScreenLocation(GoogleLatLng(point.lat, point.lng)).let {
                    androidx.compose.ui.geometry.Offset(it.x.toFloat(), it.y.toFloat())
                }
            }
            cameraState.projectionRevision++
            mapView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> cameraState.projectionRevision++ }

            readyMap.uiSettings.apply {
                isCompassEnabled = false
                isMapToolbarEnabled = false
                isMyLocationButtonEnabled = false
                isZoomControlsEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
            }
            readyMap.isBuildingsEnabled = true

            // Keep Google's required logo/attribution clear of the bottom action bar
            // without shifting the visual camera target away from the Compose crosshair.
            // Symmetric top/bottom padding preserves the true geometric center.
            val verticalPadding = (104f * context.resources.displayMetrics.density).roundToInt()
            readyMap.setPadding(0, verticalPadding, 0, verticalPadding)

            readyMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition(cameraState)))

            readyMap.setOnCameraMoveStartedListener { reason ->
                if (!updatingFromCompose) {
                    val userGesture = reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE
                    if (userGesture) userGestureMovedCamera = true
                    cameraState.isUserInteracting = userGesture
                }
            }
            readyMap.setOnCameraMoveListener {
                cameraState.projectionRevision++
                if (updatingFromCompose) return@setOnCameraMoveListener
                val position = readyMap.cameraPosition
                cameraState.centerLat = clampLatitude(position.target.latitude)
                cameraState.centerLng = position.target.longitude
                cameraState.zoom = position.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
            }
            readyMap.setOnCameraIdleListener {
                cameraState.isUserInteracting = false
                val position = readyMap.cameraPosition
                val point = LatLng(position.target.latitude, position.target.longitude)
                cameraState.centerLat = point.lat
                cameraState.centerLng = point.lng
                cameraState.zoom = position.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
                onCameraIdle?.invoke(point, cameraState.zoom, userGestureMovedCamera)
                userGestureMovedCamera = false
            }
            readyMap.setOnMapLongClickListener { point ->
                onMapLongPress?.invoke(LatLng(point.latitude, point.longitude))
            }
            readyMap.setOnMapClickListener { point ->
                onMapTap?.invoke(LatLng(point.latitude, point.longitude))
            }

            setMapType(currentMapType ?: MapType.STANDARD)
            setDarkMap(darkMapEnabled)
            redrawOverlays()
        }
    }

    fun start() {
        if (started) return
        started = true
        mapView.onStart()
        mapView.onResume()
    }

    fun destroy() {
        cameraState.projection = null
        cameraState.projectionRevision++
        if (started) {
            mapView.onPause()
            mapView.onStop()
            started = false
        }
        mapView.onDestroy()
    }

    fun setMapType(type: MapType) {
        currentMapType = type
        val readyMap = map ?: return
        readyMap.mapType = when (type) {
            MapType.STANDARD -> GoogleMap.MAP_TYPE_NORMAL
            MapType.DETAILED_STREETS -> GoogleMap.MAP_TYPE_NORMAL
            // HYBRID keeps satellite imagery while overlaying Google road and place labels.
            // SATELLITE intentionally has no labels; it is not suitable for route selection.
            MapType.SATELLITE -> GoogleMap.MAP_TYPE_HYBRID
            MapType.TERRAIN -> GoogleMap.MAP_TYPE_TERRAIN
        }
    }

    fun setDarkMap(enabled: Boolean) {
        darkMapEnabled = enabled
        map?.setMapStyle(if (enabled) MapStyleOptions(DARK_MAP_STYLE) else null)
    }

    fun syncCameraFromCompose(camera: MapCameraState) {
        val readyMap = map ?: return
        if (camera.isUserInteracting) return

        val current = readyMap.cameraPosition
        val latDiff = abs(current.target.latitude - camera.centerLat)
        val lngDiff = abs(current.target.longitude - camera.centerLng)
        val zoomDiff = abs(current.zoom - camera.zoom)
        if (latDiff < 0.000001 && lngDiff < 0.000001 && zoomDiff < 0.001f) return

        updatingFromCompose = true
        try {
            readyMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition(camera)))
        } finally {
            updatingFromCompose = false
        }
    }

    fun setOverlays(
        markers: List<MapMarker>,
        polylines: List<MapPolyline>,
        circles: List<MapCircle>,
    ) {
        pendingMarkers = markers
        pendingPolylines = polylines
        pendingCircles = circles
        redrawOverlays()
    }

    private fun redrawOverlays() {
        val readyMap = map ?: return
        // Do not call GoogleMap.clear(). In a running mock route the location /
        // camera state changes frequently and clearing every overlay causes
        // every fixed waypoint to blink. Reuse existing native objects instead.
        val circles = pendingCircles + pendingMarkers
            .filter { it.radiusMeters > 0.0 }
            .map { marker ->
                MapCircle(
                    marker.point,
                    marker.radiusMeters,
                    marker.color.copy(alpha = 0.30f),
                    marker.color.copy(alpha = 0.14f),
                )
            }

        while (nativeCircles.size > circles.size) nativeCircles.removeAt(nativeCircles.lastIndex).remove()
        circles.forEachIndexed { index, circle ->
            val point = GoogleLatLng(circle.center.lat, circle.center.lng)
            if (index >= nativeCircles.size) {
                nativeCircles.add(
                    readyMap.addCircle(
                        CircleOptions()
                            .center(point)
                            .radius(circle.radiusMeters)
                            .strokeColor(circle.strokeColor.toArgb())
                            .fillColor(circle.fillColor.toArgb())
                            .strokeWidth(2f),
                    ),
                )
            } else if (displayedCircles.getOrNull(index) != circle) {
                nativeCircles[index].apply {
                    center = point
                    radius = circle.radiusMeters
                    strokeColor = circle.strokeColor.toArgb()
                    fillColor = circle.fillColor.toArgb()
                }
            }
        }
        displayedCircles = circles.toList()

        val lines = pendingPolylines.filter { it.points.size >= 2 }
        while (nativePolylines.size > lines.size) nativePolylines.removeAt(nativePolylines.lastIndex).remove()
        lines.forEachIndexed { index, line ->
            if (index >= nativePolylines.size) {
                nativePolylines.add(
                    readyMap.addPolyline(
                        PolylineOptions()
                            .addAll(line.points.map { GoogleLatLng(it.lat, it.lng) })
                            .color(line.color.toArgb())
                            .width(line.widthPx)
                            .geodesic(false),
                    ),
                )
            } else if (displayedPolylines.getOrNull(index) != line) {
                nativePolylines[index].apply {
                    points = line.points.map { GoogleLatLng(it.lat, it.lng) }
                    color = line.color.toArgb()
                    width = line.widthPx
                }
            }
        }
        displayedPolylines = lines.toList()

        while (nativeMarkers.size > pendingMarkers.size) nativeMarkers.removeAt(nativeMarkers.lastIndex)?.remove()
        pendingMarkers.forEachIndexed { index, marker ->
            val position = GoogleLatLng(marker.point.lat, marker.point.lng)
            if (index >= nativeMarkers.size) {
                nativeMarkers.add(
                    readyMap.addMarker(
                        MarkerOptions()
                            .position(position)
                            .icon(markerIcon(mapView.context, marker))
                            .anchor(0.5f, if (marker.style == MarkerStyle.DESTINATION) 1f else 0.5f),
                    ),
                )
            } else {
                val previous = displayedMarkers.getOrNull(index)
                nativeMarkers[index]?.let { existing ->
                    if (previous?.point != marker.point) existing.position = position
                    if (previous?.style != marker.style || previous?.color != marker.color ||
                        previous?.label != marker.label || previous.visualScale != marker.visualScale
                    ) {
                        existing.setIcon(markerIcon(mapView.context, marker))
                        existing.setAnchor(0.5f, if (marker.style == MarkerStyle.DESTINATION) 1f else 0.5f)
                    }
                }
            }
        }
        displayedMarkers = pendingMarkers.toList()
    }

    private fun cameraPosition(camera: MapCameraState): CameraPosition =
        CameraPosition.Builder()
            .target(GoogleLatLng(camera.centerLat, camera.centerLng))
            .zoom(camera.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
            .bearing(0f)
            .tilt(0f)
            .build()
}

private fun markerIcon(context: Context, marker: MapMarker): BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val px = (34f * density).roundToInt().coerceAtLeast(24)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = px / 2f

    when (marker.style) {
        MarkerStyle.ACTIVE -> {
            // Purely visual hop/landing; the underlying Android mock provider
            // receives the final destination directly, never interpolated GPS fixes.
            val scale = marker.visualScale.coerceIn(0.75f, 1.30f)
            paint.style = Paint.Style.FILL
            if (scale > 1.01f) {
                paint.color = marker.color.copy(alpha = ((scale - 1f) * 0.65f).coerceIn(0f, 0.25f)).toArgb()
                canvas.drawCircle(center, center, px * 0.46f, paint)
            }
            canvas.save()
            canvas.scale(scale, scale, center, center)
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(center, center, px * 0.40f, paint)
            paint.color = marker.color.toArgb()
            canvas.drawCircle(center, center, px * 0.31f, paint)
            // Match the real-location dot: solid centre with a white outer rim.
            canvas.restore()
        }
        MarkerStyle.CANDIDATE -> {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (2.2f * density).coerceAtLeast(2f)
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(center, center, px * 0.31f, paint)
            paint.strokeWidth = (1.4f * density).coerceAtLeast(1.5f)
            paint.color = marker.color.toArgb()
            canvas.drawCircle(center, center, px * 0.31f, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(center, center, px * 0.07f, paint)
        }
        MarkerStyle.REAL -> {
            // Compact Google-Maps-like dot: translucent halo, slim white rim, solid blue core.
            paint.style = Paint.Style.FILL
            paint.color = marker.color.copy(alpha = 0.14f).toArgb()
            canvas.drawCircle(center, center, px * 0.46f, paint)
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(center, center, px * 0.265f, paint)
            paint.color = marker.color.toArgb()
            canvas.drawCircle(center, center, px * 0.205f, paint)
        }
        MarkerStyle.ROUTE_WAYPOINT -> {
            // White numbered discs with a dark border, matching the route editor.
            val radius = px * 0.39f
            paint.style = Paint.Style.FILL
            paint.setShadowLayer(2f * density, 0f, 1.2f * density, 0x55000000)
            paint.color = marker.color.toArgb()
            canvas.drawCircle(center, center, radius, paint)
            paint.clearShadowLayer()
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(center, center, radius - (2.8f * density), paint)
            paint.color = 0xFF20232B.toInt()
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            val number = marker.label.orEmpty()
            paint.textSize = (if (number.length > 1) 12f else 15f) * density
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(number, center, center - (paint.ascent() + paint.descent()) / 2f, paint)
        }
        MarkerStyle.DESTINATION -> {
            val pin = Path().apply {
                moveTo(center, px * 0.95f)
                cubicTo(px * 0.22f, px * 0.56f, px * 0.17f, px * 0.42f,
                    px * 0.17f, px * 0.35f)
                cubicTo(px * 0.17f, px * 0.02f, px * 0.83f, px * 0.02f,
                    px * 0.83f, px * 0.35f)
                cubicTo(px * 0.83f, px * 0.55f, px * 0.68f, px * 0.76f,
                    center, px * 0.95f)
                close()
            }
            paint.style = Paint.Style.FILL
            paint.color = android.graphics.Color.WHITE
            paint.setShadowLayer(2.5f * density, 0f, 1.5f * density, 0x55000000)
            canvas.drawPath(pin, paint)
            paint.clearShadowLayer()
            canvas.save()
            canvas.scale(0.88f, 0.88f, center, px * 0.50f)
            paint.color = marker.color.toArgb()
            canvas.drawPath(pin, paint)
            canvas.restore()
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(center, px * 0.34f, px * 0.11f, paint)
        }
    }

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/** Dark navy Google Maps vector style; does not alter roads, routing or location services. */
private val DARK_MAP_STYLE = """
[
 {"elementType":"geometry","stylers":[{"color":"#172437"}]},
 {"elementType":"labels.text.fill","stylers":[{"color":"#9caec3"}]},
 {"elementType":"labels.text.stroke","stylers":[{"color":"#172437"}]},
 {"featureType":"road","elementType":"geometry","stylers":[{"color":"#354b65"}]},
 {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#4e6580"}]},
 {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#b9cbe0"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0b1a2e"}]},
 {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#193b36"}]},
 {"featureType":"landscape.natural","elementType":"geometry","stylers":[{"color":"#192d36"}]},
 {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#26394d"}]}
]
""".trimIndent()
