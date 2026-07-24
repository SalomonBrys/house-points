import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.github.terrakok.navigation3.browser.ChronologicalBrowserNavigation
import com.github.terrakok.navigation3.browser.buildBrowserHistoryFragment
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentName

actual @Composable fun BrowserNavigationSync(backStack: NavBackStack<NavKey>) {
    ChronologicalBrowserNavigation(
        backStack = backStack,
        saveKey = { key -> (key as? Screen)?.let { buildBrowserHistoryFragment(it.pathSegment) } },
        // A blank/unparseable fragment (notably the real empty hash on a fresh
        // page load) must propagate as null so the library leaves the back
        // stack untouched, rather than resolving to Leaderboard and being
        // appended onto the already-correct initial stack (see Screen.kt).
        restoreKey = { fragment -> getBrowserHistoryFragmentName(fragment)?.let(::screenForPath) },
    )
}
