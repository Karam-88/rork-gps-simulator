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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

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

@Composable
fun GpsReadinessChecklist(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Read Android's system language, independent of the language saved by the app.
    val systemLanguage = Resources.getSystem().configuration.locales[0].language
    var languageOverride by remember { mutableStateOf<String?>(null) }
    val deviceLanguage = languageOverride ?: systemLanguage
    val isArabic = deviceLanguage == "ar"
    fun localized(ar: String, de: String, en: String) = when (deviceLanguage) {
        "ar" -> ar
        "de" -> de
        else -> en
    }
    var status by remember { mutableStateOf(readSetup(context)) }
    val refresh = { status = readSetup(context) }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val completed = listOf(status.developer, status.mockSelected, status.locationGranted, status.locationEnabled).count { it }
    val allRequiredReady = completed == 4
    val title = localized("إعداد GPS Simulator", "GPS Simulator einrichten", "Set up GPS Simulator")
    val subtitle = localized("تُحدَّث علامات التحقق تلقائيًا عند العودة من الإعدادات", "Die Prüfungen aktualisieren sich automatisch nach der Rückkehr aus den Einstellungen", "Checks update automatically when you return from Settings")

    CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
    Column(
        modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            localized("لغة التعليمات", "Sprache der Anleitung", "Instructions language"),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                null to localized("تلقائي", "Automatisch", "Auto"),
                "ar" to "العربية",
                "de" to "Deutsch",
                "en" to "English",
            ).forEach { (choice, label) ->
                val selected = choice == languageOverride
                if (selected) {
                    Button(
                        onClick = { languageOverride = choice },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                } else {
                    OutlinedButton(
                        onClick = { languageOverride = choice },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { completed / 4f },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(localized("$completed من 4 متطلبات", "$completed von 4 Voraussetzungen", "$completed of 4 requirements"))

        ChecklistRow(
            localized("خيارات المطوّر", "Entwickleroptionen", "Developer options"),
            status.developer,
            localized("فتح الإعدادات", "Einstellungen", "Open settings"),
        ) {
            openSetting(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
        ChecklistRow(
            localized("اختيار GPS Simulator كتطبيق الموقع الوهمي", "GPS Simulator als App für simulierte Standorte auswählen", "Select GPS Simulator as mock location app"),
            status.mockSelected,
            localized("اختيار التطبيق", "App auswählen", "Select app"),
        ) {
            openSetting(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
        ChecklistRow(
            localized("السماح بالموقع الدقيق", "Genauen Standort erlauben", "Allow precise location"),
            status.locationGranted,
            localized("طلب الإذن", "Erlauben", "Grant access"),
        ) {
            locationLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ))
        }
        ChecklistRow(
            localized("تشغيل خدمات الموقع", "Standortdienste aktiviert", "Location services enabled"),
            status.locationEnabled,
            localized("إعدادات الموقع", "Standorteinstellungen", "Location settings"),
        ) {
            openSetting(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }

        HorizontalDivider()
        Text(
            localized("موصى به لاستقرار التشغيل في الخلفية", "Empfohlen für zuverlässigen Hintergrundbetrieb", "Recommended for background reliability"),
            style = MaterialTheme.typography.titleSmall,
        )
        ChecklistRow(
            localized("استثناء التطبيق من تحسين البطارية (اختياري)", "Akkuoptimierung für die App deaktivieren (optional)", "Battery optimization exemption (optional)"),
            status.batteryExempt,
            localized("إعدادات البطارية", "Akku-Einstellungen", "Battery settings"),
        ) {
            // Samsung/One UI often omits the app from the generic optimization list.
            // Open this app's own settings first so the user can select Battery > Unrestricted.
            openSetting(
                context,
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                Intent(Settings.ACTION_SETTINGS),
            )
        }
        Text(
            localized(
                "على Samsung: التطبيقات ← GPS Simulator ← البطارية ← غير مقيّد. قد لا تتطابق علامة الفحص مع إعدادات Samsung؛ هذا الخيار اختياري.",
                "Auf Samsung: Apps → GPS Simulator → Akku → Uneingeschränkt. Der Haken erkennt nicht alle Samsung-Akkueinstellungen; dieser Schritt ist optional.",
                "On Samsung: Apps → GPS Simulator → Battery → Unrestricted. The check may not reflect every Samsung battery setting; this step is optional.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            localized("تشير العلامات إلى جاهزية إعدادات Android، لكنها لا تضمن قبول الموقع لدى جميع التطبيقات. اختبر استقرار الموقع أثناء تشغيل المحاكاة.", "Die Häkchen bestätigen die Android-Einstellungen, nicht die Übernahme des Standorts durch jede App. Prüfe die Stabilität während einer Simulation.", "Checks indicate Android setup readiness, not acceptance by every app. Test stability with an active session."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onFinished,
            enabled = allRequiredReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(localized("الدخول إلى الخريطة", "Weiter zur Karte", "Continue to map"))
        }
        OutlinedButton(onClick = refresh, modifier = Modifier.fillMaxWidth()) {
            Text(localized("إعادة الفحص", "Erneut prüfen", "Check again"))
        }
        TextButton(onClick = onFinished, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(localized("لاحقًا (قد لا تعمل المحاكاة)", "Später (Simulation funktioniert möglicherweise nicht)", "Later (mocking may not work)"))
        }
        Text(
            localized("إعدادات الإصدار التجريبي 2", "Test-Einrichtung 2", "Test setup 2"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
    }
}

@Composable
private fun ChecklistRow(title: String, checked: Boolean, action: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (checked) "Ready" else "Not ready",
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (!checked) TextButton(onClick = onClick) { Text(action) }
        }
    }
}
