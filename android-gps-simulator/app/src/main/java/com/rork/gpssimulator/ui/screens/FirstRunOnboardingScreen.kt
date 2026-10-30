package com.rork.gpssimulator.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.rork.gpssimulator.i18n.*
import com.rork.gpssimulator.ui.onboarding.*
import kotlinx.coroutines.launch

private val Navy = Color(0xFF030D1D)
private val Blue = Color(0xFF008BFF)
private val Cyan = Color(0xFF38DFFF)
private val Muted = Color(0xFFBCD2F1)

/** Keeps the existing completion callback and preference key; updating never resets onboarding. */
@Composable
fun FirstRunOnboardingScreen(
    @Suppress("UNUSED_PARAMETER") isArabic: Boolean,
    onFinished: () -> Unit,
) {
    val language = LocalStrings.current.language
    val copy = onboardingCopy(language)
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val reduced = rememberOnboardingReducedMotion()
    val resumed = rememberOnboardingResumed()
    var finishing by remember { mutableStateOf(false) }
    val finish = { if (!finishing) { finishing = true; onFinished() } }
    // Scope RTL to onboarding: do not change the established map coordinate/navigation conventions.
    CompositionLocalProvider(LocalLayoutDirection provides if (language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        BackHandler(pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(0) } }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF071A34), Navy, Color(0xFF06162D))))) {
            Column(Modifier.safeDrawingPadding().widthIn(max = 600.dp).fillMaxSize().align(Alignment.Center).padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = finish, modifier = Modifier.testTag("onboarding-skip")) {
                        Text(copy.skip, color = Muted)
                    }
                }
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("onboarding-pager"),
                    beyondViewportPageCount = 0,
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    val progress = rememberOnboardingProgress(page, pager.currentPage == page && resumed, reduced)
                    OnboardingPage(page, copy, progress, pager.currentPage == page && resumed && !reduced)
                }
                Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(bottom = 12.dp)
                        .semantics { contentDescription = "${pager.currentPage + 1} / 2" }) {
                        repeat(2) { index ->
                            Box(Modifier.size(if (pager.currentPage == index) 10.dp else 8.dp).clip(CircleShape)
                                .background(if (pager.currentPage == index) Cyan else Color(0xFF435E86)))
                        }
                    }
                    Button(
                        onClick = { if (pager.currentPage == 0) scope.launch {
                            if (reduced) pager.scrollToPage(1) else pager.animateScrollToPage(1)
                        } else finish() },
                        enabled = !finishing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 15.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF0060FF), Color(0xFF00B7F4))))
                            .border(1.dp, Cyan.copy(alpha = .8f), RoundedCornerShape(28.dp)).testTag("onboarding-next"),
                    ) { Text(if (pager.currentPage == 0) copy.next else copy.start, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: Int, copy: OnboardingCopy, progress: Float, pulse: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = with(LocalDensity.current) { maxHeight.roundToPx() }
        // At large accessibility font sizes content can scroll vertically, but the CTA stays outside
        // this scroll container. Actual text measurements determine illustration space, never ratios
        // from a reference screenshot or a guessed number of title lines.
        SubcomposeLayout(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("onboarding-content-$page")) { constraints ->
            val loose = Constraints(maxWidth = constraints.maxWidth)
            val header = subcompose("header") { OnboardingHeader(page, copy, progress) }.single().measure(loose)
            val footer = subcompose("features") { OnboardingFeatures(page, copy, progress) }.single().measure(loose)
            val gap = 10.dp.roundToPx()
            val sceneHeight = (viewport - header.height - footer.height - gap * 2)
                .coerceIn(96.dp.roundToPx(), 340.dp.roundToPx())
            val scene = subcompose("scene") {
                OnboardingScene(page, progress, pulse, copy, Modifier.fillMaxSize().testTag("onboarding-scene-$page"))
            }.single().measure(Constraints.fixed(constraints.maxWidth, sceneHeight))
            val total = header.height + scene.height + footer.height + gap * 2
            layout(constraints.maxWidth, maxOf(total, viewport)) {
                header.placeRelative(0, 0)
                scene.placeRelative(0, header.height + gap)
                footer.placeRelative(0, header.height + gap + scene.height + gap)
            }
        }
    }
}

@Composable
private fun OnboardingHeader(page: Int, copy: OnboardingCopy, progress: Float) {
    Column(Modifier.fillMaxWidth().testTag("onboarding-header-$page"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page == 0) {
                val appear = stage(progress, 0f, .12f)
                Box(Modifier.size(36.dp).graphicsLayer { alpha = appear; scaleX = .8f + .2f * appear; scaleY = scaleX }
                    .clip(RoundedCornerShape(12.dp)).background(Brush.radialGradient(listOf(Blue.copy(alpha = .5f), Navy)))
                    .border(1.dp, Blue, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MyLocation, null, tint = Cyan, modifier = Modifier.size(25.dp))
                }
            }
            Text(if (page == 0) copy.welcome else copy.routes, color = Color.White, fontSize = 24.sp,
                lineHeight = 33.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).alpha(stage(progress, .03f, .16f)))
        }
        Text(if (page == 0) copy.subtitle else copy.routeSubtitle, color = Muted, fontSize = 15.sp,
            lineHeight = 23.sp, modifier = Modifier.fillMaxWidth().alpha(stage(progress, .08f, .2f)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingFeatures(page: Int, copy: OnboardingCopy, progress: Float) {
    Column(Modifier.fillMaxWidth().testTag("onboarding-features-$page"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (page == 1) {
            val modes = listOf(Icons.Default.DirectionsWalk to copy.walk, Icons.Default.DirectionsCar to copy.car,
                Icons.Default.DirectionsBike to copy.bike, Icons.Default.DirectionsRun to copy.run)
            FlowRow(Modifier.fillMaxWidth().alpha(stage(progress, .35f, .48f)), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 4) {
                modes.forEachIndexed { index, (icon, label) ->
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(if (index == 0) Blue.copy(alpha = .25f) else Color(0xFF0B203B))
                        .border(1.dp, if (index == 0) Cyan else Color(0xFF203D60), RoundedCornerShape(12.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(icon, null, tint = if (index == 0) Cyan else Muted, modifier = Modifier.size(20.dp))
                        Text(label, fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
        val labels = if (page == 0) listOf(copy.search, copy.select, copy.simple) else listOf(copy.developer, copy.mockApp, copy.startSimulation)
        val icons = listOf(Icons.Default.Search, Icons.Default.LocationOn, Icons.Default.Layers)
        labels.forEachIndexed { i, label ->
            val visible = if (page == 0) stage(progress, .67f + i * .1f, .77f + i * .1f) else stage(progress, .44f + i * .06f, .52f + i * .06f)
            // Full-width compact rows: no three-card row that squeezes Arabic or enlarged text.
            Row(Modifier.fillMaxWidth().alpha(visible).clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF0C2849), Color(0xFF08172C))))
                .border(1.dp, Color(0xFF1B446A), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (page == 0) Icon(icons[i], null, tint = Cyan, modifier = Modifier.size(22.dp))
                else Text("${i + 1}", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.width(18.dp))
                Text(label, color = Color.White, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}
