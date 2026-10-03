package sklepsearch

fun Mall.getFloor(floorNumber: Int): Floor? {
    return floors.find { it.number == floorNumber }
}

fun Mall.findStoreAt(point: Point, floorNumber: Int): Store? {
    val floor = getFloor(floorNumber) ?: return null
    return floor.stores.find { it.area.contains(point) }
}
