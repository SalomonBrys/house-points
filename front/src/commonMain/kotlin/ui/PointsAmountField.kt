import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit

/**
 * Whether the app's nav drawer is currently open. Provided by `AppRoot` and read
 * only by the web [PointsAmountField] actual, which overlays a real DOM `<input>`
 * on the Compose canvas — outside Compose's z-order/scrim — and so must hide itself
 * while the drawer is open (the drawer, drawn in-canvas, cannot cover it).
 */
val LocalDrawerOpen = staticCompositionLocalOf { false }

/**
 * Digit-only entry for the teacher's points amount ([SignedAmountInput] in
 * `TeacherScreen.kt`, `SPECS.md §6`). Desktop backs it with a plain
 * `BasicTextField`; the web targets (wasmJs/js) back it with a real HTML
 * `<input>` instead, because Compose Multiplatform renders web text fields
 * onto a canvas, leaving `KeyboardType.Number` with no HTML `inputmode` to
 * map onto — mobile browsers otherwise show the default (alphabetic)
 * keyboard. See the platform `actual` implementations.
 *
 * @param displayColor color of the typed digits (green/red/disabled per sign+enabled state).
 * @param placeholderColor color of the "0" shown when [amountText] is empty.
 */
@Composable
expect fun PointsAmountField(
    amountText: String,
    onAmountChange: (String) -> Unit,
    enabled: Boolean,
    displayColor: Color,
    placeholderColor: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
)
