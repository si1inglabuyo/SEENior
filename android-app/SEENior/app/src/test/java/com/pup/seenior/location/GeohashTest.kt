package com.pup.seenior.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The codec behind `Alerts.location_cluster_id`.
 *
 * The property under test changed on 2026-08-31. It used to be that the cell had to stay coarse
 * enough not to identify a house; CLAUDE.md §11 now says the opposite, because a barangay
 * responder has to be able to reach a senior who has fallen and the system already hands that
 * responder her street address. So these assert that a location is resolved finely enough to act
 * on — and, still, that codes written under the old setting keep decoding to what they meant.
 */
class GeohashTest {

    /** Central Signal Village, Taguig — the pilot barangay. */
    private val PILOT_LATITUDE = 14.5243
    private val PILOT_LONGITUDE = 121.0546

    @Test
    fun `encodes a known point to the published geohash`() {
        // Cross-checked against the reference implementation rather than against this one, so a
        // bug in the bit interleaving cannot agree with itself and pass.
        assertEquals("ezs42", Geohash.encode(42.6, -5.6, precision = 5))
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, precision = 11))
    }

    @Test
    fun `cluster precision yields a cell finer than a phone's own GPS error`() {
        val cell = Geohash.decode(Geohash.encode(PILOT_LATITUDE, PILOT_LONGITUDE))!!

        val heightMetres = (cell.northLatitude - cell.southLatitude) * 111_320.0
        // Longitude degrees shorten with latitude; at ~14.5°N the factor is cos(14.5°) ≈ 0.968.
        val widthMetres = (cell.eastLongitude - cell.westLongitude) * 111_320.0 * 0.968

        // A handset fix is good to roughly 5-10 m outdoors, so a cell at or under that adds no
        // error of its own — the stored value is as good as what the sensor gave us.
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
        // Two points ~15 m apart. Under the old ~150 m setting these encoded identically, which
        // is what made an alert hard to act on: a responder was handed a block, not a door. The
        // reversal is deliberate and documented in CLAUDE.md §11 — this test is what would catch
        // a silent revert of it.
        val house = Geohash.encode(PILOT_LATITUDE, PILOT_LONGITUDE)
        val neighbour = Geohash.encode(PILOT_LATITUDE + 0.00013, PILOT_LONGITUDE)

        assertNotEquals(house, neighbour)
    }

    @Test
    fun `still decodes the wider cells written before locations were kept precisely`() {
        // A real value captured on the pilot handset on 2026-08-31, under the ~150 m setting.
        // Rows like this are still in the database, and the family's map must keep drawing them
        // as the areas they always meant rather than as false pinpoints.
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
        // location_cluster_id predates this encoding and is a free-form 64-char column, so the
        // map has to survive a row holding something else entirely.
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
