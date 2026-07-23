import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.app_name
import house_points.front.generated.resources.drawer_logout
import house_points.front.generated.resources.history_title
import house_points.front.generated.resources.login_title
import house_points.front.generated.resources.nav_back
import house_points.front.generated.resources.nav_open_menu
import house_points.front.generated.resources.public_display_title
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.stringResource
import org.kodein.di.compose.localDI
import org.kodein.di.direct
import org.kodein.di.instance

/** Registers every [Screen] subtype for Navigation3's polymorphic back-stack serialization. */
private val navSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(PublicDisplay::class, PublicDisplay.serializer())
        subclass(Login::class, Login.serializer())
        subclass(History::class, History.serializer())
        subclass(TeacherHome::class, TeacherHome.serializer())
        subclass(AdminHome::class, AdminHome.serializer())
    }
}

private val navConfig = SavedStateConfiguration { serializersModule = navSerializersModule }

/**
 * Top-level app shell: one shared drawer + top bar wrapping the Navigation3
 * back stack, so individual screens carry no chrome of their own. Public
 * screens (leaderboard/[PublicDisplay], history, login) are always reachable
 * from the drawer; a successful login routes to the role's home and a logout
 * routes back to [PublicDisplay] — driven by observing [Session] rather than
 * by the screens navigating themselves.
 */
@Composable
fun AppRoot() {
    val di = localDI()
    val session = di.direct.instance<Session>()
    val auth = di.direct.instance<AuthRepository>()
    val authState by session.state.collectAsState()

    val backStack = rememberNavBackStack(navConfig, PublicDisplay)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.LoggedIn -> {
                val home = if (state.role == "admin") AdminHome else TeacherHome
                if (backStack.lastOrNull() != home) {
                    backStack.clear()
                    backStack.add(home)
                }
            }

            AuthState.LoggedOut -> {
                if (backStack.lastOrNull() is TeacherHome || backStack.lastOrNull() is AdminHome) {
                    backStack.clear()
                    backStack.add(PublicDisplay)
                }
            }
        }
    }

    val currentScreen = backStack.lastOrNull() as? Screen

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                authState = authState,
                currentScreen = currentScreen,
                onNavigate = { screen ->
                    scope.launch { drawerState.close() }
                    backStack.clear()
                    backStack.add(screen)
                },
                onLogout = {
                    scope.launch {
                        drawerState.close()
                        auth.logout()
                    }
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = stringResource(currentScreen?.titleRes ?: Res.string.app_name),
                    canPop = backStack.size > 1,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    onBack = { backStack.removeLastOrNull() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        entry<Login> { LoginScreen() }
                        entry<History> { HistoryScreen() }
                        entry<PublicDisplay> { PublicDisplayScreen() }
                        entry<TeacherHome> { TeacherScreen() }
                        entry<AdminHome> { AdminScreen() }
                    },
                )
            }
        }
    }
}

@Composable
private fun AppTopBar(
    title: String,
    canPop: Boolean,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (canPop) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.nav_back))
                }
            } else {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Filled.Menu, contentDescription = stringResource(Res.string.nav_open_menu))
                }
            }
        },
    )
}

@Composable
private fun AppDrawerContent(
    authState: AuthState,
    currentScreen: Screen?,
    onNavigate: (Screen) -> Unit,
    onLogout: () -> Unit,
) {
    ModalDrawerSheet {
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.public_display_title)) },
            icon = { Icon(Icons.Filled.Slideshow, contentDescription = null) },
            selected = currentScreen == PublicDisplay,
            onClick = { onNavigate(PublicDisplay) },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.history_title)) },
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
            selected = currentScreen == History,
            onClick = { onNavigate(History) },
        )
        when (authState) {
            AuthState.LoggedOut -> {
                NavigationDrawerItem(
                    label = { Text(stringResource(Res.string.login_title)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null) },
                    selected = currentScreen == Login,
                    onClick = { onNavigate(Login) },
                )
            }

            is AuthState.LoggedIn -> {
                // Every account (teacher or admin) can award/void points, so the
                // teacher page is always shown; admins additionally see their
                // management page.
                NavigationDrawerItem(
                    label = { Text(stringResource(TeacherHome.titleRes)) },
                    icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                    selected = currentScreen == TeacherHome,
                    onClick = { onNavigate(TeacherHome) },
                )
                if (authState.role == "admin") {
                    NavigationDrawerItem(
                        label = { Text(stringResource(AdminHome.titleRes)) },
                        icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null) },
                        selected = currentScreen == AdminHome,
                        onClick = { onNavigate(AdminHome) },
                    )
                }
                NavigationDrawerItem(
                    label = { Text(stringResource(Res.string.drawer_logout)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    selected = false,
                    onClick = onLogout,
                )
            }
        }
    }
}
