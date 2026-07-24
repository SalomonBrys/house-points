import androidx.navigation3.runtime.NavKey
import team_points.front.generated.resources.Res
import team_points.front.generated.resources.admin_title
import team_points.front.generated.resources.history_title
import team_points.front.generated.resources.login_title
import team_points.front.generated.resources.profile_title
import team_points.front.generated.resources.public_display_title
import team_points.front.generated.resources.teacher_title
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

/**
 * The app's screens, per `SPECS.md §6`. `@Serializable` is required by
 * Navigation3's multiplatform back stack (`rememberNavBackStack` needs to
 * (de)serialize entries on non-JVM targets — see [navSerializersModule]).
 * [titleRes] backs the shared top app bar in [AppRoot]; per
 * `front/ARCHITECTURE.md`, every displayed string is a compose resource
 * (`front/src/commonMain/composeResources/values/strings.xml`), so this holds
 * a resource reference rather than a literal string. [pathSegment] backs the
 * browser-URL sync on web (`BrowserNavigationSync`) — a plain internal
 * identifier, not user-facing, so unlike [titleRes] it's a literal.
 */
@Serializable
sealed interface Screen : NavKey {
    val titleRes: StringResource
    val pathSegment: String
}

/** The app's default screen — the public team ranking, labeled "Classement". */
@Serializable
data object Leaderboard : Screen {
    override val titleRes = Res.string.public_display_title
    // Must be a real, non-blank name: navigation3-browser's
    // getBrowserHistoryFragmentName() treats a blank fragment as unparseable
    // (always null) and can never round-trip an empty name back to a screen.
    override val pathSegment = "leaderboard"
}

@Serializable
data object Login : Screen {
    override val titleRes = Res.string.login_title
    override val pathSegment = "login"
}

@Serializable
data object History : Screen {
    override val titleRes = Res.string.history_title
    override val pathSegment = "history"
}

@Serializable
data object TeacherHome : Screen {
    override val titleRes = Res.string.teacher_title
    override val pathSegment = "teacher"
}

@Serializable
data object AdminHome : Screen {
    override val titleRes = Res.string.admin_title
    override val pathSegment = "admin"
}

/** Authenticated-only: shows the current username and lets it change its password. */
@Serializable
data object Profile : Screen {
    override val titleRes = Res.string.profile_title
    override val pathSegment = "profile"
}

/**
 * Reverse lookup for [pathSegment], used by `BrowserNavigationSync` (web only)
 * to turn a browser URL fragment back into a [Screen]. Falls back to
 * [Leaderboard] for an unrecognized or empty segment (bad/stale link) rather
 * than leaving the app on no screen at all.
 */
fun screenForPath(segment: String): Screen = when (segment) {
    Login.pathSegment -> Login
    History.pathSegment -> History
    TeacherHome.pathSegment -> TeacherHome
    AdminHome.pathSegment -> AdminHome
    Profile.pathSegment -> Profile
    else -> Leaderboard
}
