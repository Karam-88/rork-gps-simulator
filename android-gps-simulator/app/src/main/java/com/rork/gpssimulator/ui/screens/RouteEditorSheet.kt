package com.rork.gpssimulator.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import com.rork.gpssimulator.i18n.AppLanguage
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.SimRoute
import com.rork.gpssimulator.data.model.SpeedProfile
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.map.MapMarker
import com.rork.gpssimulator.ui.map.MarkerStyle
import com.rork.gpssimulator.ui.map.MapPolyline
import com.rork.gpssimulator.ui.map.MapView
import com.rork.gpssimulator.ui.map.rememberMapCameraState
import com.rork.gpssimulator.ui.theme.LocalAppColors
import com.rork.gpssimulator.ui.theme.MonoValueStyle
import com.rork.gpssimulator.util.Format
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditorSheet(
    existing: SimRoute?,
    viewModel: AppViewModel,
    onSave: (SimRoute) -> Unit,
    onStart: (SimRoute) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings by viewModel.settings.collectAsState()
    val realSample by viewModel.realSample.collectAsState()
    val activePoint by viewModel.activePoint.collectAsState()

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var profile by remember {
        mutableStateOf(
            when (existing?.profile ?: settings.speedProfile) {
                SpeedProfile.CUSTOM -> SpeedProfile.RUNNING
                else -> existing?.profile ?: settings.speedProfile
            },
        )
    }
    var loop by remember { mutableStateOf(existing?.loop ?: false) }
    val points = remember {
        mutableStateListOf<LatLng>().apply { existing?.points?.let { addAll(it) } }
    }

    // The editor should start at the device's real position, not the old world/map
    // camera seed. A running mock session must not override the real GPS preview.
    val currentLocation = realSample?.point ?: activePoint
    val initialRoutePoint = existing?.points?.firstOrNull()
    val camera = rememberMapCameraState(
        initialCenter = initialRoutePoint
            ?: currentLocation
            ?: LatLng(settings.lastCameraLat, settings.lastCameraLng),
        initialZoom = 15f,
    )
    // GPS is asynchronous. The first composition may have no location yet;
    // recenter once when the first fix arrives, without chasing later updates.
    var awaitingInitialFix by remember {
        mutableStateOf(initialRoutePoint == null && currentLocation == null)
    }
    LaunchedEffect(currentLocation, initialRoutePoint) {
        if (awaitingInitialFix && initialRoutePoint == null && currentLocation != null) {
            camera.isUserInteracting = false
            camera.snapTo(currentLocation, 16f)
            awaitingInitialFix = false
        }
    }

    val routeName = name.trim().ifBlank { "${strings[K.create_route]} ${System.currentTimeMillis() / 1000}" }
    val draft = SimRoute(
        id = existing?.id ?: "",
        name = routeName,
        points = points.toList(),
        profile = profile,
        customSpeedKmh = existing?.customSpeedKmh ?: settings.customSpeedKmh.toDouble(),
        loop = loop,
    )
    val isValid = points.size >= 2 || (points.size == 1 && realSample?.point != null)

    val profiles = listOf(
        Triple(SpeedProfile.WALKING, Icons.Default.DirectionsWalk, strings[K.walking]),
        Triple(SpeedProfile.DRIVING, Icons.Default.DirectionsCar, strings[K.driving]),
        Triple(SpeedProfile.CYCLING, Icons.Default.DirectionsBike, strings[K.cycling]),
        Triple(SpeedProfile.RUNNING, Icons.Default.DirectionsRun, "Run"),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        // Use the actual sheet viewport, not a fixed dp height. The map occupies 75%
        // and the remaining 25% holds a compact, non-scrolling route editor footer.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight(0.96f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp),
        ) {
            val mapHeight = maxHeight * 0.75f
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mapHeight)
                        .clip(RoundedCornerShape(20.dp)),
                ) {
                    MapView(
                        camera = camera,
                        mapType = settings.mapType,
                        markers = buildList {
                            currentLocation?.let { point ->
                                add(
                                    MapMarker(
                                        point = point,
                                        color = MaterialTheme.colorScheme.primary,
                                        radiusMeters = if (activePoint == null) {
                                            realSample?.accuracy?.toDouble() ?: settings.accuracyM.toDouble()
                                        } else settings.accuracyM.toDouble(),
                                        style = MarkerStyle.REAL,
                                    ),
                                )
                            }
                            points.forEachIndexed { index, point ->
                                add(
                                    MapMarker(
                                        point = point,
                                        color = when (index) {
                                            0 -> appColors.success
                                            points.lastIndex -> appColors.danger
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                    ),
                                )
                            }
                        },
                        polylines = if (points.size >= 2) {
                            listOf(MapPolyline(points.toList(), MaterialTheme.colorScheme.primary))
                        } else emptyList(),
                        onMapTap = { tapped -> points.add(tapped) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Point count and route looping overlay the map without shrinking it.
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${points.size} ${strings[K.waypoints]}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(strings[K.loop_route], style = MaterialTheme.typography.labelSmall)
                        Switch(checked = loop, onCheckedChange = { loop = it })
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SmallMapAction(Icons.Default.Add, strings[K.add_waypoint]) {
                            points.add(camera.center)
                        }
                        SmallMapAction(Icons.Default.MyLocation, strings[K.my_location]) {
                            currentLocation?.let {
                                camera.isUserInteracting = false
                                camera.snapTo(it, 16f)
                            }
                        }
                        if (points.isNotEmpty()) {
                            SmallMapAction(Icons.Default.Close, strings[K.delete]) {
                                points.removeAt(points.lastIndex)
                            }
                        }
                    }
                }

                // The route title and name now appear BELOW the larger map.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = strings[K.create_route],
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Button(
                            onClick = {
                                onSave(
                                    draft.copy(
                                        id = existing?.id ?: UUID.randomUUID().toString(),
                                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                                    ),
                                )
                            },
                            enabled = isValid,
                            modifier = Modifier.height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(strings[K.save], style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    val isArabic = settings.language == AppLanguage.ARABIC
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = {
                                Text(
                                    strings[K.route_name],
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = if (isArabic) TextAlign.Right else TextAlign.Left,
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = if (isArabic) TextAlign.Right else TextAlign.Left,
                                textDirection = if (isArabic) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            // Material text fields have a 56dp minimum height. A forced
                            // 48dp height clips the Arabic baseline and pushes text down.
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        profiles.forEach { (value, icon, label) ->
                            val selected = profile == value
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    )
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .clickable(role = Role.Button) { profile = value },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        Button(
                            onClick = {
                                onStart(
                                    draft.copy(
                                        id = existing?.id ?: UUID.randomUUID().toString(),
                                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                                    ),
                                )
                            },
                            enabled = isValid,
                            modifier = Modifier.weight(1.8f).heightIn(min = 52.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = if (settings.language == AppLanguage.GERMAN) "Starten" else strings[K.start_route],
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutePointsPanel(
    pointsCount: Int,
    onAdd: () -> Unit,
) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(22.dp),
            )
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                RoundedCornerShape(22.dp),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = strings[K.create_route],
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape)
                    .clickable(role = Role.Button, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = strings[K.add_waypoint],
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (pointsCount == 0) {
            Text(
                text = strings[K.tap_map_to_add],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            repeat(pointsCount.coerceAtMost(5)) { index ->
                val isLast = index == pointsCount - 1 && pointsCount > 1
                val label = when {
                    index == 0 -> strings[K.start_point]
                    isLast -> strings[K.destination]
                    else -> "Waypoint $index"
                }
                WaypointRow(number = index + 1, label = label, highlighted = index == 0)
                if (index < pointsCount.coerceAtMost(5) - 1) Spacer(Modifier.height(8.dp))
            }
            if (pointsCount > 5) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "+${pointsCount - 5} ${strings[K.waypoints]}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun WaypointRow(number: Int, label: String, highlighted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(
                    if (highlighted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MovementProfilePanel(
    selected: SpeedProfile,
    onSelected: (SpeedProfile) -> Unit,
) {
    val strings = LocalStrings.current
    val profiles = listOf(
        Triple(SpeedProfile.WALKING, Icons.Default.DirectionsWalk, strings[K.walking]),
        Triple(SpeedProfile.DRIVING, Icons.Default.DirectionsCar, strings[K.driving]),
        Triple(SpeedProfile.CYCLING, Icons.Default.DirectionsBike, strings[K.cycling]),
        Triple(SpeedProfile.RUNNING, Icons.Default.DirectionsRun, "Run"),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(22.dp),
            )
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                RoundedCornerShape(22.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            text = "Movement Profile",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            profiles.forEach { (profile, icon, label) ->
                val active = selected == profile
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Button) { onSelected(profile) },
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .border(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                                shape = RoundedCornerShape(16.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallMapAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}
