import androidx.navigation3.runtime.NavKey
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.admin_title
import house_points.front.generated.resources.history_title
import house_points.front.generated.resources.login_title
import house_points.front.generated.resources.public_display_title
import house_points.front.generated.resources.teacher_title
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

/**
 * The app's screens, per `SPECS.md §6`. `@Serializable` is required by
 * Navigation3's multiplatform back stack (`rememberNavBackStack` needs to
 * (de)serialize entries on non-JVM targets — see [navSerializersModule]).
 * [titleRes] backs the shared top app bar in [AppRoot]; per
 * `front/ARCHITECTURE.md`, every displayed string is a compose resource
 * (`front/src/commonMain/composeResources/values/strings.xml`), so this holds
 * a resource reference rather than a literal string.
 */
@Serializable
sealed interface Screen : NavKey {
    val titleRes: StringResource
}

/** The app's default screen — the public house ranking, labeled "Classement". */
@Serializable
data object PublicDisplay : Screen {
    override val titleRes = Res.string.public_display_title
}

@Serializable
data object Login : Screen {
    override val titleRes = Res.string.login_title
}

@Serializable
data object History : Screen {
    override val titleRes = Res.string.history_title
}

@Serializable
data object TeacherHome : Screen {
    override val titleRes = Res.string.teacher_title
}

@Serializable
data object AdminHome : Screen {
    override val titleRes = Res.string.admin_title
}
