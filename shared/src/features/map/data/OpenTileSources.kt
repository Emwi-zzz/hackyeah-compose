package features.map.data

import features.map.domain.TileSource

object OpenTileSources {
    val STADIA_DARK = TileSource(
        id = "stadia_dark",
        name = "Dark streets",
        description = "Dark city map with streets down to building scale",
        attribution = "© Stadia Maps, © OpenMapTiles, © OpenStreetMap contributors",
        urlTemplate = "https://tiles.stadiamaps.com/tiles/alidade_smooth_dark/{z}/{x}/{y}.png",
        minZoom = 0,
        maxZoom = 20
    )

    val OSM_STANDARD = TileSource(
        id = "osm_standard",
        name = "OpenStreetMap",
        description = "Standard crowd-sourced map of Krakow and world",
        attribution = "© OpenStreetMap contributors (ODbL)",
        urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
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
        STADIA_DARK,
        OSM_STANDARD,
        OPEN_TOPO
    )

    val DEFAULT: TileSource = STADIA_DARK
}
