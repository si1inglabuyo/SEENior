package com.pup.seenior.ui.family

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pup.seenior.location.AddressGeocoder
import com.pup.seenior.location.Geohash
import com.pup.seenior.location.LatLon
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What the map is showing, and — just as importantly — what it is entitled to claim.
 *
 * The two are deliberately different things. A [Cluster] is where the phone actually was when the
 * alert fired. [RegisteredAddress] is merely where the senior lives, and is used when no fix was
 * captured. Collapsing them into one "location" would let the screen imply the system knew where
 * someone was on an occasion when it did not.
 *
 * How precisely a [Cluster] is known is read from the cell itself rather than assumed, because
 * both kinds are in the data: alerts raised before 2026-08-31 carry ~150 m cells and newer ones
 * carry ~5 m. The screen draws and captions whichever it is actually holding.
 */
private sealed interface MapTarget {
    data class Cluster(val cell: Geohash.Cell) : MapTarget
    data class RegisteredAddress(val point: LatLon) : MapTarget
}

/**
 * The alert map (CLAUDE.md §11 — cluster, never coordinates).
 *
 * Prefers the alert's own cluster and falls back to the senior's registered address, captioning
 * whichever it used. When neither resolves it shows [MapPlaceholder], which is why that composable
 * still exists: "we do not know" stays a state the screen can be in.
 *
 * @param interactive whether the map takes touch gestures. False on a preview embedded in a
 *   scrolling card — a map that swallows drags there traps the page instead of scrolling it.
 */
@Composable
fun AlertLocationMap(
    clusterId: String?,
    registeredAddress: String,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    interactive: Boolean = false
) {
    val context = LocalContext.current

    val cell = remember(clusterId) { clusterId?.let(Geohash::decode) }
    var fallback by remember(registeredAddress) { mutableStateOf<LatLon?>(null) }
    var resolving by remember(clusterId, registeredAddress) { mutableStateOf(cell == null) }

    // Only geocoded when there is no cluster to draw. An alert that carried a fix never touches
    // the network for this, so the common case costs nothing.
    LaunchedEffect(clusterId, registeredAddress) {
        if (cell != null) {
            resolving = false
            return@LaunchedEffect
        }
        resolving = true
        fallback = AddressGeocoder.resolve(context, registeredAddress)
        resolving = false
    }

    val target: MapTarget? = when {
        cell != null -> MapTarget.Cluster(cell)
        fallback != null -> MapTarget.RegisteredAddress(fallback!!)
        else -> null
    }

    Column(modifier) {
        when {
            target != null -> MapSurface(target, height, interactive)

            resolving -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(FamilyColors.FieldBackground, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = FamilyColors.Blue,
                    strokeWidth = 2.dp
                )
            }

            else -> MapPlaceholder(Modifier.height(height))
        }

        target?.let {
            Text(
                text = when (it) {
                    is MapTarget.Cluster -> {
                        val span = it.cell.approximateSpanMetres()
                        if (span <= PIN_THRESHOLD_METRES) {
                            "Where the phone was when the alert was raised. Captured once, at " +
                                "that moment only — SEENior does not track location at any " +
                                "other time."
                        } else {
                            "Approximate area when the alert was raised — about " +
                                "${span.roundToInt()} m across."
                        }
                    }
                    is MapTarget.RegisteredAddress ->
                        "No location was captured for this alert. Showing " +
                            "$registeredAddress, the senior's registered address."
                },
                color = FamilyColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun MapSurface(target: MapTarget, height: Dp, interactive: Boolean) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp)),
        factory = { viewContext ->
            OsmdroidSetup.ensure(viewContext)
            val map = if (interactive) MapView(viewContext) else StaticMapView(viewContext)
            map.apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(interactive)
                // The design has no zoom chrome; pinch covers it on the screen that needs it.
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                onResume()
            }
        },
        update = { map -> map.render(target) },
        // Stops the tile threads when the screen goes away. Nothing else holds this view, so
        // without it every visit to an alert leaves a downloader running for the process's life.
        onRelease = { map ->
            map.onPause()
            map.onDetach()
        }
    )
}

/**
 * Draws [target] onto the map, replacing whatever was there.
 *
 * Overlays are cleared first because [AndroidView] reuses the same [MapView] across recompositions
 * — without this, an alert screen revisited with a different alert stacks the old cell under the
 * new one.
 */
private fun MapView.render(target: MapTarget) {
    // AndroidView runs update() on every recomposition, and the alerts screen recomposes every
    // 20 seconds on its poll. Re-framing each time would drag the map back to centre under the
    // finger of anyone panning it, so the camera is set once per target and then left alone.
    // The overlays are still redrawn -- they are cheap, and they must follow a changed target.
    val alreadyFramed = tag == target
    overlays.clear()

    when (target) {
        is MapTarget.Cluster -> {
            val cell = target.cell
            val centre = GeoPoint(cell.centerLatitude, cell.centerLongitude)

            // Draw what is actually known. A cell finer than GPS error is a position, and a pin
            // says so; a 150 m cell from an older alert is a region, and a square says that. Using
            // one shape for both would either overstate the old alerts or understate the new ones.
            if (cell.approximateSpanMetres() <= PIN_THRESHOLD_METRES) {
                overlays.add(
                    Marker(this).apply {
                        position = centre
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                )
            } else {
                overlays.add(
                    Polygon(this).apply {
                        points = listOf(
                            GeoPoint(cell.southLatitude, cell.westLongitude),
                            GeoPoint(cell.northLatitude, cell.westLongitude),
                            GeoPoint(cell.northLatitude, cell.eastLongitude),
                            GeoPoint(cell.southLatitude, cell.eastLongitude)
                        )
                        // The Paint accessors, not the setFillColor/setStrokeColor shorthands —
                        // osmdroid deprecated those in 6.1.
                        fillPaint.color = AndroidColor.argb(56, 217, 83, 79)
                        outlinePaint.color = AndroidColor.rgb(217, 83, 79)
                        outlinePaint.strokeWidth = 3f
                    }
                )
            }
            if (!alreadyFramed) {
                controller.setZoom(
                    if (cell.approximateSpanMetres() <= PIN_THRESHOLD_METRES) POSITION_ZOOM
                    else AREA_ZOOM
                )
                controller.setCenter(centre)
            }
        }

        is MapTarget.RegisteredAddress -> {
            val point = GeoPoint(target.point.latitude, target.point.longitude)
            overlays.add(
                Marker(this).apply {
                    position = point
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
            )
            if (!alreadyFramed) {
                controller.setZoom(ADDRESS_ZOOM)
                controller.setCenter(point)
            }
        }
    }
    tag = target
    invalidate()
}

/**
 * The widest cell still drawn as a point rather than an area.
 *
 * Set above a phone GPS's own error (~5-10 m in the open) and well below the ~150 m cells older
 * alerts carry, so each is drawn as what it is.
 */
private const val PIN_THRESHOLD_METRES = 30.0

/** Street level, for a cell that names a position. */
private const val POSITION_ZOOM = 18.5

/** Roughly frames a 150 m cell from an alert raised before locations were kept precisely. */
private const val AREA_ZOOM = 17.0

/**
 * The longer side of a cell in metres.
 *
 * Longitude degrees shorten towards the poles, so the east-west side is scaled by the cosine of
 * the latitude; without it a cell would read as far wider than it is.
 */
private fun Geohash.Cell.approximateSpanMetres(): Double = max(
    (northLatitude - southLatitude) * 111_320.0,
    (eastLongitude - westLongitude) * 111_320.0 * cos(Math.toRadians(centerLatitude))
)

/** A little wider: a geocoded address lands on the street, so the surroundings help place it. */
private const val ADDRESS_ZOOM = 16.5

/**
 * A map that declines every touch, so the scrolling card it sits in keeps its gestures.
 *
 * Returning false from [dispatchTouchEvent] — rather than disabling the view — is what lets the
 * drag reach the Compose scroll container above it. [MapView] otherwise consumes the gesture and
 * pans, leaving a senior's family stuck at the bottom of the alert card.
 */
private class StaticMapView(context: Context) : MapView(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}

/**
 * osmdroid's one-time global setup.
 *
 * Both settings are requirements rather than tuning. The tile server's fair-use policy rejects
 * callers that do not identify themselves, and pointing the cache at app-private storage is what
 * keeps this off `WRITE_EXTERNAL_STORAGE` — a permission this app has no other reason to ask a
 * senior for.
 */
private object OsmdroidSetup {
    @Volatile
    private var configured = false

    fun ensure(context: Context) {
        if (configured) return
        synchronized(this) {
            if (configured) return
            val app = context.applicationContext
            Configuration.getInstance().apply {
                // load() overwrites fields from the stored preferences, so it has to run before
                // the values below are set, not after.
                load(app, app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                userAgentValue = "SEENior/1.0 (PUP capstone; passive senior monitoring)"
                osmdroidBasePath = File(app.filesDir, "osmdroid").apply { mkdirs() }
                osmdroidTileCache = File(osmdroidBasePath, "tiles").apply { mkdirs() }
            }
            configured = true
        }
    }
}
