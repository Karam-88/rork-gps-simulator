package com.rork.gpssimulator.location

import org.junit.Assert.*
import org.junit.Test

class MockFusedPumpTest {
    private class Backend {
        val calls = mutableListOf<String>()
        val callbacks = ArrayDeque<(Exception?) -> Unit>()
        val failures = mutableListOf<Exception>()
        val pump = MockFusedPump<Int>(
            setMode = { mode, done -> calls.add("mode:$mode"); callbacks.addLast(done) },
            send = { fix, done -> calls.add("fix:$fix"); callbacks.addLast(done) },
            onFailure = { failures.add(it) },
        )
        fun complete(error: Exception? = null) = callbacks.removeFirst()(error)
    }

    @Test fun enablesBeforeFirstFixAndCoalescesSlowUpdates() {
        val b = Backend()
        b.pump.submit(1)
        b.pump.submit(2)
        assertEquals(listOf("mode:true"), b.calls)
        b.complete()
        assertEquals("fix:2", b.calls.last())
        b.pump.submit(3)
        b.pump.submit(4)
        b.complete()
        assertEquals("fix:4", b.calls.last())
        b.complete()
        assertEquals(0, b.callbacks.size)
    }

    @Test fun stopDuringEnableWaitsThenDisablesWithoutSendingStaleFix() {
        val b = Backend()
        var stopped: Boolean? = null
        b.pump.submit(1)
        b.pump.stop { stopped = it }
        assertNull(stopped)
        b.complete()
        assertEquals(listOf("mode:true", "mode:false"), b.calls)
        assertNull(stopped)
        b.complete()
        assertEquals(true, stopped)
    }

    @Test fun stopDuringUpdateDropsPendingFixAndWaitsForDisable() {
        val b = Backend()
        b.pump.submit(1)
        b.complete()
        b.pump.submit(2)
        var stopped = false
        b.pump.stop { stopped = it }
        b.complete()
        assertFalse(stopped)
        assertEquals(listOf("mode:true", "fix:1", "mode:false"), b.calls)
        b.complete()
        assertTrue(stopped)
        assertEquals(0, b.callbacks.size)
    }

    @Test fun failedEnableDoesNotSendFixAndStillCleansUp() {
        val b = Backend()
        b.pump.submit(1)
        b.complete(SecurityException())
        assertEquals(1, b.failures.size)
        b.pump.stop {}
        assertEquals(listOf("mode:true", "mode:false"), b.calls)
        b.complete()
    }

    @Test fun failedUpdateReportsFailureWithoutBusyRetryLoop() {
        val b = Backend()
        b.pump.submit(1)
        b.complete()
        b.pump.submit(2)
        b.complete(IllegalStateException())
        assertEquals(1, b.failures.size)
        assertEquals(0, b.callbacks.size)
        assertEquals(2, b.calls.size)
        b.pump.stop {}
        b.complete()
    }

    @Test fun failedCleanupIsNotReportedAsSuccessAndCanBeRetried() {
        val b = Backend()
        b.pump.submit(1)
        b.complete()
        b.complete()
        var clean = true
        b.pump.stop { clean = it }
        b.complete(IllegalStateException())
        assertFalse(clean)
        b.pump.stop { clean = it }
        b.complete()
        assertTrue(clean)
    }

    @Test fun cannotStartNewSessionUntilOldCleanupAcknowledged() {
        val b = Backend()
        b.pump.submit(1)
        b.pump.stop {}
        assertThrows(IllegalStateException::class.java) { b.pump.submit(2) }
        b.complete()
        b.complete()
        b.pump.submit(3)
        assertEquals("mode:true", b.calls.last())
        b.complete()
        assertEquals("fix:3", b.calls.last())
    }

    @Test fun repeatedStopsShareOneDisableOperation() {
        val b = Backend()
        b.pump.submit(1)
        b.complete()
        b.complete()
        var completed = 0
        b.pump.stop { if (it) completed++ }
        b.pump.stop { if (it) completed++ }
        assertEquals(1, b.calls.count { it == "mode:false" })
        b.complete()
        assertEquals(2, completed)
    }

    @Test fun fifteenMinutesOfStaticOrDestinationHoldStillSendsEveryTick() {
        val b = Backend()
        b.pump.submit(42)
        b.complete()
        b.complete()
        // Virtual ticks only: this does NOT assert real-device/Maps endurance.
        repeat(900) {
            b.pump.submit(42)
            b.complete()
        }
        assertEquals(901, b.calls.count { it == "fix:42" })
        assertEquals(1, b.calls.count { it == "mode:true" })
        assertFalse(b.calls.contains("mode:false"))
        b.pump.stop {}
        b.complete()
    }

    @Test fun synchronousBackendExceptionIsReportedAndCleanedUp() {
        var failures = 0
        val modes = mutableListOf<Boolean>()
        val pump = MockFusedPump<Int>(
            setMode = { mode, done ->
                modes.add(mode)
                if (mode) throw SecurityException() else done(null)
            },
            send = { _, _ -> fail("No fix before enable") },
            onFailure = { failures++ },
        )
        pump.submit(1)
        assertEquals(1, failures)
        var clean = false
        pump.stop { clean = it }
        assertTrue(clean)
        assertEquals(listOf(true, false), modes)
    }
}
