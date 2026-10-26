package com.rork.gpssimulator.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Only shows the update indicator when Google Play confirms an eligible update.
 * This is deliberately independent of the existing map control styling.
 */
@Composable
fun rememberPlayUpdateAvailable(): Boolean {
    val context = LocalContext.current
    var available by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        runCatching {
            AppUpdateManagerFactory.create(context).appUpdateInfo
                .addOnSuccessListener { info ->
                    available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                }
                .addOnFailureListener { available = false }
        }.onFailure { available = false }
    }
    return available
}

@Composable
fun UpdateNotificationDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(11.dp)
            .background(Color(0xFFFF454E), CircleShape)
    )
}

@Composable
fun AppUpdateNoticeDialog(arabic: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val direction = if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(24.dp),
            containerColor = Color(0xFF0D1C2E),
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.size(62.dp).background(Color(0xFF193B65), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.FileDownload, null, tint = Color(0xFF57ADFF), modifier = Modifier.size(32.dp))
                    }
                    Text(
                        if (arabic) "تحديث جديد متوفر!" else "New update available!",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            },
            text = {
                Text(
                    if (arabic) "يتوفر إصدار أحدث من GPS Simulator. يتضمن تحسينات في الأداء وإصلاحات لبعض المشكلات."
                    else "A newer version of GPS Simulator is available, with performance improvements and bug fixes.",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFB9C8D9),
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.rork.gpssimulator"))
                            runCatching { context.startActivity(intent) }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087CFF)),
                    ) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(if (arabic) "تحديث التطبيق" else "Update app", fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (arabic) "لاحقًا — متابعة استخدام الإصدار الحالي" else "Later — continue with current version",
                            color = Color(0xFFB9C8D9),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
            dismissButton = {},
        )
    }
}
