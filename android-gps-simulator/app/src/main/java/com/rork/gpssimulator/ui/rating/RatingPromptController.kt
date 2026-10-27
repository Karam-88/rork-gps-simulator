package com.rork.gpssimulator.ui.rating

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Process-scoped: Activity recreation and closing/reopening the task cannot prompt again.
private object RatingPromptSession { var shown = false }

class RatingPromptController(context: Context) {
    private val prefs = context.getSharedPreferences("gps_simulator_rating_prompt", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("gps_simulator_rating", Context.MODE_PRIVATE)
    private val _pending = MutableStateFlow(false)
    val pending = _pending.asStateFlow()

    fun completedSuccessfully(durationMs: Long) {
        if (durationMs < RatingPromptPolicy.MIN_SESSION_MS) return
        val completed = prefs.getInt("completed_sessions", 0) + 1
        prefs.edit().putInt("completed_sessions", completed).apply()
        _pending.value = RatingPromptPolicy.eligible(
            completed, prefs.getBoolean("never_ask", false) || legacy.getBoolean("confirmed_rating", false),
            prefs.getLong("next_eligible_at", 0), System.currentTimeMillis(), RatingPromptSession.shown,
        )
    }

    fun shown() {
        RatingPromptSession.shown = true
        // Persist a cooldown even if Android kills the process with the dialog open.
        prefs.edit().putLong("next_eligible_at", System.currentTimeMillis() + RatingPromptPolicy.POSTPONE_MS).apply()
        _pending.value = false
    }

    fun postpone() {
        shown()
    }

    fun choseToRate() {
        RatingPromptSession.shown = true
        prefs.edit().putBoolean("never_ask", true).apply()
        _pending.value = false
    }
}
