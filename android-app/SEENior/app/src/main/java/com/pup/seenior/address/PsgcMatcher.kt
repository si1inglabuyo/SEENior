package com.pup.seenior.address

import android.content.Context
import com.pup.seenior.location.OsmPlace
import java.util.Locale

/**
 * A map point resolved to entries that exist in the bundled PSGC dataset. Every field except
 * [barangay] is a verbatim key from `ph_locations.json`, so it can go straight into the
 * sign-up dropdowns. [barangay] is nullable because it is the one level the map may fail at.
 */
data class PsgcMatch(
    val regionName: String,
    val province: String,
    val city: String,
    val barangay: String?
)

/**
 * Reconciles OpenStreetMap's naming with the PSGC dataset the app stores.
 *
 * This can't be skipped: `Seniors.barangay` scopes a barangay responder's dashboard. If the
 * map wrote OSM's spelling there, the senior's alerts could reach no responder. So the map
 * only supplies a guess, and only values found in the dataset survive.
 *
 * The city is the anchor. On 2026-08-31 a point in Taguig returned `region` = "Metro Manila"
 * and no `state`, while PSGC files that city under region "NCR" and province "TAGUIG -
 * PATEROS". So province and region are derived from the matched municipality, never matched.
 */
object PsgcMatcher {

    /** Resolves [place], or null if even the city couldn't be identified. Null means the senior fills the address in by hand. */
    suspend fun match(context: Context, place: OsmPlace): PsgcMatch? =
        matchIn(PhAddressRepository.load(context), place)

    /** The matching itself, against a dataset passed in, so it can be tested without an Android context. */
    internal fun matchIn(regions: Map<String, RegionNode>, place: OsmPlace): PsgcMatch? {
        if (place.cityNames.isEmpty()) return null

        val wantedCities = place.cityNames.map(::normaliseCity)
        val wantedBarangays = place.barangayNames.map(::normalisePlace)

        val candidates = buildList {
            for (region in regions.values) {
                for ((provinceName, province) in region.provinceList) {
                    for ((cityName, city) in province.municipalityList) {
                        if (normaliseCity(cityName) !in wantedCities) continue
                        add(
                            PsgcMatch(
                                regionName = region.regionName,
                                province = provinceName,
                                city = cityName,
                                barangay = city.barangayList.firstOrNull { barangay ->
                                    normalisePlace(barangay) in wantedBarangays
                                }
                            )
                        )
                    }
                }
            }
        }

        return when {
            candidates.isEmpty() -> null
            candidates.size == 1 -> candidates.single()
            // Municipality names repeat across provinces (several SAN ISIDROs), so the barangay
            // breaks the tie. If it can't, return nothing rather than file the senior under the wrong province.
            else -> candidates.filter { it.barangay != null }.singleOrNull()
        }
    }

    /**
     * Folds away cosmetic differences: case, punctuation and PSGC's parenthetical qualifiers
     * ("ADAMS (POB.)"). Uses [Locale.ROOT] because a Turkish locale would uppercase "i" to "İ".
     */
    private fun normalisePlace(name: String): String = name
        .replace(PARENTHETICAL, " ")
        .uppercase(Locale.ROOT)
        .replace(NON_NAME, " ")
        .replace(REPEATED_SPACE, " ")
        .trim()

    /** The same, plus city wording: PSGC writes "CITY OF LAS PIÑAS" where OSM writes "Las Piñas". */
    private fun normaliseCity(name: String): String = normalisePlace(name)
        .removePrefix("CITY OF ")
        .removeSuffix(" CITY")
        .trim()

    private val PARENTHETICAL = Regex("""\(.*?\)""")

    /** Keeps letters, digits and spaces. Ñ survives because the class is defined by exclusion. */
    private val NON_NAME = Regex("""[^\p{L}\p{N} ]""")

    private val REPEATED_SPACE = Regex(""" {2,}""")
}
