package com.pup.seenior.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The codec behind `Alerts.location_cluster_id`. Since 2026-08-31 the cell must be fine
 * enough to act on (a responder has to reach a fallen senior), so these assert that, and
 * that codes written under the old ~150 m setting still decode to what they meant.
 */
class GeohashTest {

    /** Central Signal Village, Taguig — the pilot barangay. */
    private val PILOT_LATITUDE = 14.5243
    private val PILOT_LONGITUDE = 121.0546

    @Test
    fun `encodes a known point to the published geohash`() {
        // Cross-checked against the reference implementation, so a bit-interleaving bug can't agree with itself.
        assertEquals("ezs42", Geohash.encode(42.6, -5.6, precision = 5))
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, precision = 11))
    }

    @Test
    fun `cluster precision yields a cell finer than a phone's own GPS error`() {
        val cell = Geohash.decode(Geohash.encode(PILOT_LATITUDE, PILOT_LONGITUDE))!!

        val heightMetres = (cell.northLatitude - cell.southLatitude) * 111_320.0
        // Longitude degrees shorten with latitude; at ~14.5°N the factor is cos(14.5°) ≈ 0.968.
        val widthMetres = (cell.eastLongitude - cell.westLongitude) * 111_320.0 * 0.968

        // A handset fix is good to ~5-10 m outdoors, so a cell at or under that adds no error.
        assertTrue("cell was ${heightMetres}m tall", heightMetres in 1.0..10.0)
        assertTrue("cell was ${widthMetres}m wide", widthMetres in 1.0..10.0)
    }

    @Test
    fun `decoded cell contains the point it was encoded from`() {
        val cell = Geohash.decode(Geohash.encode(PILOT_LATITUDE, PILOT_LONGITUDE))!!

        assertTrue(PILOT_LATITUDE in cell.southLatitude..cell.northLatitude)
        assertTrue(PILOT_LONGITUDE in cell.westLongitude..cell.eastLongitude)
    }

    @Test
    fun `cell centre lands within a few metres of the true point`() {
        val cell = Geohash.decode(Geohash.encode(PILOT_LATITUDE, PILOT_LONGITUDE))!!

        val offsetMetres = abs(cell.centerLatitude - PILOT_LATITUDE) * 111_320.0
        assertTrue("centre was ${offsetMetres}m off", offsetMetres < 5.0)
    }

    @Test
    fun `tells a house apart from its neighbour`() {
        // Two points ~15 m apart. Under the old ~150 m setting these encoded identically; this
        // test catches a silent revert (see the spec, section 11).
        val house = Geohash.encode(PILOT_LATITUDE, PILOT_LONGITUDE)
        val neighbour = Geohash.encode(PILOT_LATITUDE + 0.00013, PILOT_LONGITUDE)

        assertNotEquals(house, neighbour)
    }

    @Test
    fun `still decodes the wider cells written before locations were kept precisely`() {
        // A real value from the pilot handset (2026-08-31, ~150 m setting). Such rows remain in
        // the database and must keep drawing as areas, not false pinpoints.
        val legacy = Geohash.decode("wdw4d9w")!!

        val heightMetres = (legacy.northLatitude - legacy.southLatitude) * 111_320.0
        assertTrue("legacy cell was ${heightMetres}m tall", heightMetres in 100.0..200.0)
        assertTrue(legacy.centerLatitude in 14.0..15.0)
        assertTrue(legacy.centerLongitude in 120.0..122.0)
    }

    @Test
    fun `separates points a few hundred metres apart`() {
        val here = Geohash.encode(PILOT_LATITUDE, PILOT_LONGITUDE)
        val elsewhere = Geohash.encode(PILOT_LATITUDE + 0.005, PILOT_LONGITUDE + 0.005)

        assertNotEquals(here, elsewhere)
    }

    @Test
    fun `always produces the agreed length`() {
        assertEquals(Geohash.CLUSTER_PRECISION, Geohash.encode(PILOT_LATITUDE, PILOT_LONGITUDE).length)
        assertEquals(Geohash.CLUSTER_PRECISION, Geohash.encode(0.0, 0.0).length)
        assertEquals(Geohash.CLUSTER_PRECISION, Geohash.encode(-89.9, 179.9).length)
    }

    @Test
    fun `clamps an out-of-range reading instead of throwing inside the alert path`() {
        // A flaky provider must not be able to crash the capture that a real emergency depends on.
        assertEquals(Geohash.encode(90.0, 180.0), Geohash.encode(120.0, 400.0))
    }

    @Test
    fun `refuses a cluster id that is not a geohash`() {
        // location_cluster_id predates this encoding and is free-form, so the map must survive other values.
        assertNull(Geohash.decode(""))
        assertNull(Geohash.decode("has spaces"))
        // "a", "i", "l" and "o" are excluded from the geohash alphabet.
        assertNull(Geohash.decode("ailoail"))
        assertNull(Geohash.decode("0123456789bcdef"))
    }

    @Test
    fun `decoding is case insensitive`() {
        val hash = Geohash.encode(PILOT_LATITUDE, PILOT_LONGITUDE)

        assertEquals(Geohash.decode(hash), Geohash.decode(hash.uppercase()))
    }
}
