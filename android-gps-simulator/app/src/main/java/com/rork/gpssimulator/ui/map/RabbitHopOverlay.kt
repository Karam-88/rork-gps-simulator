package com.rork.gpssimulator.ui.map

import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun RabbitHopOverlay(camera: MapCameraState, markers: List<MapMarker>, request: RabbitHopRequest?) {
    val context = LocalContext.current
    fun motionDisabled() = Settings.Global.getFloat(context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reducedMotion by remember { mutableStateOf(motionDisabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reducedMotion = motionDisabled() }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    var frame by remember { mutableStateOf<RabbitHopFrame?>(null) }
    LaunchedEffect(request, reducedMotion) {
        if (request == null || reducedMotion) { frame = null; return@LaunchedEffect }
        val origin = frame?.point ?: request.from
        val lift = frame?.liftDp ?: 0f
        if (origin == request.to && lift == 0f) { frame = null; return@LaunchedEffect }
        // Cancellation retains the displayed position/height for the next manual tap.
        animate(0f, 1f, animationSpec = tween(700, easing = LinearEasing)) { fraction, _ ->
            frame = rabbitHopFrame(origin, request.to, fraction, lift)
        }
        frame = null
    }
    val dotPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    Canvas(Modifier.fillMaxSize().clipToBounds()) {
        val animatedMarker = markers.firstOrNull { it.style == MarkerStyle.CANDIDATE && it.point == request?.to }
            ?: markers.firstOrNull { it.style == MarkerStyle.ACTIVE }
        markers.forEach { marker ->
            val current = if (marker === animatedMarker) frame else null
            val ground = camera.project(current?.point ?: marker.point) ?: return@forEach
            val lift = (current?.liftDp ?: 0f).dp.toPx()
            val center = ground - Offset(0f, lift)
            if (marker.style == MarkerStyle.ACTIVE || marker.style == MarkerStyle.REAL) {
                drawIntoCanvas {
                    LocationDot.draw(it.nativeCanvas, center.x, center.y, density, marker.color.toArgb(), dotPaint)
                }
                return@forEach
            }
            val radius = 11.dp.toPx()
            drawOval(Color.Black.copy(alpha = if (lift > 0f) 0.16f else 0.10f),
                topLeft = ground + Offset(-radius, radius * 0.65f), size = Size(radius * 2, radius * 0.55f))
            scale(current?.scaleX ?: 1f, current?.scaleY ?: 1f, pivot = center) {
                drawCircle(Color.White, radius, center)
                drawCircle(marker.color, radius * 0.78f, center)
                drawCircle(Color.White, radius * 0.23f, center)
            }
        }
    }
}

