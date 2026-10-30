package com.rork.gpssimulator.ui.onboarding

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.rork.gpssimulator.ui.screens.FirstRunOnboardingScreen
import com.rork.gpssimulator.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Run with the system animation scale enabled; layout matrix separately covers reduced motion. */
class OnboardingAnimationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeAnimationFramesAndEarlyNavigation() {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppTheme { FirstRunOnboardingScreen(false) {} } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("onboarding-next").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("onboarding-skip").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1400)
        screenshot("welcome-1500ms")
        compose.mainClock.advanceTimeBy(1400)
        screenshot("welcome-hop")
        compose.mainClock.advanceTimeBy(2300)
        screenshot("welcome-complete")
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.mainClock.advanceTimeBy(2000)
        screenshot("route-drawing")
        compose.mainClock.advanceTimeBy(2500)
        screenshot("route-travel")
        compose.mainClock.advanceTimeBy(3000)
        screenshot("route-complete")
        // Returning must retain the completed one-shot sequence, not replay the bunny-hop.
        compose.onNodeWithTag("onboarding-pager").performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(1000)
        screenshot("welcome-return")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "onboarding-screenshots").apply { mkdirs() }
        compose.waitForIdle()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "motion-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
