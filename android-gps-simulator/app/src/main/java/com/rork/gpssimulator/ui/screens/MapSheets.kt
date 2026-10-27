package com.rork.gpssimulator.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.MapType
import com.rork.gpssimulator.data.model.UnitSystem
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.ui.SelectedLocation
import com.rork.gpssimulator.ui.components.StatusDot
import com.rork.gpssimulator.ui.map.MapMarker
import com.rork.gpssimulator.ui.map.MapView
import com.rork.gpssimulator.ui.map.MarkerStyle
import com.rork.gpssimulator.ui.map.rememberMapCameraState
import com.rork.gpssimulator.ui.theme.LocalAppColors
import com.rork.gpssimulator.ui.theme.MonoValueStyle
import com.rork.gpssimulator.util.Format

/**
 * The single, deliberate confirmation step between aiming the crosshair and
 * changing the system location. Tapping the primary button here acts immediately
 * — there is no second confirmation dialog.
 *
 * When [isMockActive] is true the sheet describes a *candidate* coordinate: the
 * primary action becomes "Move Here" (redirect the live session) and dismissing
 * only cancels the candidate, never the session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPreviewSheet(
    selection: SelectedLocation,
    units: UnitSystem,
    mapType: MapType,
    isMockActive: Boolean,
    onDismiss: () -> Unit,
    onSaveFavorite: () -> Unit,
    onCopied: () -> Unit,
    onPrimary: () -> Unit,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isSaved by remember(selection.point) { mutableStateOf(false) }

    val previewCamera = rememberMapCameraState(selection.point, 15f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            if (isMockActive) {
                // Makes it unmistakable that the running session is still at its
                // own coordinate until the user confirms the move.
                Row(
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(color = MaterialTheme.colorScheme.primary, size = 10)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings[K.new_location_selected],
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Map snippet of the pinned point.
            MapView(
                camera = previewCamera,
                mapType = mapType,
                markers = listOf(
                    MapMarker(
                        point = selection.point,
                        color = MaterialTheme.colorScheme.primary,
                        style = MarkerStyle.CANDIDATE,
                    ),
                ),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = selection.name.ifBlank { strings[K.place_unknown] },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (selection.address.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = selection.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.muted,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Exact coordinates.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Format.coordPair(selection.point),
                    style = MonoValueStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PreviewAction(
                    icon = Icons.Default.ContentCopy,
                    label = strings[K.copy],
                    modifier = Modifier.weight(1f),
                ) {
                    copyToClipboard(context, Format.coordPair(selection.point))
                    onCopied()
                }
                PreviewAction(
                    icon = Icons.Default.Share,
                    label = strings[K.share],
                    modifier = Modifier.weight(1f),
                ) {
                    shareCoordinate(context, selection.point, selection.name)
                }
                PreviewAction(
                    icon = if (isSaved) Icons.Default.Star else Icons.Default.StarBorder,
                    label = if (isSaved) strings[K.saved] else strings[K.save_favorite],
                    tint = if (isSaved) appColors.warning else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                ) {
                    if (!isSaved) {
                        onSaveFavorite()
                        isSaved = true
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = if (isMockActive) {
                        Icons.Default.MyLocation
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (isMockActive) {
                        strings[K.move_here]
                    } else {
                        strings[K.start_mock_location]
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (isMockActive) {
                Spacer(Modifier.height(6.dp))
                // Cancels the candidate only. Stopping the session is exclusively
                // the red X on the map.
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = strings[K.cancel_selection],
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.muted,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun PreviewAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * Persistent telemetry panel shown only while the system-level mock location is
 * confirmed running. Values update live as the mocked coordinate changes.
 *
 * There is intentionally no close affordance here: stopping is exclusively the
 * large red X at the bottom of the map.
 */
@Composable
fun MockActiveBanner(
    lat: Double,
    lng: Double,
    accuracy: Float,
    altitude: Float,
    speedKmh: Double,
    elapsedMs: Long,
    isPaused: Boolean,
    units: UnitSystem,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val transition = rememberInfiniteTransition(label = "bannerPulse")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dotAlpha",
    )

    var detailsExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { detailsExpanded = !detailsExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                StatusDot(
                    color = if (isPaused) appColors.warning
                    else MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha),
                    size = 8,
                )
                Text(
                    text = strings[K.mock_location_active_banner],
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Format.elapsed(elapsedMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = if (detailsExpanded) "⌃" else "⌄",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(Modifier.height(5.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(strings[K.lat], Format.coord(lat), Modifier.weight(1f))
                StatCell(strings[K.lng], Format.coord(lng), Modifier.weight(1f))
            }

            if (detailsExpanded) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell(
                        strings[K.altitude],
                        Format.shortDistance(altitude.toDouble(), units),
                        Modifier.weight(1f),
                    )
                    StatCell(strings[K.speed], Format.speed(speedKmh, units), Modifier.weight(1f))
                    StatCell(
                        strings[K.accuracy],
                        Format.shortDistance(accuracy.toDouble(), units),
                        Modifier.weight(1f),
                    )
                }
                if (isPaused) {
                    Text(
                        text = strings[K.pause],
                        style = MaterialTheme.typography.labelSmall,
                        color = appColors.warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MonoValueStyle,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
        )
    }
}

fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("coordinates", text))
}

fun shareCoordinate(context: Context, point: LatLng, name: String) {
    val label = name.ifBlank { "Location" }
    val body = "$label\n${Format.coordPair(point)}\nhttps://maps.google.com/?q=${point.lat},${point.lng}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, label))
}
