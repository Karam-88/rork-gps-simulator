package com.rork.gpssimulator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compact blue target reticle matching the location-select action icon.
 * Always pinned to the exact map centre; map taps never move the reticle.
 */
@Composable
fun Crosshair(
    color: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    crosshairSize: Dp = 40.dp,
    opacity: Float = 1f,
    style: Int = 4,
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 1f,
        animationSpec = tween(220),
        label = "crosshairScale",
    )

    Canvas(modifier = modifier.size(crosshairSize)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val unit = size.minDimension / 2f
        val ringRadius = unit * 0.54f * scale
        val tickInner = unit * 0.46f * scale
        val tickOuter = unit * 0.77f * scale
        val visibleColor = color.copy(alpha = opacity.coerceIn(0f, 1f))
        val halo = Color.White.copy(alpha = 0.9f * opacity.coerceIn(0f, 1f))
        val ringWidth = 5.dp.toPx()
        val haloWidth = 7.dp.toPx()

        // Short cardinal ticks overlap the ring, like the blue target in the reference.
        fun tick(dx: Float, dy: Float) {
            val start = Offset(center.x + dx * tickInner, center.y + dy * tickInner)
            val end = Offset(center.x + dx * tickOuter, center.y + dy * tickOuter)
            drawLine(halo, start, end, strokeWidth = haloWidth, cap = StrokeCap.Square)
            drawLine(visibleColor, start, end, strokeWidth = ringWidth, cap = StrokeCap.Square)
        }

        when (style) {
            1 -> {
                // Minimal crosshair: four short strokes and a small centre dot.
                tick(1f, 0f)
                tick(-1f, 0f)
                tick(0f, 1f)
                tick(0f, -1f)
            }
            2 -> {
                // Small circular target without external arms.
                drawCircle(halo, radius = ringRadius, center = center, style = Stroke(width = haloWidth))
                drawCircle(visibleColor, radius = ringRadius, center = center, style = Stroke(width = ringWidth))
            }
            else -> {
                // Four-arm reticle (style 4), compact and fixed to the map centre.
                tick(1f, 0f)
                tick(-1f, 0f)
                tick(0f, 1f)
                tick(0f, -1f)
            }
        }

        drawCircle(halo, radius = unit * 0.18f * scale, center = center)
        drawCircle(visibleColor, radius = unit * 0.12f * scale, center = center)
    }
}
