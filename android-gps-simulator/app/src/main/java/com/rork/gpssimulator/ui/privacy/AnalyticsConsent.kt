package com.rork.gpssimulator.ui.privacy

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Explicit opt-in to optional Firebase Analytics, separate from acknowledging
 * the privacy policy and separate from the app's essential map/GPS functions.
 *
 * All paths (including upgrades from older builds that auto-collected Analytics)
 * start from no consent. The manifest disables collection before Activity startup.
 */
object AnalyticsConsent {
    private const val PREFS = "gps_simulator_privacy"
    private const val POLICY_VERSION_KEY = "analytics_policy_version"
    private const val CONSENT_KEY = "analytics_enabled"
    private const val CURRENT_POLICY_VERSION = "2026-10-31"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasDecision(context: Context): Boolean =
        prefs(context).getString(POLICY_VERSION_KEY, null) == CURRENT_POLICY_VERSION &&
            prefs(context).contains(CONSENT_KEY)

    fun isAllowed(context: Context): Boolean =
        hasDecision(context) && prefs(context).getBoolean(CONSENT_KEY, false)

    /** Called in MainActivity.onCreate before composing app UI or tracking any screens. */
    fun initialize(context: Context) {
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(isAllowed(context))
    }

    /** Explicit user action only; rejecting disables collection but never blocks core GPS. */
    fun setAllowed(context: Context, allowed: Boolean) {
        prefs(context).edit()
            .putString(POLICY_VERSION_KEY, CURRENT_POLICY_VERSION)
            .putBoolean(CONSENT_KEY, allowed)
            .apply()

        val analytics = FirebaseAnalytics.getInstance(context)
        analytics.setAnalyticsCollectionEnabled(allowed)
        if (!allowed) {
            // Discard cached analytics identifiers and pending local analytics events.
            // This does not retroactively erase analytics already processed by Google.
            analytics.resetAnalyticsData()
        }
    }
}
