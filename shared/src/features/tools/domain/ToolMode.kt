package features.tools.domain

enum class ToolMode(
    val title: String,
    val iconName: String,
    val hint: String
) {
    PAN("Navigate", "Pan", "Drag to pan, scroll or pinch to zoom"),
    MEASURE("Measure", "Ruler", "Click points on map to measure real geodesic distance"),
    ADD_PIN("Add Pin", "Pin", "Click anywhere on Krakow map to drop a custom render marker"),
    DRAW_PATH("Draw Path", "Line", "Click points to draw a polyline route. Click 'Finish' when done"),
    DRAW_POLYGON("Draw Area", "Polygon", "Click points to define a polygon area. Click 'Finish' when done"),
    INSPECT("Inspect", "Crosshair", "Click or hover to inspect exact geographic and tile coordinates")
}
