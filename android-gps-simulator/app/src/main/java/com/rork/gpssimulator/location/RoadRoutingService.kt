package com.rork.gpssimulator.location

import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.SimRoute
import com.rork.gpssimulator.data.model.SpeedProfile
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Preview road routing: uses separate OSRM graphs for car/bike/foot.
 * The publicly hosted FOSSGIS endpoint is intended only for light non-commercial
 * testing; production distribution needs an operator-approved or self-hosted provider.
 * Fail closed: a failed route never becomes a fabricated straight-line session.
 */
class RoadRoutingService {
    private val client = HttpClient(Android) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun resolve(route: SimRoute): Result<SimRoute> = withContext(Dispatchers.IO) {
        runCatching {
            require(route.points.size in 2..12) { "Please add 2–12 waypoints." }
            require(route.points.all { it.isValid() }) { "Invalid waypoint coordinates." }
            val mode = when (route.profile) {
                SpeedProfile.DRIVING, SpeedProfile.CUSTOM -> "car"
                SpeedProfile.CYCLING -> "bike"
                SpeedProfile.WALKING, SpeedProfile.RUNNING -> "foot"
            }
            val allPoints = mutableListOf<LatLng>()
            val stops = if (route.loop) route.points + route.points.first() else route.points
            for ((index, pair) in stops.zipWithNext().withIndex()) {
                // OSRM coordinates are lon,lat; GeoJSON coordinates are also lon,lat.
                val (from, to) = pair
                val url = "https://routing.openstreetmap.de/routed-$mode/route/v1/driving/" +
                    "${from.lng},${from.lat};${to.lng},${to.lat}" +
                    "?overview=full&geometries=geojson&steps=false"
                val response = client.get(url) { header("User-Agent", "GPSSimulator/1.1 Android; support gps.simulator.support@gmail.com") }
                require(response.status.value == 200) { "Routing server unavailable. Try again later." }
                val obj = json.parseToJsonElement(response.bodyAsText()).jsonObject
                require(obj["code"]?.jsonPrimitive?.content == "Ok") { "No supported road or path found." }
                val routeObject = obj["routes"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: error("No supported road or path found.")
                val coords = routeObject["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
                    ?: error("Route geometry is missing.")
                val segment = coords.map { xy ->
                    val pair = xy.jsonArray
                    LatLng(pair[1].jsonPrimitive.content.toDouble(), pair[0].jsonPrimitive.content.toDouble())
                }
                require(segment.size >= 2 && segment.size <= 100_000 && segment.all { it.isValid() }) {
                    "Invalid routing geometry."
                }
                // Reject endpoints that have been snapped too far from user-selected stops.
                require(segment.first().distanceTo(from) <= 250 && segment.last().distanceTo(to) <= 250) {
                    "Waypoint too far from a supported road or path."
                }
                // Bound cumulative geometry before appending each segment, so an
                // unusually large multi-leg response cannot grow memory unchecked.
                val appendedCount = segment.size - if (index == 0) 0 else 1
                require(allPoints.size + appendedCount <= 300_000) {
                    "Route is too long. Please use fewer waypoints."
                }
                allPoints.addAll(if (index == 0) segment else segment.drop(1))
                if (index < stops.size - 2) delay(1100) // public provider <= 1 request/sec
            }
            require(allPoints.size >= 2 && allPoints.size <= 300_000) { "Could not calculate a real route." }
            // A loop geometry already closes back to the first point; keeping the
            // loop flag preserves repeated playback without a new straight leg.
            route.copy(points = allPoints)
        }.onFailure { failure ->
            // Cancellation is not a routing failure: allow the caller to stop
            // requests when the user leaves the editor or cancels calculation.
            if (failure is CancellationException) throw failure
        }
    }
}
