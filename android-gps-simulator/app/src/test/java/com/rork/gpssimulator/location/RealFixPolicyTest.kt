package com.rork.gpssimulator.location

import com.rork.gpssimulator.i18n.AppLanguage
import com.rork.gpssimulator.i18n.RealRecoveryStatus
import com.rork.gpssimulator.i18n.realRecoveryMessage
import org.junit.Assert.*
import org.junit.Test

class RealFixPolicyTest {
    private val second = 1_000_000_000L

    @Test fun threeStopCyclesRequireNewNonMockFixes() {
        repeat(3) { cycle ->
            val stop = (100 + cycle * 60) * second
            val epoch = cycle.toLong()
            assertFalse(RealFixPolicy.accepts(true, stop + second, stop + second, stop, epoch, epoch))
            assertFalse(RealFixPolicy.accepts(false, stop - second, stop + second, stop, epoch, epoch))
            assertTrue(RealFixPolicy.accepts(false, stop + second, stop + second, stop, epoch, epoch))
        }
    }

    @Test fun queuedCallbackFromPreviousRegistrationCannotCompleteRecovery() {
        assertFalse(RealFixPolicy.accepts(false, 101 * second, 102 * second, 100 * second, 1, 2))
        assertTrue(RealFixPolicy.accepts(false, 101 * second, 102 * second, 100 * second, 2, 2))
    }

    @Test fun foregroundReturnRejectsOldFixButAcceptsCurrentFix() {
        val stop = 100 * second
        assertFalse(RealFixPolicy.accepts(false, 101 * second, 180 * second, stop, 4, 4))
        assertTrue(RealFixPolicy.accepts(false, 180 * second, 180 * second, stop, 4, 4))
    }

    @Test fun twelveSecondTimeoutDoesNotInvalidateLaterFreshFix() {
        assertTrue(RealFixPolicy.accepts(false, 125 * second, 125 * second, 100 * second, 3, 3))
    }

    @Test fun missingOrFutureMonotonicTimestampsAreRejected() {
        assertFalse(RealFixPolicy.accepts(false, 0, 100 * second, 0, 1, 1))
        assertFalse(RealFixPolicy.accepts(false, 101 * second, 100 * second, 0, 1, 1))
    }

    @Test fun ageLimitAppliesEvenToNonMockCallbacks() {
        assertTrue(RealFixPolicy.accepts(false, second, 31 * second, 0, 1, 1))
        assertFalse(RealFixPolicy.accepts(false, second, 31 * second + 1, 0, 1, 1))
    }

    @Test fun everyRecoveryStateHasCopyForEverySupportedLanguage() {
        AppLanguage.entries.forEach { language ->
            val copy = RealRecoveryStatus.entries.map { realRecoveryMessage(language, it) }
            assertTrue(copy.all { it.isNotBlank() })
            assertEquals(RealRecoveryStatus.entries.size, copy.toSet().size)
        }
        assertTrue(realRecoveryMessage(AppLanguage.ARABIC, RealRecoveryStatus.ACQUIRING).contains("الحقيقي"))
    }
}
