package features.places.data

import core.geometry.GeoPoint
import features.places.domain.Place
import features.places.domain.PlaceCategory

object KrakowCuratedPlaces {
    val ALL: List<Place> = listOf(
        Place(
            id = "wawel_castle",
            name = "Wawel Royal Castle",
            polishName = "Zamek Królewski na Wawelu",
            description = "Fortified architectural complex on Wawel Hill, the historic seat of Polish kings overlooking the Vistula River.",
            category = PlaceCategory.CASTLE,
            coordinate = GeoPoint.WAWEL_CASTLE,
            yearEstablished = "1364",
            highlights = listOf("Crown Treasury", "Royal Private Apartments", "Dragon's Den (Smocza Jama)", "Vistula View")
        ),
        Place(
            id = "kosciol_mariacki",
            name = "St. Mary's Basilica",
            polishName = "Bazylika Mariacka",
            description = "Brick Gothic church with asymmetric towers adjacent to the Main Market Square, home to Veit Stoss's magnificent wooden altarpiece and the hourly trumpet call (Hejnał).",
            category = PlaceCategory.CHURCH,
            coordinate = GeoPoint.KOSCIOL_MARIACKI,
            yearEstablished = "1320",
            highlights = listOf("Veit Stoss Altarpiece", "Bugle Call (Hejnał Mariacki)", "Gothic Stained Glass", "Starry Vault Ceiling")
        ),
        Place(
            id = "sukiennice",
            name = "Cloth Hall (Sukiennice)",
            polishName = "Sukiennice",
            description = "Renaissance merchant hall in the centre of Rynek Główny, formerly a major centre of international trade and now housing stalls and the 19th-century Polish Art Gallery.",
            category = PlaceCategory.MONUMENT,
            coordinate = GeoPoint.SUKIENNICE,
            yearEstablished = "1257",
            highlights = listOf("Upper Art Gallery", "Underground Museum (Rynek Underground)", "Historic Guild Coats of Arms")
        ),
        Place(
            id = "rynek_glowny",
            name = "Main Market Square",
            polishName = "Rynek Główny",
            description = "The principal urban square of Kraków's Old Town, dating back to 1257, one of the largest medieval town squares in Europe.",
            category = PlaceCategory.SQUARE,
            coordinate = GeoPoint.KRAKOW_RYNEK,
            yearEstablished = "1257",
            highlights = listOf("Town Hall Tower", "St. Adalbert's Church", "Adam Mickiewicz Monument", "Open Cafes")
        ),
        Place(
            id = "kazimierz_plac_nowy",
            name = "Kazimierz (Plac Nowy)",
            polishName = "Kazimierz - Plac Nowy",
            description = "Historic Jewish quarter and bohemian cultural hub famous for Okrąglak rotunda, zapiekanki, historic synagogues, and vibrant street life.",
            category = PlaceCategory.CULTURE,
            coordinate = GeoPoint.KAZIMIERZ_PLAC_NOWY,
            yearEstablished = "1335",
            highlights = listOf("Okrąglak", "Zapiekanki", "Old Synagogue", "Remuh Cemetery", "Artisan Cafes")
        ),
        Place(
            id = "kopiec_kosciuszki",
            name = "Kościuszko Mound",
            polishName = "Kopiec Kościuszki",
            description = "Artificial commemorative mound erected in 1823 in honor of Tadeusz Kościuszko, providing sweeping panoramic views across Kraków and the Tatra mountains on clear days.",
            category = PlaceCategory.MOUND,
            coordinate = GeoPoint.KOPIEC_KOSCIUSZKI,
            yearEstablished = "1823",
            highlights = listOf("Panoramic View", "Austrian Fort 2 'Kościuszko'", "Wax Figures Museum", "Las Wolski Trailhead")
        ),
        Place(
            id = "kopiec_krakusa",
            name = "Krakus Mound",
            polishName = "Kopiec Krakusa",
            description = "Prehistoric monumental tumulus in Podgórze, according to legend the burial site of King Krakus, the mythical founder of Kraków.",
            category = PlaceCategory.MOUND,
            coordinate = GeoPoint(50.0381, 19.9583),
            yearEstablished = "c. 7th century",
            highlights = listOf("Pagan Sun Ritual Alignment", "Podgórze Quarry View", "Historic Solstice Gatherings")
        ),
        Place(
            id = "collegium_maius",
            name = "Collegium Maius",
            polishName = "Collegium Maius UJ",
            description = "The oldest surviving building of the Jagiellonian University (founded 1364), featuring a stunning Gothic arcaded courtyard and historic astronomical instruments.",
            category = PlaceCategory.CULTURE,
            coordinate = GeoPoint(50.0617, 19.9339),
            yearEstablished = "1400",
            highlights = listOf("Copernicus Instruments", "Jagiellonian Globe", "Musical Clock Parade", "Arcaded Courtyard")
        ),
        Place(
            id = "teatr_slowackiego",
            name = "Juliusz Słowacki Theatre",
            polishName = "Teatr im. Juliusza Słowackiego",
            description = "19th-century Eclectic theatre modeled after Charles Garnier's Paris Opera, one of Poland's most celebrated national stages.",
            category = PlaceCategory.CULTURE,
            coordinate = GeoPoint(50.0642, 19.9427),
            yearEstablished = "1893",
            highlights = listOf("Siemiradzki Curtain", "Neoclassical Architecture", "Planty Park Front")
        ),
        Place(
            id = "barbakan_florian",
            name = "Barbican & St. Florian's Gate",
            polishName = "Barbakan i Brama Floriańska",
            description = "Formidable Gothic fortified outpost and gate tower, once the main entrance to the Royal City for visiting monarchs.",
            category = PlaceCategory.MONUMENT,
            coordinate = GeoPoint(50.0656, 19.9416),
            yearEstablished = "1498",
            highlights = listOf("Defense Loophole Slits", "City Wall Walkway", "Painters' Open Gallery")
        ),
        Place(
            id = "blonia_krakowskie",
            name = "Błonia Park",
            polishName = "Błonia Krakowskie",
            description = "Massive 48-hectare historic meadow situated near the city centre, venue for historic gatherings, sports, and recreational leisure.",
            category = PlaceCategory.PARK,
            coordinate = GeoPoint.BLONIA_KRAKOWSKIE,
            yearEstablished = "1366",
            highlights = listOf("48 Hectares Open Meadow", "Cracovia & Wisła Stadia", "Views of Kościuszko Mound")
        ),
        Place(
            id = "krakow_glowny",
            name = "Kraków Główny Railway Station",
            polishName = "Dworzec Kraków Główny",
            description = "Central railway transportation terminal interconnected with Galeria Krakowska and the subterranean fast tram tunnel.",
            category = PlaceCategory.TRANSPORT,
            coordinate = GeoPoint.KRAKOW_GLOWNY,
            yearEstablished = "1847",
            highlights = listOf("Intercity & International Rail", "Kraków Fast Tram (KST)", "Historic Station Facade")
        ),
        Place(
            id = "nowa_huta_centralny",
            name = "Nowa Huta (Plac Centralny)",
            polishName = "Plac Centralny im. R. Reagana",
            description = "Monumental Socialist Realist central square planned in 1949 as an ideal utopian workers' city with grand radial boulevards.",
            category = PlaceCategory.SQUARE,
            coordinate = GeoPoint.NOWA_HUTA_PLAC_CENTRALNY,
            yearEstablished = "1949",
            highlights = listOf("Socialist Realist Architecture", "Avenue of Roses (Aleja Róż)", "Nowa Huta Undergrounds")
        )
    )
}
