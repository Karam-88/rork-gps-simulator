package com.rork.gpssimulator.data

import kotlinx.serialization.Serializable

@Serializable
enum class CrosshairShape { MINIMAL, PRECISION, CORNERS }

@Serializable
enum class CrosshairColor(val argb: Long) {
    WHITE(0xFF111827), LIGHT_BLUE(0xFF5DB8EE), GREEN(0xFF57BE65),
    ORANGE(0xFFEBA434), PURPLE(0xFF9E52EB), RED(0xFFDD4E49),
    YELLOW(0xFFF2CC45), PINK(0xFFEC70AC), CYAN(0xFF20C9CE),
    BLUE(0xFF326FE4), TEAL(0xFF009B86), LIME(0xFFB4D94B),
}

@Serializable
enum class MockMarkerColor(val argb: Long) {
    RED(0xFFDD4E49), GREEN(0xFF57BE65), PURPLE(0xFF9E52EB),
    ORANGE(0xFFEB7B2F), PINK(0xFFD74D96), YELLOW(0xFFE3B832),
    LIGHT_BLUE(0xFF5DB8EE), BLUE(0xFF326FE4), CYAN(0xFF20C9CE),
    TEAL(0xFF009B86), LIME(0xFFB4D94B), BLACK(0xFF222831),
}

/** All distances are dp. Stroke width never scales with the selected size. */
internal data class CrosshairGeometry(val size: Float, val thickness: Float) {
    val outer = (size - thickness) / 2f
    val ringRadius = outer * 0.70f
    val dotRadius = (size * 0.045f).coerceIn(0.9f, ringRadius - thickness / 2f - 0.5f)
    val armInner = maxOf(outer * 0.50f, dotRadius + thickness / 2f + 0.5f)
    val minimalArmInner = maxOf(outer * 0.36f, dotRadius + thickness / 2f + 0.5f)
    val corner = outer * 0.84f
    val cornerInner = corner * 0.50f
}
