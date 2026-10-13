package com.pup.seenior.address

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.dto.PookDto
import kotlinx.coroutines.CancellationException

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

    /**
     * The pook (area of the barangay) the senior picked, for barangays that have them. Optional
     * and a label only, so it never affects [isComplete] or [dirty]. Cleared whenever the
     * address above it changes, since a pook belongs to one barangay.
     */
    var pook by mutableStateOf<PookDto?>(null)
        private set
    var pookOptions by mutableStateOf<List<PookDto>>(emptyList())
        private set

    /** True when the chosen barangay is one the server lists pooks for (U.P. Campus). */
    val showsPook: Boolean
        get() = barangay?.filter { it.isLetter() }.equals("UPCampus", ignoreCase = true)

    /** Fetches the pooks for the chosen barangay. No connection leaves the list empty; the field is optional. */
    suspend fun loadPooks() {
        val name = barangay
        if (name == null || !showsPook) {
            pookOptions = emptyList()
            return
        }
        pookOptions = try {
            RetrofitClient.api.pooks(name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun onPookSelected(name: String) {
        pook = pookOptions.firstOrNull { it.name == name }
    }

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
        region = name; province = null; city = null; barangay = null; pook = null; dirty = true
    }

    fun onProvinceSelected(name: String) {
        province = name; city = null; barangay = null; pook = null; dirty = true
    }

    fun onCitySelected(name: String) {
        city = name; barangay = null; pook = null; dirty = true
    }

    fun onBarangaySelected(name: String) {
        barangay = name; pook = null; dirty = true
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
        pook = null
        if (streetLine.isNotBlank()) streetAddress = streetLine
        dirty = true
    }

    /**
     * Reads a stored address back into the form for Edit Profile. Only levels in the dataset
     * are kept; an older free-text address lands whole in the street line and the senior is
     * asked to choose. Leaves [dirty] false, since loading isn't an edit.
     */
    fun fillFrom(address: String, storedBarangay: String, storedPookId: Int? = null, storedPookName: String? = null) {
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
        // Kept only if the address still reads back to the barangay the pook was chosen in.
        pook = if (b != null && storedPookId != null && storedPookName != null) {
            PookDto(id = storedPookId, barangay = b, name = storedPookName)
        } else null
        dirty = false
    }
}
