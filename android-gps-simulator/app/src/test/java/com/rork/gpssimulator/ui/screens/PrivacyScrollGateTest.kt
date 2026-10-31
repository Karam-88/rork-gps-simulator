package com.rork.gpssimulator.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Consent and rejection must stay disabled until the full WebView policy is shown. */
class PrivacyScrollGateTest {
    @Test fun loadingNeverUnlocksEvenIfScrollAppearsAtBottom() {
        assertFalse(hasReachedPrivacyPolicyEnd(false, 1300, 700, 2000, 20))
    }

    @Test fun topAndIntermediateScrollAreNotEnough() {
        assertFalse(hasReachedPrivacyPolicyEnd(true, 0, 700, 2000, 20))
        assertFalse(hasReachedPrivacyPolicyEnd(true, 700, 700, 2000, 20))
        assertFalse(hasReachedPrivacyPolicyEnd(true, 1270, 700, 2000, 20))
    }

    @Test fun endOfPolicyUnlocksBothChoices() {
        assertTrue(hasReachedPrivacyPolicyEnd(true, 1300, 700, 2000, 20))
        assertTrue(hasReachedPrivacyPolicyEnd(true, 1280, 700, 2000, 20))
    }

    @Test fun noScrollableContentDoesNotTrapAccessibilityUsers() {
        assertTrue(hasReachedPrivacyPolicyEnd(true, 0, 1200, 1100, 20))
        assertFalse(hasReachedPrivacyPolicyEnd(false, 0, 1200, 1100, 20))
    }

    @Test fun cannotUnlockBeforeViewportOrContentIsMeasured() {
        assertFalse(hasReachedPrivacyPolicyEnd(true, 0, 0, 0, 20))
        assertFalse(hasReachedPrivacyPolicyEnd(true, 0, 700, 0, 20))
        assertFalse(hasReachedPrivacyPolicyEnd(true, 0, 0, 2000, 20))
    }
}
