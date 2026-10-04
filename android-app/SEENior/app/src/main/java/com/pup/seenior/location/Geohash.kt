package com.pup.seenior.location

/**
 * The encoding behind `Alerts.location_cluster_id`.
 *
 * A geohash names a grid cell, and the string length sets its size. At [CLUSTER_PRECISION]
 * the cell is about five metres, finer than a phone's GPS error, so it records where the
 * senior actually was. This reverses the field's original ~150 m design (see the spec,
 * section 11): a responder has to be able to reach a fallen senior, and the registered street
 * address is already disclosed to them during an active alert. What protects the senior is
 * unchanged: location is read once, only when an alert fires, and shown only to their linked
 * family and their barangay, under RA 10173 section 12(c).
 *
 * Do not describe this value as anonymous or de-identified; it identifies a place. The format
 * is kept because it is compact, shows its own precision and decodes to bounds the map can
 * draw. Shorter codes from before still decode, to the wider cells they always meant.
 */
object Geohash {

    /** Base-32 alphabet from the original geohash spec: no "a", "i", "l" or "o". */
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    /** ~5 m x 5 m. Every location id this app writes uses this length. Ten would be finer than a metre, which is GPS noise. */
    const val CLUSTER_PRECISION = 9

    private const val MAX_PRECISION = 12

    /** A decoded cell: the bounds the original fix was somewhere inside. */
    data class Cell(
        val southLatitude: Double,
        val westLongitude: Double,
        val northLatitude: Double,
        val eastLongitude: Double
    ) {
        val centerLatitude: Double get() = (southLatitude + northLatitude) / 2.0
        val centerLongitude: Double get() = (westLongitude + eastLongitude) / 2.0
    }

    /**
     * Encodes one fix into a location id. Out-of-range inputs are clamped, so a bad reading
     * gives a wrong-but-valid cell instead of throwing in the alert path.
     */
    fun encode(
        latitude: Double,
        longitude: Double,
        precision: Int = CLUSTER_PRECISION
    ): String {
        require(precision in 1..MAX_PRECISION) { "precision must be 1..$MAX_PRECISION" }

        val lat = latitude.coerceIn(-90.0, 90.0)
        val lon = longitude.coerceIn(-180.0, 180.0)

        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0

        val hash = StringBuilder(precision)
        var bitsInChar = 0
        var charValue = 0
        // Geohash interleaves the two axes starting with longitude.
        var longitudeTurn = true

        while (hash.length < precision) {
            if (longitudeTurn) {
                val mid = (lonMin + lonMax) / 2.0
                if (lon >= mid) {
                    charValue = charValue * 2 + 1
                    lonMin = mid
                } else {
                    charValue *= 2
                    lonMax = mid
                }
            } else {
                val mid = (latMin + latMax) / 2.0
                if (lat >= mid) {
                    charValue = charValue * 2 + 1
                    latMin = mid
                } else {
                    charValue *= 2
                    latMax = mid
                }
            }
            longitudeTurn = !longitudeTurn

            if (bitsInChar < 4) {
                bitsInChar++
            } else {
                hash.append(BASE32[charValue])
                bitsInChar = 0
                charValue = 0
            }
        }
        return hash.toString()
    }

    /**
     * Decodes a location id back to its cell, or null if [hash] isn't a geohash. Null is a
     * real case: `location_cluster_id` is a free-form column that predates this encoding.
     */
    fun decode(hash: String): Cell? {
        if (hash.isEmpty() || hash.length > MAX_PRECISION) return null

        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0
        var longitudeTurn = true

        for (char in hash) {
            val value = BASE32.indexOf(char.lowercaseChar())
            if (value < 0) return null

            // Most significant of the five bits first, matching the order encode() packed them.
            for (bitIndex in 4 downTo 0) {
                val bitSet = (value shr bitIndex) and 1 == 1
                if (longitudeTurn) {
                    val mid = (lonMin + lonMax) / 2.0
                    if (bitSet) lonMin = mid else lonMax = mid
                } else {
                    val mid = (latMin + latMax) / 2.0
                    if (bitSet) latMin = mid else latMax = mid
                }
                longitudeTurn = !longitudeTurn
            }
        }
        return Cell(latMin, lonMin, latMax, lonMax)
    }
}
