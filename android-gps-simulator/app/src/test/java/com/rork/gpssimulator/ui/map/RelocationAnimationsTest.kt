package com.rork.gpssimulator.ui.map

import com.rork.gpssimulator.data.model.LatLng
import org.junit.Assert.*
import org.junit.Test

class RelocationAnimationsTest {
    private val a = LatLng(51.0, 7.0)
    private val b = LatLng(52.0, 8.0)
    private val c = LatLng(53.0, 9.0)

    @Test fun navigationHandoffIsConsumedOnlyOnce() {
        val events = RelocationAnimations { 100L }
        events.request(a, b)
        val id = events.pending.value!!.request.id
        assertEquals(b, events.take(id)!!.to)
        assertNull(events.take(id))
        assertNull(events.pending.value)
    }
    @Test fun latestTeleportWinsWithoutStaleConsumption() {
        val events = RelocationAnimations { 100L }
        events.request(a, b)
        val oldId = events.pending.value!!.request.id
        events.request(b, c)
        assertNull(events.take(oldId))
        val next = events.pending.value!!
        assertEquals(c, events.take(next.request.id)!!.to)
    }
    @Test fun reopeningDoesNotReplayAnExpiredNavigationRequest() {
        var time = 0L
        val events = RelocationAnimations { time }
        events.request(a, b)
        val id = events.pending.value!!.request.id
        time = 6_000_000_000L
        assertNull(events.take(id))
    }
    @Test fun duplicateAndInvalidCoordinatesDoNotGenerateAHop() {
        val events = RelocationAnimations()
        events.request(a, a)
        events.request(null, b)
        events.request(a, LatLng(100.0, 0.0))
        assertNull(events.pending.value)
    }
    @Test fun stopCancelsPendingTeleport() {
        val events = RelocationAnimations()
        events.request(a, b)
        events.clear()
        assertNull(events.pending.value)
    }
    @Test fun eachNewExplicitRelocationHasANewIdentity() {
        val events = RelocationAnimations()
        val ids = listOf(b, c, a).map { point ->
            events.request(LatLng(50.0, 6.0), point)
            val id = events.pending.value!!.request.id
            assertEquals(point, events.take(id)!!.to)
            id
        }
        assertEquals(3, ids.toSet().size)
    }
}
