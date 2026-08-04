package com.pup.seenior.address

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

data class RegionNode(
    @SerializedName("region_name") val regionName: String,
    @SerializedName("province_list") val provinceList: Map<String, ProvinceNode>
)

data class ProvinceNode(
    @SerializedName("municipality_list") val municipalityList: Map<String, MunicipalityNode>
)

data class MunicipalityNode(
    @SerializedName("barangay_list") val barangayList: List<String>
)

/**
 * Loads the bundled PSGC dataset (assets/ph_locations.json) once and keeps it cached
 * for the process lifetime. ~2 MB JSON, parsed off the main thread.
 */
object PhAddressRepository {

    @Volatile
    private var cache: Map<String, RegionNode>? = null

    suspend fun load(context: Context): Map<String, RegionNode> =
        cache ?: withContext(Dispatchers.IO) {
            val parsed: Map<String, RegionNode> =
                context.assets.open("ph_locations.json").use { stream ->
                    val type = object : TypeToken<Map<String, RegionNode>>() {}.type
                    Gson().fromJson(InputStreamReader(stream, Charsets.UTF_8), type)
                }
            cache = parsed
            parsed
        }
}
