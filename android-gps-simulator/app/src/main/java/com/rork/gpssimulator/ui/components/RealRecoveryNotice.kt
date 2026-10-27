package com.rork.gpssimulator.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.gpssimulator.i18n.*

/** Non-modal: only the close/retry controls consume touches over the map. */
@Composable
internal fun RealRecoveryNotice(
    status: RealRecoveryStatus?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val labels = realRecoveryLabels(strings.language)
    // Retain the final content through the short exit transition.
    var lastStatus by remember { mutableStateOf(RealRecoveryStatus.ACQUIRING) }
    LaunchedEffect(status) { if (status != null) lastStatus = status }
    val shown = status ?: lastStatus
    val loading = shown == RealRecoveryStatus.ACQUIRING
    val success = shown == RealRecoveryStatus.RESTORED
    val accent = when { loading -> Color(0xFF66B6FF); success -> Color(0xFF57E8C3); else -> Color(0xFFFFC27A) }
    val surface = when { loading -> Color(0xFF101D36); success -> Color(0xFF092D2A); else -> Color(0xFF30251D) }
    val iconScale = remember { Animatable(1f) }
    LaunchedEffect(shown) {
        if (success) {
            iconScale.snapTo(0.72f)
            iconScale.animateTo(1f, tween(320))
        } else iconScale.snapTo(1f)
    }
    AnimatedVisibility(status != null, modifier, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
        Row(
            Modifier.widthIn(max = 420.dp).fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(22.dp), ambientColor = accent.copy(alpha = 0.3f), spotColor = accent.copy(alpha = 0.25f))
                .background(Brush.horizontalGradient(listOf(surface, Color(0xFF09141F))), RoundedCornerShape(22.dp))
                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(46.dp).background(accent.copy(alpha = 0.10f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(Modifier.size(30.dp), color = accent, strokeWidth = 3.dp)
                else Icon(if (success) Icons.Default.CheckCircleOutline else Icons.Default.ErrorOutline,
                    contentDescription = null, tint = accent, modifier = Modifier.size(32.dp).scale(iconScale.value))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(realRecoveryMessage(strings.language, shown).trimEnd('…', '.'), color = Color.White,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (loading || success) Text(if (loading) labels.waiting else labels.restored,
                    color = Color(0xFFB9C8D7), fontSize = 12.sp)
                else TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(labels.retry, color = accent)
                }
            }
            if (!loading) IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, strings[K.close], tint = Color(0xFFB9C8D7), modifier = Modifier.size(20.dp))
            } else Spacer(Modifier.width(4.dp))
        }
    }
}
