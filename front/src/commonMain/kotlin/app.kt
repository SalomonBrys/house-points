import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import org.kodein.di.compose.withDI

@Composable
fun App() {
    // Team images (SPECS.md) are plain public GETs, so a bare Ktor fetcher is
    // enough — no need to route through the app's authed HttpClient. `remember`
    // (rather than a top-level `init`) keeps this Compose-lifecycle-scoped like
    // the rest of the app's setup; `setSafe` is a no-op if already installed.
    remember {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(KtorNetworkFetcherFactory()) }
                .build()
        }
    }

    withDI(appDI) {
        AppTheme {
            AppRoot()
        }
    }
}