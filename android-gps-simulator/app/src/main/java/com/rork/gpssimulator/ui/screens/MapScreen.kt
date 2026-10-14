package com.rork.gpssimulator.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.MapType
import com.rork.gpssimulator.data.model.MockState
import com.rork.gpssimulator.data.model.SavedKind
import com.rork.gpssimulator.data.model.SessionKind
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.components.Crosshair
import com.rork.gpssimulator.ui.components.JoystickControl
import com.rork.gpssimulator.ui.components.SelectionOption
import com.rork.gpssimulator.ui.components.SelectionSheet
import com.rork.gpssimulator.ui.components.StatusDot
import com.rork.gpssimulator.ui.map.MapMarker
import com.rork.gpssimulator.ui.map.MapPolyline
import com.rork.gpssimulator.ui.map.MapView
import com.rork.gpssimulator.ui.map.MarkerStyle
import com.rork.gpssimulator.ui.map.TileSource
import com.rork.gpssimulator.ui.map.rememberMapCameraState
import com.rork.gpssimulator.ui.theme.LocalAppColors
import com.rork.gpssimulator.ui.theme.MonoValueStyle
import com.rork.gpssimulator.util.Format
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

/**
 * Constant clearance kept between the lowest floating control and the bottom
 * navigation bar, so primary actions never visually merge with the tab bar.
 */
private val ControlSafeGap = 18.dp

/**
 * True when the user has disabled system animations, in which case the active
 * marker renders without its pulse.
 */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

@Composable
fun MapScreen(
    viewModel: AppViewModel,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpenSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    val settings by viewModel.settings.collectAsState()
    val mockState by viewModel.mockState.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val activePoint by viewModel.activePoint.collectAsState()
    val realSample by viewModel.realSample.collectAsState()
    val elapsedMs by viewModel.elapsedMs.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val sessionKind by viewModel.sessionKind.collectAsState()
    val routeProgress by viewModel.routeProgress.collectAsState()
    val currentSpeed by viewModel.currentSpeedKmh.collectAsState()
    val cameraTarget by viewModel.cameraTarget.collectAsState()

    val camera = rememberMapCameraState(
        initialCenter = LatLng(settings.lastCameraLat, settings.lastCameraLng),
        initialZoom = if (settings.rememberLastPosition) settings.lastCameraZoom else settings.defaultZoom,
    )

    var showLayerSheet by remember { mutableStateOf(false) }

    val isMockActive = mockState == MockState.MOCK_ACTIVE

    // Pausing only means something while something is moving. For a fixed point
    // there is nothing to freeze, so the control is hidden rather than faked.
    val isMotionSession = sessionKind == SessionKind.ROUTE ||
        sessionKind == SessionKind.JOYSTICK ||
        settings.joystickEnabled

    // Pulse animation for the active marker, skipped when the user has turned
    // system animations off. The State is deliberately NOT read here with `by`:
    // it is read inside the map's draw scope instead, so the animation
    // invalidates drawing only and never recomposes this screen each frame.
    val reducedMotion = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "pulseFraction",
    )

    // Camera moves requested by the view model (search result, my location, stop…).
    LaunchedEffect(cameraTarget) {
        val target = cameraTarget ?: return@LaunchedEffect
        camera.animateTo(
            point = target.first,
            newZoom = target.second ?: camera.zoom,
            animate = settings.animateMap,
        )
        viewModel.consumeCameraTarget()
    }

    // Keep the map centred on the moving mock position during route/joystick runs.
    LaunchedEffect(activePoint, sessionKind) {
        val point = activePoint ?: return@LaunchedEffect
        if (sessionKind == SessionKind.ROUTE || sessionKind == SessionKind.JOYSTICK) {
            camera.snapTo(point)
        }
    }

    val markers = buildList {
        if (!isMockActive) {
            realSample?.let { sample ->
                add(
                    MapMarker(
                        point = sample.point,
                        color = appColors.success,
                        radiusMeters = sample.accuracy.toDouble(),
                        style = MarkerStyle.REAL,
                    ),
                )
            }
        }
        // The pending candidate, in either state. Drawn as an outline so it can
        // never be mistaken for the live mocked position.
        selected?.let {
            add(
                MapMarker(
                    point = it.point,
                    color = MaterialTheme.colorScheme.primary,
                    style = MarkerStyle.CANDIDATE,
                ),
            )
        }
        // The live mocked coordinate, added last so it owns the top of the stack.
        activePoint?.let { point ->
            add(
                MapMarker(
                    point = point,
                    color = MaterialTheme.colorScheme.primary,
                    pulsing = !isPaused && !reducedMotion,
                    radiusMeters = settings.accuracyM.toDouble(),
                    style = MarkerStyle.ACTIVE,
                ),
            )
        }
    }

    val polylines = routeProgress?.let { progress ->
        listOf(MapPolyline(progress.route.points, MaterialTheme.colorScheme.primary))
    } ?: emptyList()

    Box(modifier = modifier.fillMaxSize()) {
        MapView(
            camera = camera,
            mapType = settings.mapType,
            markers = markers,
            polylines = polylines,
            pulseFraction = { pulse.value },
            onCameraIdle = { center, zoom -> viewModel.persistCamera(center, zoom) },
            modifier = Modifier.fillMaxSize(),
        )

        // Fixed targeting reticle. It stays put even during an active session,
        // where it aims the next candidate without touching the live coordinate.
        Crosshair(
            color = MaterialTheme.colorScheme.primary,
            isActive = camera.isUserInteracting,
            modifier = Modifier.align(Alignment.Center),
        )

        // ---- Top chrome ----
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (isMockActive) {
                MockActiveBanner(
                    lat = activePoint?.lat ?: 0.0,
                    lng = activePoint?.lng ?: 0.0,
                    accuracy = settings.accuracyM,
                    altitude = settings.altitudeM,
                    speedKmh = currentSpeed,
                    elapsedMs = elapsedMs,
                    isPaused = isPaused,
                    units = settings.units,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatusPill(
                        text = if (mockState == MockState.SELECTED) {
                            strings[K.location_selected]
                        } else {
                            strings[K.real_gps]
                        },
                        dotColor = if (mockState == MockState.SELECTED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            appColors.success
                        },
                    )
                    CoordinatePill(
                        lat = camera.centerLat,
                        lng = camera.centerLng,
                        latLabel = strings[K.lat],
                        lngLabel = strings[K.lng],
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }

        // ---- Floating side controls ----
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MapControlButton(Icons.Default.Search, strings[K.search_places], onOpenSearch)
            MapControlButton(Icons.Default.MyLocation, strings[K.my_location]) {
                viewModel.returnToRealLocation()
            }
            MapControlButton(Icons.Default.Layers, strings[K.map_layers]) { showLayerSheet = true }
            MapControlButton(Icons.Default.Star, strings[K.favorites], onOpenFavorites)
        }

        // ---- Map attribution ----
        // Credit for the tiles of the active map type. It sits in the safe gap
        // below the bottom controls, using the same insets as that column, so it
        // can never overlap a control or the side buttons, and it follows the map
        // type on its own because it reads settings.mapType.
        AttributionChip(
            text = TileSource.of(settings.mapType).attribution,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 8.dp, bottom = bottomInset + 3.dp),
        )

        // ---- Bottom controls ----
        // Every floating bottom control lives in this single column, so they can
        // never overlap each other or the navigation bar. The column clears the
        // system navigation/gesture inset first, then the app's own bottom bar,
        // then a constant safe gap — no device-specific offsets anywhere.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = bottomInset + ControlSafeGap)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (settings.joystickEnabled && isMockActive) {
                JoystickControl(
                    speedLabel = Format.speed(currentSpeed, settings.units),
                    isPaused = isPaused,
                    onVector = { x, y -> viewModel.setJoystickVector(x, y) },
                    onTogglePause = { viewModel.togglePause() },
                    onClose = {
                        viewModel.setJoystickVector(0f, 0f)
                        viewModel.updateSettings { it.copy(joystickEnabled = false) }
                    },
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 16.dp),
                )
            }

            if (isMockActive) {
                // Apply the map centre immediately to the running mock session.
                ActiveControls(
                    showPause = isMotionSession,
                    isPaused = isPaused,
                    selectPointLabel = strings[K.select_point_short],
                    pauseLabel = if (isPaused) strings[K.resume] else strings[K.pause],
                    stopLabel = strings[K.stop],
                    onSelectPoint = {
                        val point = camera.center
                        viewModel.selectPoint(point)
                        viewModel.moveMockTo(point)
                    },
                    onPause = { viewModel.togglePause() },
                    onStop = { viewModel.stopMock() },
                )
            } else {
                Button(
                    onClick = {
                        // Start directly from the crosshair without opening a preview sheet.
                        viewModel.selectPoint(camera.center)
                        viewModel.startMock()
                    },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = strings[K.set_mock_location],
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    if (showLayerSheet) {
        SelectionSheet(
            title = strings[K.map_type],
            options = listOf(
                SelectionOption(MapType.STANDARD, strings[K.standard]),
                SelectionOption(MapType.DETAILED_STREETS, strings[K.detailed_streets]),
                SelectionOption(MapType.SATELLITE, strings[K.satellite]),
                SelectionOption(MapType.TERRAIN, strings[K.terrain]),
            ),
            selected = settings.mapType,
            onSelect = { type -> viewModel.updateSettings { it.copy(mapType = type) } },
            onDismiss = { showLayerSheet = false },
        )
    }
}

/**
 * Compact secondary action offered while a session runs, so the user can pin a
 * new candidate without the red X ever leaving the screen.
 */
@Composable
private fun SelectPointButton(
    label: String,
    lat: Double,
    lng: Double,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .shadow(10.dp, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "${Format.coord(lat)}, ${Format.coord(lng)}",
                    style = MonoValueStyle.copy(fontSize = 13.sp),
                    color = LocalAppColors.current.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, dotColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.shadow(6.dp, RoundedCornerShape(50)),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(dotColor, size = 10)
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Opaque backing for the caption under a round control, so it stays legible over any map tile. */
@Composable
private fun ButtonCaption(text: String) {
    Surface(
        modifier = Modifier.shadow(4.dp, RoundedCornerShape(50)),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/**
 * Small, muted tile-provider credit for a bottom corner of the map. Its text is
 * sized in dp on purpose: the chip lives in the [ControlSafeGap] below the
 * controls and must not grow into them when the system font scale is raised.
 */
@Composable
private fun AttributionChip(text: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Surface(
        modifier = modifier.shadow(2.dp, RoundedCornerShape(50)),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = with(density) { 9.dp.toSp() },
                lineHeight = with(density) { 11.dp.toSp() },
                // The credits are English text that starts with "©": keep them
                // left-to-right in an RTL layout too, where the bidi algorithm
                // would otherwise push the leading "©" to the end of the line.
                textDirection = TextDirection.Ltr,
            ),
            color = LocalAppColors.current.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun CoordinatePill(
    lat: Double,
    lng: Double,
    latLabel: String,
    lngLabel: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.shadow(6.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            CoordinateLine(latLabel, Format.coord(lat))
            Spacer(Modifier.height(2.dp))
            CoordinateLine(lngLabel, Format.coord(lng))
        }
    }
}

@Composable
private fun CoordinateLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalAppColors.current.muted,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MonoValueStyle.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(50.dp)
            .shadow(8.dp, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

@Composable
private fun ActiveControls(
    showPause: Boolean,
    isPaused: Boolean,
    selectPointLabel: String,
    pauseLabel: String,
    stopLabel: String,
    onSelectPoint: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    val appColors = LocalAppColors.current
    // A soft, repeating halo behind STOP so the exit from a live session is always noticeable.
    val infiniteTransition = rememberInfiniteTransition(label = "stopPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stopPulseScale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stopPulseAlpha",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier
                    .size(66.dp)
                    .shadow(10.dp, CircleShape)
                    .clickable(role = Role.Button, onClick = onSelectPoint),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = selectPointLabel,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            ButtonCaption(selectPointLabel)
        }

        if (showPause) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier
                        .size(66.dp)
                        .shadow(10.dp, CircleShape)
                        .clickable(role = Role.Button, onClick = onPause),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = pauseLabel,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                ButtonCaption(pauseLabel)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .scale(pulseScale)
                        .background(appColors.danger.copy(alpha = pulseAlpha), CircleShape),
                )
                Surface(
                    modifier = Modifier
                        .size(82.dp)
                        .shadow(14.dp, CircleShape)
                        .clickable(role = Role.Button, onClick = onStop),
                    shape = CircleShape,
                    color = appColors.danger,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stopLabel,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            ButtonCaption(stopLabel)
        }
    }
}
