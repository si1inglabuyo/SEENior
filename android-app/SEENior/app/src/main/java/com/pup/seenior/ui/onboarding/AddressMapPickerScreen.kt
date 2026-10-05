package com.pup.seenior.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pup.seenior.address.PsgcMatch
import com.pup.seenior.address.PsgcMatcher
import com.pup.seenior.location.AddressGeocoder
import com.pup.seenior.location.AlertLocationCapture
import com.pup.seenior.location.Geohash
import com.pup.seenior.location.LocationPermissionState
import com.pup.seenior.ui.LocalOnboardingCopy
import com.pup.seenior.ui.onboarding.components.OnboardingHeading
import com.pup.seenior.ui.onboarding.components.OnboardingTopBar
import com.pup.seenior.ui.onboarding.components.PrimaryPillButton
import com.pup.seenior.ui.theme.SeniorColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import com.pup.seenior.location.MapTiles
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import java.io.File

/** What the picker currently believes is under the pin. */
private sealed interface PinAddress {
    data object Looking : PinAddress
    data object Unknown : PinAddress
    data class Found(val match: PsgcMatch, val streetLine: String) : PinAddress
}

/** Plain holder rather than Compose state: the map is read at click time, never rendered from. */
private class MapHandle {
    var map: MapView? = null
}

/**
 * Lets a senior set their address by dragging a map instead of four dropdowns.
 *
 * The pin stays at the centre of the frame and the map moves under it, so a drag anywhere is
 * a correct gesture and the target can't be missed. The result is a suggestion: only values
 * that exist in the PSGC dataset (see [PsgcMatcher]) come back, with the dropdowns filled in
 * and still editable.
 *
 * Used by sign-up and Edit Profile; [onApply] receives the result and [showStepDots] turns
 * off the onboarding progress dots for Edit Profile.
 */
@Composable
fun AddressMapPickerScreen(
    onApply: (PsgcMatch, String) -> Unit,
    onBack: () -> Unit,
    onConfirmed: () -> Unit,
    showStepDots: Boolean = true
) {
    val copy = LocalOnboardingCopy.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mapHandle = remember { MapHandle() }

    var pinAddress by remember { mutableStateOf<PinAddress>(PinAddress.Unknown) }
    // Held so a new drag cancels an older lookup still in flight, which could arrive late and describe the wrong spot.
    var lookupJob by remember { mutableStateOf<Job?>(null) }

    var findingMe by remember { mutableStateOf(false) }
    var findMeFailed by remember { mutableStateOf(false) }

    fun lookUp(point: GeoPoint) {
        lookupJob?.cancel()
        findMeFailed = false
        lookupJob = scope.launch {
            pinAddress = PinAddress.Looking
            val place = AddressGeocoder.reverse(point.latitude, point.longitude)
            val match = place?.let { PsgcMatcher.match(context, it) }
            pinAddress = if (place != null && match != null) {
                PinAddress.Found(match, place.streetLine)
            } else {
                PinAddress.Unknown
            }
        }
    }

    /** Centres on the phone and says so when it can't, since a silent tap looks broken and the live-fix wait can be long indoors. */
    fun findMe() {
        if (findingMe) return
        findingMe = true
        findMeFailed = false
        scope.launch {
            val found = centreOnSenior(context, mapHandle, ::lookUp)
            findMeFailed = !found
            findingMe = false
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) findMe()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            OnboardingTopBar(currentStep = 1, totalSteps = if (showStepDots) 5 else 0, onBack = onBack)
            OnboardingHeading(
                title = copy.mapTitle,
                subtitle = copy.mapSubtitle
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .background(SeniorColors.FieldBackground, RoundedCornerShape(16.dp))
            ) {
                AndroidView(
                    // Clipped on the view itself, as the alert map is: Compose's AndroidView
                    // doesn't clip children, and osmdroid draws tiles at a scrolled offset.
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    factory = { viewContext ->
                        ensureOsmdroid(viewContext)
                        MapView(viewContext).apply {
                            setTileSource(MapTiles.Street)
                            setMultiTouchControls(true)
                            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                            controller.setZoom(START_ZOOM)
                            controller.setCenter(GeoPoint(START_LATITUDE, START_LONGITUDE))
                            // Nominatim allows one request a second, so the lookup waits for the
                            // map to settle instead of firing during the drag.
                            addMapListener(
                                DelayedMapListener(
                                    object : MapListener {
                                        override fun onScroll(event: ScrollEvent?): Boolean {
                                            lookUp(GeoPoint(mapCenter.latitude, mapCenter.longitude))
                                            return true
                                        }

                                        override fun onZoom(event: ZoomEvent?): Boolean = false
                                    },
                                    SETTLE_DELAY_MS
                                )
                            )
                            onResume()
                            mapHandle.map = this
                        }
                    },
                    onRelease = { map ->
                        map.onPause()
                        map.onDetach()
                        mapHandle.map = null
                    }
                )

                // Painted over the map, not added as an overlay, so it stays at the centre. Lifted
                // by half its height so the pin's point marks the spot.
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = copy.mapPinDescription,
                    tint = PinRed,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-22).dp)
                        .size(44.dp)
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .clickable {
                            if (hasAnyLocationPermission(context)) {
                                findMe()
                            } else {
                                // Location is normally asked two screens later; granting now means no second dialog.
                                LocationPermissionState.markAsked(context)
                                locationPermission.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = null,
                        tint = SeniorColors.GreenDark,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        if (findingMe) copy.mapFindingMe else copy.mapFindMe,
                        color = SeniorColors.GreenDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            AddressPreview(pinAddress, findMeFailed)

            PrimaryPillButton(
                text = copy.mapUseAddress,
                onClick = {
                    (pinAddress as? PinAddress.Found)?.let {
                        onApply(it.match, it.streetLine)
                        onConfirmed()
                    }
                },
                enabled = pinAddress is PinAddress.Found,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )
        }
    }

    DisposableEffect(Unit) { onDispose { lookupJob?.cancel() } }
}

@Composable
private fun AddressPreview(state: PinAddress, findMeFailed: Boolean) {
    val copy = LocalOnboardingCopy.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(96.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        when {
            // Shown over whatever the pin says: the senior just asked for something and did not get it.
            findMeFailed -> Text(copy.mapFindMeFailed, color = SeniorColors.TextSecondary, fontSize = 15.sp)
            else -> AddressPreviewBody(state)
        }
    }
}

@Composable
private fun AddressPreviewBody(state: PinAddress) {
    val copy = LocalOnboardingCopy.current
    Box(contentAlignment = Alignment.CenterStart) {
        when (state) {
            PinAddress.Looking -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = SeniorColors.Green,
                    strokeWidth = 2.dp
                )
                Text(copy.mapLookingUp, color = SeniorColors.TextSecondary, fontSize = 15.sp)
            }

            PinAddress.Unknown -> Text(
                copy.mapNoName,
                color = SeniorColors.TextSecondary,
                fontSize = 15.sp
            )

            is PinAddress.Found -> Column {
                Text(
                    listOf(state.streetLine, state.match.barangay.orEmpty(), state.match.city)
                        .filter { it.isNotBlank() }
                        .joinToString(", "),
                    color = SeniorColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    // Said plainly: the barangay is what tier 3 depends on, and a wrong one fails silently.
                    if (state.match.barangay == null) copy.mapNoBarangay else copy.mapCheckThis,
                    color = SeniorColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

private fun hasAnyLocationPermission(context: Context): Boolean = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/**
 * Moves the map to the phone's current position, then looks that spot up. Goes through
 * [AlertLocationCapture] so only one place in the app asks where the phone is. The geohash
 * round trip costs a couple of metres, which doesn't matter for a map being dragged.
 */
private suspend fun centreOnSenior(
    context: Context,
    mapHandle: MapHandle,
    lookUp: (GeoPoint) -> Unit
): Boolean {
    val map = mapHandle.map ?: return false
    val cell = AlertLocationCapture.capture(context)?.let(Geohash::decode) ?: return false
    val point = GeoPoint(cell.centerLatitude, cell.centerLongitude)
    map.controller.setZoom(HOME_ZOOM)
    map.controller.animateTo(point)
    lookUp(point)
    return true
}

private fun ensureOsmdroid(context: Context) {
    val app = context.applicationContext
    Configuration.getInstance().apply {
        load(app, app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        userAgentValue = "SEENior/1.0 (PUP capstone; passive senior monitoring)"
        osmdroidBasePath = File(app.filesDir, "osmdroid").apply { mkdirs() }
        osmdroidTileCache = File(osmdroidBasePath, "tiles").apply { mkdirs() }
    }
}

/** Conventional map-pin red, matching the family app's alert accent. */
private val PinRed = Color(0xFFD9534F)

/** Metro Manila, wide enough to recognise but close enough to drag from. */
private const val START_LATITUDE = 14.5995
private const val START_LONGITUDE = 120.9842
private const val START_ZOOM = 11.0

/** Close enough to pick out a house once the phone has said where it is. */
private const val HOME_ZOOM = 18.0

/** How still the map must be before a lookup is worth spending. */
private const val SETTLE_DELAY_MS = 700L
