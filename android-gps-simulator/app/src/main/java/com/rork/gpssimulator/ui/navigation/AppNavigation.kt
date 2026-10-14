package com.rork.gpssimulator.ui.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rork.gpssimulator.data.model.ThemeMode
import com.rork.gpssimulator.i18n.AppLanguage
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.i18n.Strings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.screens.AboutScreen
import com.rork.gpssimulator.ui.screens.CoordinateToolsScreen
import com.rork.gpssimulator.ui.screens.DiagnosticsScreen
import com.rork.gpssimulator.ui.screens.GeofenceScreen
import com.rork.gpssimulator.ui.screens.HistoryScreen
import com.rork.gpssimulator.ui.screens.LicensesScreen
import com.rork.gpssimulator.ui.screens.MapScreen
import com.rork.gpssimulator.ui.screens.MockSetupScreen
import com.rork.gpssimulator.ui.screens.PresetsScreen
import com.rork.gpssimulator.ui.screens.RoutesScreen
import com.rork.gpssimulator.ui.screens.SearchScreen
import com.rork.gpssimulator.ui.screens.SettingsScreen
import com.rork.gpssimulator.ui.theme.AppTheme

object Routes {
    const val MAP = "map"
    const val ROUTES = "routes"
    const val PRESETS = "presets"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val DIAGNOSTICS = "diagnostics"
    const val GEOFENCE = "geofence"
    const val COORDINATE_TOOLS = "coordinate_tools"
    const val MOCK_SETUP = "mock_setup"
    const val ABOUT = "about"
    const val LICENSES = "licenses"
}

private data class TabItem(
    val route: String,
    val icon: ImageVector,
    val labelKey: K,
)

private val Tabs = listOf(
    TabItem(Routes.MAP, Icons.Default.Map, K.tab_map),
    TabItem(Routes.ROUTES, Icons.Default.Route, K.tab_routes),
    // This destination contains both Favorites and developer Presets, but Favorites
    // are the user-facing saved locations. Expose them directly in the bottom bar
    // so a newly saved favorite is always easy to find.
    TabItem(Routes.PRESETS, Icons.Default.Star, K.favorites),
    TabItem(Routes.HISTORY, Icons.Default.History, K.tab_history),
    TabItem(Routes.SETTINGS, Icons.Default.Settings, K.tab_settings),
)

/**
 * Content height of the Material bottom navigation bar, excluding the system
 * navigation inset the bar adds underneath itself. Callers add the measured
 * inset so floating controls clear the bar on every navigation mode.
 */
private val BottomBarContentHeight = 80.dp

@Composable
fun AppNavigation() {
    val viewModel: AppViewModel = viewModel()
    val settings by viewModel.settings.collectAsState()

    val darkTheme = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val strings = remember(settings.language) { Strings(settings.language) }
    val layoutDirection = if (settings.language == AppLanguage.ARABIC) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    AppTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(
            LocalStrings provides strings,
            LocalLayoutDirection provides layoutDirection,
        ) {
            AppScaffold(viewModel)
        }
    }
}

@Composable
private fun AppScaffold(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val strings = LocalStrings.current
    val snackbarHostState = remember { SnackbarHostState() }

    val message by viewModel.message.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        viewModel.refreshPermissionState()
    }

    // Ask for location access on first composition. Also request notification
    // permission on API 33+ so the "Mock location active" foreground-service
    // notification can actually be shown; the mock session itself does not
    // depend on this being granted.
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

    // Re-check readiness whenever the app returns to the foreground, since the
    // user may have just selected this app in Developer Options.
    //
    // Deliberately no ON_STOP handling here: ON_STOP fires on ordinary
    // backgrounding (switching to another app, pressing Home) — tying the mock
    // session's lifetime to it was the root cause of mocking silently
    // reverting to real GPS. The active session now lives in
    // MockLocationService, independent of this screen's lifecycle; the
    // "stop when app closes" preference is honoured in the service's
    // onTaskRemoved, which only fires when the task is actually removed from
    // Recents.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissionState()
    }

    // Surface one-shot messages.
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = current.text,
            duration = SnackbarDuration.Short,
        )
        viewModel.consumeMessage()
    }

    // The tab bar stays available during MOCK ACTIVE so the user can jump to
    // Favorites / History and redirect the running session with "Move Here".
    // The red X sits above it via bottomInset and is never covered.
    val showBottomBar = currentRoute in Tabs.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(navController = navController, currentRoute = currentRoute)
            }
        },
    ) { _ ->
        // The map is full-bleed, so screens handle their own insets instead of
        // consuming the scaffold padding.
        //
        // This is the bar's *content* height only. Screens that use their own
        // Scaffold already receive the system navigation inset through its content
        // padding; the full-bleed map adds that inset itself.
        val bottomInset = if (showBottomBar) BottomBarContentHeight else 0.dp

        NavHost(
            navController = navController,
            startDestination = Routes.MAP,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.MAP) {
                MapScreen(
                    viewModel = viewModel,
                    bottomInset = bottomInset,
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenFavorites = { navController.navigateToTab(Routes.PRESETS) },
                )
            }
            composable(Routes.ROUTES) {
                RoutesScreen(
                    viewModel = viewModel,
                    bottomInset = bottomInset,
                    onRouteStarted = { navController.navigateToTab(Routes.MAP) },
                )
            }
            composable(Routes.PRESETS) {
                PresetsScreen(
                    viewModel = viewModel,
                    bottomInset = bottomInset,
                    onUseLocation = { navController.navigateToTab(Routes.MAP) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    viewModel = viewModel,
                    bottomInset = bottomInset,
                    onRestore = { navController.navigateToTab(Routes.MAP) },
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
                    onOpenPresets = { navController.navigateToTab(Routes.PRESETS) },
                    onOpenRoutes = { navController.navigateToTab(Routes.ROUTES) },
                )
            }

            // ---- Detail destinations (no bottom bar) ----
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
                    onUseCoordinate = { navController.navigateToTab(Routes.MAP) },
                )
            }
            composable(Routes.MOCK_SETUP) {
                MockSetupScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
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

@Composable
private fun AppBottomBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    val strings = LocalStrings.current
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = NavigationBarDefaults.Elevation,
    ) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        Tabs.forEach { tab ->
            val selected = backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateToTab(tab.route) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = strings[tab.labelKey],
                        modifier = Modifier.size(26.dp),
                    )
                },
                label = {
                    Text(
                        text = strings[tab.labelKey],
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

/** Switches top-level tabs without stacking duplicate destinations. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
