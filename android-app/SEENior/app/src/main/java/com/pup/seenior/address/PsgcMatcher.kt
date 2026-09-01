package com.pup.seenior.address

import android.content.Context
import com.pup.seenior.location.OsmPlace
import java.util.Locale

/**
 * A point on the map resolved to entries that actually exist in the bundled PSGC dataset.
 *
 * Every field except [barangay] is a verbatim key from `ph_locations.json`, so the values can be
 * dropped straight into the sign-up dropdowns and will still be there when the list is rebuilt.
 * [barangay] is nullable because it is the one level the map is allowed to fail at; see
 * [PsgcMatcher].
 */
data class PsgcMatch(
    val regionName: String,
    val province: String,
    val city: String,
    val barangay: String?
)

/**
 * Reconciles OpenStreetMap's naming with the PSGC dataset the app actually stores.
 *
 * **Why this cannot be skipped.** `Seniors.barangay` is what scopes a barangay responder's
 * dashboard (CLAUDE.md §11, role-based access). If the map wrote OSM's spelling into that field,
 * a senior could be onboarded to a barangay string no responder queries for, and her alerts would
 * reach nobody at tier 3 — silently, with every screen looking correct. So the map never supplies
 * an address; it supplies a guess, and only values found in the dataset survive.
 *
 * **Why the city is the anchor.** Checked against live Nominatim on 2026-08-31: a point in Taguig
 * returns `region` = "Metro Manila" and no `state` at all, while PSGC files that same city under
 * region "NCR" and province "TAGUIG - PATEROS" — an administrative grouping no map service will
 * ever return. Province and region are therefore never matched, only *derived* from whichever
 * municipality was found. That inverts the obvious top-down approach for a reason.
 */
object PsgcMatcher {

    /**
     * Resolves [place], or null when even the city could not be identified.
     *
     * A null result is not an error state to hide — it means the senior fills the address in by
     * hand, exactly as they did before this feature existed.
     */
    suspend fun match(context: Context, place: OsmPlace): PsgcMatch? =
        matchIn(PhAddressRepository.load(context), place)

    /**
     * The matching itself, against a dataset passed in rather than loaded.
     *
     * Split out so it can be tested without an Android context. This is the function that decides
     * which barangay a senior is filed under, so it is worth being able to assert on directly.
     */
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
            // Municipality names repeat across provinces — there are several SAN ISIDROs. The
            // barangay breaks the tie, because the same barangay name inside the same city name in
            // two different provinces is vanishingly unlikely. If it cannot, guessing would mean
            // filing a senior under the wrong province, so nothing is returned and she picks.
            else -> candidates.filter { it.barangay != null }.singleOrNull()
        }
    }

    /**
     * Folds away everything the two sources disagree about cosmetically: case, punctuation, and
     * the parenthetical qualifiers PSGC carries ("ADAMS (POB.)", "PARANAS (WRIGHT)").
     *
     * [Locale.ROOT] rather than the device locale — under a Turkish locale "i".uppercase() is "İ",
     * which would stop half the dataset matching on a phone nobody tested.
     */
    private fun normalisePlace(name: String): String = name
        .replace(PARENTHETICAL, " ")
        .uppercase(Locale.ROOT)
        .replace(NON_NAME, " ")
        .replace(REPEATED_SPACE, " ")
        .trim()

    /**
     * The same, plus the "city" wording each source attaches differently — PSGC writes
     * "CITY OF LAS PIÑAS" and "QUEZON CITY" where OSM writes "Las Piñas" and "Quezon City".
     */
    private fun normaliseCity(name: String): String = normalisePlace(name)
        .removePrefix("CITY OF ")
        .removeSuffix(" CITY")
        .trim()

    private val PARENTHETICAL = Regex("""\(.*?\)""")

    /** Keeps letters, digits and spaces. Ñ survives because the class is defined by exclusion. */
    private val NON_NAME = Regex("""[^\p{L}\p{N} ]""")

    private val REPEATED_SPACE = Regex(""" {2,}""")
}
