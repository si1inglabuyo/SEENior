package com.pup.seenior.location

import android.content.Context
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** A plottable point. Deliberately not [android.location.Location] — nothing here is a fix. */
data class LatLon(val latitude: Double, val longitude: Double)

/**
 * What OpenStreetMap says is at a point, before reconciling with PSGC. The two name lists are
 * narrowest first; see [AddressGeocoder.reverse].
 */
data class OsmPlace(
    val houseNumber: String,
    val road: String,
    val barangayNames: List<String>,
    val cityNames: List<String>
) {
    /** The house-number-and-street line, as a senior would write it. */
    val streetLine: String
        get() = listOf(houseNumber, road).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * Turns a senior's registered address into a point, so the alert map has something to show
 * when no cluster was captured. This is the fallback: a cluster says where the phone was when
 * the alert fired, while this says only where the senior lives, which is already stored in
 * the cloud (`Seniors.address`) and used by the "Navigate here" button. The map must still
 * label the two differently (see [com.pup.seenior.ui.family.AlertLocationMap]). Uses
 * OpenStreetMap's Nominatim, matching the osmdroid tiles: free, no API key.
 */
object AddressGeocoder {

    private const val CACHE_NAME = "geocoded_addresses"

    /** Nominatim's policy: at most one request a second and an identifying User-Agent. Both are honoured. */
    private const val MIN_REQUEST_INTERVAL_MS = 1_100L
    private const val USER_AGENT = "SEENior/1.0 (PUP capstone; passive senior monitoring)"

    private val requestGate = Mutex()
    private var lastRequestAt = 0L

    /**
     * Addresses that came back with no match, for this process only. Not persisted, since a
     * failure can be a dead network and a stored "no" would blank the map for good.
     */
    private val unresolvable: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private interface NominatimService {
        @GET("search")
        suspend fun search(
            @Query("q") query: String,
            @Query("format") format: String = "jsonv2",
            @Query("limit") limit: Int = 1
        ): List<NominatimPlace>

        @GET("reverse")
        suspend fun reverse(
            @Query("lat") latitude: Double,
            @Query("lon") longitude: Double,
            @Query("format") format: String = "jsonv2",
            @Query("addressdetails") addressDetails: Int = 1,
            // Street level: finer returns a building name, coarser loses the road.
            @Query("zoom") zoom: Int = 18
        ): NominatimReverse
    }

    private data class NominatimReverse(
        @SerializedName("address") val address: Map<String, String>?
    )

    private data class NominatimPlace(
        @SerializedName("lat") val latitude: String,
        @SerializedName("lon") val longitude: String
    )

    private val service: NominatimService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder().header("User-Agent", USER_AGENT).build()
                )
            }
            .build()

        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimService::class.java)
    }

    /**
     * Resolves [address] to a point, or null. Cached permanently once found, since a
     * registered address doesn't move; this keeps it within Nominatim's fair-use policy.
     */
    suspend fun resolve(context: Context, address: String): LatLon? {
        val key = address.trim()
        if (key.isEmpty() || key in unresolvable) return null

        val prefs = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
        prefs.getString(key, null)?.let { cached ->
            parseCached(cached)?.let { return it }
        }

        val place = requestGate.withLock {
            val sinceLast = System.currentTimeMillis() - lastRequestAt
            if (sinceLast < MIN_REQUEST_INTERVAL_MS) {
                delay(MIN_REQUEST_INTERVAL_MS - sinceLast)
            }
            lastRequestAt = System.currentTimeMillis()
            runCatching { service.search(key) }.getOrNull()?.firstOrNull()
        }

        val point = place?.let {
            val latitude = it.latitude.toDoubleOrNull() ?: return@let null
            val longitude = it.longitude.toDoubleOrNull() ?: return@let null
            LatLon(latitude, longitude)
        }

        if (point == null) {
            unresolvable += key
            return null
        }
        prefs.edit().putString(key, "${point.latitude},${point.longitude}").apply()
        return point
    }

    /**
     * Looks up what is at a point, used by the onboarding map picker and the family alert map.
     * Returns OpenStreetMap's own naming, unresolved against PSGC (that is
     * [com.pup.seenior.address.PsgcMatcher]'s job).
     *
     * Candidates are ordered lists because OSM has no single key per level. In the pilot
     * barangay a point returns `quarter` = "South Signal Village" (a real barangay) and
     * `suburb` = "Signal Village" (not one), so the narrowest naming wins and the order matters.
     */
    suspend fun reverse(latitude: Double, longitude: Double): OsmPlace? {
        val address = requestGate.withLock {
            val sinceLast = System.currentTimeMillis() - lastRequestAt
            if (sinceLast < MIN_REQUEST_INTERVAL_MS) {
                delay(MIN_REQUEST_INTERVAL_MS - sinceLast)
            }
            lastRequestAt = System.currentTimeMillis()
            runCatching { service.reverse(latitude, longitude) }.getOrNull()?.address
        } ?: return null

        fun pick(vararg keys: String) = keys.mapNotNull { address[it]?.trim()?.takeIf(String::isNotEmpty) }

        return OsmPlace(
            houseNumber = address["house_number"]?.trim().orEmpty(),
            road = address["road"]?.trim().orEmpty(),
            barangayNames = pick("quarter", "neighbourhood", "village", "suburb", "hamlet"),
            cityNames = pick("city", "town", "municipality", "city_district", "county")
        )
    }

    private fun parseCached(value: String): LatLon? {
        val parts = value.split(',')
        if (parts.size != 2) return null
        val latitude = parts[0].toDoubleOrNull() ?: return null
        val longitude = parts[1].toDoubleOrNull() ?: return null
        return LatLon(latitude, longitude)
    }
}
