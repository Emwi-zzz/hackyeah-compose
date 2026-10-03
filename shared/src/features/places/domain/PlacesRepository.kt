package features.places.domain

interface PlacesRepository {
    fun getCuratedKrakowPlaces(): List<Place>
    suspend fun searchPlaces(query: String): List<Place>
    fun getPlaceById(id: String): Place?
}
