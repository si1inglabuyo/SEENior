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
 * What OpenStreetMap says is at a point, before any attempt to reconcile it with PSGC.
 *
 * The two name lists are in narrowest-first order; see [AddressGeocoder.reverse].
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
 * Turns a senior's registered address into a point, so the alert map has something to show when
 * no cluster was captured.
 *
 * This is the fallback tier, not the primary one, and the difference matters to what the map is
 * allowed to claim. A cluster says *where the phone was when the alert fired*; this says only
 * *where the senior lives*, which is a fact already stored in plain text in the cloud
 * (`Seniors.address`) and already used by the existing "Navigate here" button. Geocoding it adds
 * no disclosure — it re-expresses something the family can already read on screen. The map must
 * label the two differently all the same; see [com.pup.seenior.ui.family.AlertLocationMap].
 *
 * Uses OpenStreetMap's Nominatim service, matching the osmdroid tile choice: free, no API key, no
 * second Google Cloud billing dependency.
 */
object AddressGeocoder {

    private const val CACHE_NAME = "geocoded_addresses"

    /**
     * Nominatim's usage policy caps callers at one request a second and requires an identifying
     * User-Agent. Both are honoured below rather than treated as advisory — the alternative is
     * having the project's traffic blocked during a panel demo.
     */
    private const val MIN_REQUEST_INTERVAL_MS = 1_100L
    private const val USER_AGENT = "SEENior/1.0 (PUP capstone; passive senior monitoring)"

    private val requestGate = Mutex()
    private var lastRequestAt = 0L

    /**
     * Addresses that came back with no match, for this process only.
     *
     * Not written to the cache: a failure can be a dead network as easily as a bad address, and a
     * persisted "no" would make one flight-mode moment permanently blank the map for that senior.
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
            // Street level. Finer than this returns the building, whose name is not part of an
            // address a senior would recognise; coarser loses the road.
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
     * Resolves [address] to a point, or null if it cannot be resolved.
     *
     * Cached permanently once found: a registered address does not move, and the family app opens
     * the same senior's alert screen repeatedly. The cache is what keeps this inside Nominatim's
     * fair-use policy in normal running — the network is touched roughly once per senior, ever.
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
     * Looks up what is at a point, for the map-drag address picker.
     *
     * Returns OpenStreetMap's own naming, deliberately unresolved against the PSGC dataset —
     * [com.pup.seenior.address.PsgcMatcher] does that, and keeping the two apart means the
     * matching rules can be reasoned about and corrected without touching the network layer.
     *
     * Candidates are returned as ordered lists rather than single fields because OSM has no one
     * key for either level. Checked against the pilot barangay on 2026-08-31, a point in Central
     * Signal Village comes back with `quarter` = "South Signal Village" (a real barangay) *and*
     * `suburb` = "Signal Village" (an informal area that is not one) — so the order below is
     * load-bearing, not a guess: the narrowest naming wins.
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
