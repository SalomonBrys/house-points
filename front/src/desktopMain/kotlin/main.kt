import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import team_points.front.generated.resources.Res
import team_points.front.generated.resources.app_name
import org.jetbrains.compose.resources.stringResource

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = stringResource(Res.string.app_name)) {
        App()
    }
}
