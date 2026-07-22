import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.history_coming_soon
import org.jetbrains.compose.resources.stringResource

/**
 * Stub: paginated public event history (`GET /api/events`, `SPECS.md §6`).
 * Follow-up. Chrome (top bar/drawer) lives in [AppRoot], this is content-only.
 */
@Composable
fun HistoryScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(Res.string.history_coming_soon))
    }
}
