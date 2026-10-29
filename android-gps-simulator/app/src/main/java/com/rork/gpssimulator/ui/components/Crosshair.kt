package com.rork.gpssimulator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.data.CrosshairGeometry
import com.rork.gpssimulator.data.CrosshairShape

/** Shared by Settings and the screen-space map overlay. No camera-dependent scaling. */
@Composable
fun Crosshair(
    color: Color = Color.White,
    modifier: Modifier = Modifier,
    crosshairSize: Dp = 40.dp,
    thickness: Dp = 3.dp,
    opacity: Float = 0.9f,
    shape: CrosshairShape = CrosshairShape.PRECISION,
) {
    val dimension = crosshairSize.value.coerceIn(20f, 80f)
    val geometry = CrosshairGeometry(dimension, thickness.value.coerceIn(1f, 6f))
    Canvas(modifier.size(dimension.dp).graphicsLayer {
        // Composite the whole symbol once, so overlapping ring/arms have equal opacity.
        alpha = opacity.coerceIn(0.2f, 1f)
        compositingStrategy = CompositingStrategy.Offscreen
    }) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val stroke = geometry.thickness.dp.toPx()
        val outer = geometry.outer.dp.toPx()
        val inner = geometry.armInner.dp.toPx()
        fun arm(dx: Float, dy: Float) {
            drawLine(color, center + Offset(dx * inner, dy * inner),
                center + Offset(dx * outer, dy * outer), stroke, StrokeCap.Round)
        }
        when (shape) {
            CrosshairShape.MINIMAL, CrosshairShape.PRECISION -> {
                if (shape == CrosshairShape.PRECISION) {
                    drawCircle(color, geometry.ringRadius.dp.toPx(), center, style = Stroke(stroke))
                }
                arm(1f, 0f); arm(-1f, 0f); arm(0f, 1f); arm(0f, -1f)
            }
            CrosshairShape.CORNERS -> {
                val corner = geometry.corner.dp.toPx()
                val end = geometry.cornerInner.dp.toPx()
                for (x in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) {
                    val bracket = Path().apply {
                        moveTo(center.x + x * end, center.y + y * corner)
                        lineTo(center.x + x * corner, center.y + y * corner)
                        lineTo(center.x + x * corner, center.y + y * end)
                    }
                    drawPath(bracket, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Miter))
                }
            }
        }
        drawCircle(color, geometry.dotRadius.dp.toPx(), center)
    }
}
