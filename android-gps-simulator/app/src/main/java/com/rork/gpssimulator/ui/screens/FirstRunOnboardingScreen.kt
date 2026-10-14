package com.rork.gpssimulator.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * First-run onboarding for the Android mock-location setup that GPS Simulator
 * needs before it can send simulated coordinates to the system.
 */
@Composable
fun FirstRunOnboardingScreen(
    isArabic: Boolean,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    val title = when (step) {
        0 -> if (isArabic) "مرحبًا بك في GPS Simulator" else "Welcome to GPS Simulator"
        1 -> if (isArabic) "فعّل خيارات المطوّر" else "Enable Developer Options"
        else -> if (isArabic) "اختر تطبيق الموقع الوهمي" else "Select the mock location app"
    }
    val body = when (step) {
        0 -> if (isArabic) {
            "يساعدك GPS Simulator على محاكاة موقع GPS ومسارات الحركة لأغراض الاختبار والتطوير. قبل البدء، يحتاج Android إلى إعداد سريع لمرة واحدة."
        } else {
            "GPS Simulator lets you simulate GPS locations and routes for testing and development. Android needs a quick one-time setup before you start."
        }
        1 -> if (isArabic) {
            "افتح إعدادات الهاتف ← حول الهاتف، ثم اضغط على رقم الإصدار (Build number) عدة مرات حتى تظهر رسالة تفعيل خيارات المطوّر."
        } else {
            "Open phone Settings → About phone, then tap Build number several times until Android confirms that Developer Options are enabled."
        }
        else -> if (isArabic) {
            "افتح خيارات المطوّر ← اختيار تطبيق الموقع الوهمي (Select mock location app) ← اختر GPS Simulator. بعدها عد إلى التطبيق واضغط ابدأ."
        } else {
            "Open Developer Options → Select mock location app → choose GPS Simulator. Then return here and tap Get Started."
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 5.dp)
                            .size(if (index == step) 11.dp else 8.dp)
                            .background(
                                if (index == step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (step) {
                        0 -> Icons.Default.LocationOn
                        1 -> Icons.Default.Settings
                        else -> Icons.Default.Check
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (step >= 1) {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        }.onFailure {
                            context.startActivity(
                                Intent(Settings.ACTION_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (isArabic) "فتح إعدادات المطوّر" else "Open Developer Options")
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    if (step < 2) step++ else onFinished()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    text = when {
                        step < 2 && isArabic -> "التالي"
                        step < 2 -> "Next"
                        isArabic -> "ابدأ"
                        else -> "Get Started"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
