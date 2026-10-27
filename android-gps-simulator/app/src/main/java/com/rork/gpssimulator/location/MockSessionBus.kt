package com.rork.gpssimulator.location

import com.rork.gpssimulator.data.model.GeofenceEvent
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.SessionKind
import com.rork.gpssimulator.data.model.SimRoute
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide, in-memory source of truth for the active mock session.
 *
 * [MockLocationService] is the only writer: it owns the push loop and the
 * system-level mock provider, independent of any Activity/ViewModel. The UI
 * (through AppViewModel) only ever *reads* this bus and sends commands to the
 * service — it must never derive the session's lifetime from its own
 * lifecycle. Because this is a plain Kotlin singleton scoped to the process,
 * a freshly created ViewModel (e.g. after the Activity was recreated while
 * the foreground service kept the process alive) immediately observes the
 * live session on first collection, which is what makes "reopen the app
 * while mocking" reconnect correctly instead of showing REAL GPS.
 */
object MockSessionBus {

    data class StopBoundary(val elapsedNanos: Long, val cleanupSucceeded: Boolean)
    // Written before publishing session=null, so readers never race provider cleanup.
    var lastStop: StopBoundary? = null
        internal set

    /** Live, authoritative snapshot of what is currently being fed to Android. */
    data class Session(
        val kind: SessionKind,
        val point: LatLng,
        val accuracy: Float,
        val altitude: Double,
        val speedKmh: Double,
        val bearing: Float,
        val isPaused: Boolean,
        val sessionStartedAt: Long,
        val placeName: String,
        val placeAddress: String,
        val route: SimRoute? = null,
        val routeTraveledMeters: Double = 0.0,
    )

    sealed interface Event {
        data class Failure(val message: String) : Event
        data object RouteFinished : Event
    }

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events

    /**
     * Geofence membership is only meaningful while a mock session is feeding
     * points, so it is evaluated inside the service's own push loop (which now
     * owns every point update) rather than back in the ViewModel.
     */
    private val _geofenceEvents = MutableStateFlow<List<GeofenceEvent>>(emptyList())
    val geofenceEvents: StateFlow<List<GeofenceEvent>> = _geofenceEvents.asStateFlow()

    private val _geofenceInside = MutableStateFlow<Set<String>>(emptySet())
    val geofenceInside: StateFlow<Set<String>> = _geofenceInside.asStateFlow()

    /** Called only by [MockLocationService]. */
    internal fun publish(session: Session?) {
        _session.value = session
    }

    internal fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    internal fun emit(event: Event) {
        _events.tryEmit(event)
    }

    internal fun publishGeofenceState(events: List<GeofenceEvent>, inside: Set<String>) {
        _geofenceEvents.value = events
        _geofenceInside.value = inside
    }

    /** Called by AppViewModel when the user wipes local data. */
    fun resetGeofenceState() {
        _geofenceEvents.value = emptyList()
        _geofenceInside.value = emptySet()
    }
}
