package com.rork.gpssimulator.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.rork.gpssimulator.R

/**
 * Full-screen first-run onboarding artwork.
 *
 * The artwork is 1024x1792 and contains all visual copy/progress/button styling.
 * We keep it letterboxed with ContentScale.Fit so no title, map, or button can be
 * cropped on short, tall, or wide Android displays. Only the visible bottom CTA
 * area is tappable; accidental taps elsewhere no longer advance the onboarding.
 */
@Composable
fun FirstRunOnboardingScreen(
    @Suppress("UNUSED_PARAMETER") isArabic: Boolean,
    onFinished: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val activity = context as? Activity

    val pages = remember {
        intArrayOf(
            R.drawable.onboarding_welcome,
            R.drawable.onboarding_location,
            R.drawable.onboarding_routes,
            R.drawable.onboarding_mock_setup,
        )
    }

    // The onboarding artwork intentionally contains no status, signal or battery
    // graphics. Hide Android system bars while it is visible so those icons do not
    // sit on top of the artwork either, then restore them when onboarding finishes.
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = page > 0) {
        page--
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF071426)),
        contentAlignment = Alignment.Center,
    ) {
        val artworkAspect = 1024f / 1792f
        val containerAspect = maxWidth.value / maxHeight.value
        val artworkWidth = if (containerAspect > artworkAspect) maxHeight * artworkAspect else maxWidth
        val artworkHeight = if (containerAspect > artworkAspect) maxHeight else maxWidth / artworkAspect

        Box(
            modifier = Modifier
                .width(artworkWidth)
                .height(artworkHeight),
        ) {
            Image(
                painter = painterResource(pages[page]),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            // Matches the large visual CTA baked into each 1024x1792 artwork.
            // The touch target scales with the fitted artwork and remains comfortably
            // larger than Android's minimum target on normal phone sizes.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.91f)
                    .height(artworkHeight * 0.105f)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (page == pages.lastIndex) "Get Started" else "Next",
                    ) {
                        if (page < pages.lastIndex) {
                            page++
                        } else {
                            onFinished()
                        }
                    },
            )
        }
    }
}
