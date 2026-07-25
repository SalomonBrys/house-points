import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement

/**
 * Compose Multiplatform renders web text fields onto a canvas, so
 * `KeyboardType.Number` never becomes an HTML `inputmode` and mobile
 * browsers default to the alphabetic keyboard. Overlaying a real
 * `<input inputmode="numeric">` (via [HtmlElementView]) is what actually
 * makes iOS Safari and Android Chrome show the numeric pad.
 *
 * The digit-only filtering/truncation itself still lives in the caller's
 * `onAmountChange` (see `SignedAmountInput` in `TeacherScreen.kt`); this
 * `<input>` is kept in sync with the resulting [amountText] every
 * recomposition, which also strips any stray non-digit character a
 * physical keyboard might have typed.
 *
 * [HtmlElementView]'s web measure policy sizes the overlay to the incoming
 * constraints only — it never measures the HTML content — so without an
 * explicit height the field collapses to 0px (invisible). [fontSize] is
 * used both for the input's `font-size` and, converted to dp, as that
 * explicit height.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PointsAmountField(
    amountText: String,
    onAmountChange: (String) -> Unit,
    enabled: Boolean,
    displayColor: Color,
    placeholderColor: Color,
    fontSize: TextUnit,
    modifier: Modifier,
) {
    val currentOnAmountChange = rememberUpdatedState(onAmountChange)
    val fieldHeight = with(LocalDensity.current) { fontSize.toDp() }
    val drawerOpen = LocalDrawerOpen.current

    HtmlElementView(
        factory = {
            (document.createElement("input") as HTMLInputElement).apply {
                type = "text"
                inputMode = "numeric"
                pattern = "[0-9]*"
                autocomplete = "off"
                maxLength = 3
                placeholder = "0"
                className = "points-amount-field"
                style.apply {
                    border = "none"
                    outline = "none"
                    background = "transparent"
                    padding = "0"
                    margin = "0"
                    width = "100%"
                    textAlign = "center"
                    lineHeight = "1"
                    setProperty("box-sizing", "border-box")
                }
                oninput = { currentOnAmountChange.value(value) }
            }
        },
        modifier = modifier.height(fieldHeight),
        update = { input ->
            if (input.value != amountText) input.value = amountText
            input.disabled = !enabled
            input.style.color = displayColor.toCssColor()
            input.style.setProperty("caret-color", displayColor.toCssColor())
            input.style.fontSize = "${fontSize.value}px"
            input.style.setProperty("--points-placeholder-color", placeholderColor.toCssColor())
            // This overlaid DOM <input> sits above the Compose canvas in the
            // browser's stacking context, so the in-canvas nav drawer/scrim can
            // never cover it — hide it explicitly while the drawer is open.
            input.style.visibility = if (drawerOpen) "hidden" else "visible"
        },
    )
}

private fun Color.toCssColor(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return "rgba($r, $g, $b, $alpha)"
}
