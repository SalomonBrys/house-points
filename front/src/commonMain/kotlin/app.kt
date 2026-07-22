import androidx.compose.runtime.Composable
import org.kodein.di.compose.withDI

@Composable
fun App() {
    withDI(appDI) {
        AppTheme {
            AppRoot()
        }
    }
}