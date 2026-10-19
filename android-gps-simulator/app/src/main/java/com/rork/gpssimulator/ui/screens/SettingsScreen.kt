package com.rork.gpssimulator.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.data.ImportResult
import com.rork.gpssimulator.data.TransferService
import com.rork.gpssimulator.data.model.MapType
import com.rork.gpssimulator.data.model.MockReadiness
import com.rork.gpssimulator.data.model.SavedKind
import com.rork.gpssimulator.data.model.SpeedProfile
import com.rork.gpssimulator.data.model.ThemeMode
import com.rork.gpssimulator.data.model.UnitSystem
import com.rork.gpssimulator.i18n.AppLanguage
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.components.ConfirmDialog
import com.rork.gpssimulator.ui.components.ExpandableSection
import com.rork.gpssimulator.ui.components.GroupCard
import com.rork.gpssimulator.ui.components.InfoPill
import com.rork.gpssimulator.ui.components.NumberInputDialog
import com.rork.gpssimulator.ui.components.RowDivider
import com.rork.gpssimulator.ui.components.SectionHeader
import com.rork.gpssimulator.ui.components.SelectionOption
import com.rork.gpssimulator.ui.components.SelectionSheet
import com.rork.gpssimulator.ui.components.SettingRow
import com.rork.gpssimulator.ui.components.SliderRow
import com.rork.gpssimulator.ui.components.StatusDot
import com.rork.gpssimulator.ui.components.SwitchRow
import com.rork.gpssimulator.ui.theme.LocalAppColors
import com.rork.gpssimulator.util.Format
import kotlin.math.roundToInt

/** Which single-choice sheet is currently open. */
private enum class OpenSheet { NONE, THEME, LANGUAGE, MAP_TYPE, UNITS, SPEED }

/** Which numeric entry dialog is open. */
private enum class OpenNumberDialog { NONE, ACCURACY, ALTITUDE, CUSTOM_SPEED }

/** Which destructive confirmation is open. */
private enum class OpenConfirm {
    NONE, RESET_SETTINGS, RESET_ADVANCED, CLEAR_HISTORY, CLEAR_FAVORITES, CLEAR_PRESETS, DELETE_ALL
}

/** Simple keyword matcher backing the settings search field. */
private class SettingsFilter(private val query: String) {
    val isActive: Boolean = query.isNotBlank()
    private val needle = query.trim().lowercase()

    fun matches(vararg terms: String): Boolean {
        if (!isActive) return true
        return terms.any { it.lowercase().contains(needle) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpenDiagnostics: () -> Unit,
    onOpenGeofence: () -> Unit,
    onOpenCoordinateTools: () -> Unit,
    onOpenMockSetup: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenRoutes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val context = LocalContext.current

    val settings by viewModel.settings.collectAsState()
    val readiness by viewModel.mockReadiness.collectAsState()
    val savedLocations by viewModel.savedLocations.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val history by viewModel.history.collectAsState()

    val transfer = remember { TransferService(context) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var openSheet by remember { mutableStateOf(OpenSheet.NONE) }
    var numberDialog by remember { mutableStateOf(OpenNumberDialog.NONE) }
    var confirm by remember { mutableStateOf(OpenConfirm.NONE) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }

    val filter = SettingsFilter(query)
    val playServicesAvailable = remember { viewModel.isPlayServicesAvailable() }

    if (showTerms) {
        val arabic = settings.language == AppLanguage.ARABIC
        AlertDialog(
            onDismissRequest = { showTerms = false },
            title = { Text(strings[K.terms_of_service]) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (arabic) {
                            "شروط استخدام GPS Simulator\n\n" +
                                "هذا التطبيق مخصص لاختبار المواقع والمسارات على أجهزة Android التي تملكها أو يحق لك اختبارها. " +
                                "يجب تفعيل الموقع الوهمي من إعدادات المطور بموافقة المستخدم.\n\n" +
                                "استخدم التطبيق بما يتوافق مع القوانين وشروط الخدمات الأخرى. " +
                                "لا تستخدمه للخداع أو انتهاك خصوصية الآخرين أو التحايل على الخدمات.\n\n" +
                                "تُقدَّم الخدمة كما هي دون ضمان لاستمرار عملها على جميع الأجهزة أو التطبيقات. " +
                                "قد تؤثر إعدادات النظام وتطبيقات أخرى في دقة المحاكاة.\n\n" +
                                "للاستفسارات: gps.simulator.support@gmail.com"
                        } else {
                            "GPS Simulator Terms of Use\n\n" +
                                "This app is intended for testing locations and routes on Android devices " +
                                "you own or are authorized to test. Mock location must be enabled voluntarily " +
                                "in Android developer options.\n\n" +
                                "Use the app in compliance with applicable laws and third-party service terms. " +
                                "Do not use it for deception, privacy violations or bypassing service rules.\n\n" +
                                "The app is provided as-is without a guarantee of compatibility with every " +
                                "device or third-party app. System settings may affect simulated location behavior.\n\n" +
                                "Questions: gps.simulator.support@gmail.com"
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTerms = false }) {
                    Text(if (arabic) "إغلاق" else "Close")
                }
            },
        )
    }

    // ---- File pickers ----
    val exportLocationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = transfer.write(uri, transfer.locationsToJson(savedLocations))
            viewModel.postMessage(
                if (ok) strings[K.export_success] else strings[K.import_failed],
                isError = !ok,
            )
        }
    }
    val exportRoutesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = transfer.write(uri, transfer.routesToGpx(routes))
            viewModel.postMessage(
                if (ok) strings[K.export_success] else strings[K.import_failed],
                isError = !ok,
            )
        }
    }
    val importLocationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            when (val result = transfer.importLocations(uri)) {
                is ImportResult.Locations -> {
                    viewModel.replaceSaved(savedLocations + result.items)
                    viewModel.postMessage(strings[K.import_success])
                }
                is ImportResult.Failure -> viewModel.postMessage(result.message, isError = true)
                is ImportResult.Routes -> viewModel.postMessage(strings[K.import_failed], isError = true)
            }
        }
    }
    val importGpxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            when (val result = transfer.importGpx(uri)) {
                is ImportResult.Routes -> {
                    viewModel.replaceRoutes(routes + result.items)
                    viewModel.postMessage(strings[K.import_success])
                }
                is ImportResult.Failure -> viewModel.postMessage(result.message, isError = true)
                is ImportResult.Locations -> viewModel.postMessage(strings[K.import_failed], isError = true)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = {
                                Text(
                                    strings[K.search_settings],
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = strings[K.settings_title],
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    }) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = strings[K.search_settings],
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomInset + 24.dp),
        ) {
            // ================= GENERAL =================
            val generalTerms = arrayOf(
                strings[K.theme], strings[K.language], strings[K.map_type],
                strings[K.distance_units], strings[K.reset_settings], "theme", "language", "units",
            )
            if (filter.matches(*generalTerms)) {
                SectionHeader(strings[K.section_general])
                GroupCard {
                    if (filter.matches(strings[K.theme], "theme", "dark", "light")) {
                        SettingRow(
                            icon = Icons.Default.Brightness6,
                            title = strings[K.theme],
                            value = when (settings.theme) {
                                ThemeMode.SYSTEM -> strings[K.theme_system]
                                ThemeMode.LIGHT -> strings[K.theme_light]
                                ThemeMode.DARK -> strings[K.theme_dark]
                            },
                            onClick = { openSheet = OpenSheet.THEME },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.language], "language", "arabic", "english")) {
                        SettingRow(
                            icon = Icons.Default.Language,
                            title = strings[K.language],
                            value = if (settings.language == AppLanguage.ARABIC) {
                                strings[K.lang_arabic]
                            } else {
                                strings[K.lang_english]
                            },
                            onClick = { openSheet = OpenSheet.LANGUAGE },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.map_type], "map", "satellite", "terrain", "streets")) {
                        SettingRow(
                            icon = Icons.Default.Layers,
                            title = strings[K.map_type],
                            value = when (settings.mapType) {
                                MapType.STANDARD -> strings[K.standard]
                                MapType.DETAILED_STREETS -> strings[K.detailed_streets]
                                MapType.SATELLITE -> strings[K.satellite]
                                MapType.TERRAIN -> strings[K.terrain]
                            },
                            onClick = { openSheet = OpenSheet.MAP_TYPE },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.distance_units], "units", "metric", "imperial")) {
                        SettingRow(
                            icon = Icons.Default.Straighten,
                            title = strings[K.distance_units],
                            value = when (settings.units) {
                                UnitSystem.AUTOMATIC -> strings[K.units_auto]
                                UnitSystem.METRIC -> strings[K.units_metric]
                                UnitSystem.IMPERIAL -> strings[K.units_imperial]
                            },
                            onClick = { openSheet = OpenSheet.UNITS },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.reset_settings], "reset")) {
                        SettingRow(
                            icon = Icons.Default.Restore,
                            title = strings[K.reset_settings],
                            description = strings[K.reset_settings_desc],
                            iconTint = appColors.warning,
                            onClick = { confirm = OpenConfirm.RESET_SETTINGS },
                        )
                    }
                }
            }

            // ================= MAP =================
            val mapTerms = arrayOf(
                strings[K.animate_map], strings[K.remember_position],
                strings[K.return_real_on_stop], strings[K.default_zoom], "map", "zoom", "animate",
            )
            if (filter.matches(*mapTerms)) {
                SectionHeader(strings[K.section_map])
                GroupCard {
                    if (filter.matches(strings[K.animate_map], "animate", "map")) {
                        SwitchRow(
                            icon = Icons.Default.Navigation,
                            title = strings[K.animate_map],
                            description = strings[K.animate_map_desc],
                            checked = settings.animateMap,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(animateMap = value) }
                            },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.remember_position], "remember", "map")) {
                        SwitchRow(
                            icon = Icons.Default.Map,
                            title = strings[K.remember_position],
                            description = strings[K.remember_position_desc],
                            checked = settings.rememberLastPosition,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(rememberLastPosition = value) }
                            },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.return_real_on_stop], "gps", "stop", "real")) {
                        SwitchRow(
                            icon = Icons.Default.MyLocation,
                            title = strings[K.return_real_on_stop],
                            description = strings[K.return_real_on_stop_desc],
                            checked = settings.returnToRealOnStop,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(returnToRealOnStop = value) }
                            },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.default_zoom], "zoom", "map")) {
                        SliderRow(
                            icon = Icons.Default.ZoomIn,
                            title = strings[K.default_zoom],
                            description = strings[K.default_zoom_desc],
                            value = settings.defaultZoom,
                            valueRange = 3f..19f,
                            steps = 15,
                            valueLabel = settings.defaultZoom.roundToInt().toString(),
                            onValueChange = { value ->
                                viewModel.updateSettings { it.copy(defaultZoom = value) }
                            },
                        )
                    }
                }
            }

            // ================= LOCATION =================
            val locationTerms = arrayOf(
                strings[K.mock_status], strings[K.location_provider], strings[K.use_play_services],
                strings[K.stop_on_close], strings[K.restore_last_location],
                "mock", "gps", "provider", "permission",
            )
            if (filter.matches(*locationTerms)) {
                SectionHeader(strings[K.section_location])
                GroupCard {
                    if (filter.matches(strings[K.mock_status], "mock", "setup", "permission")) {
                        MockStatusRow(
                            readiness = readiness,
                            onConfigure = onOpenMockSetup,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.use_play_services], "provider", "google", "play")) {
                        SwitchRow(
                            icon = Icons.Default.Explore,
                            title = strings[K.use_play_services],
                            description = if (playServicesAvailable) {
                                strings[K.use_play_services_desc]
                            } else {
                                strings[K.unavailable_on_device]
                            },
                            checked = settings.usePlayServices && playServicesAvailable,
                            enabled = playServicesAvailable,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(usePlayServices = value) }
                            },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.stop_on_close], "mock", "close")) {
                        SwitchRow(
                            icon = Icons.Default.Lock,
                            title = strings[K.stop_on_close],
                            description = strings[K.stop_on_close_desc],
                            checked = settings.stopOnAppClose,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(stopOnAppClose = value) }
                            },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.restore_last_location], "restore", "mock")) {
                        SwitchRow(
                            icon = Icons.Default.Cached,
                            title = strings[K.restore_last_location],
                            description = strings[K.restore_last_location_desc],
                            checked = settings.restoreLastLocation,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(restoreLastLocation = value) }
                            },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.gps_diagnostics], "gps", "diagnostic")) {
                        SettingRow(
                            icon = Icons.Default.Analytics,
                            title = strings[K.gps_diagnostics],
                            description = strings[K.gps_diagnostics_desc],
                            onClick = onOpenDiagnostics,
                            showChevron = true,
                        )
                    }
                }
            }

            // ================= SIMULATION =================
            val simTerms = arrayOf(
                strings[K.randomize_location], strings[K.randomization_radius],
                strings[K.sim_accuracy], strings[K.sim_altitude], strings[K.default_speed],
                strings[K.advanced_params], "accuracy", "altitude", "speed", "random", "bearing",
                "interval",
            )
            if (filter.matches(*simTerms)) {
                SectionHeader(strings[K.section_simulation])
                GroupCard {
                    if (filter.matches(strings[K.randomize_location], "random")) {
                        SwitchRow(
                            icon = Icons.Default.Shuffle,
                            title = strings[K.randomize_location],
                            description = strings[K.randomize_location_desc],
                            checked = settings.randomizeEnabled,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(randomizeEnabled = value) }
                            },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.randomization_radius], "random", "radius")) {
                        SliderRow(
                            icon = Icons.Default.Adjust,
                            title = strings[K.randomization_radius],
                            description = strings[K.randomization_radius_desc],
                            value = settings.randomizationRadiusM,
                            valueRange = 0f..100f,
                            valueLabel = "${settings.randomizationRadiusM.roundToInt()} m",
                            enabled = settings.randomizeEnabled,
                            onValueChange = { value ->
                                viewModel.updateSettings { it.copy(randomizationRadiusM = value) }
                            },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.sim_accuracy], "accuracy", "gps")) {
                        SliderRow(
                            icon = Icons.Default.MyLocation,
                            title = strings[K.sim_accuracy],
                            description = strings[K.sim_accuracy_desc],
                            value = settings.accuracyM,
                            valueRange = 1f..100f,
                            valueLabel = "${settings.accuracyM.roundToInt()} m",
                            onValueChange = { value ->
                                viewModel.updateSettings { it.copy(accuracyM = value) }
                            },
                            onValueLabelClick = { numberDialog = OpenNumberDialog.ACCURACY },
                        )
                        SettingRow(
                            icon = Icons.Default.Restore,
                            title = strings[K.reset_to_default],
                            iconTint = appColors.muted,
                            onClick = { viewModel.updateSettings { it.copy(accuracyM = 5f) } },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.sim_altitude], "altitude", "elevation")) {
                        SliderRow(
                            icon = Icons.Default.Height,
                            title = strings[K.sim_altitude],
                            description = strings[K.sim_altitude_desc],
                            value = settings.altitudeM,
                            valueRange = -500f..9000f,
                            valueLabel = "${settings.altitudeM.roundToInt()} m",
                            onValueChange = { value ->
                                viewModel.updateSettings { it.copy(altitudeM = value) }
                            },
                            onValueLabelClick = { numberDialog = OpenNumberDialog.ALTITUDE },
                        )
                        SettingRow(
                            icon = Icons.Default.Restore,
                            title = strings[K.reset_to_default],
                            iconTint = appColors.muted,
                            onClick = { viewModel.updateSettings { it.copy(altitudeM = 250f) } },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.default_speed], "speed", "walking", "driving")) {
                        SettingRow(
                            icon = Icons.Default.Speed,
                            title = strings[K.default_speed],
                            description = strings[K.default_speed_desc],
                            value = Format.speed(settings.speedKmh, settings.units),
                            onClick = { openSheet = OpenSheet.SPEED },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(
                            strings[K.advanced_params], "advanced", "bearing", "interval",
                            "interpolation",
                        )
                    ) {
                        ExpandableSection(
                            icon = Icons.Default.Tune,
                            title = strings[K.advanced_params],
                            description = strings[K.advanced_params_desc],
                            expanded = advancedExpanded || filter.isActive,
                            onToggle = { advancedExpanded = !advancedExpanded },
                        ) {
                            Column {
                                SliderRow(
                                    icon = Icons.Default.Navigation,
                                    title = strings[K.bearing],
                                    description = strings[K.bearing_desc],
                                    value = settings.bearingDeg,
                                    valueRange = 0f..359f,
                                    valueLabel = "${settings.bearingDeg.roundToInt()}°",
                                    onValueChange = { value ->
                                        viewModel.updateSettings { it.copy(bearingDeg = value) }
                                    },
                                )
                                RowDivider()
                                SliderRow(
                                    icon = Icons.Default.Timer,
                                    title = strings[K.update_interval],
                                    description = strings[K.update_interval_desc],
                                    value = settings.updateIntervalMs.toFloat(),
                                    valueRange = 250f..5000f,
                                    steps = 18,
                                    valueLabel = "${settings.updateIntervalMs} ms",
                                    onValueChange = { value ->
                                        viewModel.updateSettings {
                                            it.copy(updateIntervalMs = value.roundToInt())
                                        }
                                    },
                                )
                                RowDivider()
                                SwitchRow(
                                    icon = Icons.Default.Shuffle,
                                    title = strings[K.coordinate_variation],
                                    checked = settings.coordinateVariation,
                                    onCheckedChange = { value ->
                                        viewModel.updateSettings { it.copy(coordinateVariation = value) }
                                    },
                                )
                                RowDivider()
                                SwitchRow(
                                    icon = Icons.Default.Timeline,
                                    title = strings[K.route_interpolation],
                                    checked = settings.routeInterpolation,
                                    onCheckedChange = { value ->
                                        viewModel.updateSettings { it.copy(routeInterpolation = value) }
                                    },
                                )
                                RowDivider()
                                SettingRow(
                                    icon = Icons.Default.Restore,
                                    title = strings[K.reset_advanced],
                                    iconTint = appColors.warning,
                                    onClick = { confirm = OpenConfirm.RESET_ADVANCED },
                                )
                            }
                        }
                    }
                }
            }

            // ================= DEVELOPER TOOLS =================
            val toolTerms = arrayOf(
                strings[K.route_simulator], strings[K.joystick_mode], strings[K.coordinate_tools],
                strings[K.developer_presets], strings[K.geofence_tester], strings[K.mock_setup_title],
                "route", "joystick", "geofence", "coordinate", "preset",
            )
            if (filter.matches(*toolTerms)) {
                SectionHeader(strings[K.section_developer_tools])
                GroupCard {
                    if (filter.matches(strings[K.route_simulator], "route")) {
                        SettingRow(
                            icon = Icons.Default.Route,
                            title = strings[K.route_simulator],
                            description = strings[K.route_simulator_desc],
                            onClick = onOpenRoutes,
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.joystick_mode], "joystick")) {
                        SwitchRow(
                            icon = Icons.Default.Gamepad,
                            title = strings[K.joystick_mode],
                            description = strings[K.joystick_desc],
                            checked = settings.joystickEnabled,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(joystickEnabled = value) }
                            },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.coordinate_tools], "coordinate")) {
                        SettingRow(
                            icon = Icons.Default.LocationOn,
                            title = strings[K.coordinate_tools],
                            description = strings[K.coordinate_tools_desc],
                            onClick = onOpenCoordinateTools,
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.developer_presets], "preset")) {
                        SettingRow(
                            icon = Icons.Default.Science,
                            title = strings[K.developer_presets],
                            description = strings[K.developer_presets_desc],
                            onClick = onOpenPresets,
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.geofence_tester], "geofence")) {
                        SettingRow(
                            icon = Icons.Default.Adjust,
                            title = strings[K.geofence_tester],
                            description = strings[K.geofence_tester_desc],
                            onClick = onOpenGeofence,
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.mock_setup_title], "mock", "setup")) {
                        SettingRow(
                            icon = Icons.Default.Settings,
                            title = strings[K.mock_setup_title],
                            description = strings[K.mock_setup_desc],
                            onClick = onOpenMockSetup,
                            showChevron = true,
                        )
                    }
                }
            }

            // ================= DATA & PRIVACY =================
            val dataTerms = arrayOf(
                strings[K.save_history], strings[K.import_export], strings[K.clear_history],
                strings[K.local_data_info], strings[K.delete_all_data], "history", "privacy",
                "export", "import", "data", "backup",
            )
            if (filter.matches(*dataTerms)) {
                SectionHeader(strings[K.section_data_privacy])
                GroupCard {
                    if (filter.matches(strings[K.save_history], "history", "privacy")) {
                        SwitchRow(
                            icon = Icons.Default.History,
                            title = strings[K.save_history],
                            description = strings[K.save_history_desc],
                            checked = settings.saveHistory,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(saveHistory = value) }
                            },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.export_locations], "export", "backup", "json")) {
                        SettingRow(
                            icon = Icons.Default.Upload,
                            title = strings[K.export_locations],
                            description = strings[K.import_export_desc],
                            enabled = savedLocations.isNotEmpty(),
                            onClick = {
                                if (savedLocations.isEmpty()) {
                                    viewModel.postMessage(strings[K.nothing_to_export], isError = true)
                                } else {
                                    exportLocationsLauncher.launch("gps-simulator-locations.json")
                                }
                            },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.import_locations], "import", "json", "csv")) {
                        SettingRow(
                            icon = Icons.Default.Download,
                            title = strings[K.import_locations],
                            onClick = {
                                importLocationsLauncher.launch(
                                    arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "*/*"),
                                )
                            },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.export_routes], "export", "gpx", "route")) {
                        SettingRow(
                            icon = Icons.Default.CloudUpload,
                            title = strings[K.export_routes],
                            enabled = routes.isNotEmpty(),
                            onClick = {
                                if (routes.isEmpty()) {
                                    viewModel.postMessage(strings[K.nothing_to_export], isError = true)
                                } else {
                                    exportRoutesLauncher.launch("gps-simulator-routes.gpx")
                                }
                            },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.import_gpx], "import", "gpx", "route")) {
                        SettingRow(
                            icon = Icons.Default.Download,
                            title = strings[K.import_gpx],
                            onClick = {
                                importGpxLauncher.launch(
                                    arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*"),
                                )
                            },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.clear_history], "clear", "history")) {
                        SettingRow(
                            icon = Icons.Default.DeleteSweep,
                            title = strings[K.clear_history],
                            iconTint = appColors.warning,
                            enabled = history.isNotEmpty(),
                            onClick = { confirm = OpenConfirm.CLEAR_HISTORY },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.clear_favorites], "clear", "favorites")) {
                        SettingRow(
                            icon = Icons.Default.Star,
                            title = strings[K.clear_favorites],
                            iconTint = appColors.warning,
                            enabled = savedLocations.any { it.kind == SavedKind.FAVORITE },
                            onClick = { confirm = OpenConfirm.CLEAR_FAVORITES },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.clear_presets], "clear", "presets")) {
                        SettingRow(
                            icon = Icons.Default.Science,
                            title = strings[K.clear_presets],
                            iconTint = appColors.warning,
                            enabled = savedLocations.any { it.kind == SavedKind.PRESET },
                            onClick = { confirm = OpenConfirm.CLEAR_PRESETS },
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.local_data_info], "privacy", "data")) {
                        SettingRow(
                            icon = Icons.Default.Security,
                            title = strings[K.local_data_info],
                            description = strings[K.local_data_body],
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.delete_all_data], "delete", "data", "privacy")) {
                        SettingRow(
                            icon = Icons.Default.DeleteForever,
                            title = strings[K.delete_all_data],
                            description = strings[K.delete_all_data_desc],
                            iconTint = appColors.danger,
                            onClick = { confirm = OpenConfirm.DELETE_ALL },
                        )
                    }
                }
            }

            // ================= APP =================
            val appTerms = arrayOf(
                strings[K.share_app], strings[K.rate_app], strings[K.send_feedback],
                strings[K.privacy_policy], strings[K.terms_of_service],
                strings[K.open_source_licenses], strings[K.about], "share", "rate", "about",
                "privacy", "terms", "license",
            )
            if (filter.matches(*appTerms)) {
                SectionHeader(strings[K.section_app])
                GroupCard {
                    if (filter.matches(strings[K.share_app], "share")) {
                        SettingRow(
                            icon = Icons.Default.Share,
                            title = strings[K.share_app],
                            description = strings[K.share_app_desc],
                            onClick = { shareApp(context) },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.rate_app], "rate")) {
                        SettingRow(
                            icon = Icons.Default.StarRate,
                            title = strings[K.rate_app],
                            description = strings[K.rate_app_desc],
                            onClick = { openPlayStoreRating(context) },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.send_feedback], "feedback")) {
                        SettingRow(
                            icon = Icons.Default.Description,
                            title = strings[K.send_feedback],
                            description = strings[K.send_feedback_desc],
                            onClick = { sendSupportFeedback(context) },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.privacy_policy], "privacy")) {
                        SettingRow(
                            icon = Icons.Default.Policy,
                            title = strings[K.privacy_policy],
                            onClick = { openPrivacyPolicy(context) },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.terms_of_service], "terms")) {
                        SettingRow(
                            icon = Icons.Default.Gavel,
                            title = strings[K.terms_of_service],
                            onClick = { showTerms = true },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.open_source_licenses], "license")) {
                        SettingRow(
                            icon = Icons.Default.Description,
                            title = strings[K.open_source_licenses],
                            onClick = onOpenLicenses,
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.app_permissions], "permission")) {
                        SettingRow(
                            icon = Icons.Default.Security,
                            title = strings[K.app_permissions],
                            onClick = { openAppSettings(context) },
                            showChevron = true,
                        )
                        RowDivider()
                    }
                    if (filter.matches(strings[K.about], "about", "version")) {
                        SettingRow(
                            icon = Icons.Default.Info,
                            title = strings[K.about],
                            onClick = onOpenAbout,
                            showChevron = true,
                        )
                    }
                }
            }
        }
    }

    // ---- Selection sheets ----
    when (openSheet) {
        OpenSheet.THEME -> SelectionSheet(
            title = strings[K.theme],
            options = listOf(
                SelectionOption(ThemeMode.SYSTEM, strings[K.theme_system]),
                SelectionOption(ThemeMode.LIGHT, strings[K.theme_light]),
                SelectionOption(ThemeMode.DARK, strings[K.theme_dark]),
            ),
            selected = settings.theme,
            onSelect = { value -> viewModel.updateSettings { it.copy(theme = value) } },
            onDismiss = { openSheet = OpenSheet.NONE },
        )
        OpenSheet.LANGUAGE -> SelectionSheet(
            title = strings[K.language],
            options = listOf(
                SelectionOption(AppLanguage.ENGLISH, strings[K.lang_english]),
                SelectionOption(AppLanguage.ARABIC, strings[K.lang_arabic]),
            ),
            selected = settings.language,
            onSelect = { value -> viewModel.updateSettings { it.copy(language = value) } },
            onDismiss = { openSheet = OpenSheet.NONE },
        )
        OpenSheet.MAP_TYPE -> SelectionSheet(
            title = strings[K.map_type],
            options = listOf(
                SelectionOption(MapType.STANDARD, strings[K.standard]),
                SelectionOption(MapType.DETAILED_STREETS, strings[K.detailed_streets]),
                SelectionOption(MapType.SATELLITE, strings[K.satellite]),
                SelectionOption(MapType.TERRAIN, strings[K.terrain]),
            ),
            selected = settings.mapType,
            onSelect = { value -> viewModel.updateSettings { it.copy(mapType = value) } },
            onDismiss = { openSheet = OpenSheet.NONE },
        )
        OpenSheet.UNITS -> SelectionSheet(
            title = strings[K.distance_units],
            options = listOf(
                SelectionOption(UnitSystem.AUTOMATIC, strings[K.units_auto]),
                SelectionOption(UnitSystem.METRIC, strings[K.units_metric]),
                SelectionOption(UnitSystem.IMPERIAL, strings[K.units_imperial]),
            ),
            selected = settings.units,
            onSelect = { value -> viewModel.updateSettings { it.copy(units = value) } },
            onDismiss = { openSheet = OpenSheet.NONE },
        )
        OpenSheet.SPEED -> SelectionSheet(
            title = strings[K.default_speed],
            options = listOf(
                SelectionOption(SpeedProfile.WALKING, strings[K.walking], "5 km/h"),
                SelectionOption(SpeedProfile.CYCLING, strings[K.cycling], "18 km/h"),
                SelectionOption(SpeedProfile.DRIVING, strings[K.driving], "60 km/h"),
                SelectionOption(
                    SpeedProfile.CUSTOM,
                    strings[K.custom],
                    "${settings.customSpeedKmh.roundToInt()} km/h",
                ),
            ),
            selected = settings.speedProfile,
            onSelect = { value ->
                viewModel.updateSettings { it.copy(speedProfile = value) }
                if (value == SpeedProfile.CUSTOM) numberDialog = OpenNumberDialog.CUSTOM_SPEED
            },
            onDismiss = { openSheet = OpenSheet.NONE },
        )
        OpenSheet.NONE -> Unit
    }

    // ---- Numeric entry dialogs ----
    when (numberDialog) {
        OpenNumberDialog.ACCURACY -> NumberInputDialog(
            title = strings[K.sim_accuracy],
            initialValue = settings.accuracyM.roundToInt().toString(),
            suffix = "m",
            confirmLabel = strings[K.ok],
            cancelLabel = strings[K.cancel],
            onConfirm = { value ->
                viewModel.updateSettings {
                    it.copy(accuracyM = value.toFloat().coerceIn(1f, 100f))
                }
            },
            onDismiss = { numberDialog = OpenNumberDialog.NONE },
        )
        OpenNumberDialog.ALTITUDE -> NumberInputDialog(
            title = strings[K.sim_altitude],
            initialValue = settings.altitudeM.roundToInt().toString(),
            suffix = "m",
            confirmLabel = strings[K.ok],
            cancelLabel = strings[K.cancel],
            allowNegative = true,
            onConfirm = { value ->
                viewModel.updateSettings {
                    it.copy(altitudeM = value.toFloat().coerceIn(-500f, 9000f))
                }
            },
            onDismiss = { numberDialog = OpenNumberDialog.NONE },
        )
        OpenNumberDialog.CUSTOM_SPEED -> NumberInputDialog(
            title = strings[K.custom_speed],
            initialValue = settings.customSpeedKmh.roundToInt().toString(),
            suffix = "km/h",
            confirmLabel = strings[K.ok],
            cancelLabel = strings[K.cancel],
            onConfirm = { value ->
                viewModel.updateSettings {
                    it.copy(
                        customSpeedKmh = value.toFloat().coerceIn(0.5f, 500f),
                        speedProfile = SpeedProfile.CUSTOM,
                    )
                }
            },
            onDismiss = { numberDialog = OpenNumberDialog.NONE },
        )
        OpenNumberDialog.NONE -> Unit
    }

    // ---- Confirmations ----
    when (confirm) {
        OpenConfirm.RESET_SETTINGS -> ConfirmDialog(
            title = strings[K.reset_settings],
            body = strings[K.reset_settings_confirm],
            confirmLabel = strings[K.confirm],
            cancelLabel = strings[K.cancel],
            destructive = true,
            onConfirm = { viewModel.resetSettings() },
            onDismiss = { confirm = OpenConfirm.NONE },
        )
        OpenConfirm.RESET_ADVANCED -> ConfirmDialog(
            title = strings[K.reset_advanced],
            body = strings[K.advanced_params_desc],
            confirmLabel = strings[K.confirm],
            cancelLabel = strings[K.cancel],
            onConfirm = {
                viewModel.updateSettings {
                    it.copy(
                        bearingDeg = 0f,
                        updateIntervalMs = 1000,
                        coordinateVariation = false,
                        routeInterpolation = true,
                    )
                }
                viewModel.postMessage(strings[K.advanced_reset])
            },
            onDismiss = { confirm = OpenConfirm.NONE },
        )
        OpenConfirm.CLEAR_HISTORY -> ConfirmDialog(
            title = strings[K.clear_history],
            body = strings[K.clear_history_confirm],
            confirmLabel = strings[K.delete],
            cancelLabel = strings[K.cancel],
            destructive = true,
            onConfirm = { viewModel.clearHistory() },
            onDismiss = { confirm = OpenConfirm.NONE },
        )
        OpenConfirm.CLEAR_FAVORITES -> ConfirmDialog(
            title = strings[K.clear_favorites],
            body = strings[K.clear_favorites_confirm],
            confirmLabel = strings[K.delete],
            cancelLabel = strings[K.cancel],
            destructive = true,
            onConfirm = { viewModel.clearSaved(SavedKind.FAVORITE) },
            onDismiss = { confirm = OpenConfirm.NONE },
        )
        OpenConfirm.CLEAR_PRESETS -> ConfirmDialog(
            title = strings[K.clear_presets],
            body = strings[K.clear_presets_confirm],
            confirmLabel = strings[K.delete],
            cancelLabel = strings[K.cancel],
            destructive = true,
            onConfirm = { viewModel.clearSaved(SavedKind.PRESET) },
            onDismiss = { confirm = OpenConfirm.NONE },
        )
        OpenConfirm.DELETE_ALL -> ConfirmDialog(
            title = strings[K.delete_all_data],
            body = strings[K.delete_all_data_confirm],
            confirmLabel = strings[K.delete],
            cancelLabel = strings[K.cancel],
            destructive = true,
            onConfirm = { viewModel.deleteAllLocalData() },
            onDismiss = { confirm = OpenConfirm.NONE },
        )
        OpenConfirm.NONE -> Unit
    }
}

/** Row showing Android mock-location readiness with a Configure action. */
@Composable
private fun MockStatusRow(
    readiness: MockReadiness,
    onConfigure: () -> Unit,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current

    val (label, color) = when (readiness) {
        MockReadiness.READY -> strings[K.mock_ready] to appColors.success
        MockReadiness.SETUP_REQUIRED -> strings[K.mock_setup_required] to appColors.warning
        MockReadiness.PERMISSION_REQUIRED -> strings[K.mock_permission_required] to appColors.danger
        MockReadiness.DEV_OPTIONS_REQUIRED ->
            strings[K.mock_dev_options_required] to appColors.danger
    }

    SettingRow(
        icon = Icons.Default.Security,
        title = strings[K.mock_status],
        iconTint = color,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoPill(
                    text = label,
                    color = color,
                    leading = { StatusDot(color, size = 9) },
                )
                if (readiness != MockReadiness.READY) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onConfigure) {
                        Text(
                            text = strings[K.configure],
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        },
    )
}

private fun shareApp(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "GPS Simulator — a precise location testing tool for Android developers.",
        )
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private fun openAppSettings(context: android.content.Context) {
    try {
        val intent = Intent(
            AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No settings activity available on this device.
    }
}

private const val PRIVACY_POLICY_URL = "https://mohammad-km.github.io/rork-gps-simulator/privacy-policy.html"

private fun openPrivacyPolicy(context: android.content.Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
    } catch (e: ActivityNotFoundException) {
        // No browser available on this device.
    }
}

private fun openPlayStoreRating(context: android.content.Context) {
    val packageName = context.packageName
    val urls = listOf(
        Uri.parse("market://details?id=$packageName"),
        Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
    )
    for (uri in urls) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            return
        } catch (_: ActivityNotFoundException) {
            // Fall back to the browser when Play Store is unavailable.
        }
    }
}

private fun sendSupportFeedback(context: android.content.Context) {
    val subject = "GPS Simulator feedback"
    val body = "App: GPS Simulator\\nVersion: ${com.rork.gpssimulator.BuildConfig.VERSION_NAME}\\nAndroid: ${android.os.Build.VERSION.RELEASE}\n\nFeedback:\\n"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:gps.simulator.support@gmail.com")).apply {
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (_: ActivityNotFoundException) {
        // No configured email app; the settings screen remains usable.
    }
}
