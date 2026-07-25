import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.github.terrakok.navigation3.browser.ChronologicalBrowserNavigation
import com.github.terrakok.navigation3.browser.buildBrowserHistoryFragment
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentName
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentParameters

actual @Composable fun BrowserNavigationSync(backStack: NavBackStack<NavKey>) {
    ChronologicalBrowserNavigation(
        backStack = backStack,
        saveKey = { key ->
            (key as? Screen)?.let { screen ->
                // Only the Historique screen carries extra state (its team/teacher
                // filter) worth reflecting in the URL's query part.
                val params = if (screen is History) {
                    when (val selection = screen.filter) {
                        HistoryFilterSelection.All -> emptyMap()
                        is HistoryFilterSelection.ByTeam -> mapOf("team_id" to selection.teamId.toString())
                        is HistoryFilterSelection.ByTeacher -> mapOf("teacher_id" to selection.teacherId.toString())
                    }
                } else {
                    emptyMap()
                }
                buildBrowserHistoryFragment(screen.pathSegment, params)
            }
        },
        // A blank/unparseable fragment (notably the real empty hash on a fresh
        // page load) must propagate as null so the library leaves the back
        // stack untouched, rather than resolving to Leaderboard and being
        // appended onto the already-correct initial stack (see Screen.kt).
        restoreKey = { fragment ->
            getBrowserHistoryFragmentName(fragment)?.let { name ->
                val screen = screenForPath(name)
                if (screen is History) {
                    val params = getBrowserHistoryFragmentParameters(fragment)
                    val selection = params["team_id"]?.toIntOrNull()?.let { HistoryFilterSelection.ByTeam(it) }
                        ?: params["teacher_id"]?.toIntOrNull()?.let { HistoryFilterSelection.ByTeacher(it) }
                        ?: HistoryFilterSelection.All
                    History(selection)
                } else {
                    screen
                }
            }
        },
    )
}
