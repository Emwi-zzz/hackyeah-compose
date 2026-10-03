package core.geometry

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90, got: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180, got: $longitude" }
    }

    companion object {
        val KRAKOW_RYNEK = GeoPoint(50.0619, 19.9373)
        val WAWEL_CASTLE = GeoPoint(50.0540, 19.9354)
        val SUKIENNICE = GeoPoint(50.0617, 19.9373)
        val KOSCIOL_MARIACKI = GeoPoint(50.0617, 19.9391)
        val KAZIMIERZ_PLAC_NOWY = GeoPoint(50.0519, 19.9449)
        val KOPIEC_KOSCIUSZKI = GeoPoint(50.0549, 19.8933)
        val KRAKOW_GLOWNY = GeoPoint(50.0683, 19.9482)
        val NOWA_HUTA_PLAC_CENTRALNY = GeoPoint(50.0718, 20.0375)
        val BLONIA_KRAKOWSKIE = GeoPoint(50.0594, 19.9078)
        val KRAKOW_CENTER = KRAKOW_RYNEK
    }
}
