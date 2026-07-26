import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp

/**
 * A vertically-stacked pair of panes with a draggable divider between them,
 * hand-rolled instead of `org.jetbrains.compose.components:components-splitpane`
 * — that artifact only publishes `metadataApiElements` + desktop/JVM variants
 * (checked Maven Central for 1.11.1 and 1.12.0-beta02), so it can't resolve
 * for this app's `wasmJs`/`js` targets. Only [LeaderboardScreen]'s history
 * pane uses this today.
 *
 * [top]/[bottom] split the available height, starting at [initialFraction]
 * and persisted across recomposition (but not navigation) via
 * [rememberSaveable], dragged within [minFraction]/[maxFraction] via the
 * handle in between.
 */
@Composable
fun VerticalSplitPane(
    modifier: Modifier = Modifier,
    initialFraction: Float = 0.65f,
    minFraction: Float = 0.2f,
    maxFraction: Float = 0.85f,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    var fraction by rememberSaveable { mutableStateOf(initialFraction) }
    BoxWithConstraints(modifier) {
        val totalPx = constraints.maxHeight.toFloat()
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(fraction.coerceIn(minFraction, maxFraction))) { top() }
            SplitHandle(
                onDrag = { deltaPx ->
                    if (totalPx > 0f) {
                        fraction = (fraction + deltaPx / totalPx).coerceIn(minFraction, maxFraction)
                    }
                },
            )
            Column(Modifier.weight(1f - fraction.coerceIn(minFraction, maxFraction))) { bottom() }
        }
    }
}

/** The draggable divider itself: a thin rule plus a centered grab bar. */
@Composable
private fun SplitHandle(onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> onDrag(delta) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider(Modifier.align(Alignment.TopCenter))
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}
