package com.rork.gpssimulator.ui.map

import com.rork.gpssimulator.data.model.LatLng
import org.junit.Assert.*
import org.junit.Test

class RabbitHopTest {
    private val a = LatLng(51.0, 7.0)
    private val b = LatLng(52.0, 8.0)
    @Test fun risesAndTravelsTogetherFromFirstFrame() {
        val early = rabbitHopFrame(a, b, 100f / 700)
        assertTrue(early.point.lat > a.lat)
        assertTrue(early.point.lat < b.lat)
        assertTrue(early.liftDp > 0f)
        val quarter = rabbitHopFrame(a, b, 0.25f)
        val middle = rabbitHopFrame(a, b, 0.5f)
        assertTrue(quarter.point.lat > early.point.lat)
        assertTrue(quarter.liftDp > early.liftDp)
        assertEquals(36f, middle.liftDp, 0.001f)
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
    @Test fun descendsContinuouslyWithoutVerticalLandingOrRebound() {
        val halfway = rabbitHopFrame(a, b, 0.5f)
        val nearEnd = rabbitHopFrame(a, b, 0.9f)
        val later = rabbitHopFrame(a, b, 0.95f)
        val end = rabbitHopFrame(a, b, 1f)
        assertTrue(halfway.liftDp > nearEnd.liftDp)
        assertTrue(nearEnd.liftDp > later.liftDp)
        assertTrue(later.liftDp > end.liftDp)
        assertTrue(nearEnd.point.lat < later.point.lat)
        assertTrue(later.point.lat < end.point.lat)
        assertEquals(b, end.point)
        assertEquals(0f, end.liftDp, 0f)
    }
}
