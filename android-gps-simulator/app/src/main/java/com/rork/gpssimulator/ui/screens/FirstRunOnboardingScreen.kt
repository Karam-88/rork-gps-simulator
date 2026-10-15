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
import androidx.compose.foundation.layout.padding
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.rork.gpssimulator.R

/**
 * Full-screen first-run onboarding artwork.
 *
 * The artwork is intentionally rendered with Crop so it fills the entire phone
 * screen instead of looking like a poster with empty bars above and below it.
 * On taller/narrower phones only the far left/right decorative edges are cropped;
 * the centered titles, phone mockups, progress dots and CTA remain visible.
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

    // The artwork itself contains no battery, signal or status icons. Hide Android
    // system bars while onboarding is visible, then restore them afterwards.
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
    ) {
        Image(
            painter = painterResource(pages[page]),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )

        // Tap target follows the CTA position baked into the image. Because Crop
        // scales by height on narrow phones, this remains aligned across devices.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = maxHeight * 0.045f)
                .fillMaxWidth(0.88f)
                .height(maxHeight * 0.09f)
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
