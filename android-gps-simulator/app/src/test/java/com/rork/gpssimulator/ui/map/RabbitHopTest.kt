package com.rork.gpssimulator.ui.map

import com.rork.gpssimulator.data.model.LatLng
import org.junit.Assert.*
import org.junit.Test

class RabbitHopTest {
    private val a = LatLng(51.0, 7.0)
    private val b = LatLng(52.0, 8.0)
    @Test fun liftsBeforeTravelling() {
        val lift = rabbitHopFrame(a, b, 100f / 700)
        assertEquals(a, lift.point)
        assertTrue(lift.liftDp > 10f)
        assertEquals(36f, rabbitHopFrame(a, b, 200f / 700).liftDp, 0.001f)
    }
    @Test fun travelsWhileElevatedThenLandsExactly() {
        val middle = rabbitHopFrame(a, b, 350f / 700)
        assertEquals(51.5, middle.point.lat, 0.00001)
        assertEquals(36f, middle.liftDp, 0.001f)
        val end = rabbitHopFrame(a, b, 1f)
        assertEquals(b, end.point)
        assertEquals(0f, end.liftDp, 0f)
        assertEquals(1f, end.scaleX, 0.0001f)
        assertEquals(1f, end.scaleY, 0.0001f)
        assertEquals(end, rabbitHopFrame(a, b, 2f))
    }
    @Test fun interruptedHopStartsAtCurrentVisualPositionAndHeight() {
        val current = rabbitHopFrame(a, b, 0.5f)
        val next = rabbitHopFrame(current.point, LatLng(53.0, 9.0), 0f, current.liftDp)
        assertEquals(current.point, next.point)
        assertEquals(current.liftDp, next.liftDp, 0f)
    }
    @Test fun crossesAntimeridianByShortPath() {
        val middle = rabbitHopFrame(LatLng(0.0, 179.0), LatLng(0.0, -179.0), 0.5f)
        assertTrue(kotlin.math.abs(middle.point.lng) > 179.9)
    }
    @Test fun landingHasOnlyOneSmallRebound() {
        val impact = rabbitHopFrame(a, b, 630f / 700)
        val rebound = rabbitHopFrame(a, b, 665f / 700)
        assertEquals(b, impact.point)
        assertTrue(impact.liftDp < 0.01f)
        assertTrue(rebound.liftDp in 0f..4.01f)
    }
}
