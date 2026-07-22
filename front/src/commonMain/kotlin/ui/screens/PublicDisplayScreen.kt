import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.public_display_coming_soon
import org.jetbrains.compose.resources.stringResource

/**
 * Stub: fullscreen, auto-refreshing public display (`SPECS.md §6`) — polls
 * `GET /api/events/since` for new events and re-fetches `GET /api/houses`
 * for totals. Follow-up; reuses [PublicViewModel]'s data path once built out.
 * Chrome (top bar/drawer) lives in [AppRoot], this is content-only. (A truly
 * chrome-less fullscreen mode is also a follow-up.)
 */
@Composable
fun PublicDisplayScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(Res.string.public_display_coming_soon))
    }
}
