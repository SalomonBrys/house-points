import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.admin_coming_soon
import house_points.front.generated.resources.welcome_message
import org.jetbrains.compose.resources.stringResource
import org.kodein.di.compose.localDI
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * Stub: role home for `admin` accounts (`SPECS.md §6`). House/teacher
 * management is a follow-up; proves routing for the admin role. Logout lives
 * in [AppRoot]'s drawer. Chrome (top bar/drawer) lives in [AppRoot], this is
 * content-only.
 */
@Composable
fun AdminScreen() {
    val di = localDI()
    val session = di.direct.instance<Session>()
    val authState by session.state.collectAsState()
    val username = (authState as? AuthState.LoggedIn)?.username.orEmpty()

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(Res.string.welcome_message, username))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.admin_coming_soon))
    }
}
