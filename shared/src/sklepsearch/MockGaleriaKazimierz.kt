package sklepsearch

object MockGaleriaKazimierz {

    private fun createKazimierzFloor(
        floorNumber: Int,
        storesList: List<Triple<String, String, Path2D>>,
        escalatorsList: List<Pair<Point, EscalatorDirection>>,
        elevatorsList: List<Point>
    ): Floor {
        // Complex, non-rectangular building footprint with architectural Bézier curves:
        // Sweeping cubic Bézier curve for the northeast glass rotunda & entrance,
        // curved southern riverfront facade via quadratic Bézier curve,
        // and filleted inner courtyard corner via quadratic Bézier curve.
        val complexBox = Path2D().apply {
            moveTo(80.0, 160.0)
            lineTo(620.0, 100.0) // Angled north facade
            // Sweeping cubic Bézier curve for the northeast glass rotunda & entrance
            curveTo(760.0, 95.0, 920.0, 180.0, 900.0, 340.0)
            lineTo(900.0, 840.0) // East facade
            // Curved southern riverfront facade using quadratic Bézier curve
            quadTo(730.0, 920.0, 560.0, 880.0)
            lineTo(560.0, 560.0) // Inner courtyard wing
            // Filleted curved inner courtyard corner using quadratic Bézier curve
            quadTo(560.0, 520.0, 520.0, 520.0)
            lineTo(80.0, 520.0)  // West wing south boundary
            closePath()
        }

        var instanceCounter = (floorNumber + 10) * 1000L

        val stores = storesList.mapIndexed { idx, (name, category, area) ->
            val bounds = area.getBounds()
            val entryPoint = Point(bounds.centerX, bounds.centerY)
            Store(
                Instanceid = instanceCounter++,
                Shopid = 500L + floorNumber * 50 + idx,
                name = name,
                area = area,
                entryPoints = listOf(entryPoint),
                category = category
            )
        }

        val escalators = escalatorsList.mapIndexed { idx, (pt, dir) ->
            Escalator(
                id = 5000L + floorNumber * 10 + idx,
                coordinates = pt,
                direction = dir
            )
        }

        val elevators = elevatorsList.mapIndexed { idx, pt ->
            Elevator(
                id = 6000L + floorNumber * 10 + idx,
                coordinates = pt
            )
        }

        return Floor(
            number = floorNumber,
            box = complexBox,
            stores = stores,
            elevators = elevators,
            escalators = escalators
        )
    }

    val INSTANCE: Mall by lazy {
        // --- Floor 0 (Ground) ---
        val floor0Stores = listOf(
            // North-west angled store
            Triple(
                "Eurospar Supermarket",
                "Grocery",
                Path2D().apply {
                    moveTo(100.0, 180.0)
                    lineTo(340.0, 150.0)
                    lineTo(340.0, 480.0)
                    lineTo(100.0, 480.0)
                    closePath()
                }
            ),
            // Central-north trapezoid store
            Triple(
                "Zara",
                "Fashion",
                Path2D().apply {
                    moveTo(360.0, 145.0)
                    lineTo(580.0, 115.0)
                    lineTo(580.0, 360.0)
                    lineTo(360.0, 360.0)
                    closePath()
                }
            ),
            // North-east angled store
            Triple(
                "Sephora",
                "Cosmetics",
                Path2D().apply {
                    moveTo(620.0, 120.0)
                    lineTo(880.0, 330.0)
                    lineTo(740.0, 460.0)
                    lineTo(600.0, 360.0)
                    closePath()
                }
            ),
            // East wing south store
            Triple(
                "Jeff's American Grill",
                "Food Court",
                Path2D().apply {
                    moveTo(600.0, 540.0)
                    lineTo(880.0, 540.0)
                    lineTo(880.0, 850.0)
                    lineTo(600.0, 850.0)
                    closePath()
                }
            ),
            // Courtyard cafe with curved historic rotunda facade using cubic Bézier curve
            Triple(
                "Starbucks Rotunda",
                "Cafe",
                Path2D().apply {
                    moveTo(380.0, 400.0)
                    lineTo(540.0, 400.0)
                    lineTo(540.0, 470.0)
                    // Curved south wall using cubic Bézier curve
                    curveTo(540.0, 515.0, 380.0, 515.0, 380.0, 470.0)
                    closePath()
                }
            )
        )

        val floor0 = createKazimierzFloor(
            floorNumber = 0,
            storesList = floor0Stores,
            escalatorsList = listOf(
                Point(450.0, 320.0) to EscalatorDirection.UP,
                Point(500.0, 320.0) to EscalatorDirection.DOWN
            ),
            elevatorsList = listOf(
                Point(360.0, 480.0),
                Point(840.0, 480.0)
            )
        )

        // --- Floor 1 (+1) ---
        val floor1Stores = listOf(
            Triple(
                "H&M Flagship",
                "Fashion",
                Path2D().apply {
                    moveTo(100.0, 180.0)
                    lineTo(340.0, 150.0)
                    lineTo(340.0, 480.0)
                    lineTo(100.0, 480.0)
                    closePath()
                }
            ),
            Triple(
                "Empik Cultural Hub",
                "Books & Media",
                Path2D().apply {
                    moveTo(360.0, 145.0)
                    lineTo(580.0, 115.0)
                    lineTo(580.0, 360.0)
                    lineTo(360.0, 360.0)
                    closePath()
                }
            ),
            Triple(
                "Reserved",
                "Fashion",
                Path2D().apply {
                    moveTo(620.0, 120.0)
                    lineTo(880.0, 330.0)
                    lineTo(740.0, 460.0)
                    lineTo(600.0, 360.0)
                    closePath()
                }
            ),
            Triple(
                "Calypso Fitness",
                "Fitness",
                Path2D().apply {
                    moveTo(600.0, 540.0)
                    lineTo(880.0, 540.0)
                    lineTo(880.0, 850.0)
                    lineTo(600.0, 850.0)
                    closePath()
                }
            )
        )

        val floor1 = createKazimierzFloor(
            floorNumber = 1,
            storesList = floor1Stores,
            escalatorsList = listOf(
                Point(450.0, 320.0) to EscalatorDirection.UP,
                Point(500.0, 320.0) to EscalatorDirection.DOWN
            ),
            elevatorsList = listOf(
                Point(360.0, 480.0),
                Point(840.0, 480.0)
            )
        )

        // --- Floor 2 (+2) ---
        val floor2Stores = listOf(
            Triple(
                "Cinema City & IMAX (Kazimierz)",
                "Entertainment",
                Path2D().apply {
                    moveTo(100.0, 180.0)
                    lineTo(580.0, 115.0)
                    lineTo(580.0, 480.0)
                    lineTo(100.0, 480.0)
                    closePath()
                }
            ),
            Triple(
                "Food Court Terrace",
                "Food Court",
                Path2D().apply {
                    moveTo(600.0, 340.0)
                    lineTo(880.0, 340.0)
                    lineTo(880.0, 840.0)
                    // Curved panoramic south-east balcony using quadratic Bézier curve
                    quadTo(740.0, 875.0, 600.0, 840.0)
                    closePath()
                }
            )
        )

        val floor2 = createKazimierzFloor(
            floorNumber = 2,
            storesList = floor2Stores,
            escalatorsList = listOf(
                Point(480.0, 320.0) to EscalatorDirection.DOWN
            ),
            elevatorsList = listOf(
                Point(360.0, 480.0),
                Point(840.0, 480.0)
            )
        )

        Mall(
            id = 2L,
            name = "Galeria Kazimierz",
            size = Size(1000, 1000),
            // Located at Podgórska 34, along the Vistula river bend in Krakow
            upperLeft = GeoPoint(longitude = 19.9545, latitude = 50.0550),
            downRight = GeoPoint(longitude = 19.9615, latitude = 50.0505),
            minFloor = 0,
            entryPoints = listOf(
                Point(100.0, 350.0),  // Podgórska Street entrance
                Point(580.0, 880.0),  // Rzeźnicza entrance
                Point(620.0, 100.0)   // Daszyńskiego entrance
            ),
            floors = listOf(floor0, floor1, floor2)
        )
    }
}
