package com.rork.gpssimulator.ui.onboarding

import com.rork.gpssimulator.i18n.*
import org.junit.Assert.*
import org.junit.Test

class OnboardingCopyTest {
    @Test fun allApplicationLanguagesHaveCompleteNativeCopy() {
        AppLanguage.entries.forEach { language ->
            val copy = onboardingCopy(language)
            val values = OnboardingCopy::class.java.declaredFields.filter { it.type == String::class.java }.map {
                it.isAccessible = true
                it.get(copy) as String
            }
            assertEquals(21, values.size)
            assertTrue(language.name, values.all { it.isNotBlank() })
            if (language != AppLanguage.ENGLISH) assertNotEquals(onboardingCopy(AppLanguage.ENGLISH).welcome, copy.welcome)
        }
    }
}
