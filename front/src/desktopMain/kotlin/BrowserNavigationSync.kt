import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

actual @Composable fun BrowserNavigationSync(backStack: NavBackStack<NavKey>) {
    // No browser to sync with on desktop.
}
