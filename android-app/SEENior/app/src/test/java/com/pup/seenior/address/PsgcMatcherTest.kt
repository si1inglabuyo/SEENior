package com.pup.seenior.address

import com.pup.seenior.location.OsmPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the map is allowed to put in a senior's address.
 *
 * This is the highest-stakes matching in the app and the stakes are not obvious: `Seniors.barangay`
 * scopes a barangay responder's dashboard, so a plausible-but-wrong value here would file a senior
 * under a barangay nobody queries for. Her sign-up would look perfect and tier 3 of her escalation
 * chain would reach nobody. Every case below is about refusing to guess.
 *
 * The fixture mirrors the real `ph_locations.json` structure, including the shapes that actually
 * bite: a PSGC province ("TAGUIG - PATEROS") no map service returns, "CITY OF"/"CITY" wording that
 * differs between sources, parenthetical barangay qualifiers, and a municipality name that repeats
 * across provinces.
 */
class PsgcMatcherTest {

    private val dataset: Map<String, RegionNode> = mapOf(
        "NCR" to RegionNode(
            regionName = "NCR",
            provinceList = mapOf(
                "TAGUIG - PATEROS" to ProvinceNode(
                    municipalityList = mapOf(
                        "TAGUIG" to MunicipalityNode(
                            barangayList = listOf(
                                "CENTRAL SIGNAL VILLAGE",
                                "NORTH SIGNAL VILLAGE",
                                "SOUTH SIGNAL VILLAGE"
                            )
                        )
                    )
                ),
                "NATIONAL CAPITAL REGION - FOURTH DISTRICT" to ProvinceNode(
                    municipalityList = mapOf(
                        "CITY OF LAS PIÑAS" to MunicipalityNode(barangayList = listOf("TALON DOS"))
                    )
                ),
                "NATIONAL CAPITAL REGION - SECOND DISTRICT" to ProvinceNode(
                    municipalityList = mapOf(
                        "QUEZON CITY" to MunicipalityNode(barangayList = listOf("BAGONG SILANGAN"))
                    )
                )
            )
        ),
        "01" to RegionNode(
            regionName = "REGION I",
            provinceList = mapOf(
                "ILOCOS NORTE" to ProvinceNode(
                    municipalityList = mapOf(
                        "ADAMS" to MunicipalityNode(barangayList = listOf("ADAMS (POB.)")),
                        // Deliberate collision with the Region II entry below.
                        "SAN ISIDRO" to MunicipalityNode(barangayList = listOf("POBLACION NORTE"))
                    )
                )
            )
        ),
        "02" to RegionNode(
            regionName = "REGION II",
            provinceList = mapOf(
                "NUEVA VIZCAYA" to ProvinceNode(
                    municipalityList = mapOf(
                        "SAN ISIDRO" to MunicipalityNode(barangayList = listOf("SALVACION"))
                    )
                )
            )
        )
    )

    private fun place(
        barangays: List<String> = emptyList(),
        cities: List<String> = emptyList(),
        road: String = "",
        houseNumber: String = ""
    ) = OsmPlace(houseNumber, road, barangays, cities)

    @Test
    fun `derives province and region that the map service never returns`() {
        // The exact reply live Nominatim gave for the pilot barangay on 2026-08-31. OSM offered
        // region "Metro Manila" and no province at all; both PSGC values below had to come from
        // the dataset, keyed off the city.
        val match = PsgcMatcher.matchIn(
            dataset,
            place(
                barangays = listOf("South Signal Village", "Signal Village"),
                cities = listOf("Taguig"),
                road = "Colonel Ballecer Street"
            )
        )!!

        assertEquals("NCR", match.regionName)
        assertEquals("TAGUIG - PATEROS", match.province)
        assertEquals("TAGUIG", match.city)
    }

    @Test
    fun `prefers the narrower name when OSM offers an area that is not a barangay`() {
        // "Signal Village" is a real place and not a barangay; "South Signal Village" is the
        // barangay. Order matters here, and getting it backwards produces no match at all.
        val match = PsgcMatcher.matchIn(
            dataset,
            place(
                barangays = listOf("South Signal Village", "Signal Village"),
                cities = listOf("Taguig")
            )
        )!!

        assertEquals("SOUTH SIGNAL VILLAGE", match.barangay)
    }

    @Test
    fun `reconciles the city wording the two sources disagree about`() {
        val lasPinas = PsgcMatcher.matchIn(dataset, place(cities = listOf("Las Piñas")))!!
        assertEquals("CITY OF LAS PIÑAS", lasPinas.city)

        val quezon = PsgcMatcher.matchIn(dataset, place(cities = listOf("Quezon City")))!!
        assertEquals("QUEZON CITY", quezon.city)
    }

    @Test
    fun `matches a barangay through its PSGC parenthetical qualifier`() {
        val match = PsgcMatcher.matchIn(
            dataset,
            place(barangays = listOf("Adams"), cities = listOf("Adams"))
        )!!

        // The stored value keeps the dataset's exact spelling, qualifier included — it has to be
        // selectable in the dropdown that is rebuilt from the same file.
        assertEquals("ADAMS (POB.)", match.barangay)
    }

    @Test
    fun `uses the barangay to break a tie between identically named municipalities`() {
        val match = PsgcMatcher.matchIn(
            dataset,
            place(barangays = listOf("Salvacion"), cities = listOf("San Isidro"))
        )!!

        assertEquals("REGION II", match.regionName)
        assertEquals("NUEVA VIZCAYA", match.province)
    }

    @Test
    fun `refuses to guess between identically named municipalities`() {
        // Two SAN ISIDROs, nothing to separate them. Picking either would file the senior in the
        // wrong province; returning nothing sends her to the dropdowns, which is the safe failure.
        assertNull(PsgcMatcher.matchIn(dataset, place(cities = listOf("San Isidro"))))
    }

    @Test
    fun `leaves the barangay unset rather than inventing one`() {
        val match = PsgcMatcher.matchIn(
            dataset,
            place(barangays = listOf("Somewhere Nobody Mapped"), cities = listOf("Taguig"))
        )!!

        assertEquals("TAGUIG", match.city)
        assertNull(match.barangay)
    }

    @Test
    fun `gives up when the city itself is unknown`() {
        assertNull(PsgcMatcher.matchIn(dataset, place(cities = listOf("Vientiane"))))
        assertNull(PsgcMatcher.matchIn(dataset, place(barangays = listOf("Talon Dos"))))
    }

    @Test
    fun `builds the street line as a person would write it`() {
        assertEquals("12 Colonel Ballecer Street", place(road = "Colonel Ballecer Street", houseNumber = "12").streetLine)
        assertEquals("Colonel Ballecer Street", place(road = "Colonel Ballecer Street").streetLine)
        assertEquals("", place().streetLine)
    }
}
