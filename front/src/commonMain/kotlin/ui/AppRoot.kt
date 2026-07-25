import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import team_points.front.generated.resources.Res
import team_points.front.generated.resources.app_name
import team_points.front.generated.resources.drawer_logout
import team_points.front.generated.resources.history_title
import team_points.front.generated.resources.history_title_filtered
import team_points.front.generated.resources.login_title
import team_points.front.generated.resources.nav_back
import team_points.front.generated.resources.nav_open_menu
import team_points.front.generated.resources.public_display_title
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
        subclass(Leaderboard::class, Leaderboard.serializer())
        subclass(Login::class, Login.serializer())
        subclass(History::class, History.serializer())
        subclass(TeacherHome::class, TeacherHome.serializer())
        subclass(AdminHome::class, AdminHome.serializer())
        subclass(Profile::class, Profile.serializer())
    }
}

private val navConfig = SavedStateConfiguration { serializersModule = navSerializersModule }

/**
 * Keeps [AppRoot]'s nav back stack in sync with the browser's address bar and
 * history (back/forward) on web targets, via terrakok/navigation3-browser
 * (`webMain`-only dependency) in "chronological" mode — URL navigation drives
 * the app, like a normal website. No-op on desktop (no browser to sync with).
 */
@Composable
expect fun BrowserNavigationSync(backStack: NavBackStack<NavKey>)

/**
 * Top-level app shell: one shared drawer + top bar wrapping the Navigation3
 * back stack, so individual screens carry no chrome of their own. Public
 * screens (leaderboard/[Leaderboard], history, login) are always reachable
 * from the drawer; an explicit login routes to the role's home, a restored
 * session (see [AuthRepository.restoreSession]) preserves whatever route the
 * URL/back stack already resolved to (so a reload or direct link stays put),
 * and a logout routes back to [Leaderboard] too — driven by observing
 * [Session] rather than by the screens navigating themselves.
 */
@Composable
fun AppRoot() {
    val di = localDI()
    val session = di.direct.instance<Session>()
    val auth = di.direct.instance<AuthRepository>()
    val authState by session.state.collectAsState()
    val historyFilter = di.direct.instance<HistoryFilter>()
    val historyFilterName by historyFilter.selectionName.collectAsState()

    val backStack = rememberNavBackStack(navConfig, Leaderboard)
    BrowserNavigationSync(backStack)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // One-shot: restores a returning user's session from persisted tokens, if
    // any (see AuthRepository.restoreSession). The LaunchedEffect(authState)
    // below reacts to the resulting state change and routes accordingly.
    LaunchedEffect(Unit) {
        auth.restoreSession()
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.LoggedIn -> {
                // Only an explicit login routes to the role's home. A restored
                // session preserves the route the URL/back stack already
                // resolved to, so a reload or direct link stays put instead of
                // snapping to Leaderboard. (With no hash the initial stack is
                // already [Leaderboard], so a returning user opening the bare
                // site still lands there.)
                if (state.source == LoginSource.EXPLICIT) {
                    val home = if (state.role == "admin") AdminHome else TeacherHome
                    if (backStack.lastOrNull() != home) {
                        backStack.clear()
                        backStack.add(home)
                    }
                }
            }

            AuthState.LoggedOut -> {
                val current = backStack.lastOrNull()
                if (current is TeacherHome || current is AdminHome || current is Profile) {
                    backStack.clear()
                    backStack.add(Leaderboard)
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
                val title = if (currentScreen is History) {
                    when (currentScreen.filter) {
                        HistoryFilterSelection.All -> stringResource(Res.string.history_title)
                        else -> historyFilterName?.let { stringResource(Res.string.history_title_filtered, it) }
                            ?: stringResource(Res.string.history_title) // not resolved yet (rare, brief)
                    }
                } else {
                    stringResource(currentScreen?.titleRes ?: Res.string.app_name)
                }
                AppTopBar(
                    title = title,
                    canPop = backStack.size > 1,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    actions = {
                        if (currentScreen is Leaderboard) LeaderboardTopBarActions()
                        if (currentScreen is History) {
                            HistoryTopBarActions(
                                selection = currentScreen.filter,
                                onSelect = { selection -> backStack[backStack.lastIndex] = History(selection) },
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                // Classement wants the full window width (e.g. to fit more
                // grid columns); every other screen keeps the readable-width cap.
                val contentWidthModifier = if (currentScreen is Leaderboard) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.widthIn(max = 840.dp)
                }
                NavDisplay(
                    backStack = backStack,
                    modifier = contentWidthModifier.fillMaxSize(),
                    onBack = { backStack.removeLastOrNull() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        entry<Login> { LoginScreen() }
                        entry<History> { key -> HistoryScreen(key.filter) }
                        entry<Leaderboard> {
                            LeaderboardScreen(onTeamClick = { team ->
                                backStack.add(History(HistoryFilterSelection.ByTeam(team.id)))
                            })
                        }
                        entry<TeacherHome> { TeacherScreen() }
                        entry<AdminHome> { AdminScreen() }
                        entry<Profile> { ProfileScreen() }
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
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
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
        actions = actions,
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
            selected = currentScreen == Leaderboard,
            onClick = { onNavigate(Leaderboard) },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.history_title)) },
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
            selected = currentScreen is History,
            onClick = { onNavigate(History()) },
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
                    label = { Text(stringResource(Profile.titleRes)) },
                    icon = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                    selected = currentScreen == Profile,
                    onClick = { onNavigate(Profile) },
                )
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
