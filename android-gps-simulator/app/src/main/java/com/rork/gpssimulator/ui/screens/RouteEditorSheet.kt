package com.rork.gpssimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.SimRoute
import com.rork.gpssimulator.data.model.SpeedProfile
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.map.MapMarker
import com.rork.gpssimulator.ui.map.MapPolyline
import com.rork.gpssimulator.ui.map.MapView
import com.rork.gpssimulator.ui.map.rememberMapCameraState
import com.rork.gpssimulator.ui.theme.LocalAppColors
import com.rork.gpssimulator.ui.theme.MonoValueStyle
import com.rork.gpssimulator.util.Format
import java.util.UUID

/**
 * Route builder: tap the embedded map to append waypoints, pick a speed profile,
 * and optionally loop the route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditorSheet(
    existing: SimRoute?,
    viewModel: AppViewModel,
    onSave: (SimRoute) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings by viewModel.settings.collectAsState()
    val realSample by viewModel.realSample.collectAsState()
    val activePoint by viewModel.activePoint.collectAsState()

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var profile by remember { mutableStateOf(existing?.profile ?: settings.speedProfile) }
    var customSpeed by remember {
        mutableStateOf((existing?.customSpeedKmh ?: settings.customSpeedKmh.toDouble()).toString())
    }
    var loop by remember { mutableStateOf(existing?.loop ?: false) }
    val points = remember {
        mutableStateListOf<LatLng>().apply { existing?.points?.let { addAll(it) } }
    }

    val currentLocation = activePoint ?: realSample?.point
    val camera = rememberMapCameraState(
        initialCenter = existing?.points?.firstOrNull()
            ?: currentLocation
            ?: LatLng(settings.lastCameraLat, settings.lastCameraLng),
        initialZoom = 14f,
    )

    val customSpeedValue = customSpeed.trim().toDoubleOrNull() ?: 0.0
    val isValid = name.isNotBlank() && points.size >= 2 &&
        (profile != SpeedProfile.CUSTOM || customSpeedValue > 0)

    val draft = SimRoute(
        id = existing?.id ?: "",
        name = name,
        points = points.toList(),
        profile = profile,
        customSpeedKmh = customSpeedValue,
        loop = loop,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = strings[K.create_route],
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings[K.route_name], style = MaterialTheme.typography.bodyLarge) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = strings[K.tap_map_to_add],
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.muted,
            )

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(18.dp)),
            ) {
                MapView(
                    camera = camera,
                    mapType = settings.mapType,
                    markers = points.mapIndexed { index, point ->
                        MapMarker(
                            point = point,
                            color = when (index) {
                                0 -> appColors.success
                                points.lastIndex -> appColors.danger
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                    },
                    polylines = if (points.size >= 2) {
                        listOf(MapPolyline(points.toList(), MaterialTheme.colorScheme.primary))
                    } else emptyList(),
                    onMapTap = { tapped -> points.add(tapped) },
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (points.isNotEmpty()) {
                        SmallMapAction(Icons.Default.Close, strings[K.delete]) {
                            points.removeAt(points.lastIndex)
                        }
                    }
                    SmallMapAction(Icons.Default.MyLocation, strings[K.my_location]) {
                        (activePoint ?: realSample?.point)?.let { point ->
                            camera.snapTo(point)
                        }
                    }
                    SmallMapAction(Icons.Default.Add, strings[K.add_waypoint]) {
                        points.add(camera.center)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${points.size} ${strings[K.waypoints]}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (points.size >= 2) {
                    Text(
                        text = Format.distance(draft.distanceMeters, settings.units),
                        style = MonoValueStyle.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (points.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = strings[K.route_points_required],
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.muted,
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = strings[K.speed_profile],
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeedProfile.entries.forEach { option ->
                    FilterChip(
                        selected = profile == option,
                        onClick = { profile = option },
                        label = {
                            Text(profileLabel(option), style = MaterialTheme.typography.bodyMedium)
                        },
                        leadingIcon = if (profile == option) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                    )
                }
            }

            if (profile == SpeedProfile.CUSTOM) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = customSpeed,
                    onValueChange = { customSpeed = it },
                    label = {
                        Text(strings[K.custom_speed], style = MaterialTheme.typography.bodyLarge)
                    },
                    suffix = { Text("km/h", style = MaterialTheme.typography.bodyLarge) },
                    singleLine = true,
                    isError = customSpeed.isNotBlank() && customSpeedValue <= 0,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings[K.loop_route],
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Switch(checked = loop, onCheckedChange = { loop = it })
            }

            Spacer(Modifier.height(22.dp))

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
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = strings[K.save],
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SmallMapAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}
