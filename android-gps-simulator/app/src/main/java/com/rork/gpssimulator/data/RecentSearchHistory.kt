package com.rork.gpssimulator.data

import java.util.Locale

/**
 * A local-only, most-recent-first list of completed searches.
 * Never retain more than five phrases or save each intermediate keystroke.
 */
internal object RecentSearchHistory {
    const val MAX_ITEMS = 5

    fun normalize(items: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return items.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filter { seen.add(it.lowercase(Locale.ROOT)) }
            .take(MAX_ITEMS)
            .toList()
    }

    fun add(items: List<String>, query: String): List<String> {
        val term = query.trim()
        if (term.isEmpty()) return normalize(items)
        return normalize(listOf(term) + items)
    }

    fun remove(items: List<String>, query: String): List<String> =
        normalize(items.filterNot { it.equals(query, ignoreCase = true) })
}
