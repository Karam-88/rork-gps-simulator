package com.rork.gpssimulator.ui.onboarding

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.rork.gpssimulator.i18n.OnboardingCopy
import kotlin.math.*

private val Electric = Color(0xFF00B9FF)
private val Red = Color(0xFFFF4B67)

/** All artwork is native vector geometry inside one 360 x 300 design space, uniformly fitted.
 * Text is real Compose text outside the drawing, and respects system font scaling.
 * No map SDK, network, bitmap crop, sensor, permission or simulation dependency. */
@Composable
fun OnboardingScene(page: Int, progress: Float, pulse: Boolean, copy: OnboardingCopy, modifier: Modifier = Modifier) {
    val phase = if (pulse) {
        val transition = rememberInfiniteTransition(label = "gps pulse")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "pulse radius").value
    } else 0f
    val numberPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; textSize = 12f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    } }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.weight(1f).fillMaxWidth().clearAndSetSemantics {}) {
            val fit = min(size.width / 360f, size.height / 300f)
            translate((size.width - 360f * fit) / 2f, (size.height - 300f * fit) / 2f) {
                scale(fit, fit, Offset.Zero) {
                    val enter = stage(progress, .12f, .32f)
                    val zoom = if (page == 1) .9f + .1f * enter else 1f
                    scale(zoom, zoom, Offset(180f, 150f)) {
                        translate(top = (1f - enter) * 18f) {
                            drawPhoneMap(enter)
                            if (page == 0) {
                                val blue = stage(progress, .28f, .4f)
                                pin(Offset(105f, 208f), Electric, blue, phase)
                                val appear = stage(progress, .4f, .48f)
                                val hop = stage(progress, .48f, .63f)
                                val point = Offset(195f + 53f * hop, 138f - 34f * hop - sin(hop * PI).toFloat() * 28f)
                                // Exactly one hop: the timeline runs once and survives page navigation.
                                pin(point, Red, appear, 0f)
                                if (hop > 0f) {
                                    val trail = Path().apply { moveTo(105f, 208f); cubicTo(100f, 105f, 167f, 86f, 248f, 104f) }
                                    drawPath(trail, Electric.copy(alpha = .35f * appear), style = Stroke(1.5f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f))))
                                }
                            } else {
                                val route = illustrationRoute()
                                val measure = PathMeasure().apply { setPath(route, false) }
                                val segment = Path()
                                measure.getSegment(0f, measure.length * stage(progress, .2f, .48f), segment)
                                drawPath(segment, Electric.copy(alpha = .12f), style = Stroke(15f, cap = StrokeCap.Round))
                                drawPath(segment, Electric.copy(alpha = .3f), style = Stroke(7f, cap = StrokeCap.Round))
                                drawPath(segment, Color(0xFF74EEFF), style = Stroke(2.8f, cap = StrokeCap.Round))
                                pin(measure.getPosition(0f), Electric, stage(progress, .12f, .24f), phase)
                                listOf(.26f, .5f, .72f).forEachIndexed { i, fraction ->
                                    val alpha = stage(progress, .28f + i * .06f, .34f + i * .06f)
                                    val point = measure.getPosition(measure.length * fraction)
                                    drawCircle(Electric.copy(alpha = alpha * .2f), 15f, point)
                                    drawCircle(Color(0xFF0073D9).copy(alpha = alpha), 10f, point)
                                    drawCircle(Color(0xFF9AF5FF).copy(alpha = alpha), 10f, point, style = Stroke(1f))
                                    numberPaint.alpha = (alpha * 255).toInt()
                                    drawContext.canvas.nativeCanvas.drawText("${i + 1}", point.x, point.y + 4f, numberPaint)
                                }
                                pin(measure.getPosition(measure.length), Red, stage(progress, .43f, .52f), 0f)
                                val travel = stage(progress, .58f, .94f)
                                if (progress >= .58f) {
                                    val point = measure.getPosition(measure.length * travel)
                                    drawCircle(Color.White.copy(alpha = .15f), 12f, point)
                                    drawCircle(Electric, 6f, point)
                                    drawCircle(Color.White, 6f, point, style = Stroke(2f))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (page == 0) {
            Row(Modifier.fillMaxWidth().alpha(stage(progress, .6f, .7f)).clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0B203A)).border(1.dp, Color(0xFF2679AB), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocationOn, null, tint = Red, modifier = Modifier.size(20.dp))
                Text(copy.selected, color = Color(0xFFC4DCFA), fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("Central Park", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
        } else {
            Row(Modifier.alpha(stage(progress, .94f, 1f)), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF40E8BA), modifier = Modifier.size(18.dp))
                Text(copy.complete, color = Color(0xFF93F5DA), fontSize = 12.sp)
            }
        }
    }
}

private fun illustrationRoute() = Path().apply {
    moveTo(103f, 221f)
    cubicTo(109f, 200f, 145f, 207f, 153f, 180f)
    cubicTo(160f, 154f, 207f, 172f, 213f, 144f)
    cubicTo(219f, 120f, 243f, 134f, 250f, 104f)
}

private fun DrawScope.drawPhoneMap(alpha: Float) {
    fun polygon(vararg p: Pair<Float, Float>) = Path().apply {
        moveTo(p[0].first, p[0].second); p.drop(1).forEach { lineTo(it.first, it.second) }; close()
    }
    drawCircle(Brush.radialGradient(listOf(Electric.copy(alpha = .12f * alpha), Color.Transparent),
        center = Offset(180f, 150f), radius = 148f), 148f, Offset(180f, 150f))
    val outer = Path().apply {
        moveTo(84f, 30f); lineTo(248f, 19f)
        quadraticBezierTo(263f, 18f, 268f, 35f)
        lineTo(325f, 242f); quadraticBezierTo(331f, 258f, 315f, 260f)
        lineTo(51f, 277f); quadraticBezierTo(32f, 279f, 37f, 261f)
        lineTo(67f, 49f); quadraticBezierTo(70f, 31f, 84f, 30f); close()
    }
    drawPath(outer, Electric.copy(alpha = .045f * alpha), style = Stroke(18f))
    drawPath(outer, Color(0xFF3277BA).copy(alpha = alpha), style = Stroke(6f))
    drawPath(outer, Color(0xFF020915).copy(alpha = alpha))
    val screen = Path().apply {
        moveTo(88f, 42f); lineTo(244f, 32f)
        quadraticBezierTo(253f, 31f, 257f, 44f)
        lineTo(312f, 236f); quadraticBezierTo(315f, 247f, 304f, 248f)
        lineTo(61f, 265f); quadraticBezierTo(47f, 266f, 50f, 252f)
        lineTo(78f, 55f); quadraticBezierTo(80f, 43f, 88f, 42f); close()
    }
    clipPath(screen) {
        drawRect(Brush.linearGradient(listOf(Color(0xFF12394D).copy(alpha = alpha), Color(0xFF09213D).copy(alpha = alpha))))
        val river = Path().apply { moveTo(70f, 290f); cubicTo(190f, 170f, 60f, 140f, 170f, 20f) }
        drawPath(river, Color(0xFF0067AA).copy(alpha = alpha), style = Stroke(25f))
        // Deterministic city blocks provide depth without a large raster asset.
        for (row in 0..8) for (col in 0..8) {
            val x = 40f + col * 32f + (row % 2) * 8f
            val y = 35f + row * 29f
            val green = (col + row * 3) % 5 == 0
            val h = 4f + (col * 7 + row) % 8
            val top = polygon(x to y, x + 20f to y - 2f, x + 24f to y + 12f, x + 3f to y + 14f)
            val side = polygon(x + 3f to y + 14f, x + 24f to y + 12f, x + 24f to y + 12f + h, x + 3f to y + 14f + h)
            drawPath(side, Color(0xFF07152B).copy(alpha = alpha))
            drawPath(top, (if (green) Color(0xFF17665E) else Color(0xFF345574)).copy(alpha = alpha))
        }
        for (i in 0..7) {
            drawLine(Color(0xFF6384A3).copy(alpha = .28f * alpha), Offset(20f, 60f + i * 29f), Offset(340f, 39f + i * 29f), 3f)
        }
        drawPath(illustrationRoute(), Color(0xFF7393AC).copy(alpha = .48f * alpha), style = Stroke(9f, cap = StrokeCap.Round))
        drawPath(illustrationRoute(), Color(0xFF182F47).copy(alpha = alpha), style = Stroke(5f, cap = StrokeCap.Round))
    }
    drawPath(screen, Color(0xFF9BDFFF).copy(alpha = .6f * alpha), style = Stroke(1f))
    drawRoundRect(Color(0xFF020915).copy(alpha = alpha), Offset(137f, 31f), Size(69f, 10f), CornerRadius(5f))
    drawLine(Color(0xFF9AC8EF).copy(alpha = .7f * alpha), Offset(144f, 266f), Offset(207f, 262f), 3f, StrokeCap.Round)
}

private fun DrawScope.pin(point: Offset, color: Color, alpha: Float, phase: Float) {
    if (alpha <= 0f) return
    drawOval(color.copy(alpha = .12f * alpha), point - Offset(22f, 7f), Size(44f, 14f))
    drawOval(color.copy(alpha = .65f * alpha), point - Offset(15f, 5f), Size(30f, 10f), style = Stroke(1.5f))
    if (phase > 0f) {
        val radius = 15f + phase * 18f
        drawOval(color.copy(alpha = .45f * (1f - phase) * alpha), point - Offset(radius, radius / 3f),
            Size(radius * 2f, radius * 2f / 3f), style = Stroke(1.4f))
    }
    val shape = Path().apply {
        moveTo(point.x, point.y)
        cubicTo(point.x - 8f, point.y - 12f, point.x - 15f, point.y - 20f, point.x - 15f, point.y - 28f)
        cubicTo(point.x - 15f, point.y - 48f, point.x + 15f, point.y - 48f, point.x + 15f, point.y - 28f)
        cubicTo(point.x + 15f, point.y - 20f, point.x + 8f, point.y - 12f, point.x, point.y)
        close()
    }
    drawPath(shape, color.copy(alpha = .12f * alpha), style = Stroke(10f))
    drawPath(shape, color.copy(alpha = alpha))
    drawPath(shape, Color.White.copy(alpha = .9f * alpha), style = Stroke(1.3f))
    drawCircle(Color.White.copy(alpha = alpha), 6f, point - Offset(0f, 29f))
}
