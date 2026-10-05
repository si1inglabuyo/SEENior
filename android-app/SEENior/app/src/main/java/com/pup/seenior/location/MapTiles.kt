package com.pup.seenior.location

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * The one tile source every map in the app uses: Esri's World Street Map. The public
 * `tile.openstreetmap.org` servers refuse production apps under their tile usage policy, and
 * CARTO's free basemap now answers every tile with an "API KEY REQUIRED" placeholder image
 * (still HTTP 200, so check the picture, not the status). The attribution must stay visible
 * wherever tiles are shown.
 */
object MapTiles {
    private const val BASE =
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/"

    val Street: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "EsriWorldStreet", 0, 19, 256, "", arrayOf(BASE),
        "Tiles © Esri — Sources: Esri, HERE, Garmin, OpenStreetMap contributors",
    ) {
        // Esri orders the path z/y/x, the reverse of osmdroid's z/x/y default.
        override fun getTileURLString(pMapTileIndex: Long): String =
            BASE + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
    }
}
