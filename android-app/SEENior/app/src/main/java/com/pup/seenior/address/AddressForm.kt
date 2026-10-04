package com.pup.seenior.address

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The structured address a senior fills in: region -> province -> city -> barangay + a street
 * line, backed by the bundled PSGC dataset. Selecting a level resets the ones below it.
 * Shared by sign-up and Edit Profile. The barangay is only ever chosen from the dataset, since
 * tier 3 of the escalation chain depends on it.
 */
class AddressForm {

    private var locations by mutableStateOf<Map<String, RegionNode>>(emptyMap())

    var region by mutableStateOf<String?>(null)
        private set
    var province by mutableStateOf<String?>(null)
        private set
    var city by mutableStateOf<String?>(null)
        private set
    var barangay by mutableStateOf<String?>(null)
        private set
    var streetAddress by mutableStateOf("")
        private set

    /** True once the senior has touched any part of the address, so Edit Profile leaves an older free-text address alone. */
    var dirty by mutableStateOf(false)
        private set

    suspend fun load(context: Context) {
        locations = PhAddressRepository.load(context)
    }

    private val regionNode: RegionNode?
        get() = locations.values.firstOrNull { it.regionName == region }

    val regionOptions: List<String>
        get() = locations.values.map { it.regionName }.sorted()
    val provinceOptions: List<String>
        get() = regionNode?.provinceList?.keys?.sorted() ?: emptyList()
    val cityOptions: List<String>
        get() = regionNode?.provinceList?.get(province)?.municipalityList?.keys?.sorted() ?: emptyList()
    val barangayOptions: List<String>
        get() = regionNode?.provinceList?.get(province)
            ?.municipalityList?.get(city)?.barangayList?.sorted() ?: emptyList()

    val isComplete: Boolean
        get() = region != null && province != null && city != null &&
            barangay != null && streetAddress.isNotBlank()

    /** The address as stored in `Seniors.address`: "street, barangay, city, province, region". */
    fun joined(): String =
        listOf(streetAddress.trim(), barangay!!, city!!, province!!, region!!).joinToString(", ")

    fun onRegionSelected(name: String) {
        region = name; province = null; city = null; barangay = null; dirty = true
    }

    fun onProvinceSelected(name: String) {
        province = name; city = null; barangay = null; dirty = true
    }

    fun onCitySelected(name: String) {
        city = name; barangay = null; dirty = true
    }

    fun onBarangaySelected(name: String) {
        barangay = name; dirty = true
    }

    fun onStreetChanged(value: String) {
        streetAddress = value; dirty = true
    }

    /**
     * Fills the address from a spot pinned on the map. Set together, without the cascade resets
     * of the per-field setters, since the four values came from the same PSGC entry. The
     * barangay can arrive null (nothing matched); the senior then picks it from the dropdown,
     * already narrowed to the city.
     */
    fun applyPickedAddress(match: PsgcMatch, streetLine: String) {
        region = match.regionName
        province = match.province
        city = match.city
        barangay = match.barangay
        if (streetLine.isNotBlank()) streetAddress = streetLine
        dirty = true
    }

    /**
     * Reads a stored address back into the form for Edit Profile. Only levels in the dataset
     * are kept; an older free-text address lands whole in the street line and the senior is
     * asked to choose. Leaves [dirty] false, since loading isn't an edit.
     */
    fun fillFrom(address: String, storedBarangay: String) {
        val parts = address.split(", ").map { it.trim() }
        var r: String? = null
        var p: String? = null
        var c: String? = null
        var b: String? = null
        var street = address
        if (parts.size >= 5) {
            val regionNode = locations.values.firstOrNull { it.regionName == parts[parts.size - 1] }
            val provinceNode = regionNode?.provinceList?.get(parts[parts.size - 2])
            val cityNode = provinceNode?.municipalityList?.get(parts[parts.size - 3])
            val barangayName = parts[parts.size - 4]
            if (regionNode != null && provinceNode != null && cityNode != null &&
                barangayName in cityNode.barangayList && barangayName == storedBarangay
            ) {
                r = regionNode.regionName
                p = parts[parts.size - 2]
                c = parts[parts.size - 3]
                b = barangayName
                street = parts.subList(0, parts.size - 4).joinToString(", ")
            }
        }
        region = r; province = p; city = c; barangay = b; streetAddress = street
        dirty = false
    }
}
