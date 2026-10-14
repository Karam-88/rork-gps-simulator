package com.rork.gpssimulator.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.rork.gpssimulator.data.model.Geofence
import com.rork.gpssimulator.data.model.HistoryEntry
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.MockSessionRecord
import com.rork.gpssimulator.data.model.SavedKind
import com.rork.gpssimulator.data.model.SavedLocation
import com.rork.gpssimulator.data.model.SessionKind
import com.rork.gpssimulator.data.model.SimRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Single local persistence surface. Everything is stored on-device in
 * SharedPreferences as JSON; nothing is uploaded anywhere.
 */
class AppRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _settings = MutableStateFlow(load(KEY_SETTINGS, AppSettings()))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _saved = MutableStateFlow(loadList<SavedLocation>(KEY_SAVED))
    val saved: StateFlow<List<SavedLocation>> = _saved.asStateFlow()

    private val _routes = MutableStateFlow(loadList<SimRoute>(KEY_ROUTES))
    val routes: StateFlow<List<SimRoute>> = _routes.asStateFlow()

    private val _history = MutableStateFlow(loadList<HistoryEntry>(KEY_HISTORY))
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _geofences = MutableStateFlow(loadList<Geofence>(KEY_GEOFENCES))
    val geofences: StateFlow<List<Geofence>> = _geofences.asStateFlow()

    private val _recentSearches = MutableStateFlow(loadList<String>(KEY_RECENT))
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _mockSession = MutableStateFlow(load(KEY_MOCK_SESSION, MockSessionRecord()))
    val mockSessionRecord: StateFlow<MockSessionRecord> = _mockSession.asStateFlow()

    // ---- Active mock session (owned by MockLocationService) ----

    /**
     * Persists the active-session snapshot so it survives process death. This is
     * a best-effort record only: while [MockLocationService] is alive, its own
     * in-memory state (via MockSessionBus) is authoritative.
     *
     * When a running STATIC mock is redirected to another fixed coordinate,
     * preserve the point being left in History before replacing the durable
     * snapshot. Route/joystick movement is deliberately excluded so their
     * frequent coordinate updates do not flood History with hundreds of rows.
     */
    fun updateMockSession(transform: (MockSessionRecord) -> MockSessionRecord) {
        val previous = _mockSession.value
        val next = transform(previous)

        if (
            previous.active && next.active &&
            previous.kind == SessionKind.STATIC && next.kind == SessionKind.STATIC &&
            !previous.lat.isNaN() && !previous.lng.isNaN() &&
            (previous.lat != next.lat || previous.lng != next.lng)
        ) {
            val now = System.currentTimeMillis()
            addHistory(
                HistoryEntry(
                    id = UUID.randomUUID().toString(),
                    name = previous.placeName.ifBlank { "Dropped pin" },
                    address = previous.placeAddress,
                    point = LatLng(previous.lat, previous.lng),
                    startedAt = previous.sessionStartedAt,
                    durationMs = (now - previous.sessionStartedAt).coerceAtLeast(0L),
                    kind = SessionKind.STATIC,
                ),
            )
        }

        _mockSession.value = next
        store(KEY_MOCK_SESSION, next)
    }

    /**
     * Marks the persisted session as no longer active. This deliberately flips
     * only the flag instead of resetting to a fresh [MockSessionRecord]: its
     * default coordinates are NaN, which kotlinx.serialization refuses to encode,
     * so [store] would fail silently and the old `active = true` record would stay
     * on disk. The leftover coordinates are harmless, since restoring checks
     * `active` first.
     */
    fun clearMockSession() = updateMockSession { it.copy(active = false) }

    // ---- Settings ----

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        store(KEY_SETTINGS, next)
    }

    fun resetSettings() {
        val current = _settings.value
        // Keep the persisted camera so the map does not jump unexpectedly.
        val fresh = AppSettings(
            lastCameraLat = current.lastCameraLat,
            lastCameraLng = current.lastCameraLng,
            lastCameraZoom = current.lastCameraZoom,
        )
        _settings.value = fresh
        store(KEY_SETTINGS, fresh)
    }

    // ---- Saved locations (favorites + developer presets) ----

    fun upsertSaved(location: SavedLocation) {
        val list = _saved.value.toMutableList()
        val index = list.indexOfFirst { it.id == location.id }
        if (index >= 0) list[index] = location else list.add(0, location)
        _saved.value = list
        store(KEY_SAVED, list)
    }

    fun deleteSaved(id: String) {
        val list = _saved.value.filterNot { it.id == id }
        _saved.value = list
        store(KEY_SAVED, list)
    }

    fun clearSaved(kind: SavedKind) {
        val list = _saved.value.filterNot { it.kind == kind }
        _saved.value = list
        store(KEY_SAVED, list)
    }

    fun replaceSaved(list: List<SavedLocation>) {
        _saved.value = list
        store(KEY_SAVED, list)
    }

    // ---- Routes ----

    fun upsertRoute(route: SimRoute) {
        val list = _routes.value.toMutableList()
        val index = list.indexOfFirst { it.id == route.id }
        if (index >= 0) list[index] = route else list.add(0, route)
        _routes.value = list
        store(KEY_ROUTES, list)
    }

    fun deleteRoute(id: String) {
        val list = _routes.value.filterNot { it.id == id }
        _routes.value = list
        store(KEY_ROUTES, list)
    }

    fun replaceRoutes(list: List<SimRoute>) {
        _routes.value = list
        store(KEY_ROUTES, list)
    }

    // ---- History ----

    fun addHistory(entry: HistoryEntry) {
        if (!_settings.value.saveHistory) return
        val list = (listOf(entry) + _history.value).take(MAX_HISTORY)
        _history.value = list
        store(KEY_HISTORY, list)
    }

    fun deleteHistory(id: String) {
        val list = _history.value.filterNot { it.id == id }
        _history.value = list
        store(KEY_HISTORY, list)
    }

    fun clearHistory() {
        _history.value = emptyList()
        store(KEY_HISTORY, emptyList<HistoryEntry>())
    }

    // ---- Geofences ----

    fun upsertGeofence(fence: Geofence) {
        val list = _geofences.value.toMutableList()
        val index = list.indexOfFirst { it.id == fence.id }
        if (index >= 0) list[index] = fence else list.add(0, fence)
        _geofences.value = list
        store(KEY_GEOFENCES, list)
    }

    fun deleteGeofence(id: String) {
        val list = _geofences.value.filterNot { it.id == id }
        _geofences.value = list
        store(KEY_GEOFENCES, list)
    }

    // ---- Recent searches ----

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val list = (listOf(trimmed) + _recentSearches.value.filterNot { it.equals(trimmed, true) })
            .take(MAX_RECENT)
        _recentSearches.value = list
        store(KEY_RECENT, list)
    }

    // ---- Destructive ----

    fun deleteAllLocalData() {
        prefs.edit().clear().apply()
        _settings.value = AppSettings()
        _saved.value = emptyList()
        _routes.value = emptyList()
        _history.value = emptyList()
        _geofences.value = emptyList()
        _recentSearches.value = emptyList()
        _mockSession.value = MockSessionRecord()
    }

    // ---- Serialization helpers ----

    private inline fun <reified T> load(key: String, fallback: T): T {
        val raw = prefs.getString(key, null) ?: return fallback
        return try {
            json.decodeFromString<T>(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode $key, using default")
            fallback
        }
    }

    private inline fun <reified T> loadList(key: String): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<T>>(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode list $key, using empty list")
            emptyList()
        }
    }

    private inline fun <reified T> store(key: String, value: T) {
        try {
            prefs.edit().putString(key, json.encodeToString(value)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist $key")
        }
    }

    companion object {
        private const val TAG = "AppRepository"
        private const val PREFS = "gps_simulator_prefs"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_SAVED = "saved_locations"
        private const val KEY_ROUTES = "routes"
        private const val KEY_HISTORY = "history"
        private const val KEY_GEOFENCES = "geofences"
        private const val KEY_RECENT = "recent_searches"
        private const val KEY_MOCK_SESSION = "mock_session"
        private const val MAX_HISTORY = 200
        private const val MAX_RECENT = 10

        @Volatile
        private var instance: AppRepository? = null

        /**
         * Single process-wide instance. The mock location foreground service and
         * the ViewModel must observe the exact same StateFlows (settings, routes,
         * geofences, ...) so that changes made from the UI while a session is
         * running are seen immediately, rather than each holding its own stale
         * SharedPreferences snapshot.
         */
        fun getInstance(context: Context): AppRepository =
            instance ?: synchronized(this) {
                instance ?: AppRepository(context.applicationContext).also { instance = it }
            }
    }
}
