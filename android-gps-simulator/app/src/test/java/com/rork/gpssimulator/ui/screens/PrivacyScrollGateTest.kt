package com.rork.gpssimulator.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression for early "end reached" during initial Android WebView layout. */
class PrivacyScrollGateTest {
    private fun canChoose(
        loaded: Boolean = true,
        scrolled: Boolean = false,
        position: Int = 0,
        viewport: Int = 700,
        total: Int = 3500,
        tolerance: Int = 12,
    ): Boolean = hasReachedPrivacyPolicyEnd(
        pageLoaded = loaded,
        hasActuallyScrolledDown = scrolled,
        scrollY = position,
        viewportExtentPx = viewport,
        scrollRangePx = total,
        tolerancePx = tolerance,
    )

    @Test fun initialPageLoadCannotUnlockEitherButton() {
        assertFalse(canChoose(loaded = false, scrolled = false, viewport = 700, total = 700))
        assertFalse(canChoose(loaded = true, scrolled = false, viewport = 700, total = 700))
        assertFalse(canChoose(loaded = true, scrolled = false, viewport = 700, total = 3500))
        assertFalse(canChoose(loaded = true, scrolled = false, position = 2800))
    }

    @Test fun incompleteOrTemporarilyShortWebviewRangeCannotUnlock() {
        assertFalse(canChoose(scrolled = true, position = 0, viewport = 700, total = 0))
        assertFalse(canChoose(scrolled = true, position = 10, viewport = 700, total = 700))
        assertFalse(canChoose(scrolled = true, position = 12, viewport = 700, total = 705))
        assertFalse(canChoose(scrolled = true, position = 100, viewport = 700, total = 900))
        assertFalse(canChoose(scrolled = true, position = 20, viewport = 0, total = 3500))
    }

    @Test fun evenAfterUserScrollIntermediatePositionsRemainLocked() {
        assertFalse(canChoose(scrolled = true, position = 10))
        assertFalse(canChoose(scrolled = true, position = 700))
        assertFalse(canChoose(scrolled = true, position = 1500))
        assertFalse(canChoose(scrolled = true, position = 2750))
    }

    @Test fun fullyLoadedAndActuallyScrolledToBottomUnlocksBothChoices() {
        assertTrue(canChoose(scrolled = true, position = 2800))
        assertTrue(canChoose(scrolled = true, position = 2788))
        assertFalse(canChoose(scrolled = true, position = 2787))
    }

    @Test fun accessibilityScrollingDoesNotDependOnTouchEvents() {
        // The WebView scroll-change callback observes accessible scrolling too.
        assertTrue(canChoose(scrolled = true, position = 2800, viewport = 700, total = 3500))
    }

    @Test fun negativeToleranceCannotForcePrematureUnlock() {
        assertFalse(canChoose(scrolled = true, position = 2799, tolerance = -500))
        assertTrue(canChoose(scrolled = true, position = 2800, tolerance = -500))
    }
}
