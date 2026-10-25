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
    // Read the app's live Android configuration, not the static system Resources singleton.
    val systemLanguage = context.resources.configuration.locales[0].language
    val language = if (followSystemLanguage) systemLanguage else selectedLanguage.languageCode
    var languageMenuExpanded by remember { mutableStateOf(false) }
    fun t(ar: String, de: String, en: String) = when (language) {
        "ar" -> ar
        "de" -> de
        "fr" -> when (en) {
            "Automatic · Phone language" -> "Automatique · Langue du téléphone"
            "Recommended for stability (optional)" -> "Recommandé pour la stabilité (facultatif)"
            "Battery: Unrestricted" -> "Batterie : sans restriction"
            "Complete all four required checks to continue" -> "Effectuez les quatre vérifications requises pour continuer"
            "Enable these required settings for mock locations. Checks update automatically." -> "Activez ces paramètres pour les positions fictives. Les vérifications se mettent à jour automatiquement."
            "In Developer options: Location → Select mock location app → GPS Simulator." -> "Dans les options pour les développeurs : Localisation → Application de position fictive → GPS Simulator."
            "Battery detection may not reflect some Samsung settings. It does not block entry." -> "La détection de la batterie peut être imprécise sur certains Samsung. Elle ne bloque pas l'accès."
            "Select GPS Simulator as mock app" -> "Sélectionner GPS Simulator comme application de position fictive"
            "Allow precise location" -> "Autoriser la localisation précise"
            "Set up GPS Simulator" -> "Configurer GPS Simulator"
            "Continue to map" -> "Continuer vers la carte"
            "Developer options" -> "Options pour les développeurs"
            "Enable location services" -> "Activer les services de localisation"
            "Complete" -> "Terminé"
            "Required" -> "Obligatoire"
            "Open" -> "Ouvrir"
            "Select" -> "Sélectionner"
            "Allow" -> "Autoriser"
            else -> en
        }
        "es" -> when (en) {
            "Automatic · Phone language" -> "Automático · Idioma del teléfono"
            "Recommended for stability (optional)" -> "Recomendado para mayor estabilidad (opcional)"
            "Battery: Unrestricted" -> "Batería: sin restricciones"
            "Complete all four required checks to continue" -> "Completa las cuatro comprobaciones para continuar"
            "Enable these required settings for mock locations. Checks update automatically." -> "Activa estos ajustes para ubicaciones simuladas. Las comprobaciones se actualizan automáticamente."
            "In Developer options: Location → Select mock location app → GPS Simulator." -> "En Opciones de desarrollador: Ubicación → Seleccionar aplicación de ubicación simulada → GPS Simulator."
            "Battery detection may not reflect some Samsung settings. It does not block entry." -> "La detección de batería puede no reflejar algunos ajustes de Samsung. No impide continuar."
            "Select GPS Simulator as mock app" -> "Seleccionar GPS Simulator como aplicación de ubicación simulada"
            "Allow precise location" -> "Permitir ubicación precisa"
            "Set up GPS Simulator" -> "Configurar GPS Simulator"
            "Continue to map" -> "Continuar al mapa"
            "Developer options" -> "Opciones de desarrollador"
            "Enable location services" -> "Activar servicios de ubicación"
            "Complete" -> "Completado"
            "Required" -> "Obligatorio"
            "Open" -> "Abrir"
            "Select" -> "Seleccionar"
            "Allow" -> "Permitir"
            else -> en
        }
        "tr" -> when (en) {
            "Automatic · Phone language" -> "Otomatik · Telefon dili"
            "Recommended for stability (optional)" -> "Kararlılık için önerilir (isteğe bağlı)"
            "Battery: Unrestricted" -> "Pil: Kısıtlanmamış"
            "Complete all four required checks to continue" -> "Devam etmek için dört gerekli kontrolü tamamlayın"
            "Enable these required settings for mock locations. Checks update automatically." -> "Sahte konum için gerekli ayarları açın. Kontroller otomatik güncellenir."
            "In Developer options: Location → Select mock location app → GPS Simulator." -> "Geliştirici seçeneklerinde: Konum → Sahte konum uygulamasını seç → GPS Simulator."
            "Battery detection may not reflect some Samsung settings. It does not block entry." -> "Pil denetimi bazı Samsung ayarlarını algılamayabilir. Devam etmeyi engellemez."
            "Select GPS Simulator as mock app" -> "GPS Simulator uygulamasını sahte konum uygulaması seç"
            "Allow precise location" -> "Hassas konuma izin ver"
            "Set up GPS Simulator" -> "GPS Simulator kurulumu"
            "Continue to map" -> "Haritaya devam et"
            "Developer options" -> "Geliştirici seçenekleri"
            "Enable location services" -> "Konum hizmetlerini etkinleştir"
            "Complete" -> "Tamamlandı"
            "Required" -> "Gerekli"
            "Open" -> "Aç"
            "Select" -> "Seç"
            "Allow" -> "İzin ver"
            else -> en
        }
        "pt" -> when (en) {
            "Automatic · Phone language" -> "Automático · Idioma do telefone"
            "Recommended for stability (optional)" -> "Recomendado para estabilidade (opcional)"
            "Battery: Unrestricted" -> "Bateria: sem restrições"
            "Complete all four required checks to continue" -> "Conclua as quatro verificações para continuar"
            "Enable these required settings for mock locations. Checks update automatically." -> "Ative estas definições para localizações simuladas. As verificações atualizam automaticamente."
            "In Developer options: Location → Select mock location app → GPS Simulator." -> "Nas opções do desenvolvedor: Localização → Selecionar app de localização simulada → GPS Simulator."
            "Battery detection may not reflect some Samsung settings. It does not block entry." -> "A deteção da bateria pode não refletir algumas definições Samsung. Não impede a entrada."
            "Select GPS Simulator as mock app" -> "Selecionar GPS Simulator como app de localização simulada"
            "Allow precise location" -> "Permitir localização precisa"
            "Set up GPS Simulator" -> "Configurar GPS Simulator"
            "Continue to map" -> "Continuar para o mapa"
            "Developer options" -> "Opções do desenvolvedor"
            "Enable location services" -> "Ativar serviços de localização"
            "Complete" -> "Concluído"
            "Required" -> "Obrigatório"
            "Open" -> "Abrir"
            "Select" -> "Selecionar"
            "Allow" -> "Permitir"
            else -> en
        }
        "ru" -> when (en) {
            "Automatic · Phone language" -> "Автоматически · Язык телефона"
            "Recommended for stability (optional)" -> "Рекомендуется для стабильности (необязательно)"
            "Battery: Unrestricted" -> "Батарея: без ограничений"
            "Complete all four required checks to continue" -> "Для продолжения выполните четыре обязательные проверки"
            "Enable these required settings for mock locations. Checks update automatically." -> "Включите настройки для фиктивного местоположения. Проверки обновляются автоматически."
            "In Developer options: Location → Select mock location app → GPS Simulator." -> "В параметрах разработчика: Местоположение → Выбрать приложение для фиктивной геолокации → GPS Simulator."
            "Battery detection may not reflect some Samsung settings. It does not block entry." -> "Проверка батареи может не учитывать настройки некоторых Samsung. Это не блокирует вход."
            "Select GPS Simulator as mock app" -> "Выбрать GPS Simulator для фиктивной геолокации"
            "Allow precise location" -> "Разрешить точное местоположение"
            "Set up GPS Simulator" -> "Настройка GPS Simulator"
            "Continue to map" -> "Перейти к карте"
            "Developer options" -> "Параметры разработчика"
            "Enable location services" -> "Включить службы геолокации"
            "Complete" -> "Выполнено"
            "Required" -> "Обязательно"
            "Open" -> "Открыть"
            "Select" -> "Выбрать"
            "Allow" -> "Разрешить"
            else -> en
        }
        "id" -> when (en) {
            "Automatic · Phone language" -> "Otomatis · Bahasa ponsel"
            "Recommended for stability (optional)" -> "Disarankan untuk stabilitas (opsional)"
            "Battery: Unrestricted" -> "Baterai: Tanpa batasan"
            "Complete all four required checks to continue" -> "Selesaikan empat pemeriksaan wajib untuk melanjutkan"
            "Enable these required settings for mock locations. Checks update automatically." -> "Aktifkan pengaturan lokasi tiruan ini. Pemeriksaan diperbarui otomatis."
            "In Developer options: Location → Select mock location app → GPS Simulator." -> "Di opsi pengembang: Lokasi → Pilih aplikasi lokasi tiruan → GPS Simulator."
            "Battery detection may not reflect some Samsung settings. It does not block entry." -> "Pemeriksaan baterai mungkin tidak mendeteksi pengaturan Samsung tertentu. Ini tidak menghalangi akses."
            "Select GPS Simulator as mock app" -> "Pilih GPS Simulator sebagai aplikasi lokasi tiruan"
            "Allow precise location" -> "Izinkan lokasi akurat"
            "Set up GPS Simulator" -> "Siapkan GPS Simulator"
            "Continue to map" -> "Lanjutkan ke peta"
            "Developer options" -> "Opsi pengembang"
            "Enable location services" -> "Aktifkan layanan lokasi"
            "Complete" -> "Selesai"
            "Required" -> "Wajib"
            "Open" -> "Buka"
            "Select" -> "Pilih"
            "Allow" -> "Izinkan"
            else -> en
        }
        "hi" -> when (en) {
            "Automatic · Phone language" -> "स्वचालित · फ़ोन की भाषा"
            "Recommended for stability (optional)" -> "स्थिरता के लिए अनुशंसित (वैकल्पिक)"
            "Battery: Unrestricted" -> "बैटरी: अप्रतिबंधित"
            "Complete all four required checks to continue" -> "जारी रखने के लिए चारों आवश्यक जाँच पूरी करें"
            "Enable these required settings for mock locations. Checks update automatically." -> "नकली स्थान के लिए आवश्यक सेटिंग चालू करें। जाँच अपने आप अपडेट होती है।"
            "In Developer options: Location → Select mock location app → GPS Simulator." -> "डेवलपर विकल्पों में: स्थान → मॉक लोकेशन ऐप चुनें → GPS Simulator।"
            "Battery detection may not reflect some Samsung settings. It does not block entry." -> "कुछ Samsung सेटिंग बैटरी जाँच में नहीं दिख सकतीं। इससे आगे बढ़ना नहीं रुकेगा।"
            "Select GPS Simulator as mock app" -> "GPS Simulator को मॉक लोकेशन ऐप चुनें"
            "Allow precise location" -> "सटीक स्थान की अनुमति दें"
            "Set up GPS Simulator" -> "GPS Simulator सेटअप करें"
            "Continue to map" -> "मानचित्र पर जाएँ"
            "Developer options" -> "डेवलपर विकल्प"
            "Enable location services" -> "स्थान सेवाएँ चालू करें"
            "Complete" -> "पूरा हुआ"
            "Required" -> "आवश्यक"
            "Open" -> "खोलें"
            "Select" -> "चुनें"
            "Allow" -> "अनुमति दें"
            else -> en
        }
        "ja" -> when (en) {
            "Automatic · Phone language" -> "自動 · 端末の言語"
            "Recommended for stability (optional)" -> "安定性のため推奨（任意）"
            "Battery: Unrestricted" -> "バッテリー：制限なし"
            "Complete all four required checks to continue" -> "続行するには必須の4項目を完了してください"
            "Enable these required settings for mock locations. Checks update automatically." -> "仮の位置情報に必要な設定を有効にしてください。確認結果は自動更新されます。"
            "In Developer options: Location → Select mock location app → GPS Simulator." -> "開発者向けオプション：位置情報 → 仮の位置情報アプリを選択 → GPS Simulator。"
            "Battery detection may not reflect some Samsung settings. It does not block entry." -> "一部のSamsung端末ではバッテリー設定を正しく検出できません。続行は可能です。"
            "Select GPS Simulator as mock app" -> "GPS Simulator を仮の位置情報アプリに選択"
            "Allow precise location" -> "正確な位置情報を許可"
            "Set up GPS Simulator" -> "GPS Simulator の設定"
            "Continue to map" -> "地図に進む"
            "Developer options" -> "開発者向けオプション"
            "Enable location services" -> "位置情報サービスを有効にする"
            "Complete" -> "完了"
            "Required" -> "必須"
            "Open" -> "開く"
            "Select" -> "選択"
            "Allow" -> "許可"
            else -> en
        }
        "zh" -> when (en) {
            "Automatic · Phone language" -> "自动 · 手机语言"
            "Recommended for stability (optional)" -> "建议开启以提高稳定性（可选）"
            "Battery: Unrestricted" -> "电池：不受限制"
            "Complete all four required checks to continue" -> "请完成全部四项必需检查后继续"
            "Enable these required settings for mock locations. Checks update automatically." -> "请启用模拟位置所需的设置。检查结果会自动更新。"
            "In Developer options: Location → Select mock location app → GPS Simulator." -> "在开发者选项中：位置信息 → 选择模拟位置应用 → GPS Simulator。"
            "Battery detection may not reflect some Samsung settings. It does not block entry." -> "部分三星设备的电池设置可能无法被准确检测，但不会阻止继续。"
            "Select GPS Simulator as mock app" -> "选择 GPS Simulator 作为模拟位置应用"
            "Allow precise location" -> "允许精确定位"
            "Set up GPS Simulator" -> "设置 GPS Simulator"
            "Continue to map" -> "继续前往地图"
            "Developer options" -> "开发者选项"
            "Enable location services" -> "启用位置服务"
            "Complete" -> "已完成"
            "Required" -> "必需"
            "Open" -> "打开"
            "Select" -> "选择"
            "Allow" -> "允许"
            else -> en
        }
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
                        when (language) {
                            "ar" -> "$completed من 4 متطلبات"
                            "de" -> "$completed von 4 Voraussetzungen"
                            "fr" -> "$completed sur 4 conditions remplies"
                            "es" -> "$completed de 4 requisitos completados"
                            "tr" -> "4 gereklilikten $completed tamamlandı"
                            "pt" -> "$completed de 4 requisitos concluídos"
                            "ru" -> "Выполнено требований: $completed из 4"
                            "id" -> "$completed dari 4 persyaratan selesai"
                            "hi" -> "4 में से $completed आवश्यकताएँ पूरी"
                            "ja" -> "必須項目：4件中${completed}件完了"
                            "zh" -> "已完成 $completed / 4 项要求"
                            else -> "$completed of 4 requirements"
                        },
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
                                // Open Android battery optimization settings; OEM fallback is app details.
                                openSetting(
                                    context,
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
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
