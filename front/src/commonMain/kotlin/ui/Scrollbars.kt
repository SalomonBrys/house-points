import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Shared vertical scrollbar look for every scrollable page (Historique,
 * Classement, Enseignant, Administrateur) — a fixed opacity over
 * `onSurface` reads clearly against both the light and dark color schemes
 * ([AppTheme]), unlike the platform-default gray-on-gray style.
 */
@Composable
private fun appScrollbarStyle(): ScrollbarStyle {
    val onSurface = MaterialTheme.colorScheme.onSurface
    return LocalScrollbarStyle.current.copy(
        unhoverColor = onSurface.copy(alpha = 0.3f),
        hoverColor = onSurface.copy(alpha = 0.6f),
    )
}

/**
 * Right-edge, full-height vertical scrollbar. Call inside a `Box` that wraps
 * the scrollable content (`Column.verticalScroll`, `LazyColumn`, or
 * `LazyVerticalGrid`), passing the matching [ScrollbarAdapter] built from
 * that content's scroll/list/grid state via `rememberScrollbarAdapter`.
 */
@Composable
fun BoxScope.EndVerticalScrollbar(adapter: ScrollbarAdapter) {
    VerticalScrollbar(
        adapter = adapter,
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        style = appScrollbarStyle(),
    )
}
