package com.rork.gpssimulator.ui.rating

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Explicit rating buttons use the store, as recommended by Play's review guidelines.
 * The in-app review API is quota-limited and cannot guarantee a response to a tap. */
fun openPlayRating(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
        .setPackage("com.android.vending")
    try {
        context.startActivity(market)
    } catch (_: android.content.ActivityNotFoundException) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
        }
    }
}
