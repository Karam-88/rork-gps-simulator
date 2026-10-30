package com.rork.gpssimulator.data

import com.rork.gpssimulator.data.model.ThemeMode
import com.rork.gpssimulator.i18n.AppLanguage
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.mapAppearanceTranslations
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MapAppearanceTest {
    // Same JSON configuration as AppRepository; check migration without resetting old preferences.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowSpecialFloatingPointValues = true }

    @Test fun existingPreferencesGainAppearanceDefaultsWithoutResettingOtherSettings() {
        val saved = json.decodeFromString<AppSettings>("""{"defaultZoom":11.0,"animateMap":false}""")
        assertEquals(11f, saved.defaultZoom, 0f)
        assertFalse(saved.animateMap)
        assertEquals(CrosshairShape.PRECISION, saved.crosshairShape)
        assertEquals(CrosshairColor.GREEN, saved.crosshairColor)
        assertEquals(MockMarkerColor.RED, saved.mockMarkerColor)
        assertEquals(60f, saved.crosshairSizeDp, 0f)
        assertEquals(3f, saved.crosshairThicknessDp, 0f)
        assertEquals(0.9f, saved.crosshairOpacity, 0f)
    }

    @Test fun appearanceSurvivesSerializationAndThemeChanges() {
        for (shape in CrosshairShape.entries) for (color in CrosshairColor.entries)
            for (mockColor in MockMarkerColor.entries) {
                val settings = AppSettings(crosshairShape = shape, crosshairColor = color,
                    crosshairSizeDp = 80f, crosshairThicknessDp = 6f,
                    crosshairOpacity = 0.2f, mockMarkerColor = mockColor)
                val restored = json.decodeFromString<AppSettings>(json.encodeToString(settings))
                assertEquals(settings, restored)
                ThemeMode.entries.forEach { theme ->
                    val themed = restored.copy(theme = theme)
                    assertEquals(themed, json.decodeFromString<AppSettings>(json.encodeToString(themed)))
                }
            }
    }

    @Test fun strokesNeverClipOrCoverTheCenterDotAtAnySupportedSizeAndThickness() {
        for (size in 20..80) for (halfThickness in 2..12) {
            val g = CrosshairGeometry(size.toFloat(), halfThickness / 2f)
            assertEquals(halfThickness / 2f, g.thickness, 0f)
            assertTrue(g.outer + g.thickness / 2f <= size / 2f)
            assertTrue(g.ringRadius + g.thickness / 2f <= size / 2f)
            assertTrue(g.corner + g.thickness / 2f <= size / 2f)
            assertTrue(g.dotRadius > 0)
            assertTrue(g.dotRadius < g.ringRadius - g.thickness / 2f)
            assertTrue(g.armInner - g.thickness / 2f > g.dotRadius)
            assertTrue(g.armInner < g.outer)
            assertTrue(g.minimalArmInner - g.thickness / 2f > g.dotRadius)
            assertTrue(g.minimalArmInner < g.outer)
        }
    }

    @Test fun sizeDoesNotScaleThickness() {
        for (thickness in 1..6) {
            assertEquals(CrosshairGeometry(20f, thickness.toFloat()).thickness,
                CrosshairGeometry(80f, thickness.toFloat()).thickness, 0f)
        }
    }

    @Test fun appearanceTranslationsCoverEveryLanguageWithoutFallback() {
        val keys = mapAppearanceTranslations.getValue(AppLanguage.ENGLISH).keys
        assertTrue(keys.containsAll(listOf(K.crosshair_thickness, K.mock_marker_color)))
        AppLanguage.entries.forEach { language ->
            val table = mapAppearanceTranslations.getValue(language)
            assertEquals(keys, table.keys)
            assertTrue(table.values.all { it.isNotBlank() })
        }
    }
}
