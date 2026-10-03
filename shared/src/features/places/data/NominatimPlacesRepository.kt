package features.places.data

import core.geometry.GeoPoint
import core.network.PlatformHttp
import features.places.domain.Place
import features.places.domain.PlaceCategory
import features.places.domain.PlacesRepository

class NominatimPlacesRepository : PlacesRepository {

    private val headers = mapOf(
        "User-Agent" to "KrakowMapViewer/1.0 (KotlinMultiplatform; CleanArchitecture; Krakow)",
        "Accept-Language" to "pl,en;q=0.8"
    )

    override fun getCuratedKrakowPlaces(): List<Place> {
        return KrakowCuratedPlaces.ALL
    }

    override fun getPlaceById(id: String): Place? {
        return KrakowCuratedPlaces.ALL.find { it.id == id }
    }

    override suspend fun searchPlaces(query: String): List<Place> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return getCuratedKrakowPlaces()
        }

        // Check local curated places first
        val localMatches = KrakowCuratedPlaces.ALL.filter {
            it.name.contains(trimmed, ignoreCase = true) ||
            it.polishName.contains(trimmed, ignoreCase = true) ||
            it.description.contains(trimmed, ignoreCase = true) ||
            it.category.title.contains(trimmed, ignoreCase = true)
        }

        // Query OpenStreetMap Nominatim Open API (no key required)
        val remotePlaces = runCatching {
            fetchNominatimPlaces(trimmed)
        }.getOrDefault(emptyList())

        // Combine results, prioritizing local curated landmarks and deduplicating
        val combined = mutableListOf<Place>()
        combined.addAll(localMatches)

        for (remote in remotePlaces) {
            val isDuplicate = combined.any {
                it.coordinate.latitude.isCloseTo(remote.coordinate.latitude) &&
                it.coordinate.longitude.isCloseTo(remote.coordinate.longitude)
            }
            if (!isDuplicate) {
                combined.add(remote)
            }
        }

        return combined
    }

    private suspend fun fetchNominatimPlaces(query: String): List<Place> {
        val encodedQuery = query.replace(" ", "+")
        // Bounded to Krakow viewbox (19.75, 50.15, 20.20, 49.95)
        val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&bounded=1&viewbox=19.75,50.15,20.20,49.95&limit=8"

        val responseText = PlatformHttp.getText(url, headers) ?: return emptyList()
        return parseNominatimJson(responseText)
    }

    /**
     * Clean, zero-dependency JSON parser for Nominatim search responses.
     */
    private fun parseNominatimJson(json: String): List<Place> {
        val places = mutableListOf<Place>()
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()

        // Split by JSON object boundaries: {"place_id": ...}
        val items = trimmed.removeSurrounding("[", "]").split("},{")
        var counter = 1

        for (rawItem in items) {
            val item = rawItem.replace("{", "").replace("}", "")
            val latStr = extractJsonStringField(item, "lat") ?: continue
            val lonStr = extractJsonStringField(item, "lon") ?: continue
            val displayName = extractJsonStringField(item, "display_name") ?: continue
            val placeClass = extractJsonStringField(item, "class") ?: "tourism"
            val placeType = extractJsonStringField(item, "type") ?: "landmark"

            val lat = latStr.toDoubleOrNull() ?: continue
            val lon = lonStr.toDoubleOrNull() ?: continue

            val title = displayName.split(",").firstOrNull()?.trim() ?: displayName
            val description = displayName.split(",").drop(1).take(3).joinToString(", ").trim()

            val category = when (placeClass) {
                "historic" -> PlaceCategory.MONUMENT
                "tourism" -> PlaceCategory.CULTURE
                "amenity" -> if (placeType.contains("worship")) PlaceCategory.CHURCH else PlaceCategory.CULTURE
                "leisure" -> PlaceCategory.PARK
                "highway", "railway" -> PlaceCategory.TRANSPORT
                else -> PlaceCategory.SQUARE
            }

            places.add(
                Place(
                    id = "nominatim_${counter++}",
                    name = title,
                    polishName = title,
                    description = if (description.isNotEmpty()) description else "Krakow landmark from OpenStreetMap",
                    category = category,
                    coordinate = GeoPoint(lat, lon),
                    yearEstablished = null,
                    highlights = listOf("OSM Type: $placeType", "Class: $placeClass")
                )
            )
        }

        return places
    }

    private fun extractJsonStringField(jsonChunk: String, fieldName: String): String? {
        val pattern = "\"$fieldName\"\\s*:\\s*\"([^\"]*)\""
        val regex = Regex(pattern)
        return regex.find(jsonChunk)?.groupValues?.getOrNull(1)
    }

    private fun Double.isCloseTo(other: Double, epsilon: Double = 0.0008): Boolean {
        return kotlin.math.abs(this - other) < epsilon
    }
}
