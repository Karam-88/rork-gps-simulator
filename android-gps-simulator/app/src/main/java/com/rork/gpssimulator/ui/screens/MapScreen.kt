package com.rork.gpssimulator.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
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
import com.rork.gpssimulator.data.model.SimRoute
import com.rork.gpssimulator.data.model.SpeedProfile
import com.rork.gpssimulator.location.RoadRoutingService
import kotlinx.coroutines.launch
import com.rork.gpssimulator.data.model.MapType
import com.rork.gpssimulator.data.model.MockState
import com.rork.gpssimulator.data.model.SavedKind
import com.rork.gpssimulator.data.model.SessionKind
import com.rork.gpssimulator.data.model.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.components.AppUpdateNoticeDialog
import com.rork.gpssimulator.ui.components.UpdateNotificationDot
import com.rork.gpssimulator.ui.components.rememberPlayUpdateAvailable
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
    onOpenRoutes: () -> Unit,
    onRatingPromptSafeChanged: (Boolean) -> Unit = {},
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current

    val context = LocalContext.current
    val ratingPrefs = remember(context) { context.getSharedPreferences("gps_simulator_rating", android.content.Context.MODE_PRIVATE) }
    var userConfirmedRating by remember { mutableStateOf(ratingPrefs.getBoolean("confirmed_rating", false)) }
    var showRatingConfirmation by remember { mutableStateOf(false) }

    val settings by viewModel.settings.collectAsState()
    val mockState by viewModel.mockState.collectAsState()
    val selected by viewModel.selected.collectAsState()
    var crosshairPreviewPoint by remember { mutableStateOf<LatLng?>(null) }
    var crosshairStreet by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(crosshairPreviewPoint, settings.directTapSelection) {
        val point = crosshairPreviewPoint ?: return@LaunchedEffect
        if (settings.directTapSelection) return@LaunchedEffect
        crosshairStreet = null
        kotlinx.coroutines.delay(450)
        crosshairStreet = com.rork.gpssimulator.location.GeocodingService().reverse(point)?.name
            ?: if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "اسم الشارع غير متاح" else "Street name unavailable"
    }
    val activePoint by viewModel.activePoint.collectAsState()
    val activePlace by viewModel.activePlace.collectAsState()
    val realSample by viewModel.realSample.collectAsState()
    val recoveryStatus by viewModel.realRecoveryStatus.collectAsState()
    val elapsedMs by viewModel.elapsedMs.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val sessionKind by viewModel.sessionKind.collectAsState()
    val routeProgress by viewModel.routeProgress.collectAsState()
    val currentSpeed by viewModel.currentSpeedKmh.collectAsState()
    val cameraTarget by viewModel.cameraTarget.collectAsState()

    val isFreshDefaultCamera =
        settings.lastCameraLat == 37.7749 && settings.lastCameraLng == -122.4194
    val initialCenter = realSample?.point ?: if (isFreshDefaultCamera) {
        // Do not flash San Francisco on a brand-new install while Android is still
        // resolving the first real location fix. A neutral world view is less misleading.
        LatLng(20.0, 0.0)
    } else {
        LatLng(settings.lastCameraLat, settings.lastCameraLng)
    }
    val initialZoom = if (isFreshDefaultCamera && realSample == null) {
        2.5f
    } else if (settings.rememberLastPosition) {
        settings.lastCameraZoom
    } else {
        settings.defaultZoom
    }

    val camera = rememberMapCameraState(
        initialCenter = initialCenter,
        initialZoom = initialZoom,
    )
    var showMenuPanel by remember { mutableStateOf(false) }
    var routeEditing by remember { mutableStateOf(false) }
    var routePreview by remember { mutableStateOf<SimRoute?>(null) }
    var routeCalculating by remember { mutableStateOf(false) }
    var routeProfile by remember { mutableStateOf(SpeedProfile.WALKING) }
    val routeStops = remember { mutableStateListOf<LatLng>() }
    var routeCrosshairDestination by remember { mutableStateOf<LatLng?>(null) }
    val routeScope = rememberCoroutineScope()
    val routeOrigin = if (mockState == MockState.MOCK_ACTIVE) activePoint else realSample?.point
    var showProblemDialog by remember { mutableStateOf(false) }
    var showFavoriteNameDialog by remember { mutableStateOf(false) }
    var favoriteName by remember { mutableStateOf("") }
    var showFavoritesPopup by remember { mutableStateOf(false) }
    val savedLocations by viewModel.savedLocations.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }
    val ratingSafe = !showMenuPanel && !showProblemDialog && !showUpdateDialog &&
        !showRatingConfirmation && selected == null && mockState != MockState.MOCK_ACTIVE
    androidx.compose.runtime.DisposableEffect(ratingSafe) {
        onRatingPromptSafeChanged(ratingSafe)
        onDispose { onRatingPromptSafeChanged(false) }
    }
    val updateAvailable = rememberPlayUpdateAvailable()
    var centeredOnInitialRealFix by remember { mutableStateOf(false) }
    val isMockActive = mockState == MockState.MOCK_ACTIVE
    val isMotionSession = sessionKind == SessionKind.ROUTE ||
        sessionKind == SessionKind.JOYSTICK || settings.joystickEnabled

    val reducedMotion = rememberReducedMotion()
    var hopRequest by remember { mutableStateOf<com.rork.gpssimulator.ui.map.RabbitHopRequest?>(null) }
    var hopSequence by remember { mutableStateOf(0L) }
    var tapHopOrigin by remember { mutableStateOf<LatLng?>(null) }
    fun requestManualHop(point: LatLng) {
        val prior = tapHopOrigin ?: activePoint ?: realSample?.point ?: selected?.point ?: camera.center
        tapHopOrigin = null
        if (prior != point && !isMotionSession) {
            hopRequest = com.rork.gpssimulator.ui.map.RabbitHopRequest(++hopSequence, prior, point)
        }
    }
    LaunchedEffect(isMotionSession) {
        // Keep a manual hop visible when choosing a location before starting mock GPS.
        // Only route/joystick motion suppresses the decorative single-hop animation.
        if (isMotionSession) hopRequest = null
    }
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "pulseFraction",
    )

    LaunchedEffect(cameraTarget) {
        val target = cameraTarget ?: return@LaunchedEffect
        camera.animateTo(
            point = target.first,
            newZoom = target.second ?: camera.zoom,
            animate = settings.animateMap,
        )
        viewModel.consumeCameraTarget()
    }

    // On a brand-new install, automatically replace the neutral/world seed with the
    // first genuine device fix as soon as Android provides it. This prevents the app
    // from appearing to start in the United States.
    LaunchedEffect(realSample, mockState, isFreshDefaultCamera) {
        val sample = realSample ?: return@LaunchedEffect
        if (!centeredOnInitialRealFix && isFreshDefaultCamera && mockState == MockState.REAL_GPS) {
            camera.animateTo(
                point = sample.point,
                newZoom = settings.defaultZoom,
                animate = false,
            )
            viewModel.persistCamera(sample.point, settings.defaultZoom)
            centeredOnInitialRealFix = true
        }
    }

    LaunchedEffect(activePoint, sessionKind) {
        val point = activePoint ?: return@LaunchedEffect
        if (sessionKind == SessionKind.ROUTE || sessionKind == SessionKind.JOYSTICK) {
            // Follow the current mock coordinate throughout the journey.
            // The initial session position is reached immediately; later
            // updates use the configured map animation for a smooth follow.
            camera.animateTo(
                point = point,
                newZoom = camera.zoom,
                animate = settings.animateMap,
            )
        }
    }

    val darkMap = when (settings.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val markers = buildList {
        if (!isMockActive && settings.directTapSelection) {
            realSample?.let {
                add(
                    MapMarker(
                        point = it.point,
                        color = Color(0xFF2583FA),
                        radiusMeters = it.accuracy.toDouble(),
                        style = MarkerStyle.REAL,
                        renderInOverlay = true,
                    ),
                )
            }
        }
        if (settings.directTapSelection) selected?.let {
            add(
                MapMarker(
                    point = it.point,
                    color = MaterialTheme.colorScheme.primary,
                    style = MarkerStyle.CANDIDATE,
                    renderInOverlay = true,
                ),
            )
        }
        // The active marker represents an actual mock session only.
        // Selecting a point before starting must not render a fake current-location dot.
        if (isMockActive) activePoint?.let {
            add(
                MapMarker(
                    point = it,
                    color = Color(settings.mockMarkerColor.argb),
                    pulsing = !isPaused && !reducedMotion,
                    radiusMeters = settings.accuracyM.toDouble(),
                    style = MarkerStyle.ACTIVE,
                    renderInOverlay = true,
                ),
            )
        }
    }
    val draftMarkers = routeStops.mapIndexed { index, stop ->
        MapMarker(point = stop, color = Color(0xFF222222),
            label = (index + 1).toString(), style = MarkerStyle.DESTINATION)
    }
    val mapMarkers = markers + draftMarkers + (routeProgress?.route?.points?.lastOrNull()?.let { destination ->
        listOf(MapMarker(point = destination, color = Color(0xFFE53935),
            style = MarkerStyle.DESTINATION))
    } ?: emptyList())
    val polylines = if (routePreview != null) {
        listOf(MapPolyline(routePreview!!.points, Color(0xFF222222), widthPx = 3f))
    } else if (routeEditing && routeStops.size > 1) {
        listOf(MapPolyline(routeStops.toList(), Color(0xFF222222), widthPx = 3f))
    } else routeProgress?.let {
        listOf(MapPolyline(it.route.points, MaterialTheme.colorScheme.primary))
    } ?: emptyList()

    Box(modifier = modifier.fillMaxSize()) {
        MapView(
            camera = camera,
            mapType = settings.mapType,
            darkMap = darkMap,
            markers = mapMarkers,
            polylines = polylines,
            pulseFraction = { pulse.value },
            hopRequest = hopRequest,
            // Tapping the map only interacts with the map. The bottom select
            // button is the sole action that commits the crosshair position.
            onMapTap = { point ->
                if (routeEditing && routePreview == null && !routeCalculating) {
                    routeStops.add(point)
                } else if (!routeEditing && settings.directTapSelection && !isMockActive) {
                    if (tapHopOrigin == null) tapHopOrigin = activePoint ?: realSample?.point ?: selected?.point ?: camera.center
                    viewModel.selectPoint(point)
                }
            },
            onMapLongPress = { point ->
                // Long press only selects a point; it never starts mock GPS.
                if (routeEditing && routePreview == null && !routeCalculating) routeStops.add(point)
                else if (!routeEditing) viewModel.selectPoint(point)
            },
            onCameraIdle = { center, zoom, movedByUser ->
                viewModel.persistCamera(center, zoom)
                // In crosshair mode the center is the selected location. Resolve its
                // street after camera movement stops, without starting mock GPS.
                if (movedByUser && routeEditing && !settings.directTapSelection && routePreview == null) {
                    routeCrosshairDestination = center
                }
                if (movedByUser && !settings.directTapSelection && !routeEditing) {
                    crosshairPreviewPoint = center
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Selected-point address: compact capsule below the GPS status, never a full-width panel.
        if (!settings.directTapSelection && crosshairPreviewPoint != null || selected != null) {
            val location = selected
            val street = (if (!settings.directTapSelection && crosshairPreviewPoint != null)
                crosshairStreet ?: if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "جارٍ تحديد العنوان…" else "Finding address…"
                else null) ?: location?.name?.takeIf { it.isNotBlank() && !it.matches(Regex("""\\s*-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?\\s*""")) }
                ?: location?.address?.substringBefore(",")?.takeIf { it.isNotBlank() && !it.trim().matches(Regex("""-?\\d+(?:\\.\\d+)?""")) }
                ?: if (location?.isResolving == true) {
                    if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "جارٍ تحديد العنوان…" else "Finding address…"
                } else if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "اسم الشارع غير متاح" else "Street name unavailable"
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 59.dp, end = 76.dp)
                    .shadow(6.dp, RoundedCornerShape(50)),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null,
                        tint = Color(0xFFE35A36), modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(street, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.widthIn(max = 220.dp))
                    IconButton(onClick = { crosshairPreviewPoint = null; crosshairStreet = null; viewModel.clearSelection() }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close,
                            contentDescription = if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "إلغاء التحديد" else "Clear selection",
                            modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Direct-tap mode uses the touched map coordinate, not the center reticle.
        if (!settings.directTapSelection) {
            Crosshair(
                color = Color(settings.crosshairColor.argb),
                crosshairSize = settings.crosshairSizeDp.dp,
                thickness = settings.crosshairThicknessDp.dp,
                opacity = settings.crosshairOpacity,
                shape = settings.crosshairShape,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (isMockActive) {
                    StatusPill(
                        text = if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) {
                            if (isPaused) "الموقع الوهمي متوقف مؤقتًا" else "الموقع الوهمي نشط"
                        } else {
                            if (isPaused) "Mock GPS paused" else "Mock GPS active"
                        },
                        dotColor = Color(settings.mockMarkerColor.argb),
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusPill(
                            text = strings[K.real_gps],
                            dotColor = appColors.success,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.size(42.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable(role = Role.Button) { showMenuPanel = false; onOpenSearch() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Search, contentDescription = strings[K.search_places],
                    tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(21.dp))
            }
            // Put the menu in the former report-button position.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .clickable(role = Role.Button) { showMenuPanel = !showMenuPanel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "القائمة" else "Menu",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(21.dp),
                    )
                }
                if (updateAvailable) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .clickable(role = Role.Button) {
                                showMenuPanel = false
                                showUpdateDialog = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC)
                                "تحديث التطبيق" else "Update app",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(21.dp),
                        )
                        UpdateNotificationDot(modifier = Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
        if (showUpdateDialog) {
            AppUpdateNoticeDialog(
                arabic = settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC,
                onDismiss = { showUpdateDialog = false },
            )
        }
        if (showProblemDialog) {
            ReportProblemDialog(
                language = com.rork.gpssimulator.i18n.LocalStrings.current.language,
                onDismiss = { showProblemDialog = false },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MapControlButton(
                Icons.Default.HelpOutline,
                if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "الإبلاغ عن مشكلة" else "Report a problem",
            ) {
                showMenuPanel = false
                showProblemDialog = true
            }
            MapControlButton(Icons.Default.MyLocation, strings[K.my_location]) {
                showMenuPanel = false
                viewModel.centerOnCurrentLocation()
            }
            MapControlButton(Icons.Default.StarBorder, strings[K.favorites]) {
                showMenuPanel = false
                showFavoritesPopup = true
            }
            MapControlButton(
                if (darkMap) Icons.Default.WbSunny else Icons.Default.DarkMode,
                if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC)
                    (if (darkMap) "تفعيل الوضع النهاري" else "تفعيل الوضع الليلي")
                else (if (darkMap) "Switch to day mode" else "Switch to night mode"),
            ) {
                showMenuPanel = false
                val next = if (darkMap) ThemeMode.LIGHT else ThemeMode.DARK
                viewModel.updateSettings { it.copy(theme = next, mapTheme = next) }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding()
                .padding(start = 12.dp, bottom = 188.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (routeEditing) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        listOf(
                            Triple(SpeedProfile.WALKING, Icons.Default.DirectionsWalk, strings[K.walking]),
                            Triple(SpeedProfile.CYCLING, Icons.Default.DirectionsBike, strings[K.cycling]),
                            Triple(SpeedProfile.DRIVING, Icons.Default.DirectionsCar, strings[K.driving]),
                        ).forEach { (profile, icon, label) ->
                            Surface(
                                shape = CircleShape,
                                color = if (routeProfile == profile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(46.dp).clickable {
                                    routeProfile = profile
                                    routePreview = null
                                },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icon, contentDescription = label,
                                        tint = if (routeProfile == profile) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
                if (routePreview == null) {
                    Surface(
                        shape = CircleShape,
                        color = if ((routeStops.isNotEmpty() || routeCrosshairDestination != null) && !routeCalculating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(46.dp),
                    ) {
                    IconButton(
                        enabled = (routeStops.isNotEmpty() || routeCrosshairDestination != null) && !routeCalculating,
                        onClick = {
                            val origin = routeOrigin
                            if (origin == null) {
                                viewModel.postMessage(if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC)
                                    "الموقع الحالي غير متاح" else "Current location unavailable", isError = true)
                            } else {
                                routeCalculating = true
                                val stops = routeStops.toList().ifEmpty { listOfNotNull(routeCrosshairDestination) }
                                val draft = SimRoute(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = "Map route",
                                    points = listOf(origin) + stops,
                                    profile = routeProfile,
                                )
                                routeScope.launch {
                                    val result = RoadRoutingService().resolve(draft)
                                    routeCalculating = false
                                    result.onSuccess { routePreview = it }
                                    result.onFailure { error ->
                                        viewModel.postMessage(error.message ?: "Road routing unavailable", isError = true)
                                    }
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Default.Check,
                            contentDescription = if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "انتهيت من إضافة نقاط المسار" else "Finish adding route stops",
                            tint = if ((routeStops.isNotEmpty() || routeCrosshairDestination != null) && !routeCalculating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                    }
                } else {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(46.dp)) {
                        IconButton(onClick = { routePreview = null }) {
                            Icon(Icons.Default.Edit, contentDescription = if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "تعديل النقاط" else "Edit stops")
                        }
                    }
                }
            }
            MapControlButton(Icons.Default.Route, strings[K.tab_routes], active = routeEditing) {
                showMenuPanel = false
                routeEditing = !routeEditing
                routePreview = null
                routeStops.clear()
                routeCrosshairDestination = null
            }
        }

        // Independent from the right-side toolbar, so its width cannot shift the black buttons.
        if (!isMockActive || routeEditing) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
                    .size(72.dp)
                    .shadow(12.dp, CircleShape)
.clickable(enabled = !routeCalculating, role = Role.Button) {
                        showMenuPanel = false
                        if (routeEditing) {
                            val startResolvedRoute: (SimRoute) -> Unit = { preview ->
                                viewModel.startRoute(preview, onStarted = {
                                    routeEditing = false
                                    routePreview = null
                                    routeStops.clear()
                                })
                            }
                            val ready = routePreview
                            if (ready != null) {
                                startResolvedRoute(ready)
                            } else if (routeStops.isEmpty() && routeCrosshairDestination == null) {
                                viewModel.postMessage(
                                    if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC)
                                        "حدد وجهة واحدة على الأقل" else "Select at least one destination",
                                    isError = true,
                                )
                            } else {
                                val origin = routeOrigin
                                if (origin == null) {
                                    viewModel.postMessage(
                                        if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC)
                                            "الموقع الحالي غير متاح" else "Current location unavailable",
                                        isError = true,
                                    )
                                } else {
                                    routeCalculating = true
                                    val draft = SimRoute(
                                        id = java.util.UUID.randomUUID().toString(),
                                        name = "Map route",
                                        points = listOf(origin) + routeStops.toList().ifEmpty { listOfNotNull(routeCrosshairDestination) },
                                        profile = routeProfile,
                                    )
                                    routeScope.launch {
                                        val result = RoadRoutingService().resolve(draft)
                                        routeCalculating = false
                                        result.onSuccess { resolved ->
                                            routePreview = resolved
                                            startResolvedRoute(resolved)
                                        }
                                        result.onFailure { error ->
                                            viewModel.postMessage(
                                                error.message ?: "Road routing unavailable", isError = true)
                                        }
                                    }
                                }
                            }
                        } else {
                            val point = if (settings.directTapSelection) selected?.point ?: camera.center else camera.center
                            requestManualHop(point)
                            viewModel.selectPoint(point)
                            viewModel.startMock()
                        }
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = strings[K.set_mock_location],
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }

        if (showMenuPanel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    ) { showMenuPanel = false },
            )

            // Keep the four map control buttons on the physical right side,
            // while opening the floating navigation panel on the physical left.
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Absolute.Left,
            ) {
                FloatingMapMenu(
                    mapLabel = strings[K.tab_map],
                    routesLabel = strings[K.tab_routes],
                    favoritesLabel = strings[K.favorites],
                    historyLabel = strings[K.tab_history],
                    settingsLabel = strings[K.tab_settings],
                    shareLabel = if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "مشاركة التطبيق" else "Share app",
                    rateLabel = if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "تقييم التطبيق" else "Rate app",
                    ratingConfirmed = userConfirmedRating,
                    onMap = { showMenuPanel = false },
                    onRoutes = { showMenuPanel = false; onOpenRoutes() },
                    onFavorites = { showMenuPanel = false; showFavoritesPopup = true },
                    onHistory = { showMenuPanel = false; onOpenHistory() },
                    onSettings = { showMenuPanel = false; onOpenSettings() },
                    onShare = {
                        showMenuPanel = false
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=com.rork.gpssimulator")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    },
                    onRate = {
                        showMenuPanel = false
                        viewModel.ratingPrompt.choseToRate()
                        showRatingConfirmation = true
                        val browserUrl = Uri.parse("https://play.google.com/store/apps/details?id=com.rork.gpssimulator")
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.rork.gpssimulator")))
                        } catch (_: ActivityNotFoundException) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, browserUrl)) }
                        }
                    },
                )
            }
        }

        if (showFavoritesPopup) {
            AlertDialog(
                onDismissRequest = { showFavoritesPopup = false },
                title = {
                    Text(if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC)
                        "المفضلة" else "Favorites")
                },
                text = {
                    val favorites = savedLocations.filter { it.kind == SavedKind.FAVORITE }
                    if (favorites.isEmpty()) {
                        Text(strings[K.no_favorites])
                    } else {
                        Column(
                            modifier = Modifier.heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            favorites.forEach { location ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = location.name,
                                        modifier = Modifier.weight(1f).clickable {
                                            viewModel.selectSaved(location)
                                            viewModel.moveCamera(location.point, 16f)
                                            showFavoritesPopup = false
                                        }.padding(vertical = 10.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    IconButton(onClick = { viewModel.deleteSaved(location.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription =
                                            if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC)
                                                "حذف" else "Delete")
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFavoritesPopup = false }) {
                        Text(if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC)
                            "إغلاق" else "Close")
                    }
                },
            )
        }

        if (showFavoriteNameDialog) {
            AlertDialog(
                onDismissRequest = { showFavoriteNameDialog = false },
                title = { Text(if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "حفظ الموقع" else "Save location") },
                text = {
                    OutlinedTextField(
                        value = favoriteName,
                        onValueChange = { favoriteName = it },
                        label = { Text(if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "اسم الموقع" else "Location name") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = favoriteName.isNotBlank(),
                        onClick = {
                            activePoint?.let { point ->
                                viewModel.saveLocation(
                                    name = favoriteName.trim(),
                                    address = activePlace?.address.orEmpty(),
                                    point = point,
                                    kind = SavedKind.FAVORITE,
                                )
                                viewModel.postMessage(strings[K.saved])
                            }
                            showFavoriteNameDialog = false
                        },
                    ) { Text(if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "حفظ" else "Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showFavoriteNameDialog = false }) {
                        Text(if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "إلغاء" else "Cancel")
                    }
                },
            )
        }

        if (showRatingConfirmation) {
            AlertDialog(
                onDismissRequest = { showRatingConfirmation = false },
                title = { Text(if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "تقييم التطبيق" else "Rate GPS Simulator") },
                text = { Text(if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "هل قمت بتقييم التطبيق بالفعل على Google Play؟" else "Have you submitted a rating on Google Play?") },
                confirmButton = {
                    TextButton(onClick = {
                        userConfirmedRating = true
                        ratingPrefs.edit().putBoolean("confirmed_rating", true).apply()
                        showRatingConfirmation = false
                    }) { Text(if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "نعم، قيّمته" else "Yes, I rated it") }
                },
                dismissButton = {
                    TextButton(onClick = { showRatingConfirmation = false }) {
                        Text(if (settings.language == com.rork.gpssimulator.i18n.AppLanguage.ARABIC) "ليس بعد" else "Not yet")
                    }
                },
            )
        }

        AttributionChip(
            text = TileSource.of(settings.mapType).attribution,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 8.dp, bottom = (if (isMockActive) 148.dp else 100.dp) + bottomInset),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 28.dp),
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
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 10.dp),
                )
            }

            if (isMockActive && !routeEditing) {
                if (isMotionSession) {
                    Button(
                        onClick = { viewModel.togglePause() },
                        modifier = Modifier.fillMaxWidth().height(44.dp).padding(bottom = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isPaused) strings[K.resume] else strings[K.pause])
                    }
                }

                ActiveMockActionBar(
                    stopLabel = strings[K.stop],
                    selectLabel = strings[K.select_point_short],
                    favoriteLabel = strings[K.favorites],
                    dangerColor = appColors.danger,
                    onStop = { viewModel.stopMock() },
                    onSelect = {
                        val point = camera.center
                        requestManualHop(point)
                        viewModel.selectPoint(point)
                        viewModel.moveMockTo(point)
                    },
                    onFavorite = {
                        favoriteName = activePlace?.name.orEmpty()
                        showFavoriteNameDialog = true
                    },
                )
            }
        }

        // Draw last inside the map Box so controls and attribution cannot cover
        // the recovery notification after STOP. No full-screen input scrim.
        com.rork.gpssimulator.ui.components.RealRecoveryNotice(
            status = if (isMockActive) null else recoveryStatus,
            onRetry = viewModel::retryRealRecovery,
            onDismiss = viewModel::dismissRealRecovery,
            onOpenMockSettings = {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                runCatching { context.startActivity(intent) }
                    .onFailure { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
            },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 112.dp + bottomInset),
        )
    }

}

@Composable
private fun FloatingMapMenu(
    modifier: Modifier = Modifier,
    mapLabel: String,
    routesLabel: String,
    favoritesLabel: String,
    historyLabel: String,
    settingsLabel: String,
    shareLabel: String,
    rateLabel: String,
    ratingConfirmed: Boolean,
    onShare: () -> Unit,
    onRate: () -> Unit,
    onMap: () -> Unit,
    onRoutes: () -> Unit,
    onFavorites: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(
        modifier = modifier
            .width(230.dp)
            .shadow(14.dp, RoundedCornerShape(30.dp)),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 10.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            FloatingMenuEntry(Icons.Default.History, historyLabel, false, onHistory)
            FloatingMenuEntry(Icons.Default.Settings, settingsLabel, false, onSettings)
            FloatingMenuEntry(Icons.Default.Share, shareLabel, false, onShare)
            FloatingMenuEntry(if (ratingConfirmed) Icons.Default.Star else Icons.Default.StarBorder, rateLabel, false, onRate, iconTint = Color(0xFFFFC107))
        }
    }
}

@Composable
private fun FloatingMenuEntry(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    iconTint: Color? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: if (selected) MaterialTheme.colorScheme.onSurface else LocalAppColors.current.muted,
                modifier = Modifier.size(25.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else LocalAppColors.current.muted,
            )
        }
    }
}

@Composable
private fun ActiveMockActionBar(
    stopLabel: String,
    selectLabel: String,
    favoriteLabel: String,
    dangerColor: Color,
    onStop: () -> Unit,
    onSelect: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassActionButton(
            icon = Icons.Default.Star,
            label = favoriteLabel,
            accent = Color(0xFFD5A329),
            onClick = onFavorite,
        )
        GlassActionButton(
            icon = Icons.Default.LocationOn,
            label = selectLabel,
            accent = Color(0xFF3985DD),
            onClick = onSelect,
        )
        GlassActionButton(
            icon = Icons.Default.Close,
            label = stopLabel,
            accent = dangerColor,
            isStop = true,
            onClick = onStop,
        )
    }
}

@Composable
private fun GlassActionButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    isStop: Boolean = false,
) {
    val container = accent.copy(alpha = if (isStop) 0.92f else 0.18f)
    val reducedMotion = rememberReducedMotion()
    val glowTransition = rememberInfiniteTransition(label = "stopButtonGlow")
    val glowPulse by glowTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stopGlowPulse",
    )
    val pulse = if (reducedMotion) 0.45f else glowPulse
    Box(
        modifier = Modifier.size(88.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isStop) {
            // Radial-gradient circle is drawn directly, avoiding rectangular blur artifacts.
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .drawBehind {
                        val glowRadius = size.minDimension * (0.43f + pulse * 0.07f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.62f + pulse * 0.24f),
                                    accent.copy(alpha = 0.26f + pulse * 0.19f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = glowRadius,
                            ),
                            radius = glowRadius,
                        )
                    },
            )
        }
        Surface(
            modifier = Modifier
                .size(72.dp)
                .clickable(role = Role.Button, onClick = onClick),
            shape = CircleShape,
            color = container,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                accent.copy(alpha = if (isStop) 0.98f else 0.92f),
            ),
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (isStop) Color.White else accent,
                    modifier = Modifier.size(34.dp),
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
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(50.dp)
            .shadow(8.dp, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

