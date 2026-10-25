package com.rork.gpssimulator.ui.screens

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.res.Resources
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.rork.gpssimulator.i18n.AppLanguage

/**
 * Checks actual Android state, never stores a manually checked checkbox.
 * Battery exemption is recommended, not required to use the app.
 * Successful provider injection cannot be asserted without a live mock session.
 */
private data class SetupStatus(
    val developer: Boolean,
    val mockSelected: Boolean,
    val locationGranted: Boolean,
    val locationEnabled: Boolean,
    val batteryExempt: Boolean,
)

private fun readSetup(context: Context): SetupStatus {
    val developer = Settings.Global.getInt(
        context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0,
    ) == 1
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mockSelected = try {
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_MOCK_LOCATION,
            android.os.Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }
    val permission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        manager.isLocationEnabled
    } else {
        @Suppress("DEPRECATION")
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
    }
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return SetupStatus(
        developer, mockSelected, permission, locationEnabled,
        power.isIgnoringBatteryOptimizations(context.packageName),
    )
}

private fun openSetting(context: Context, intent: Intent, fallback: Intent = Intent(Settings.ACTION_SETTINGS)) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { /* OEM has no matching settings activity */ }
    }
}

/**
 * Android does not expose a public intent for the mock-location app chooser.
 * Open Developer options, requesting highlight of its mock-location row on
 * Settings builds that support this optional fragment argument.
 */
private fun openMockLocationPickerSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
        putExtra(":settings:fragment_args_key", "mock_location_app")
    }
    openSetting(context, intent, Intent(Settings.ACTION_SETTINGS))
}

@Composable
fun GpsReadinessChecklist(
    onFinished: () -> Unit,
    selectedLanguage: AppLanguage,
    followSystemLanguage: Boolean,
    onLanguageSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val systemLanguage = Resources.getSystem().configuration.locales[0].language
    val language = if (followSystemLanguage) systemLanguage else selectedLanguage.languageCode
    var languageMenuExpanded by remember { mutableStateOf(false) }
    fun t(ar: String, de: String, en: String) = when (language) {
        "ar" -> ar
        "de" -> de
        else -> en
    }
    var status by remember { mutableStateOf(readSetup(context)) }
    val refresh = { status = readSetup(context) }
    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val completed = listOf(
        status.developer, status.mockSelected, status.locationGranted, status.locationEnabled,
    ).count { it }
    val ready = completed == 4
    val background = Color(0xFF171B22)
    val cardColor = Color(0xFF282E38)
    val labelColor = Color(0xFFEFF4FF)
    val muted = Color(0xFFBAC4D3)
    val blue = Color(0xFF5C94FF)

    // A non-dismissible onboarding gate. Settings intents can still be opened,
    // and all required checks are automatically re-evaluated on resume.
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth(0.90f)
                    .fillMaxHeight(0.86f),
                shape = RoundedCornerShape(30.dp),
                color = background,
                shadowElevation = 20.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        t("إعداد GPS Simulator", "GPS Simulator einrichten", "Set up GPS Simulator"),
                        color = labelColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        t(
                            "فعّل المتطلبات الأساسية التالية لتشغيل الموقع الوهمي. سيتم التحقق منها تلقائيًا.",
                            "Aktiviere diese Voraussetzungen für simulierte Standorte. Die Prüfung erfolgt automatisch.",
                            "Enable these required settings for mock locations. Checks update automatically.",
                        ),
                        color = muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // A dropdown is usable on small phones and supports all language names.
                    Box {
                        OutlinedButton(
                            onClick = { languageMenuExpanded = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = labelColor),
                            border = BorderStroke(1.dp, blue.copy(alpha = 0.6f)),
                        ) {
                            Text(
                                if (followSystemLanguage) t("تلقائي · لغة الجهاز", "Automatisch · Gerätesprache", "Automatic · Phone language")
                                else selectedLanguage.nativeName,
                            )
                        }
                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(t("تلقائي · لغة الجهاز", "Automatisch · Gerätesprache", "Automatic · Phone language")) },
                                onClick = {
                                    languageMenuExpanded = false
                                    onLanguageSelected(null)
                                },
                            )
                            AppLanguage.entries.forEach { available ->
                                DropdownMenuItem(
                                    text = { Text(available.nativeName) },
                                    onClick = {
                                        languageMenuExpanded = false
                                        onLanguageSelected(available.languageCode)
                                    },
                                )
                            }
                        }
                    }
                    LinearProgressIndicator(
                        progress = { completed / 4f },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                        color = blue,
                        trackColor = Color(0xFF424A56),
                    )
                    Text(
                        t("$completed من 4 متطلبات", "$completed von 4 Voraussetzungen", "$completed of 4 requirements"),
                        color = labelColor,
                        style = MaterialTheme.typography.labelMedium,
                    )

                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        GpsSetupStep(
                            title = t("خيارات المطوّر", "Entwickleroptionen", "Developer options"),
                            actionLabel = t("فتح", "Öffnen", "Open"),
                            checked = status.developer,
                            background = cardColor,
                            completeLabel = t("مكتمل", "Erledigt", "Complete"),
                            requiredLabel = t("مطلوب", "Erforderlich", "Required"),
                            onClick = { openSetting(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                        )
                        GpsSetupStep(
                            title = t("اختيار GPS Simulator للموقع الوهمي", "GPS Simulator als Mock-App auswählen", "Select GPS Simulator as mock app"),
                            actionLabel = t("اختيار", "Wählen", "Select"),
                            checked = status.mockSelected,
                            background = cardColor,
                            completeLabel = t("مكتمل", "Erledigt", "Complete"),
                            requiredLabel = t("مطلوب", "Erforderlich", "Required"),
                            onClick = { openMockLocationPickerSettings(context) },
                        )
                        if (!status.mockSelected) {
                            Text(
                                t(
                                    "داخل خيارات المطوّر: انتقل إلى «الموقع» ← «اختيار تطبيق الموقع الجغرافي الوهمي» ثم اختر GPS Simulator.",
                                    "In Entwickleroptionen: Standort → App für simulierte Standorte auswählen → GPS Simulator.",
                                    "In Developer options: Location → Select mock location app → GPS Simulator.",
                                ),
                                color = muted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                        GpsSetupStep(
                            title = t("السماح بالموقع الدقيق", "Genauen Standort erlauben", "Allow precise location"),
                            actionLabel = t("السماح", "Erlauben", "Allow"),
                            checked = status.locationGranted,
                            background = cardColor,
                            completeLabel = t("مكتمل", "Erledigt", "Complete"),
                            requiredLabel = t("مطلوب", "Erforderlich", "Required"),
                            onClick = {
                                requestLocation.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ))
                            },
                        )
                        GpsSetupStep(
                            title = t("تشغيل خدمات الموقع", "Standortdienste aktivieren", "Enable location services"),
                            actionLabel = t("فتح", "Öffnen", "Open"),
                            checked = status.locationEnabled,
                            background = cardColor,
                            completeLabel = t("مكتمل", "Erledigt", "Complete"),
                            requiredLabel = t("مطلوب", "Erforderlich", "Required"),
                            onClick = { openSetting(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                        )
                        HorizontalDivider(color = Color(0xFF3D4552))
                        Text(
                            t("موصى به لاستقرار التشغيل (اختياري)", "Für Stabilität empfohlen (optional)", "Recommended for stability (optional)"),
                            color = muted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        GpsSetupStep(
                            title = t("إعدادات البطارية: غير مقيّد", "Akku: Uneingeschränkt", "Battery: Unrestricted"),
                            actionLabel = t("فتح", "Öffnen", "Open"),
                            checked = status.batteryExempt,
                            background = cardColor,
                            completeLabel = t("مكتمل", "Erledigt", "Complete"),
                            requiredLabel = t("مطلوب", "Erforderlich", "Required"),
                            onClick = {
                                openSetting(
                                    context,
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                )
                            },
                        )
                        Text(
                            t(
                                "قد لا يعكس فحص البطارية إعدادات بعض هواتف Samsung. هذا الخيار لا يمنع الدخول.",
                                "Die Batterieprüfung erkennt manche Samsung-Einstellungen nicht. Sie ist nicht verpflichtend.",
                                "Battery detection may not reflect some Samsung settings. It does not block entry.",
                            ),
                            color = muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Button(
                        onClick = { refresh(); if (readSetup(context).let { it.developer && it.mockSelected && it.locationGranted && it.locationEnabled }) onFinished() },
                        enabled = ready,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = blue,
                            contentColor = Color(0xFF09192F),
                            disabledContainerColor = Color(0xFF414956),
                            disabledContentColor = Color(0xFF8D98A9),
                        ),
                    ) {
                        Text(
                            t("متابعة إلى الخريطة", "Weiter zur Karte", "Continue to map"),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (!ready) {
                        Text(
                            t("أكمل المتطلبات الأربع للمتابعة", "Erfülle zuerst die vier Voraussetzungen", "Complete all four required checks to continue"),
                            color = muted,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GpsSetupStep(
    title: String,
    actionLabel: String,
    checked: Boolean,
    background: Color,
    onClick: () -> Unit,
    completeLabel: String,
    requiredLabel: String,
) {
    Surface(
        shape = RoundedCornerShape(17.dp),
        color = background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (checked) completeLabel else requiredLabel,
                tint = if (checked) Color(0xFF52D69B) else Color(0xFF8996A8),
                modifier = Modifier.size(23.dp),
            )
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = Color(0xFFF0F3FA),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!checked) {
                TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 5.dp)) {
                    Text(actionLabel, color = Color(0xFF7EA9FF), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
