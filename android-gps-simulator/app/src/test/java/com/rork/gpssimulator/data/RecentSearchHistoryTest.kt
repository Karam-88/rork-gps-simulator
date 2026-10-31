package com.rork.gpssimulator.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchHistoryTest {
    @Test
    fun sixthCompletedSearchEvictsOnlyOldest() {
        val firstFive = listOf("fifth", "fourth", "third", "second", "first")
        assertEquals(
            listOf("sixth", "fifth", "fourth", "third", "second"),
            RecentSearchHistory.add(firstFive, "sixth"),
        )
    }

    @Test
    fun swipeDeletionKeepsOtherItemsAndTheirOrder() {
        assertEquals(
            listOf("los angeles", "uk"),
            RecentSearchHistory.remove(listOf("los angeles", "usa", "uk"), "usa"),
        )
    }

    @Test
    fun repeatedQueryMovesToTopCaseInsensitively() {
        assertEquals(
            listOf("USA", "uk", "dortmund"),
            RecentSearchHistory.add(listOf("uk", "usa", "dortmund"), "USA"),
        )
    }

    @Test
    fun priorTenEntryListIsNormalizedToFiveAndBlankTermsAreIgnored() {
        assertEquals(
            listOf("one", "two", "three", "four", "five"),
            RecentSearchHistory.normalize(
                listOf(" one ", "two", "THREE", "", "four", "five", "six", "SEVEN"),
            ),
        )
        assertEquals(
            listOf("usa"),
            RecentSearchHistory.add(listOf("usa"), "   "),
        )
    }
}
