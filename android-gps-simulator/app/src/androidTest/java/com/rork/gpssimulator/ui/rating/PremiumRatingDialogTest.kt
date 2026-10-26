package com.rork.gpssimulator.ui.rating

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.rork.gpssimulator.i18n.AppLanguage
import com.rork.gpssimulator.i18n.reviewPromptCopy
import com.rork.gpssimulator.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PremiumRatingDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun localizedThemesAndButtons() {
        val language = mutableStateOf(AppLanguage.GERMAN)
        val dark = mutableStateOf(false)
        var rated = 0
        var later = 0
        compose.setContent {
            AppTheme(dark.value) {
                PremiumRatingDialog(language.value, dark.value, { rated++ }, { later++ })
            }
        }
        for (locale in listOf(AppLanguage.GERMAN, AppLanguage.ARABIC, AppLanguage.ENGLISH)) {
            for (night in listOf(false, true)) {
                compose.runOnIdle { language.value = locale; dark.value = night }
                val c = reviewPromptCopy(locale)
                compose.onNodeWithText(c.title).assertIsDisplayed()
                compose.onNodeWithText(c.noAds).assertIsDisplayed()
                compose.onNodeWithText(c.free).assertIsDisplayed()
                compose.onNodeWithText(c.rate).assertIsDisplayed().performClick()
                compose.onNodeWithText(c.later).assertIsDisplayed().performClick()
                screenshot("rating-${locale.languageCode}-${if (night) "dark" else "light"}")
            }
        }
        assertEquals(6, rated)
        assertEquals(6, later)
    }

    @Test fun largeFontsRemainScrollable() {
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { fontScale = 2f }
            CompositionLocalProvider(LocalConfiguration provides config,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    androidx.compose.ui.platform.LocalDensity.current.density, 2f)) {
                AppTheme { PremiumRatingDialog(AppLanguage.GERMAN, false, {}, {}) }
            }
        }
        val c = reviewPromptCopy(AppLanguage.GERMAN)
        compose.onNodeWithText(c.rate).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(c.later).performScrollTo().assertIsDisplayed().performClick()
        screenshot("rating-large-font")
    }

    @Test fun ratingPolicyPersistsAndDoesNotRepeat() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("gps_simulator_rating_prompt", 0)
        val legacy = context.getSharedPreferences("gps_simulator_rating", 0)
        prefs.edit().clear().commit()
        legacy.edit().clear().commit()
        val controller = RatingPromptController(context)
        controller.completedSuccessfully(19_999)
        assertFalse(controller.pending.value)
        repeat(2) { controller.completedSuccessfully(20_000) }
        assertFalse(controller.pending.value)
        controller.completedSuccessfully(20_000)
        assertTrue(controller.pending.value)
        controller.shown()
        controller.postpone()
        assertFalse(controller.pending.value)
        assertTrue(prefs.getLong("next_eligible_at", 0) > System.currentTimeMillis())
        prefs.edit().putLong("next_eligible_at", 0).commit()
        controller.completedSuccessfully(20_000)
        assertFalse(controller.pending.value) // process latch survives expired cooldown
        controller.choseToRate()
        assertTrue(prefs.getBoolean("never_ask", false))
        val recreated = RatingPromptController(context)
        recreated.completedSuccessfully(20_000)
        assertFalse(recreated.pending.value)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "rating-screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().useBitmap { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }
}
