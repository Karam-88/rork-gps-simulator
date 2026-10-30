package com.rork.gpssimulator.location

import android.util.Log
import com.rork.gpssimulator.data.model.LatLng
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PlaceResult(
    val name: String,
    val address: String,
    val point: LatLng,
)

/**
 * Place search and reverse geocoding via OpenStreetMap Nominatim.
 * Only the typed query (or a coordinate) leaves the device.
 */
class GeocodingService {

    private val client = HttpClient(Android) {
        expectSuccess = false
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Searches places by free text. Returns an empty list on any failure. */
    suspend fun search(query: String, limit: Int = 12): List<PlaceResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        parseCoordinate(trimmed)?.let { point ->
            return@withContext listOf(
                PlaceResult(formatCoord(point), "", point),
            )
        }

        try {
            val body = client.get("$BASE/search") {
                parameter("q", trimmed)
                parameter("format", "jsonv2")
                parameter("limit", limit)
                parameter("addressdetails", 1)
                header("User-Agent", USER_AGENT)
            }.bodyAsText()

            val array = json.parseToJsonElement(body) as? JsonArray ?: return@withContext emptyList()
            array.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val display = obj["display_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: display.substringBefore(",")
                PlaceResult(
                    name = name,
                    address = display,
                    point = LatLng(lat, lon),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Place search failed: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    /** Resolves a readable name and address for a coordinate. */
    suspend fun reverse(point: LatLng): PlaceResult? = withContext(Dispatchers.IO) {
        try {
            val body = client.get("$BASE/reverse") {
                parameter("lat", point.lat)
                parameter("lon", point.lng)
                parameter("format", "jsonv2")
                parameter("zoom", 18)
                parameter("addressdetails", 1)
                header("User-Agent", USER_AGENT)
            }.bodyAsText()

            val obj = json.parseToJsonElement(body).jsonObject
            val display = obj["display_name"]?.jsonPrimitive?.content ?: return@withContext null
            val addressParts = obj["address"] as? JsonObject
            fun field(key: String): String? = addressParts?.get(key)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val street = listOf("road", "pedestrian", "residential", "footway", "street", "path")
                .firstNotNullOfOrNull { field(it) }
            val house = field("house_number")
            val name = when {
                street != null && house != null -> "$street $house"
                street != null -> street
                else -> obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: display.substringBefore(",")
            }
            PlaceResult(name = name, address = display, point = point)
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed: ${e.javaClass.simpleName}")
            null
        }
    }

    companion object {
        private const val TAG = "GeocodingService"
        private const val BASE = "https://nominatim.openstreetmap.org"
        private const val USER_AGENT = "GPSSimulator/1.0 (Android developer testing tool)"

        private fun formatCoord(point: LatLng): String =
            String.format("%.6f, %.6f", point.lat, point.lng)

        /** Parses "lat, lng" and common DMS strings into a coordinate. */
        fun parseCoordinate(input: String): LatLng? {
            val text = input.trim()
            val decimal = Regex("^\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*[,;\\s]\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*$")
                .find(text)
            if (decimal != null) {
                val lat = decimal.groupValues[1].toDoubleOrNull()
                val lng = decimal.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null) {
                    val candidate = LatLng(lat, lng)
                    if (candidate.isValid()) return candidate
                }
            }
            return parseDms(text)
        }

        /** Parses DMS strings such as 37°46'29.6"N 122°25'09.8"W. */
        fun parseDms(input: String): LatLng? {
            val pattern = Regex(
                "(\\d{1,3})\\s*[°d:]\\s*(\\d{1,2})\\s*['m:]\\s*(\\d{1,2}(?:\\.\\d+)?)\\s*[\"s]?\\s*([NSEW])",
                RegexOption.IGNORE_CASE,
            )
            val matches = pattern.findAll(input).toList()
            if (matches.size < 2) return null

            var lat: Double? = null
            var lng: Double? = null
            matches.take(2).forEach { match ->
                val deg = match.groupValues[1].toDoubleOrNull() ?: return null
                val min = match.groupValues[2].toDoubleOrNull() ?: return null
                val sec = match.groupValues[3].toDoubleOrNull() ?: return null
                val hemi = match.groupValues[4].uppercase()
                val value = (deg + min / 60.0 + sec / 3600.0) *
                    if (hemi == "S" || hemi == "W") -1.0 else 1.0
                if (hemi == "N" || hemi == "S") lat = value else lng = value
            }

            val latValue = lat
            val lngValue = lng
            if (latValue == null || lngValue == null) return null
            val result = LatLng(latValue, lngValue)
            return if (result.isValid()) result else null
        }

        /** Formats a coordinate as degrees/minutes/seconds. */
        fun toDms(point: LatLng): String {
            fun part(value: Double, positive: String, negative: String): String {
                val hemi = if (value >= 0) positive else negative
                val abs = kotlin.math.abs(value)
                val deg = abs.toInt()
                val minFull = (abs - deg) * 60
                val min = minFull.toInt()
                val sec = (minFull - min) * 60
                return String.format("%d°%02d'%04.1f\"%s", deg, min, sec, hemi)
            }
            return "${part(point.lat, "N", "S")} ${part(point.lng, "E", "W")}"
        }
    }
}
