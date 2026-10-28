package com.rork.gpssimulator.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.MapType
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng as MlLatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView as NativeMapView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val OPENFREEMAP_BRIGHT = "https://tiles.openfreemap.org/styles/bright"
private const val OPENFREEMAP_LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
private const val OPENFREEMAP_DARK = "https://tiles.openfreemap.org/styles/dark"

/**
 * Vector-map implementation for the two street-map modes.
 *
 * Using MapLibre + OpenFreeMap removes the cold-start dependency on many raster
 * PNG requests, which is the main reason the old renderer could sit on a blank
 * background for several seconds. It also gives familiar double-tap/pinch/fling
 * gestures and a cleaner Google-like street-map appearance.
 */
@Suppress("DEPRECATION")
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
    onCameraIdle: ((LatLng, Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val holder = remember {
        MapLibre.getInstance(context.applicationContext)
        VectorMapHolder(context, camera, onMapTap, onCameraIdle)
    }

    DisposableEffect(holder) {
        holder.start()
        onDispose { holder.destroy() }
    }

    LaunchedEffect(onMapTap, onCameraIdle) {
        holder.onMapTap = onMapTap
        holder.onCameraIdle = onCameraIdle
    }

    LaunchedEffect(mapType, darkMap) {
        holder.setStyle(mapType, darkMap)
    }

    LaunchedEffect(camera.centerLat, camera.centerLng, camera.zoom) {
        holder.syncCameraFromCompose(camera)
    }

    LaunchedEffect(markers, polylines, circles, mapType) {
        holder.setOverlays(markers, polylines, circles)
    }

    AndroidView(
        factory = {
            holder.mapView.apply {
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
            }
        },
        modifier = modifier,
        update = { holder.syncCameraFromCompose(camera) },
    )
}

@Suppress("DEPRECATION")
private class VectorMapHolder(
    context: Context,
    private val cameraState: MapCameraState,
    onMapTap: ((LatLng) -> Unit)?,
    onCameraIdle: ((LatLng, Float) -> Unit)?,
) {
    val mapView = NativeMapView(context)
    var onMapTap = onMapTap
    var onCameraIdle = onCameraIdle

    private var map: MapLibreMap? = null
    private var currentStyle: String? = null
    private var pendingMarkers: List<MapMarker> = emptyList()
    private var pendingPolylines: List<MapPolyline> = emptyList()
    private var pendingCircles: List<MapCircle> = emptyList()
    private var started = false
    private var updatingFromCompose = false

    init {
        mapView.onCreate(null)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            cameraState.projection = { point ->
                readyMap.projection.toScreenLocation(MlLatLng(point.lat, point.lng)).let { Offset(it.x.toFloat(), it.y.toFloat()) }
            }
            cameraState.projectionRevision++
            mapView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> cameraState.projectionRevision++ }
            readyMap.uiSettings.apply {
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
                isDoubleTapGesturesEnabled = true
            }

            readyMap.cameraPosition = cameraPosition(cameraState)

            readyMap.addOnCameraMoveStartedListener {
                if (!updatingFromCompose) cameraState.isUserInteracting = true
            }
            readyMap.addOnCameraMoveListener {
                cameraState.projectionRevision++
                if (updatingFromCompose) return@addOnCameraMoveListener
                val position = readyMap.cameraPosition
                position.target?.let { target ->
                    cameraState.centerLat = clampLatitude(target.latitude)
                    cameraState.centerLng = target.longitude
                }
                cameraState.zoom = position.zoom.toFloat().coerceIn(MIN_ZOOM, MAX_ZOOM)
            }
            readyMap.addOnCameraIdleListener {
                cameraState.isUserInteracting = false
                val position = readyMap.cameraPosition
                val target = position.target ?: return@addOnCameraIdleListener
                val point = LatLng(target.latitude, target.longitude)
                cameraState.centerLat = point.lat
                cameraState.centerLng = point.lng
                cameraState.zoom = position.zoom.toFloat().coerceIn(MIN_ZOOM, MAX_ZOOM)
                onCameraIdle?.invoke(point, cameraState.zoom)
            }
            readyMap.addOnMapClickListener { point ->
                onMapTap?.invoke(LatLng(point.latitude, point.longitude))
                true
            }

            setStyle(MapType.STANDARD)
        }
    }

    fun start() {
        if (started) return
        started = true
        mapView.onStart()
        mapView.onResume()
    }

    fun destroy() {
        if (started) {
            mapView.onPause()
            mapView.onStop()
            started = false
        }
        mapView.onDestroy()
        cameraState.projection = null
        cameraState.projectionRevision++
    }

    fun setStyle(type: MapType, darkMap: Boolean = false) {
        val style = if (darkMap) OPENFREEMAP_DARK else when (type) {
            MapType.DETAILED_STREETS -> OPENFREEMAP_LIBERTY
            else -> OPENFREEMAP_BRIGHT
        }
        if (currentStyle == style) return
        currentStyle = style
        map?.setStyle(style) {
            redrawOverlays()
        }
    }

    fun syncCameraFromCompose(camera: MapCameraState) {
        val map = map ?: return
        if (camera.isUserInteracting) return

        val current = map.cameraPosition
        val target = current.target
        val latDiff = target?.let { kotlin.math.abs(it.latitude - camera.centerLat) } ?: Double.MAX_VALUE
        val lngDiff = target?.let { kotlin.math.abs(it.longitude - camera.centerLng) } ?: Double.MAX_VALUE
        val zoomDiff = kotlin.math.abs(current.zoom - camera.zoom.toDouble())
        if (latDiff < 0.000001 && lngDiff < 0.000001 && zoomDiff < 0.001) return

        updatingFromCompose = true
        try {
            map.cameraPosition = cameraPosition(camera)
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
        val map = map ?: return
        if (map.style == null) return

        map.removeAnnotations()

        pendingCircles.forEach { circle ->
            map.addPolygon(
                PolygonOptions()
                    .addAll(circlePoints(circle.center, circle.radiusMeters))
                    .fillColor(circle.fillColor.toArgb())
                    .strokeColor(circle.strokeColor.toArgb()),
            )
        }

        pendingPolylines.forEach { line ->
            if (line.points.size < 2) return@forEach
            map.addPolyline(
                PolylineOptions()
                    .add(line.points.map { MlLatLng(it.lat, it.lng) }.toTypedArray())
                    .color(line.color.toArgb())
                    .width(line.widthPx),
            )
        }

        pendingMarkers.forEach { marker ->
            if (marker.radiusMeters > 0.0) {
                val haloColor = marker.color.copy(alpha = 0.14f).toArgb()
                map.addPolygon(
                    PolygonOptions()
                        .addAll(circlePoints(marker.point, marker.radiusMeters))
                        .fillColor(haloColor)
                        .strokeColor(marker.color.copy(alpha = 0.30f).toArgb()),
                )
            }
            map.addMarker(
                MarkerOptions()
                    .position(MlLatLng(marker.point.lat, marker.point.lng))
                    .icon(markerIcon(mapView.context, marker)),
            )
        }
    }

    private fun cameraPosition(camera: MapCameraState): CameraPosition =
        CameraPosition.Builder()
            .target(MlLatLng(camera.centerLat, camera.centerLng))
            .zoom(camera.zoom.toDouble())
            .build()
}

private fun markerIcon(context: Context, marker: MapMarker): Icon {
    val density = context.resources.displayMetrics.density
    val px = (34f * density).roundToInt().coerceAtLeast(24)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = px / 2f

    when (marker.style) {
        MarkerStyle.ACTIVE -> {
            paint.style = Paint.Style.FILL
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(center, center, px * 0.40f, paint)
            paint.color = marker.color.toArgb()
            canvas.drawCircle(center, center, px * 0.31f, paint)
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(center, center, px * 0.09f, paint)
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
            paint.style = Paint.Style.FILL
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(center, center, px * 0.28f, paint)
            paint.color = marker.color.toArgb()
            canvas.drawCircle(center, center, px * 0.21f, paint)
        }
    }

    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

private fun circlePoints(center: LatLng, radiusMeters: Double): List<MlLatLng> {
    if (radiusMeters <= 0.0) return emptyList()
    val earthRadius = 6_378_137.0
    val angular = radiusMeters / earthRadius
    val lat1 = Math.toRadians(center.lat)
    val lon1 = Math.toRadians(center.lng)

    return (0..48).map { step ->
        val bearing = 2.0 * PI * step / 48.0
        val lat2 = kotlin.math.asin(
            sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing),
        )
        val lon2 = lon1 + kotlin.math.atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )
        MlLatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }
}
