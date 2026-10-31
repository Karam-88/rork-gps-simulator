package com.rork.gpssimulator.ui.navigation

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.analytics.FirebaseAnalytics
import com.rork.gpssimulator.ui.privacy.AnalyticsConsent
import com.rork.gpssimulator.ui.screens.PrivacyConsentScreen
import com.rork.gpssimulator.data.model.ThemeMode
import com.rork.gpssimulator.data.model.MockState
import com.rork.gpssimulator.i18n.AppLanguage
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.i18n.Strings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.screens.AboutScreen
import com.rork.gpssimulator.ui.screens.CoordinateToolsScreen
import com.rork.gpssimulator.ui.screens.DiagnosticsScreen
import com.rork.gpssimulator.ui.screens.FirstRunOnboardingScreen
import com.rork.gpssimulator.ui.screens.GpsReadinessChecklist
import com.rork.gpssimulator.ui.screens.GeofenceScreen
import com.rork.gpssimulator.ui.screens.HistoryScreen
import com.rork.gpssimulator.ui.screens.LicensesScreen
import com.rork.gpssimulator.ui.screens.MapScreen
import com.rork.gpssimulator.ui.screens.MockSetupScreen
import com.rork.gpssimulator.ui.screens.PresetsScreen
import com.rork.gpssimulator.ui.screens.RoutesScreen
import com.rork.gpssimulator.ui.screens.SearchScreen
import com.rork.gpssimulator.ui.screens.CrosshairAppearanceScreen
import com.rork.gpssimulator.ui.screens.MockMarkerColorScreen
import com.rork.gpssimulator.ui.screens.SettingsScreen
import com.rork.gpssimulator.ui.theme.AppTheme

object Routes {
    const val MAP = "map"
    const val ROUTES = "routes"
    const val PRESETS = "presets"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val CROSSHAIR = "settings/crosshair"
    const val MOCK_MARKER = "settings/mock_marker"
    const val SEARCH = "search"
    const val DIAGNOSTICS = "diagnostics"
    const val GEOFENCE = "geofence"
    const val COORDINATE_TOOLS = "coordinate_tools"
    const val MOCK_SETUP = "mock_setup"
    const val ABOUT = "about"
    const val LICENSES = "licenses"
}

@Composable
fun AppNavigation() {
    val viewModel: AppViewModel = viewModel()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val phoneLocale = configuration.locales[0]
    val preferredPhoneLanguage = AppLanguage.fromDeviceLocale(phoneLocale)
    LaunchedEffect(preferredPhoneLanguage, settings.followSystemLanguage) {
        if (settings.followSystemLanguage && settings.language != preferredPhoneLanguage) {
            viewModel.updateSettings { it.copy(language = preferredPhoneLanguage) }
        }
    }
    val onboardingPrefs = remember {
        context.getSharedPreferences("gps_simulator_onboarding", Context.MODE_PRIVATE)
    }
    var onboardingComplete by remember {
        mutableStateOf(onboardingPrefs.getBoolean("completed", false))
    }
    var setupSeen by remember { mutableStateOf(onboardingPrefs.getBoolean("gps_setup_modal_v2_completed", false)) }
    var analyticsChoiceMade by remember { mutableStateOf(AnalyticsConsent.hasDecision(context)) }
    // Onboarding happens outside the NavHost; log only these fixed, non-location labels.
    LaunchedEffect(onboardingComplete, setupSeen) {
        val screenName = when {
            !onboardingComplete -> "Onboarding"
            !setupSeen -> "GpsReadiness"
            else -> null
        }
        if (screenName != null && AnalyticsConsent.isAllowed(context)) {
            FirebaseAnalytics.getInstance(context).logEvent(
                FirebaseAnalytics.Event.SCREEN_VIEW,
                Bundle().apply {
                    putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                    putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
                },
            )
        }
    }
    var shouldCenterOnFirstRealFix by remember {
        mutableStateOf(!onboardingPrefs.getBoolean("initial_map_centered", false))
    }

    val darkTheme = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val effectiveLanguage = AppLanguage.effective(settings.language, settings.followSystemLanguage, phoneLocale)
    val strings = remember(effectiveLanguage) { Strings(effectiveLanguage) }
    val isArabic = settings.language == AppLanguage.ARABIC
    // Keep the complete application UI physically left-to-right even when
    // Android itself is using an RTL system language.
    val layoutDirection = LayoutDirection.Ltr

    AppTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(
            LocalStrings provides strings,
            LocalLayoutDirection provides layoutDirection,
        ) {
            if (!onboardingComplete) {
                FirstRunOnboardingScreen(
                    isArabic = isArabic,
                    onFinished = {
                        onboardingPrefs.edit().putBoolean("completed", true).apply()
                        onboardingComplete = true
                    },
                )
            } else if (!analyticsChoiceMade) {
                PrivacyConsentScreen(
                    language = effectiveLanguage,
                    onAgree = {
                        AnalyticsConsent.setAllowed(context, true)
                        analyticsChoiceMade = true
                    },
                    onDecline = {
                        AnalyticsConsent.setAllowed(context, false)
                        analyticsChoiceMade = true
                    },
                )
            } else if (!setupSeen) {
                GpsReadinessChecklist(
                    onFinished = {
                        onboardingPrefs.edit().putBoolean("gps_setup_modal_v2_completed", true).apply()
                        setupSeen = true
                    },
                    selectedLanguage = settings.language,
                    followSystemLanguage = settings.followSystemLanguage,
                    onLanguageSelected = { choice ->
                        viewModel.updateSettings {
                            if (choice == null) it.copy(followSystemLanguage = true)
                            else it.copy(
                                language = AppLanguage.entries.firstOrNull { it.languageCode == choice } ?: AppLanguage.ENGLISH,
                                followSystemLanguage = false,
                            )
                        }
                    },
                )
            } else {
                AppScaffold(
                    viewModel = viewModel,
                    centerOnFirstRealFix = shouldCenterOnFirstRealFix,
                    onFirstRealFixCentered = {
                        onboardingPrefs.edit().putBoolean("initial_map_centered", true).apply()
                        shouldCenterOnFirstRealFix = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AppScaffold(
    viewModel: AppViewModel,
    centerOnFirstRealFix: Boolean,
    onFirstRealFixCentered: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsState()
    val realSample by viewModel.realSample.collectAsState()
    val mockState by viewModel.mockState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val pendingRating by viewModel.ratingPrompt.pending.collectAsState()
    var showRatingPrompt by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showRatingPreview by remember { mutableStateOf(false) }
    var mapIdle by remember { mutableStateOf(false) }
    val entry by navController.currentBackStackEntryAsState()
    // Only allowlisted, static screen labels are sent to Analytics.
    // Never log any map center, GPS coordinates, searched text or mock locations.
    LaunchedEffect(entry?.destination?.route) {
        val screenName = when (entry?.destination?.route) {
            Routes.MAP -> "Map"
            Routes.ROUTES -> "Routes"
            Routes.PRESETS -> "Favorites"
            Routes.HISTORY -> "History"
            Routes.SETTINGS -> "Settings"
            Routes.CROSSHAIR -> "CrosshairSettings"
            Routes.MOCK_MARKER -> "MockMarkerSettings"
            Routes.SEARCH -> "Search"
            Routes.DIAGNOSTICS -> "Diagnostics"
            Routes.GEOFENCE -> "Geofence"
            Routes.COORDINATE_TOOLS -> "CoordinateTools"
            Routes.MOCK_SETUP -> "GpsSetup"
            Routes.ABOUT -> "About"
            Routes.LICENSES -> "Licenses"
            else -> null
        }
        if (screenName != null && AnalyticsConsent.isAllowed(context)) {
            FirebaseAnalytics.getInstance(context).logEvent(
                FirebaseAnalytics.Event.SCREEN_VIEW,
                Bundle().apply {
                    putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                    putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
                },
            )
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val safeForRating = com.rork.gpssimulator.ui.rating.RatingPromptPolicy.safeToShow(
        entry?.destination?.route == Routes.MAP, mapIdle,
        mockState == MockState.MOCK_ACTIVE, lifecycleState == Lifecycle.State.RESUMED,
    )
    LaunchedEffect(pendingRating, safeForRating) {
        if (pendingRating && safeForRating) {
            kotlinx.coroutines.delay(1500)
            viewModel.ratingPrompt.shown()
            showRatingPrompt = true
        }
        if (!safeForRating && showRatingPrompt) {
            showRatingPrompt = false
            viewModel.ratingPrompt.postpone()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        viewModel.refreshPermissionState()
    }

    LaunchedEffect(Unit) {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    LaunchedEffect(realSample, centerOnFirstRealFix) {
        if (centerOnFirstRealFix && realSample != null) {
            viewModel.returnToRealLocation(recenter = true)
            onFirstRealFixCentered()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissionState()
    }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current.text, duration = SnackbarDuration.Short)
        viewModel.consumeMessage()
    }

    if ((showRatingPrompt && safeForRating) || showRatingPreview) {
        val language = LocalStrings.current.language
        val dark = when (settings.theme) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
        com.rork.gpssimulator.ui.rating.PremiumRatingDialog(
            language = language,
            darkTheme = dark,
            onLater = {
                if (!showRatingPreview) viewModel.ratingPrompt.postpone()
                showRatingPrompt = false
                showRatingPreview = false
            },
            onRate = {
                if (!showRatingPreview) viewModel.ratingPrompt.choseToRate()
                showRatingPrompt = false
                showRatingPreview = false
                com.rork.gpssimulator.ui.rating.openPlayRating(context)
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        @Suppress("UNUSED_VARIABLE")
        val appliedScaffoldPadding = scaffoldPadding
        val bottomInset = 0.dp

        NavHost(
            navController = navController,
            startDestination = Routes.MAP,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.MAP) {
                MapScreen(
                    viewModel = viewModel,
                    bottomInset = bottomInset,
                    onRatingPromptSafeChanged = { mapIdle = it },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenFavorites = { navController.navigateToTopLevel(Routes.PRESETS) },
                    onOpenRoutes = { navController.navigateToTopLevel(Routes.ROUTES) },
                    onOpenHistory = { navController.navigateToTopLevel(Routes.HISTORY) },
                    onOpenSettings = { navController.navigateToTopLevel(Routes.SETTINGS) },
                )
            }
            composable(Routes.ROUTES) {
                RoutesScreen(
                    viewModel = viewModel,
                    bottomInset = bottomInset,
                    onRouteStarted = { navController.navigateToTopLevel(Routes.MAP) },
                )
            }
            composable(Routes.PRESETS) {
                PresetsScreen(
                    viewModel = viewModel,
                    bottomInset = bottomInset,
                    onUseLocation = { navController.navigateToTopLevel(Routes.MAP) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    viewModel = viewModel,
                    bottomInset = bottomInset,
                    onRestore = { navController.navigateToTopLevel(Routes.MAP) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    bottomInset = bottomInset,
                    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                    onOpenGeofence = { navController.navigate(Routes.GEOFENCE) },
                    onOpenCoordinateTools = { navController.navigate(Routes.COORDINATE_TOOLS) },
                    onOpenMockSetup = { navController.navigate(Routes.MOCK_SETUP) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenLicenses = { navController.navigate(Routes.LICENSES) },
                    onOpenPresets = { navController.navigateToTopLevel(Routes.PRESETS) },
                    onOpenRoutes = { navController.navigateToTopLevel(Routes.ROUTES) },
                    onTestRatingDialog = { showRatingPreview = true },
                    onOpenCrosshair = { navController.navigate(Routes.CROSSHAIR) },
                    onOpenMockMarker = { navController.navigate(Routes.MOCK_MARKER) },
                )
            }
            composable(Routes.CROSSHAIR) {
                CrosshairAppearanceScreen(viewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.MOCK_MARKER) {
                MockMarkerColorScreen(viewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.SEARCH) {
                SearchScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.GEOFENCE) {
                GeofenceScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.COORDINATE_TOOLS) {
                CoordinateToolsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onUseCoordinate = { navController.navigateToTopLevel(Routes.MAP) },
                )
            }
            composable(Routes.MOCK_SETUP) {
                GpsReadinessChecklist(
                    onFinished = { navController.popBackStack() },
                    selectedLanguage = settings.language,
                    followSystemLanguage = settings.followSystemLanguage,
                    onLanguageSelected = { choice ->
                        viewModel.updateSettings {
                            if (choice == null) it.copy(followSystemLanguage = true)
                            else it.copy(
                                language = AppLanguage.entries.firstOrNull { it.languageCode == choice } ?: AppLanguage.ENGLISH,
                                followSystemLanguage = false,
                            )
                        }
                    },
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                )
            }
            composable(Routes.LICENSES) {
                LicensesScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

