package com.rork.gpssimulator.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rork.gpssimulator.MainActivity
import com.rork.gpssimulator.BuildConfig
import com.rork.gpssimulator.data.AppRepository
import com.rork.gpssimulator.data.AppSettings
import com.rork.gpssimulator.data.model.GeofenceEvent
import com.rork.gpssimulator.data.model.HistoryEntry
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.SessionKind
import com.rork.gpssimulator.data.model.SimRoute
import com.rork.gpssimulator.i18n.Strings
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/**
 * Owns the *entire* lifetime of an active mock-location session.
 *
 * Previously the push loop and the
 * system test-provider lived inside [com.rork.gpssimulator.ui.AppViewModel],
 * whose coroutine scope (and the test providers themselves, which Android
 * ties to the calling process) died as soon as the app was backgrounded long
 * enough for the process to be reclaimed. A started **foreground service**
 * keeps the process alive and, as long as it is alive, keeps re-feeding the
 * mocked coordinate — independent of MainActivity/AppViewModel's lifecycle,
 * navigation state, or whether the map screen is even composed.
 *
 * The UI never owns this session; it only binds to request changes
 * ([startSession], [moveTo], [togglePause], [setJoystickVector], [stopSession])
 * and observes [MockSessionBus] for the live result. Ending the session is
 * exclusively [stopSession] — called from the user's explicit STOP action, or
 * from `onTaskRemoved` when the "stop when app closes" setting is enabled.
 */
class MockLocationService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): MockLocationService = this@MockLocationService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    private lateinit var repository: AppRepository
    private lateinit var mockController: MockLocationController
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pushJob: Job? = null
    private var stopping = false

    private var kind = SessionKind.STATIC
    private var route: SimRoute? = null
    private var routeTraveled = 0.0
    @Volatile private var joystickVector: Pair<Float, Float>? = null
    @Volatile private var isPaused = false
    private var sessionStartedAt = 0L
    private var placeName = ""
    private var placeAddress = ""
    private var geofenceInside: Set<String> = emptySet()
    private var lastPersistAt = 0L

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository.getInstance(applicationContext)
        mockController = MockLocationController(applicationContext) { failure ->
            MockSessionBus.emit(MockSessionBus.Event.Failure(failure.message))
            stopSession()
        }
        diagnostic("service_created")
        createNotificationChannel()
        MockSessionBus.setServiceRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        diagnostic("service_start restart=${intent == null} start_id=$startId")
        if (intent?.action == ACTION_STOP_FROM_NOTIFICATION) {
            stopSession()
            return START_NOT_STICKY
        }
        if (intent == null && MockSessionBus.session.value == null) {
            // The system restarted this service after killing the process
            // (e.g. memory pressure) while a mock session was active. Android's
            // test-location provider dies with the process regardless of
            // START_STICKY vs START_NOT_STICKY, so recreate it from the last
            // persisted state instead of silently reverting to real GPS
            // without the user ever pressing STOP.
            restoreSessionIfPersisted()
        }
        // START_STICKY: an explicit Settings -> Force Stop is handled entirely
        // by the OS itself, which will not redeliver this restart while the
        // app is in the force-stopped state. This only recovers from ordinary
        // system-initiated process death, never bypasses a user's Force Stop.
        return START_STICKY
    }

    private fun restoreSessionIfPersisted() {
        val record = repository.mockSessionRecord.value
        if (!record.active || record.lat.isNaN() || record.lng.isNaN()) return
        val point = LatLng(record.lat, record.lng)
        if (!point.isValid()) return

        try {
            startForeground(NOTIF_ID, buildStartingNotification())
        } catch (e: RuntimeException) {
            // Android may refuse to (re)start a foreground service while the app
            // is fully backgrounded (ForegroundServiceStartNotAllowedException on
            // some OEMs/API levels), or when the permission behind the service
            // type was revoked meanwhile (SecurityException). Fail the same way
            // as a provider failure below: no stale "active" record, no crash.
            Log.w(TAG, "Foreground restart refused; not restoring the mock session", e)
            repository.clearMockSession()
            stopSelf()
            return
        }
        val result = mockController.start(
            point = point,
            accuracy = record.accuracy,
            altitude = record.altitude,
            speed = 0f,
            bearing = record.bearing,
        )
        if (result is MockResult.Failure) {
            // Can't recreate the provider (e.g. this app is no longer selected
            // as the mock location app after the restart) — don't leave a
            // stale "active" record claiming a session that doesn't exist.
            repository.clearMockSession()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // Known limitation: Route/Joystick sessions resume as a frozen point
        // at their last known coordinate rather than continuing to move, since
        // MockSessionRecord only persists the coordinate, not the full route.
        // Restoration is best effort; there can be a gap during process death.
        kind = SessionKind.STATIC
        route = null
        joystickVector = null
        isPaused = record.isPaused
        placeName = record.placeName
        placeAddress = record.placeAddress
        sessionStartedAt = record.sessionStartedAt
        geofenceInside = emptySet()
        publishSession(point, 0.0, record.bearing)
        updateNotification(point)
        startStaticLoop()
        evaluateGeofences(point)
    }

    /**
     * Real "app closed" signal: the user swiped the task away from Recents.
     * Per product requirement the mock session survives this by default —
     * Android explicitly permits a foreground service to keep running after
     * its task is removed as long as it does not stop itself here. The
     * "stop when app closes" user preference is honoured by actually ending
     * the session in that case, instead of the previous (buggy) behaviour of
     * tying it to Activity.onStop, which fires on ordinary backgrounding.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (repository.settings.value.stopOnAppClose) {
            stopSession()
        }
    }

    override fun onDestroy() {
        pushJob?.cancel()
        serviceScope.cancel()
        val destroyedSession = MockSessionBus.session.value
        mockController.stop { clean ->
            if (destroyedSession != null && MockSessionBus.session.value === destroyedSession) {
                // Do not clear a replacement service's newer session from a late callback.
                // Keep the persisted record for the existing sticky restart.
                MockSessionBus.lastStop = MockSessionBus.StopBoundary(SystemClock.elapsedRealtimeNanos(), clean)
                MockSessionBus.publish(null)
            }
            diagnostic("service_destroyed cleanup_ok=$clean")
        }
        MockSessionBus.setServiceRunning(false)
        super.onDestroy()
    }

    // ---------- Public API (called by the bound ViewModel) ----------

    fun startSession(
        kind: SessionKind,
        point: LatLng,
        placeName: String,
        placeAddress: String,
        route: SimRoute? = null,
    ): MockResult {
        // Android requires startForeground() to be called within a few seconds
        // of startForegroundService() — which AppViewModel always calls
        // immediately before invoking this method — regardless of whether
        // this particular session request ends up succeeding. Calling it
        // unconditionally as the first thing this method does (not in
        // onCreate(), since the service is created as soon as the ViewModel
        // binds to it, before any session — or permission grant — exists)
        // satisfies that contract on every single call, including repeated
        // failed attempts, without ever showing a placeholder notification
        // when no session was actually requested. The success path below
        // immediately replaces this content; the failure path tears it down
        // again via stopForeground().
        startForeground(NOTIF_ID, buildStartingNotification())
        if (stopping || mockController.isStopping) {
            return MockResult.Failure(MockFailure.PROVIDER_ERROR, "Wait for mock location cleanup to finish")
        }
        diagnostic("session_start kind=$kind")

        val outgoing = MockSessionBus.session.value
        pushJob?.cancel()

        val config = repository.settings.value
        val result = mockController.start(
            point = applyRandomization(point, config),
            accuracy = config.accuracyM,
            altitude = config.altitudeM.toDouble(),
            speed = 0f,
            bearing = config.bearingDeg,
        )
        if (result is MockResult.Failure) {
            MockSessionBus.emit(MockSessionBus.Event.Failure(result.message))
            // startForeground() was already satisfied by the placeholder
            // notification posted at the top of this method, so this call just
            // tears that placeholder (or any prior session's notification)
            // back down, since this attempt failed.
            stopSession()
            return result
        }

        // Replacing a running session (e.g. starting a route while a static
        // mock is active) records history for the one being replaced, but
        // never drops back to REAL GPS in between — the bus goes straight
        // from the old Session to the new one.
        outgoing?.let { recordHistory(it) }

        this.kind = kind
        this.route = route
        this.routeTraveled = 0.0
        this.joystickVector = null
        this.isPaused = false
        this.placeName = placeName
        this.placeAddress = placeAddress
        this.sessionStartedAt = System.currentTimeMillis()
        geofenceInside = emptySet()

        startForeground(NOTIF_ID, buildNotification(point))
        publishSession(point, 0.0, config.bearingDeg)
        persist(point, config, force = true)

        if (kind == SessionKind.ROUTE && route != null) {
            startRouteLoop(route)
        } else {
            startStaticLoop()
        }
        evaluateGeofences(point)
        return MockResult.Success
    }

    /**
     * Redirects the running session to [point] without ever stopping the
     * system-level mock: providers stay installed, only the pushed coordinate
     * changes. Jumping to a fixed point ends route/joystick playback but keeps
     * the same session (its start time, its notification) alive.
     */
    fun moveTo(point: LatLng, placeName: String, placeAddress: String): MockResult {
        if (stopping) return MockResult.Failure(MockFailure.PROVIDER_ERROR, "Wait for mock location cleanup to finish")
        if (MockSessionBus.session.value == null) {
            return startSession(SessionKind.STATIC, point, placeName, placeAddress)
        }

        val config = repository.settings.value
        val result = mockController.push(
            point = applyRandomization(point, config),
            accuracy = config.accuracyM,
            altitude = config.altitudeM.toDouble(),
            speed = 0f,
            bearing = config.bearingDeg,
        )
        if (result is MockResult.Failure) {
            MockSessionBus.emit(MockSessionBus.Event.Failure(result.message))
            stopSession()
            return result
        }

        if (kind == SessionKind.ROUTE) {
            pushJob?.cancel()
            route = null
        }
        kind = SessionKind.STATIC
        joystickVector = null
        isPaused = false
        this.placeName = placeName
        this.placeAddress = placeAddress

        publishSession(point, 0.0, config.bearingDeg)
        persist(point, config, force = true)
        updateNotification(point)
        startStaticLoop()
        evaluateGeofences(point)
        return MockResult.Success
    }

    fun togglePause() {
        if (stopping) return
        val session = MockSessionBus.session.value ?: return
        isPaused = !isPaused
        MockSessionBus.publish(
            session.copy(isPaused = isPaused, speedKmh = if (isPaused) 0.0 else session.speedKmh),
        )
        if (isPaused) {
            // Hold position immediately rather than waiting for the next tick.
            push(session.point, 0.0, repository.settings.value, session.bearing)
        }
        persist(session.point, repository.settings.value, force = true)
    }

    /** Feeds a normalised joystick vector; (0,0) stops movement. */
    fun setJoystickVector(x: Float, y: Float) {
        if (stopping) return
        if (MockSessionBus.session.value == null) return
        joystickVector = if (hypot(x, y) < 0.05f) null else x to y
        if (kind != SessionKind.ROUTE) {
            kind = if (joystickVector != null) SessionKind.JOYSTICK else SessionKind.STATIC
            MockSessionBus.session.value?.let { MockSessionBus.publish(it.copy(kind = kind)) }
        }
    }

    fun stopSession(completed: () -> Unit = {}) {
        // One completion per stop action/session: repeated taps while fused cleanup
        // is pending must not duplicate history/recovery/rating qualification.
        if (stopping) return
        stopping = true
        diagnostic("session_stopping")
        val session = MockSessionBus.session.value
        pushJob?.cancel()
        pushJob = null
        // Push loops and Stop now share the main dispatcher: no cancelled loop can
        // inject or publish a stale session between cleanup and session=null.
        // A manual stop must not be resurrected by a sticky restart while the
        // asynchronous fused cleanup is being acknowledged.
        repository.clearMockSession()
        mockController.stop { clean ->
            MockSessionBus.lastStop = MockSessionBus.StopBoundary(SystemClock.elapsedRealtimeNanos(), clean)
            Log.i(TAG, "REAL_RECOVERY stop_completed cleanup_ok=$clean elapsed_ns=${MockSessionBus.lastStop?.elapsedNanos}")
            session?.let { finishSession(it) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            // A bound service can remain alive until the Activity unbinds; injection
            // and foreground operation have ended after both backends acknowledged cleanup.
            Log.i(TAG, "REAL_RECOVERY foreground_stopped")
            stopping = false
            completed()
        }
    }

    private fun finishSession(session: MockSessionBus.Session) {
        recordHistory(session)
        MockSessionBus.publish(null)
        MockSessionBus.publishGeofenceState(emptyList(), emptySet())
        repository.clearMockSession()
        kind = SessionKind.STATIC
        route = null
        joystickVector = null
        isPaused = false
    }

    // ---------- Push loops (ported from the previous ViewModel implementation) ----------

    private fun startStaticLoop() {
        pushJob?.cancel()
        pushJob = serviceScope.launch {
            while (isActive) {
                val config = repository.settings.value
                delay(config.updateIntervalMs.toLong().coerceIn(200L, 1_000L))
                val session = MockSessionBus.session.value ?: break

                if (isPaused) {
                    if (!push(session.point, 0.0, config, session.bearing)) break
                    continue
                }

                val vector = joystickVector
                if (vector != null) {
                    val (target, speedKmh, bearing) = advanceJoystick(session.point, vector.first, vector.second, config)
                    if (!push(target, speedKmh, config, bearing)) break
                    publishSession(target, speedKmh, bearing)
                    persist(target, config)
                    evaluateGeofences(target)
                } else {
                    val target = applyRandomization(session.point, config)
                    if (!push(target, 0.0, config)) break
                }
            }
        }
    }

    private fun startRouteLoop(route: SimRoute) {
        pushJob?.cancel()
        val points = if (route.loop && route.points.size > 1) {
            route.points + route.points.first()
        } else {
            route.points
        }
        val total = points.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
        if (total <= 0.0) {
            // A degenerate route still needs a heartbeat until manual stop.
            startStaticLoop()
            return
        }
        pushJob = serviceScope.launch {

            var traveled = 0.0
            while (isActive) {
                val config = repository.settings.value
                // The initial mock position is set once when the route starts.
                // Subsequent updates follow the resolved road geometry smoothly.
                val stepMs = config.updateIntervalMs.toLong().coerceIn(200L, 1_000L)
                delay(stepMs)
                val session = MockSessionBus.session.value ?: break

                if (isPaused) {
                    if (!push(session.point, 0.0, config, session.bearing)) break
                    continue
                }

                val speedKmh = route.speedKmh
                val stepMeters = (speedKmh / 3.6) * (stepMs / 1000.0)
                traveled += stepMeters

                if (traveled >= total) {
                    if (route.loop) {
                        traveled = 0.0
                    } else {
                        val end = points.last()
                        routeTraveled = total
                        publishSession(end, 0.0, session.bearing, routeTraveledMeters = total)
                        persist(end, config, force = true)
                        if (!push(end, 0.0, config)) break
                        evaluateGeofences(end)
                        MockSessionBus.emit(MockSessionBus.Event.RouteFinished)
                        diagnostic("route_completed holding_destination=true")
                        // A completed route is still an active mock session. Keep
                        // refreshing the destination until the user explicitly stops.
                        // Otherwise the final fix becomes stale and Maps may fall back
                        // to a real location even though the foreground service remains.
                        while (isActive && MockSessionBus.session.value != null) {
                            val holdConfig = repository.settings.value
                            delay(holdConfig.updateIntervalMs.toLong().coerceIn(200L, 1_000L))
                            if (MockSessionBus.session.value == null) break
                            if (!push(end, 0.0, holdConfig, session.bearing)) break
                        }
                        break
                    }
                }

                val (position, bearing) = pointAlong(points, traveled, config.routeInterpolation)
                routeTraveled = min(traveled, total)
                publishSession(position, speedKmh, bearing, routeTraveledMeters = routeTraveled)

                if (!push(position, speedKmh, config, bearing)) break
                persist(position, config)
                evaluateGeofences(position)
            }
        }
    }

    /** Pushes one update; returns false when the platform revoked mocking. */
    private fun push(
        point: LatLng,
        speedKmh: Double,
        config: AppSettings,
        bearingOverride: Float? = null,
    ): Boolean {
        val result = mockController.push(
            point = point,
            accuracy = config.accuracyM,
            altitude = config.altitudeM.toDouble(),
            speed = (speedKmh / 3.6).toFloat(),
            bearing = bearingOverride ?: config.bearingDeg,
        )
        if (result is MockResult.Failure) {
            MockSessionBus.emit(MockSessionBus.Event.Failure(result.message))
            stopSession()
            return false
        }
        return true
    }

    // ---------- State publishing & persistence ----------

    private fun publishSession(
        point: LatLng,
        speedKmh: Double,
        bearing: Float,
        routeTraveledMeters: Double = routeTraveled,
    ) {
        val config = repository.settings.value
        MockSessionBus.publish(
            MockSessionBus.Session(
                kind = kind,
                point = point,
                accuracy = config.accuracyM,
                altitude = config.altitudeM.toDouble(),
                speedKmh = speedKmh,
                bearing = bearing,
                isPaused = isPaused,
                sessionStartedAt = sessionStartedAt,
                placeName = placeName,
                placeAddress = placeAddress,
                route = route,
                routeTraveledMeters = routeTraveledMeters,
            ),
        )
    }

    private fun persist(point: LatLng, config: AppSettings, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < PERSIST_INTERVAL_MS) return
        lastPersistAt = now
        repository.updateMockSession {
            it.copy(
                active = true,
                lat = point.lat,
                lng = point.lng,
                altitude = config.altitudeM.toDouble(),
                accuracy = config.accuracyM,
                speedKmh = MockSessionBus.session.value?.speedKmh ?: 0.0,
                bearing = config.bearingDeg,
                kind = kind,
                isPaused = isPaused,
                sessionStartedAt = sessionStartedAt,
                placeName = placeName,
                placeAddress = placeAddress,
            )
        }
        repository.updateSettings { it.copy(lastMockLat = point.lat, lastMockLng = point.lng) }
    }

    private fun recordHistory(session: MockSessionBus.Session) {
        if (!repository.settings.value.saveHistory) return
        repository.addHistory(
            HistoryEntry(
                id = UUID.randomUUID().toString(),
                name = session.placeName.ifBlank { "Dropped pin" },
                address = session.placeAddress,
                point = session.point,
                startedAt = session.sessionStartedAt,
                durationMs = System.currentTimeMillis() - session.sessionStartedAt,
                kind = session.kind,
            ),
        )
    }

    private fun evaluateGeofences(point: LatLng) {
        val fences = repository.geofences.value
        if (fences.isEmpty()) return
        val previous = geofenceInside
        val current = fences.filter { point.distanceTo(it.center) <= it.radiusMeters }
            .map { it.id }
            .toSet()

        val newEvents = mutableListOf<GeofenceEvent>()
        fences.forEach { fence ->
            val wasInside = previous.contains(fence.id)
            val isInside = current.contains(fence.id)
            if (wasInside != isInside) {
                newEvents.add(
                    GeofenceEvent(
                        geofenceId = fence.id,
                        geofenceName = fence.name,
                        entered = isInside,
                        timestamp = System.currentTimeMillis(),
                    ),
                )
            }
        }
        geofenceInside = current
        if (newEvents.isNotEmpty()) {
            val merged = (newEvents + MockSessionBus.geofenceEvents.value).take(60)
            MockSessionBus.publishGeofenceState(merged, current)
        } else {
            MockSessionBus.publishGeofenceState(MockSessionBus.geofenceEvents.value, current)
        }
    }

    // ---------- Movement math (ported from AppViewModel) ----------

    private fun applyRandomization(point: LatLng, config: AppSettings): LatLng {
        if (!config.randomizeEnabled && !config.coordinateVariation) return point
        val radius = if (config.randomizeEnabled) config.randomizationRadiusM.toDouble() else 1.5
        if (radius <= 0.0) return point
        val angle = Random.nextDouble(0.0, 360.0)
        val magnitude = radius * kotlin.math.sqrt(Random.nextDouble(0.0, 1.0))
        return point.offset(magnitude, angle)
    }

    /** Pure step function: (next point, speed km/h, bearing) for one joystick tick. */
    private fun advanceJoystick(
        base: LatLng,
        dirX: Float,
        dirY: Float,
        config: AppSettings,
    ): Triple<LatLng, Double, Float> {
        val magnitude = hypot(dirX, dirY).coerceIn(0f, 1f)
        if (magnitude < 0.05f) return Triple(base, 0.0, 0f)
        val speedKmh = config.speedKmh * magnitude
        val stepMeters = (speedKmh / 3.6) * (config.updateIntervalMs / 1000.0)
        // Screen up (negative Y) is north.
        val bearing = (Math.toDegrees(kotlin.math.atan2(dirX.toDouble(), -dirY.toDouble())) + 360.0) % 360.0
        val next = base.offset(stepMeters, bearing)
        return Triple(next, speedKmh, bearing.toFloat())
    }

    /** Position and bearing at [distance] meters along the polyline. */
    private fun pointAlong(points: List<LatLng>, distance: Double, interpolate: Boolean): Pair<LatLng, Float> {
        var remaining = distance
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val segment = a.distanceTo(b)
            if (segment <= 0.0) continue
            if (remaining <= segment) {
                val t = (remaining / segment).coerceIn(0.0, 1.0)
                val position = if (interpolate) a.lerp(b, t) else a
                return position to a.bearingTo(b)
            }
            remaining -= segment
        }
        val last = points.last()
        val prev = points.getOrElse(points.lastIndex - 1) { last }
        return last to prev.bearingTo(last)
    }

    // ---------- Notification ----------

    private fun strings(): Strings = Strings(repository.settings.value.language)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            strings()[K.notification_channel_name],
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(point: LatLng): Notification {
        val strings = strings()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MockLocationService::class.java).setAction(ACTION_STOP_FROM_NOTIFICATION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(strings[K.notification_mock_active_title])
            .setContentText(Format.coordPair(point))
            .setSubText(strings[K.notification_mock_active_text])
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(0, strings[K.notification_stop_action], stopIntent)
            .build()
    }

    private fun buildStartingNotification(): Notification {
        val strings = strings()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(strings[K.notification_mock_active_title])
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(point: LatLng) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIF_ID, buildNotification(point))
    }

    companion object {
        private const val TAG = "MockLocationService"
        private const val CHANNEL_ID = "mock_location_session"
        private const val NOTIF_ID = 421
        private const val PERSIST_INTERVAL_MS = 3_000L
        const val ACTION_STOP_FROM_NOTIFICATION = "com.rork.gpssimulator.action.STOP_MOCK"
    }

    private fun diagnostic(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "MOCK_DIAG $message")
    }
}

