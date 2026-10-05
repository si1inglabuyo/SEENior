package com.pup.seenior.ui.family

import android.content.Context
import androidx.core.content.ContextCompat
import com.pup.seenior.R
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pup.seenior.location.AddressGeocoder
import com.pup.seenior.location.Geohash
import com.pup.seenior.location.LatLon
import org.osmdroid.config.Configuration
import com.pup.seenior.location.MapTiles
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
 * What the map shows, and what it may claim. A [Cluster] is where the phone actually was when
 * the alert fired; [RegisteredAddress] is only where the senior lives, used when no fix was
 * captured. Precision is read from the cell itself, since alerts before 2026-08-31 carry
 * ~150 m cells and newer ones ~5 m.
 */
private sealed interface MapTarget {
    data class Cluster(val cell: Geohash.Cell) : MapTarget
    data class RegisteredAddress(val point: LatLon) : MapTarget
}

/**
 * The alert map plus a text line for where the senior is.
 *
 * Prefers the alert's captured cell: draws it and reverse-geocodes its centre to a street
 * line so the family can read it out to a responder. This discloses nothing the pin doesn't,
 * and is allowed during an active alert under RA 10173 section 12(c). With no fix it falls
 * back to the registered address, labelled as such, and with neither it shows
 * [MapPlaceholder].
 *
 * @param interactive whether the map takes touch gestures. False in a scrolling card, where a
 *   map would trap drags.
 */
@Composable
fun AlertLocationMap(
    clusterId: String?,
    registeredAddress: String,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    interactive: Boolean = false,
    copy: FamilyStrings.Copy = LocalFamilyCopy.current
) {
    val context = LocalContext.current

    val cell = remember(clusterId) { clusterId?.let(Geohash::decode) }
    var fallback by remember(registeredAddress) { mutableStateOf<LatLon?>(null) }
    var resolving by remember(clusterId, registeredAddress) { mutableStateOf(cell == null) }

    // The street the captured fix is in, so the family reads where the senior is rather than
    // her home address. Null while looking up or if it can't be resolved.
    var capturedPlace by remember(clusterId) { mutableStateOf(clusterId?.let { reverseCache[it] }) }

    LaunchedEffect(clusterId, registeredAddress) {
        if (cell != null) {
            resolving = false
            if (capturedPlace == null && clusterId != null) {
                capturedPlace = AddressGeocoder.reverse(cell.centerLatitude, cell.centerLongitude)
                    ?.let { place ->
                        listOf(
                            place.streetLine,
                            place.barangayNames.firstOrNull().orEmpty(),
                            place.cityNames.firstOrNull().orEmpty()
                        ).filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() }
                    }
                    ?.also { reverseCache[clusterId] = it }
            }
            return@LaunchedEffect
        }
        // No fix was captured — fall back to placing the senior's registered home address.
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

        when (target) {
            is MapTarget.Cluster -> {
                // Primary line: where the phone was, in words, or a plain statement while the lookup runs.
                Text(
                    text = capturedPlace?.let(copy::currentLocationKnown)
                        ?: copy.currentLocationUnknownPlace,
                    color = FamilyColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = run {
                        val span = target.cell.approximateSpanMetres()
                        if (span <= PIN_THRESHOLD_METRES) {
                            copy.locationCapturedOnceNote
                        } else {
                            copy.approximateAreaNote(span.roundToInt())
                        }
                    },
                    color = FamilyColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            is MapTarget.RegisteredAddress -> {
                Text(
                    text = copy.noLiveLocationCaptured,
                    color = FamilyColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = copy.homeAddressLine(registeredAddress),
                    color = FamilyColors.TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // No cluster and the address couldn't be geocoded: still show the address in words.
            null -> if (!resolving && registeredAddress.isNotBlank()) {
                Text(
                    text = copy.homeAddressLine(registeredAddress),
                    color = FamilyColors.TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * Reverse-geocoded locations keyed by geohash cell, for this process only. The family alert
 * screens mount the same map and the alerts tab recomposes every 20 s, so this keeps it to
 * one Nominatim call per alert per session. Not persisted, so a failed lookup can retry.
 */
private val reverseCache = java.util.concurrent.ConcurrentHashMap<String, String>()

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
                setTileSource(MapTiles.Carto)
                setMultiTouchControls(interactive)
                // The design has no zoom chrome; pinch covers it on the screen that needs it.
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                onResume()
            }
        },
        update = { map -> map.render(target) },
        // Stops the tile threads when the screen goes away.
        onRelease = { map ->
            map.onPause()
            map.onDetach()
        }
    )
}

/**
 * Draws [target] onto the map, replacing what was there. Overlays are cleared first because
 * [AndroidView] reuses the [MapView] across recompositions.
 */
private fun MapView.render(target: MapTarget) {
    // update() runs on every recomposition (every 20 s). The camera is set once per target so
    // it doesn't snap back under a panning finger; overlays are still redrawn.
    val alreadyFramed = tag == target
    overlays.clear()

    when (target) {
        is MapTarget.Cluster -> {
            val cell = target.cell
            val centre = GeoPoint(cell.centerLatitude, cell.centerLongitude)

            // A cell finer than GPS error is a position, so draw a pin; a ~150 m cell is a
            // region, so draw a square.
            if (cell.approximateSpanMetres() <= PIN_THRESHOLD_METRES) {
                overlays.add(
                    Marker(this).apply {
                        position = centre
                        icon = bluePin(context)
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
                        // The Paint accessors, since osmdroid deprecated the shorthands in 6.1.
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
                    icon = bluePin(context)
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

/** osmdroid's default marker is a pointing figure; this is the plain blue pin instead. */
private fun bluePin(context: Context) = ContextCompat.getDrawable(context, R.drawable.ic_map_pin_blue)

/** The widest cell still drawn as a point: above phone GPS error (~5-10 m) and well below ~150 m. */
private const val PIN_THRESHOLD_METRES = 30.0

/** Street level, for a cell that names a position. */
private const val POSITION_ZOOM = 18.5

/** Roughly frames a 150 m cell from an alert raised before locations were kept precisely. */
private const val AREA_ZOOM = 17.0

/** The longer side of a cell in metres. East-west is scaled by the cosine of latitude. */
private fun Geohash.Cell.approximateSpanMetres(): Double = max(
    (northLatitude - southLatitude) * 111_320.0,
    (eastLongitude - westLongitude) * 111_320.0 * cos(Math.toRadians(centerLatitude))
)

/** A little wider: a geocoded address lands on the street, so the surroundings help place it. */
private const val ADDRESS_ZOOM = 16.5

/**
 * A map that declines every touch so its scrolling card keeps the gestures. Returning false
 * from [dispatchTouchEvent] lets the drag reach the Compose scroll container.
 */
private class StaticMapView(context: Context) : MapView(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}

/**
 * osmdroid's one-time global setup. Both settings are required: the tile server rejects
 * callers that don't identify themselves, and an app-private cache avoids needing
 * `WRITE_EXTERNAL_STORAGE`.
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
                // load() overwrites fields from stored preferences, so it must run first.
                load(app, app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                userAgentValue = "SEENior/1.0 (PUP capstone; passive senior monitoring)"
                osmdroidBasePath = File(app.filesDir, "osmdroid").apply { mkdirs() }
                osmdroidTileCache = File(osmdroidBasePath, "tiles").apply { mkdirs() }
            }
            configured = true
        }
    }
}
