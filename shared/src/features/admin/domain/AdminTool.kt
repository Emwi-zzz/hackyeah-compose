package features.admin.domain

enum class AdminTool(val title: String, val kind: Kind, val hint: String) {
    MALL_OUTLINE("Mall outline", Kind.POLYGON, "Click around the building footprint, choose straight or smooth Bézier edges, then Finish."),
    FLOOR_OUTLINE("Floor shape", Kind.POLYGON, "Click around the floor shape, choose straight or smooth Bézier edges, then Finish."),
    STORE("Store", Kind.POLYGON, "Enter a name, click around the store, choose straight or smooth Bézier edges, then Finish."),
    ENTRANCE("Entrance", Kind.POINT, "Enter a name and click where the building entrance / exit is."),
    ELEVATOR("Elevator", Kind.POINT, "Click to place an elevator on the selected floor."),
    ESCALATOR_UP("Escalator ↑", Kind.POINT, "Click to place an up escalator on the selected floor."),
    ESCALATOR_DOWN("Escalator ↓", Kind.POINT, "Click to place a down escalator on the selected floor."),
    DELETE("Delete", Kind.POINT, "Click a store, elevator, escalator or entrance to remove it.");

    enum class Kind { POLYGON, POINT }
}
