package com.rork.gpssimulator.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/** One geometry for the real dot, mock dot, bitmap fallback and Settings preview. */
internal object LocationDot {
    const val DIAMETER_DP = 48f
    const val BORDER_RADIUS_DP = 34f * 0.28f
    const val FILL_RADIUS_DP = 34f * 0.21f

    fun draw(canvas: Canvas, x: Float, y: Float, density: Float, color: Int, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = 36
        canvas.drawCircle(x, y, DIAMETER_DP / 2f * density, paint)
        paint.color = android.graphics.Color.WHITE
        paint.alpha = 255
        canvas.drawCircle(x, y, BORDER_RADIUS_DP * density, paint)
        paint.color = color
        canvas.drawCircle(x, y, FILL_RADIUS_DP * density, paint)
    }
}

@Composable
internal fun LocationDotPreview(color: Color, modifier: Modifier = Modifier) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    ComposeCanvas(modifier.size(LocationDot.DIAMETER_DP.dp)) {
        drawIntoCanvas {
            LocationDot.draw(it.nativeCanvas, center.x, center.y, density, color.toArgb(), paint)
        }
    }
}
