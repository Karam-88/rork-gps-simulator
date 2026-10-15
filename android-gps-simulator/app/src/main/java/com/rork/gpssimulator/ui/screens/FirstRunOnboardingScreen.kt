package com.rork.gpssimulator.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.rork.gpssimulator.R

/**
 * Full-screen first-run onboarding artwork.
 *
 * The four images already contain their visual titles, descriptions, progress
 * indicators and Next/Get Started buttons. The whole screen is tappable so the
 * button artwork remains functional without adding duplicate UI on top of it.
 * Completion is persisted by AppNavigation and the onboarding is therefore
 * shown only on the first launch.
 */
@Composable
fun FirstRunOnboardingScreen(
    @Suppress("UNUSED_PARAMETER") isArabic: Boolean,
    onFinished: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }

    val pages = remember {
        intArrayOf(
            R.drawable.onboarding_welcome,
            R.drawable.onboarding_location,
            R.drawable.onboarding_routes,
            R.drawable.onboarding_mock_setup,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF071426))
            .clickable(role = Role.Button) {
                if (page < pages.lastIndex) {
                    page++
                } else {
                    onFinished()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(pages[page]),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
