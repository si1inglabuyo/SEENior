package com.pup.seenior.location

/**
 * The encoding behind `Alerts.location_cluster_id`.
 *
 * A geohash names a grid cell rather than a point, and the length of the string decides how big
 * that cell is. At [CLUSTER_PRECISION] the cell is about five metres across — **finer than a
 * phone's own GPS error**, so in practice this records where the senior actually was, not a
 * region she was somewhere inside.
 *
 * That is a deliberate reversal of this field's original ~150 m design, and CLAUDE.md §11 carries
 * the reasoning: a responder has to be able to reach a senior who has fallen, and the system
 * already discloses her registered street address to that same responder during an active alert,
 * so a coarse cell was withholding far less than it appeared to while making the alert harder to
 * act on. What protects her is unchanged and is not this encoding — location is read once, only
 * when an alert fires, and is shown only to her own linked family and her own barangay, under
 * RA 10173 §12(c).
 *
 * **Do not describe this value as anonymous or de-identified.** It identifies a place. The format
 * is kept because it is compact, self-describing about its own precision, and decodes to bounds
 * the map can draw — not because it hides anything.
 *
 * Shorter codes written before this change still decode correctly, to the wider cells they always
 * meant. [decode] is length-agnostic on purpose so old alerts keep rendering honestly.
 */
object Geohash {

    /** Base-32 alphabet from the original geohash spec: no "a", "i", "l" or "o". */
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    /**
     * ~5 m x 5 m. Every location id this app writes uses this length.
     *
     * Nine, not ten: at ten the cell is finer than a metre, which no consumer GPS can justify —
     * it would be storing digits that are pure noise and inviting the reader to trust them.
     */
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
     * Encodes one fix into a location id.
     *
     * Out-of-range inputs are clamped rather than rejected: a nonsensical reading from a flaky
     * provider should degrade to a wrong-but-valid cell that the map can show, not throw inside
     * the alert path.
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
     * Decodes a location id back to the cell it names, or null if [hash] is not a geohash.
     *
     * Null is a real case, not defensive padding: `location_cluster_id` is a free-form 64-char
     * column that predates this encoding, so a row written before it — or by any future scheme —
     * must make the map fall back rather than crash it.
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
