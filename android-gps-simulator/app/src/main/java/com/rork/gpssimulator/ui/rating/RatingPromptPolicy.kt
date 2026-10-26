package com.rork.gpssimulator.ui.rating

/** Existing three-session / fourteen-day policy, independent of Android for testing. */
object RatingPromptPolicy {
    const val MIN_SESSION_MS = 20_000L
    const val POSTPONE_MS = 14L * 24 * 60 * 60 * 1000

    fun eligible(completed: Int, neverAsk: Boolean, nextEligible: Long, now: Long,
                 shownThisSession: Boolean): Boolean =
        completed >= 3 && !neverAsk && !shownThisSession && now >= nextEligible

    fun safeToShow(onMap: Boolean, mapIdle: Boolean, simulationActive: Boolean, resumed: Boolean) =
        onMap && mapIdle && !simulationActive && resumed
}
