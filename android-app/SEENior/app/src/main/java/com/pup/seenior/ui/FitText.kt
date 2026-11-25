package com.pup.seenior.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * How large the system "Font size" setting is allowed to make this app's text.
 *
 * Every layout here is built in dp for a 360 dp phone. At the largest system sizes (up to 2.0x)
 * a word like "Imbitasyon" or "Contacts" no longer fits its tab or button and gets cut in the
 * middle of the word ("Imbita / syon"), which is unreadable for exactly the seniors who turn the
 * font up. So the app honours the setting up to [MAX_FONT_SCALE] and no further, and the narrow
 * single-line spots (tabs, buttons, headers, chips) additionally shrink to fit with [FitText].
 *
 * The trade-off is deliberate and one number: raise it only after checking every screen at that
 * size on a phone. Pure, so it is unit-tested.
 */
object FontScaleGuard {

    const val MAX_FONT_SCALE = 1.3f

    /** The scale the app actually uses for a system setting of [systemFontScale]. */
    fun cap(systemFontScale: Float): Float = systemFontScale.coerceIn(0.85f, MAX_FONT_SCALE)

    /** [density] with its font scale capped; the screen density is left alone. */
    fun capped(density: Density): Density = Density(density.density, cap(density.fontScale))
}

/** The shrink rule behind [FitText], separate from the UI so it is unit-tested. */
object FitScale {

    /** Smallest a label may shrink to (60% of its normal size) before it is cut with an ellipsis. */
    const val MIN = 0.6f

    private const val STEP = 0.92f

    /** The scale to try next: smaller while the text overflows its width, otherwise unchanged. */
    fun next(current: Float, overflowsWidth: Boolean, min: Float = MIN): Float =
        if (overflowsWidth && current > min) (current * STEP).coerceAtLeast(min) else current
}

/**
 * One line of text that shrinks until it fits its width, and is never broken in the middle of a
 * word. For narrow, single-line slots: tab labels, button labels, header titles, chips and
 * badges. Paragraphs should keep using [Text], which wraps at word boundaries.
 *
 * Hidden for the one or two layout passes it takes to settle, so the text does not visibly jump.
 * At the minimum size it is ellipsised, which is a last resort and still readable.
 */
@Composable
fun FitText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    /** True for slots that share the screen width (the bottom bar): the system font size is not applied. */
    ignoreSystemFontScale: Boolean = false
) {
    val fontScale = LocalDensity.current.fontScale
    val baseSize = if (ignoreSystemFontScale) (fontSize.value / fontScale).sp else fontSize
    var scale by remember(text, fontSize, fontScale) { mutableFloatStateOf(1f) }
    var settled by remember(text, fontSize, fontScale) { mutableStateOf(false) }

    Text(
        text = text,
        modifier = modifier.alpha(if (settled) 1f else 0f),
        color = color,
        fontSize = baseSize * scale,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout ->
            // An ellipsised line reports visual overflow, not always width overflow, so both count.
            val next = FitScale.next(scale, layout.didOverflowWidth || layout.hasVisualOverflow)
            if (next != scale) scale = next else settled = true
        }
    )
}
