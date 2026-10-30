package com.rork.gpssimulator.ui.onboarding

import android.app.Activity
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

fun stage(progress: Float, start: Float, end: Float): Float = ((progress - start) / (end - start)).coerceIn(0f, 1f)

@Composable
fun rememberOnboardingReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun disabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember { mutableStateOf(disabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = disabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

@Composable
fun rememberOnboardingResumed(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return resumed
}

@Composable
fun rememberOnboardingProgress(page: Int, active: Boolean, reduced: Boolean): Float {
    var saved by rememberSaveable(page) { mutableFloatStateOf(0f) }
    val progress = remember(page) { Animatable(saved) }
    LaunchedEffect(active, reduced) {
        if (reduced) { progress.snapTo(1f); saved = 1f }
        else if (active && progress.value < 1f) {
            try {
                progress.animateTo(1f, tween(((if (page == 0) 5000 else 6500) * (1f - progress.value)).toInt(), easing = LinearEasing))
            } finally { saved = progress.value }
        }
    }
    return progress.value
}

/** The introduction is navy even in light mode. Restore the host's icon style on exit. */
@Composable
fun OnboardingSystemBars() {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
    }
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (lightStatus != null) controller?.isAppearanceLightStatusBars = lightStatus
            if (lightNavigation != null) controller?.isAppearanceLightNavigationBars = lightNavigation
        }
    }
}
