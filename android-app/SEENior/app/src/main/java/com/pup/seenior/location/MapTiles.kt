package com.pup.seenior.location

import org.osmdroid.tileprovider.tilesource.XYTileSource

/**
 * The one tile source every map in the app uses. CARTO's Voyager basemap, drawn from
 * OpenStreetMap data. The public `tile.openstreetmap.org` servers are volunteer-run, refuse
 * production apps under their tile usage policy, and blocked this app's requests.
 * The attribution must stay visible wherever tiles are shown.
 */
object MapTiles {
    val Carto = XYTileSource(
        "CartoVoyager", 0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
        ),
        "© OpenStreetMap contributors © CARTO",
    )
}
