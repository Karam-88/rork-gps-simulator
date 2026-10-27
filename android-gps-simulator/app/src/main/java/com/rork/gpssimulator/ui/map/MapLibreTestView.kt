package com.rork.gpssimulator.ui.map

import android.view.MotionEvent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.MapType
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as NativeLatLng
import org.maplibre.android.maps.MapView as NativeMapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.annotations.CircleOptions
import kotlin.math.abs

/**
 * Debug-only runtime renderer. No Google Maps API key or Play installation is needed.
 * The public OpenFreeMap style is for limited interactive testing, not bulk tile scraping.
 * Production builds continue to use the existing Google Maps renderer.
 */
@Composable
internal fun MapLibreTestView(
    camera: MapCameraState,
    mapType: MapType,
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
        LibreHolder(context, camera)
    }
    DisposableEffect(holder) {
        holder.start()
        onDispose { holder.destroy() }
    }
    SideEffect {
        holder.onTap = onMapTap
        holder.onIdle = onCameraIdle
    }
    LaunchedEffect(camera.centerLat, camera.centerLng, camera.zoom) {
        holder.syncCamera(camera)
    }
    LaunchedEffect(markers, polylines, circles, mapType) {
        holder.draw(markers, polylines, circles)
    }
    AndroidView(
        factory = {
            holder.view.apply {
                setOnTouchListener { v, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE,
                        MotionEvent.ACTION_POINTER_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
            }
        },
        modifier = modifier,
        update = { holder.syncCamera(camera) },
    )
}

private class LibreHolder(
    context: android.content.Context,
    private val camera: MapCameraState,
) {
    val view = NativeMapView(context)
    var onTap: ((LatLng) -> Unit)? = null
    var onIdle: ((LatLng, Float) -> Unit)? = null
    private var map: MapLibreMap? = null
    private var started = false
    private var programmaticMove = false
    private var markers: List<MapMarker> = emptyList()
    private var lines: List<MapPolyline> = emptyList()
    private var circles: List<MapCircle> = emptyList()

    init {
        view.onCreate(null)
        view.getMapAsync { ready ->
            map = ready
            ready.uiSettings.apply {
                isCompassEnabled = false
                isAttributionEnabled = true
                isLogoEnabled = true
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
            }
            ready.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) {
                syncCamera(camera)
                redraw()
            }
            ready.addOnMapClickListener { p ->
                onTap?.invoke(LatLng(p.latitude, p.longitude))
                true
            }
            ready.addOnCameraMoveStartedListener { reason ->
                if (!programmaticMove) camera.isUserInteracting =
                    reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
            }
            ready.addOnCameraMoveListener {
                if (!programmaticMove) {
                    val pos = ready.cameraPosition
                    pos.target?.let {
                        camera.snapTo(LatLng(it.latitude, it.longitude), pos.zoom.toFloat())
                    }
                }
            }
            ready.addOnCameraIdleListener {
                camera.isUserInteracting = false
                val pos = ready.cameraPosition
                pos.target?.let {
                    camera.snapTo(LatLng(it.latitude, it.longitude), pos.zoom.toFloat())
                    onIdle?.invoke(LatLng(it.latitude, it.longitude), pos.zoom.toFloat())
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        view.onStart()
        view.onResume()
    }
    fun destroy() {
        if (started) {
            view.onPause()
            view.onStop()
            started = false
        }
        view.onDestroy()
    }
    fun syncCamera(state: MapCameraState) {
        val m = map ?: return
        if (state.isUserInteracting) return
        val current = m.cameraPosition
        val target = current.target
        if (target != null &&
            abs(target.latitude - state.centerLat) < 0.000001 &&
            abs(target.longitude - state.centerLng) < 0.000001 &&
            abs(current.zoom - state.zoom) < 0.001) return
        programmaticMove = true
        try {
            m.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(NativeLatLng(state.centerLat, state.centerLng))
                    .zoom(state.zoom.toDouble())
                    .build()
            ))
        } finally {
            programmaticMove = false
        }
    }
    fun draw(
        markers: List<MapMarker>,
        lines: List<MapPolyline>,
        circles: List<MapCircle>,
    ) {
        this.markers = markers
        this.lines = lines
        this.circles = circles
        redraw()
    }
    private fun redraw() {
        val m = map ?: return
        if (m.style == null) return
        m.clear()
        circles.forEach { c ->
            m.addCircle(CircleOptions()
                .center(NativeLatLng(c.center.lat, c.center.lng))
                .radius(c.radiusMeters)
                .strokeColor(c.strokeColor.toArgb())
                .fillColor(c.fillColor.toArgb()))
        }
        lines.forEach { line ->
            if (line.points.size > 1) {
                m.addPolyline(PolylineOptions()
                    .addAll(line.points.map { NativeLatLng(it.lat, it.lng) })
                    .color(line.color.toArgb())
                    .width(line.widthPx))
            }
        }
        markers.forEach { marker ->
            m.addMarker(MarkerOptions()
                .position(NativeLatLng(marker.point.lat, marker.point.lng))
                .title(marker.label ?: "GPS"))
        }
    }
}
