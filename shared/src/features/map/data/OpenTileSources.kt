package features.map.data

import features.map.domain.TileSource

object OpenTileSources {
    val OSM_STANDARD = TileSource(
        id = "osm_standard",
        name = "OpenStreetMap",
        description = "Standard crowd-sourced map of Krakow and world",
        attribution = "© OpenStreetMap contributors (ODbL)",
        urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        minZoom = 0,
        maxZoom = 19
    )

    val CARTO_VOYAGER = TileSource(
        id = "carto_voyager",
        name = "Carto Voyager",
        description = "Vibrant, high-contrast city map with highlighted landmarks",
        attribution = "© OpenStreetMap contributors, © CARTO",
        urlTemplate = "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png",
        minZoom = 0,
        maxZoom = 19
    )

    val CARTO_POSITRON = TileSource(
        id = "carto_positron",
        name = "Carto Positron",
        description = "Clean minimalist light map, ideal for custom overlays and data renders",
        attribution = "© OpenStreetMap contributors, © CARTO",
        urlTemplate = "https://a.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png",
        minZoom = 0,
        maxZoom = 19
    )

    val OPEN_TOPO = TileSource(
        id = "open_topo",
        name = "OpenTopoMap",
        description = "Topographical map with contour lines and hillshades",
        attribution = "© OpenStreetMap contributors, SRTM | Map style: © OpenTopoMap (CC-BY-SA)",
        urlTemplate = "https://tile.opentopomap.org/{z}/{x}/{y}.png",
        minZoom = 0,
        maxZoom = 17
    )

    val ALL: List<TileSource> = listOf(
        OSM_STANDARD,
        CARTO_VOYAGER,
        CARTO_POSITRON,
        OPEN_TOPO
    )

    val DEFAULT: TileSource = OSM_STANDARD
}
