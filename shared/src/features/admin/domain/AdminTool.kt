package features.admin.domain

enum class AdminTool(val title: String, val kind: Kind, val hint: String) {
    MALL_OUTLINE("Mall outline", Kind.POLYGON, "Click the corners of the building footprint (shown when the map is zoomed out), then Finish."),
    FLOOR_OUTLINE("Floor shape", Kind.POLYGON, "Click the corners of the selected floor's shape, then Finish. Replaces the current shape."),
    STORE("Store", Kind.POLYGON, "Enter a name, click the corners of the store on the selected floor, then Finish."),
    ENTRANCE("Entrance", Kind.POINT, "Enter a name and click where the building entrance / exit is."),
    ELEVATOR("Elevator", Kind.POINT, "Click to place an elevator on the selected floor."),
    ESCALATOR_UP("Escalator ↑", Kind.POINT, "Click to place an up escalator on the selected floor."),
    ESCALATOR_DOWN("Escalator ↓", Kind.POINT, "Click to place a down escalator on the selected floor."),
    DELETE("Delete", Kind.POINT, "Click a store, elevator, escalator or entrance to remove it.");

    enum class Kind { POLYGON, POINT }
}
