package com.rork.gpssimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.data.model.SimRoute
import com.rork.gpssimulator.data.model.SpeedProfile
import com.rork.gpssimulator.data.model.UnitSystem
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.components.ConfirmDialog
import com.rork.gpssimulator.ui.components.EmptyState
import com.rork.gpssimulator.ui.components.InfoPill
import com.rork.gpssimulator.ui.theme.LocalAppColors
import com.rork.gpssimulator.ui.theme.MonoValueStyle
import com.rork.gpssimulator.util.Format

fun profileIcon(profile: SpeedProfile): ImageVector = when (profile) {
    SpeedProfile.WALKING -> Icons.Default.DirectionsWalk
    SpeedProfile.CYCLING -> Icons.Default.DirectionsBike
    SpeedProfile.DRIVING -> Icons.Default.DirectionsCar
    SpeedProfile.RUNNING -> Icons.Default.DirectionsRun
    SpeedProfile.CUSTOM -> Icons.Default.Speed
}

@Composable
fun profileLabel(profile: SpeedProfile): String {
    val strings = LocalStrings.current
    return when (profile) {
        SpeedProfile.WALKING -> strings[K.walking]
        SpeedProfile.CYCLING -> strings[K.cycling]
        SpeedProfile.DRIVING -> strings[K.driving]
        SpeedProfile.RUNNING -> "Run"
        SpeedProfile.CUSTOM -> strings[K.custom]
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    viewModel: AppViewModel,
    bottomInset: androidx.compose.ui.unit.Dp,
    onRouteStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val routes by viewModel.routes.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val progress by viewModel.routeProgress.collectAsState()
    val elapsedMs by viewModel.elapsedMs.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()

    var query by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SimRoute?>(null) }
    var pendingDelete by remember { mutableStateOf<SimRoute?>(null) }

    val filtered = remember(routes, query) {
        if (query.isBlank()) routes
        else routes.filter { it.name.contains(query, ignoreCase = true) }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings[K.routes_title],
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = null
                    showEditor = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = bottomInset),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(strings[K.create_route], style = MaterialTheme.typography.labelLarge)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            progress?.let { running ->
                RouteProgressPanel(
                    routeName = running.route.name,
                    traveled = running.traveledMeters,
                    total = running.totalMeters,
                    remaining = running.remainingMeters,
                    speedKmh = running.speedKmh,
                    etaSeconds = running.etaSeconds,
                    elapsedMs = elapsedMs,
                    isPaused = isPaused,
                    units = settings.units,
                    onTogglePause = { viewModel.togglePause() },
                    onStop = { viewModel.stopMock() },
                )
            }

            if (routes.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(strings[K.search_routes], style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.Route,
                        title = strings[K.no_routes],
                        body = strings[K.no_routes_body],
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = bottomInset + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.id }) { route ->
                        RouteCard(
                            route = route,
                            units = settings.units,
                            onStart = {
                                if (viewModel.startRoute(route)) {
                                    onRouteStarted()
                                }
                            },
                            onEdit = {
                                editing = route
                                showEditor = true
                            },
                            onDelete = { pendingDelete = route },
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        RouteEditorSheet(
            existing = editing,
            viewModel = viewModel,
            onSave = { route ->
                viewModel.saveRoute(route)
                viewModel.postMessage(strings[K.route_saved])
                showEditor = false
                editing = null
            },
            onDismiss = {
                showEditor = false
                editing = null
            },
        )
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = strings[K.delete_route],
            body = target.name,
            confirmLabel = strings[K.delete],
            cancelLabel = strings[K.cancel],
            destructive = true,
            onConfirm = { viewModel.deleteRoute(target.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun RouteCard(
    route: SimRoute,
    units: UnitSystem,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onStart)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = profileIcon(route.profile),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${route.points.size} ${strings[K.waypoints]}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.muted,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = appColors.muted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(strings[K.start_route], style = MaterialTheme.typography.bodyLarge) },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onStart()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(strings[K.edit], style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = strings[K.delete],
                                style = MaterialTheme.typography.bodyLarge,
                                color = appColors.danger,
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = appColors.danger)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill(text = Format.distance(route.distanceMeters, units))
            InfoPill(text = Format.duration(route.durationSeconds))
            InfoPill(
                text = profileLabel(route.profile),
                color = MaterialTheme.colorScheme.primary,
            )
            if (route.loop) {
                InfoPill(
                    text = strings[K.loop_route],
                    color = appColors.warning,
                    leading = {
                        Icon(
                            Icons.Default.Loop,
                            contentDescription = null,
                            tint = appColors.warning,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun RouteProgressPanel(
    routeName: String,
    traveled: Double,
    total: Double,
    remaining: Double,
    speedKmh: Double,
    etaSeconds: Double,
    elapsedMs: Long,
    isPaused: Boolean,
    units: UnitSystem,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val fraction = if (total > 0) (traveled / total).toFloat().coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = routeName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onTogglePause) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) strings[K.resume] else strings[K.pause],
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            IconButton(onClick = onStop) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = strings[K.stop],
                    tint = appColors.danger,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f),
        )

        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ProgressStat(strings[K.traveled], Format.distance(traveled, units), Modifier.weight(1f))
            ProgressStat(strings[K.remaining], Format.distance(remaining, units), Modifier.weight(1f))
            ProgressStat(strings[K.speed], Format.speed(speedKmh, units), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ProgressStat(strings[K.elapsed], Format.elapsed(elapsedMs), Modifier.weight(1f))
            ProgressStat(strings[K.eta], Format.duration(etaSeconds), Modifier.weight(1f))
            ProgressStat(strings[K.distance], Format.distance(total, units), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProgressStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MonoValueStyle.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
        )
    }
}
