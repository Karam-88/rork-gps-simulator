package com.rork.gpssimulator.ui.rating

import com.rork.gpssimulator.i18n.AppLanguage
import com.rork.gpssimulator.i18n.reviewPromptCopy
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class RatingPromptPolicyTest {
    @Test fun automaticAndManualLanguages() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.effective(AppLanguage.ARABIC, true, Locale.GERMAN))
        assertEquals(AppLanguage.ARABIC, AppLanguage.effective(AppLanguage.ARABIC, false, Locale.GERMAN))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.effective(AppLanguage.GERMAN, true, Locale("xx")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.effective(AppLanguage.ENGLISH, false, Locale("ar")))
        assertEquals(AppLanguage.ARABIC, AppLanguage.effective(AppLanguage.ENGLISH, true, Locale("ar")))
    }
    @Test fun allSupportedLanguagesHaveCompleteCopy() {
        AppLanguage.entries.forEach {
            val c = reviewPromptCopy(it)
            assertTrue(listOf(c.title, c.body, c.rate, c.later, c.noAds, c.free).all(String::isNotBlank))
        }
        assertEquals("Keine Werbung", reviewPromptCopy(AppLanguage.GERMAN).noAds)
        assertEquals("هل أعجبك GPS Simulator؟", reviewPromptCopy(AppLanguage.ARABIC).title)
    }
    @Test fun postponementAndPermanentSuppression() {
        assertFalse(RatingPromptPolicy.eligible(2, false, 0, 100, false))
        assertTrue(RatingPromptPolicy.eligible(3, false, 100, 100, false))
        assertFalse(RatingPromptPolicy.eligible(3, false, 101, 100, false))
        assertFalse(RatingPromptPolicy.eligible(30, true, 0, Long.MAX_VALUE, false))
        assertFalse(RatingPromptPolicy.eligible(30, false, 0, Long.MAX_VALUE, true))
    }
    @Test fun neverInterruptSimulationRoutesOrBackground() {
        assertFalse(RatingPromptPolicy.safeToShow(true, true, true, true))
        assertFalse(RatingPromptPolicy.safeToShow(false, true, false, true))
        assertFalse(RatingPromptPolicy.safeToShow(true, false, false, true))
        assertFalse(RatingPromptPolicy.safeToShow(true, true, false, false))
        assertTrue(RatingPromptPolicy.safeToShow(true, true, false, true))
    }
}
