package com.rork.gpssimulator.ui.onboarding

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.rork.gpssimulator.i18n.*
import com.rork.gpssimulator.ui.screens.FirstRunOnboardingScreen
import com.rork.gpssimulator.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class OnboardingLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun responsiveScreensAndNavigation() {
        val args = InstrumentationRegistry.getArguments()
        val arabic = args.getString("onboardingLanguage", "en") == "ar"
        val copy = onboardingCopy(if (arabic) AppLanguage.ARABIC else AppLanguage.ENGLISH)
        var finished = 0
        compose.setContent {
            CompositionLocalProvider(LocalStrings provides Strings(if (arabic) AppLanguage.ARABIC else AppLanguage.ENGLISH)) {
                AppTheme { FirstRunOnboardingScreen(arabic) { finished++ } }
            }
        }
        compose.onNodeWithTag("onboarding-next").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("onboarding-skip").assertIsDisplayed()
        compose.onNodeWithText(copy.welcome).assertExists()
        verifyBounds(0)
        screenshot("welcome")
        // All feature text remains reachable with large system fonts; no ellipsized raster text.
        compose.onNodeWithTag("onboarding-feature-0-2").performScrollTo().assertIsDisplayed()
        screenshot("welcome-features")
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithText(copy.routes).assertExists()
        verifyBounds(1)
        screenshot("routes")
        compose.onNodeWithTag("onboarding-feature-1-2").performScrollTo().assertIsDisplayed()
        screenshot("routes-setup")
        // Horizontal swipe works in both layout directions; no third page.
        compose.onNodeWithTag("onboarding-pager").performTouchInput { if (arabic) swipeLeft() else swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText(copy.next).assertExists()
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.runOnIdle { assertEquals(1, finished) }
    }

    @Test fun skipIsAvailableImmediately() {
        var finished = 0
        compose.setContent { AppTheme { FirstRunOnboardingScreen(false) { finished++ } } }
        compose.onNodeWithTag("onboarding-skip").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, finished) }
    }

    private fun verifyBounds(page: Int) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val cta = compose.onNodeWithTag("onboarding-next").fetchSemanticsNode().boundsInRoot
        assertTrue("CTA is inside the screen", cta.bottom <= root.bottom && cta.left >= root.left && cta.right <= root.right)
        val fontScale = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale
        if (fontScale <= 1.05f) {
            val lastCard = compose.onNodeWithTag("onboarding-feature-$page-2").getUnclippedBoundsInRoot()
            val pager = compose.onNodeWithTag("onboarding-pager").getUnclippedBoundsInRoot()
            assertTrue("All feature cards fit without scrolling at normal font size", lastCard.bottom.value <= pager.bottom.value + 1f)
        }
        for (tag in listOf("onboarding-header-$page", "onboarding-scene-$page", "onboarding-features-$page")) {
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("No horizontal overflow: $tag", bounds.left >= root.left && bounds.right <= root.right)
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "onboarding-screenshots").apply { mkdirs() }
        val prefix = InstrumentationRegistry.getArguments().getString("onboardingCase", "default")
        compose.waitForIdle()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$prefix-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
