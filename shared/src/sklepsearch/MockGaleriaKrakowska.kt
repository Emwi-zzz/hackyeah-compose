package sklepsearch

object MockGaleriaKrakowska {

    private fun createFloor(
        floorNumber: Int,
        storeNamesLeft: List<Pair<String, String>>,
        storeNamesRight: List<Pair<String, String>>,
        escalatorConfigs: List<Pair<Point, EscalatorDirection>>
    ): Floor {
        val floorBox = Path2D.rectangle(60.0, 60.0, 880.0, 880.0)
        val stores = mutableListOf<Store>()

        var instanceIdCounter = (floorNumber + 2) * 1000L

        // Left wing stores (x: 80 to 420, split along y)
        val leftCount = storeNamesLeft.size
        val leftSlotHeight = 840.0 / leftCount
        for (i in 0 until leftCount) {
            val (name, category) = storeNamesLeft[i]
            val y = 80.0 + i * leftSlotHeight
            val h = leftSlotHeight - 12.0
            val area = Path2D.rectangle(80.0, y, 340.0, h)
            val entry = Point(420.0, y + h / 2.0)
            stores.add(
                Store(
                    Instanceid = instanceIdCounter++,
                    Shopid = 100L + stores.size,
                    name = name,
                    area = area,
                    entryPoints = listOf(entry),
                    category = category
                )
            )
        }

        // Right wing stores (x: 580 to 920, split along y)
        val rightCount = storeNamesRight.size
        val rightSlotHeight = 840.0 / rightCount
        for (i in 0 until rightCount) {
            val (name, category) = storeNamesRight[i]
            val y = 80.0 + i * rightSlotHeight
            val h = rightSlotHeight - 12.0
            val area = Path2D.rectangle(580.0, y, 340.0, h)
            val entry = Point(580.0, y + h / 2.0)
            stores.add(
                Store(
                    Instanceid = instanceIdCounter++,
                    Shopid = 200L + stores.size,
                    name = name,
                    area = area,
                    entryPoints = listOf(entry),
                    category = category
                )
            )
        }

        // Elevators in north and south lobbies
        val elevators = listOf(
            Elevator(id = 1000L + floorNumber * 10 + 1, coordinates = Point(500.0, 180.0)),
            Elevator(id = 1000L + floorNumber * 10 + 2, coordinates = Point(500.0, 820.0))
        )

        // Escalators in central atrium
        val escalators = escalatorConfigs.mapIndexed { index, (pt, dir) ->
            Escalator(
                id = 2000L + floorNumber * 10 + index,
                coordinates = pt,
                direction = dir
            )
        }

        return Floor(
            number = floorNumber,
            box = floorBox,
            stores = stores,
            elevators = elevators,
            escalators = escalators
        )
    }

    val INSTANCE: Mall by lazy {
        val floorMinus1 = createFloor(
            floorNumber = -1,
            storeNamesLeft = listOf(
                "Carrefour Market" to "Grocery",
                "Rossmann" to "Pharmacy",
                "Piekarnia Pawlak" to "Bakery",
                "Kantor Exchange" to "Services"
            ),
            storeNamesRight = listOf(
                "InMedio Relay" to "Press",
                "Pralnia EBS" to "Services",
                "Cukiernia Sowa" to "Cafe",
                "Kraków Główny Tunnel Exit" to "Transport"
            ),
            escalatorConfigs = listOf(
                Point(480.0, 470.0) to EscalatorDirection.UP,
                Point(520.0, 530.0) to EscalatorDirection.UP
            )
        )

        val floor0 = createFloor(
            floorNumber = 0,
            storeNamesLeft = listOf(
                "Zara" to "Fashion",
                "H&M" to "Fashion",
                "Sephora" to "Cosmetics",
                "Starbucks Coffee" to "Cafe",
                "Apple iSpot" to "Electronics"
            ),
            storeNamesRight = listOf(
                "Douglas" to "Cosmetics",
                "Apart Jewellery" to "Jewelry",
                "Massimo Dutti" to "Fashion",
                "Costa Coffee" to "Cafe",
                "Swarovski" to "Jewelry"
            ),
            escalatorConfigs = listOf(
                Point(475.0, 450.0) to EscalatorDirection.UP,
                Point(525.0, 450.0) to EscalatorDirection.DOWN,
                Point(475.0, 550.0) to EscalatorDirection.UP,
                Point(525.0, 550.0) to EscalatorDirection.DOWN
            )
        )

        val floor1 = createFloor(
            floorNumber = 1,
            storeNamesLeft = listOf(
                "Media Markt" to "Electronics",
                "Empik" to "Books & Media",
                "Reserved" to "Fashion",
                "Bershka" to "Fashion"
            ),
            storeNamesRight = listOf(
                "CCC Shoes" to "Footwear",
                "Deichmann" to "Footwear",
                "Pull & Bear" to "Fashion",
                "Stradivarius" to "Fashion"
            ),
            escalatorConfigs = listOf(
                Point(475.0, 450.0) to EscalatorDirection.UP,
                Point(525.0, 450.0) to EscalatorDirection.DOWN,
                Point(475.0, 550.0) to EscalatorDirection.UP,
                Point(525.0, 550.0) to EscalatorDirection.DOWN
            )
        )

        val floor2 = createFloor(
            floorNumber = 2,
            storeNamesLeft = listOf(
                "McDonald's" to "Food Court",
                "KFC" to "Food Court",
                "Burger King" to "Food Court",
                "Pizza Hut Express" to "Food Court"
            ),
            storeNamesRight = listOf(
                "North Fish" to "Food Court",
                "Subway" to "Food Court",
                "Cinema Lounge" to "Entertainment",
                "FitFabric Gym" to "Fitness"
            ),
            escalatorConfigs = listOf(
                Point(480.0, 470.0) to EscalatorDirection.DOWN,
                Point(520.0, 530.0) to EscalatorDirection.DOWN
            )
        )

        Mall(
            id = 1L,
            name = "Galeria Krakowska",
            size = Size(1000, 1000),
            // Georeferenced footprint directly adjacent to Kraków Główny Station
            upperLeft = GeoPoint(longitude = 19.9460, latitude = 50.0682),
            downRight = GeoPoint(longitude = 19.9505, latitude = 50.0645),
            minFloor = -1,
            entryPoints = listOf(
                Point(500.0, 60.0),   // North Entrance (Plac Jana Nowaka-Jeziorańskiego)
                Point(60.0, 500.0),   // West Entrance (Pawia Street)
                Point(940.0, 500.0),  // East Entrance (Dworzec Główny PKP Platforms)
                Point(500.0, 940.0)   // South Entrance (Lubicz / Tunnel)
            ),
            floors = listOf(floorMinus1, floor0, floor1, floor2)
        )
    }
}
