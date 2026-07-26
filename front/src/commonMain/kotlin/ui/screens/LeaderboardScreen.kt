import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import team_points.front.generated.resources.Res
import team_points.front.generated.resources.action_retry
import team_points.front.generated.resources.crown
import team_points.front.generated.resources.error_load_teams
import team_points.front.generated.resources.history_empty
import team_points.front.generated.resources.team_points
import team_points.front.generated.resources.public_display_columns_decrease
import team_points.front.generated.resources.public_display_columns_increase
import team_points.front.generated.resources.public_display_crown_toggle
import team_points.front.generated.resources.public_display_font_decrease
import team_points.front.generated.resources.public_display_font_increase
import team_points.front.generated.resources.public_display_history_toggle
import team_points.front.generated.resources.public_display_refresh_description
import team_points.front.generated.resources.public_display_settings_show
import team_points.front.generated.resources.public_display_sort_by_name
import team_points.front.generated.resources.public_display_sort_by_points
import team_points.front.generated.resources.public_display_sort_description
import team_points.front.generated.resources.public_load_error
import team_points.front.generated.resources.public_no_teams
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.kodein.di.compose.localDI
import org.kodein.di.direct
import org.kodein.di.instance
import kotlin.time.Duration.Companion.minutes

enum class TeamSortOrder { NAME, POINTS }

/** Base size of [TeamCard]'s image, scaled by [LeaderboardConfig.fontScale] like its text. */
private val TeamCardImageSize = 96.dp

/**
 * User-adjustable display settings for [LeaderboardScreen], shared with its
 * top-bar controls ([LeaderboardTopBarActions]) via a DI singleton — the two
 * composables are siblings under [AppRoot], not parent/child, so nav-entry
 * scoped `ViewModel` state (as used elsewhere) can't reach both. In-memory
 * only, for the app's lifetime, like other lightweight client state
 * ([Session], [TokenStore]) — no persistence needed for a display preference.
 */
class LeaderboardConfig {
    private val _sortOrder = MutableStateFlow(TeamSortOrder.POINTS)
    val sortOrder: StateFlow<TeamSortOrder> = _sortOrder.asStateFlow()

    private val _columns = MutableStateFlow(MIN_COLUMNS)
    val columns: StateFlow<Int> = _columns.asStateFlow()

    private val _fontScale = MutableStateFlow(1f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    // Off by default: the crown (and the "winner alone on top" centering it
    // gates — see buildLeaderboardEntries) is an opt-in display flourish.
    private val _showCrown = MutableStateFlow(false)
    val showCrown: StateFlow<Boolean> = _showCrown.asStateFlow()

    // True once the crown's entrance animation has played for the current
    // "on" streak. Lives here rather than as local `remember` state in the
    // Crown composable because this config (a DI singleton) survives
    // navigating away from Classement and back, while the composable
    // subtree doesn't — see consumeCrownEntranceAnimation().
    private var crownAnimationPlayed = false

    // Off by default: the history pane is an opt-in extra, and the projector
    // use case usually wants the full window for the grid.
    private val _showHistory = MutableStateFlow(false)
    val showHistory: StateFlow<Boolean> = _showHistory.asStateFlow()

    // One-shot event (not state): the top bar's reload button and the screen's
    // data-fetching ViewModel are siblings under AppRoot, so this is how the
    // former asks the latter to refresh. Buffered so a request isn't lost if
    // the screen composable hasn't (re)subscribed yet.
    private val _refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshRequests: SharedFlow<Unit> = _refreshRequests.asSharedFlow()

    // Mirrors the ViewModel's loading state so the top bar can swap the
    // reload button for a spinner — same sibling-composable reason as above.
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setSortOrder(order: TeamSortOrder) {
        _sortOrder.value = order
    }

    fun requestRefresh() {
        _refreshRequests.tryEmit(Unit)
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun incrementColumns() {
        _columns.update { it + 1 }
    }

    fun decrementColumns() {
        _columns.update { (it - 1).coerceAtLeast(MIN_COLUMNS) }
    }

    fun increaseFontScale() {
        _fontScale.update { it + SCALE_STEP }
    }

    fun decreaseFontScale() {
        _fontScale.update { (it - SCALE_STEP).coerceAtLeast(MIN_SCALE) }
    }

    fun toggleCrown() {
        val turnedOn = !_showCrown.value
        _showCrown.value = turnedOn
        // Reset so the next off→on toggle plays the entrance animation again.
        if (!turnedOn) crownAnimationPlayed = false
    }

    /**
     * Whether a [LeaderboardEntry.Crown] mounting right now should play its
     * entrance animation — true only the first time since the crown was last
     * turned on via [toggleCrown]. Every other mount (revisiting Classement
     * after navigating away, a data refresh recomposing the grid, etc. while
     * the crown stays on) should render already fully revealed instead of
     * replaying the animation.
     */
    fun consumeCrownEntranceAnimation(): Boolean {
        if (crownAnimationPlayed) return false
        crownAnimationPlayed = true
        return true
    }

    fun toggleHistory() {
        _showHistory.update { !it }
    }

    companion object {
        const val MIN_COLUMNS = 1
        const val MIN_SCALE = 0.5f
        const val SCALE_STEP = 0.25f
    }
}

class LeaderboardViewModel(
    private val teamsRepository: TeamsRepository,
    eventsRepository: EventsRepository,
) : ViewModel() {
    // Null means "never successfully loaded yet" — once populated, a failed
    // refresh does NOT clear it, so the last good list stays on screen
    // undisturbed until a new fetch actually succeeds.
    private val _teams = MutableStateFlow<List<Team>?>(null)
    val teams: StateFlow<List<Team>?> = _teams.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Unfiltered event feed backing the opt-in history pane (LeaderboardConfig.showHistory).
    // Only fetched while the pane is actually shown (see setHistoryEnabled) —
    // this screen often runs unattended for a long time, so there's no point
    // polling an endpoint nobody's looking at.
    val history = EventsFeed(viewModelScope, eventsRepository) { beforeId ->
        eventsRepository.listPaginated(beforeId = beforeId)
    }
    private var historyEnabled = false

    init {
        refresh()
        // Classement is meant to be left open (e.g. on a projector) for a
        // whole class or longer, so it keeps itself fresh on its own.
        // viewModelScope is cancelled when the nav entry is destroyed, which
        // stops this loop — no manual cleanup needed.
        viewModelScope.launch {
            while (isActive) {
                delay(RELOAD_INTERVAL)
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _teams.value = teamsRepository.listActive()
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: getString(Res.string.error_load_teams)
            } finally {
                _isLoading.value = false
            }
        }
        if (historyEnabled) history.refresh()
    }

    /** Toggling the pane on triggers an immediate load; toggling off just stops refreshing it. */
    fun setHistoryEnabled(enabled: Boolean) {
        val wasEnabled = historyEnabled
        historyEnabled = enabled
        if (enabled && !wasEnabled) history.refresh()
    }

    companion object {
        private val RELOAD_INTERVAL = 1.minutes
    }
}

/**
 * Public leaderboard — `GET /api/teams`, no auth. The app's default screen,
 * labeled "Classement" (`SPECS.md §6`); chrome (top bar/drawer) lives in
 * [AppRoot], this is content-only — including [LeaderboardTopBarActions],
 * which [AppRoot] renders inside the shared top bar only while this screen is
 * active. A fullscreen, auto-refreshing variant (polling `GET /api/events/since`)
 * remains a future follow-up.
 */
@Composable
fun LeaderboardScreen(onTeamClick: (Team) -> Unit) {
    val di = localDI()
    val viewModel = viewModel { LeaderboardViewModel(di.direct.instance(), di.direct.instance()) }
    val teams by viewModel.teams.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoadingVm by viewModel.isLoading.collectAsState()
    val config = di.direct.instance<LeaderboardConfig>()
    val sortOrder by config.sortOrder.collectAsState()
    val columns by config.columns.collectAsState()
    val fontScale by config.fontScale.collectAsState()
    val showCrown by config.showCrown.collectAsState()
    val showHistory by config.showHistory.collectAsState()
    val isHistoryLoading by viewModel.history.isLoading.collectAsState()

    LaunchedEffect(config, viewModel) {
        config.refreshRequests.collect { viewModel.refresh() }
    }
    LaunchedEffect(viewModel, showHistory) {
        viewModel.setHistoryEnabled(showHistory)
    }
    LaunchedEffect(config, isLoadingVm, isHistoryLoading, showHistory) {
        config.setLoading(isLoadingVm || (showHistory && isHistoryLoading))
    }

    val grid = @Composable {
        LeaderboardGrid(
            teams = teams,
            errorMessage = errorMessage,
            sortOrder = sortOrder,
            columns = columns,
            fontScale = fontScale,
            showCrown = showCrown,
            onRetry = { viewModel.refresh() },
            onTeamClick = onTeamClick,
            consumeCrownEntranceAnimation = config::consumeCrownEntranceAnimation,
        )
    }
    if (showHistory) {
        VerticalSplitPane(
            modifier = Modifier.fillMaxSize(),
            top = grid,
            bottom = { LeaderboardHistoryPane(viewModel.history) },
        )
    } else {
        grid()
    }
}

@Composable
private fun LeaderboardGrid(
    teams: List<Team>?,
    errorMessage: String?,
    sortOrder: TeamSortOrder,
    columns: Int,
    fontScale: Float,
    showCrown: Boolean,
    onRetry: () -> Unit,
    onTeamClick: (Team) -> Unit,
    consumeCrownEntranceAnimation: () -> Boolean,
) {
    Box(Modifier.fillMaxSize().padding(vertical = 8.dp, horizontal = 16.dp)) {
        val currentTeams = teams
        val currentError = errorMessage
        when {
            // Never loaded successfully yet and it just failed — nothing else
            // to show, so this is the one case that still surfaces an error.
            currentTeams == null && currentError != null -> Column(Modifier.align(Alignment.Center)) {
                Text(
                    stringResource(Res.string.public_load_error, currentError),
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
            }

            // First load still in flight: no centered spinner (the top bar
            // already shows one) — just nothing to render yet.
            currentTeams == null -> Unit

            currentTeams.isEmpty() -> Text(stringResource(Res.string.public_no_teams), modifier = Modifier.align(Alignment.Center))

            else -> {
                val sortedTeams = remember(currentTeams, sortOrder) {
                    when (sortOrder) {
                        TeamSortOrder.NAME -> currentTeams.sortedBy { it.name }
                        TeamSortOrder.POINTS -> currentTeams.sortedByDescending { it.totalPoints }
                    }
                }
                val entries = remember(sortedTeams, columns, sortOrder, showCrown) {
                    buildLeaderboardEntries(sortedTeams, columns, sortOrder, showCrown)
                }
                val gridState = rememberLazyGridState()
                // Toggling the crown on reshuffles row 1 (the new leader/tied
                // group centers there) — jump back to the top so that's
                // immediately visible rather than wherever the list happened
                // to be scrolled to. Toggling off doesn't need this: nothing
                // is "revealed" by hiding the crown.
                LaunchedEffect(showCrown) {
                    if (showCrown) gridState.scrollToItem(0)
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is LeaderboardEntry.Placeholder -> Box(Modifier)
                            is LeaderboardEntry.Crown -> Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(
                                        fadeInSpec = tween(1500),
                                        placementSpec = tween(1500),
                                        fadeOutSpec = tween(1500),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                // animateItem's fadeInSpec only animates an item
                                // appearing within an already-composed viewport —
                                // it doesn't fire when the item is simultaneously
                                // scrolled into view for the first time (as it
                                // always is here, since showing the crown also
                                // snaps to the top). Animate explicitly instead,
                                // driven by this composable's own mount rather
                                // than by LazyGrid's diffing, so it plays
                                // unconditionally. Height (not just alpha) is
                                // animated too — via Modifier.height, a real
                                // layout size, not a draw-time scale — so the
                                // row's occupied space grows in step with the
                                // fade instead of snapping to full height while
                                // only its content fades in.
                                //
                                // This composable also remounts (replaying the
                                // above) every time Classement itself is
                                // recomposed from scratch — e.g. navigating
                                // away and back — even though the crown was
                                // already showing. consumeCrownEntranceAnimation()
                                // (LeaderboardConfig, a DI singleton that
                                // survives that navigation) makes sure the
                                // animation only actually plays the first time
                                // since the crown was last turned on.
                                val shouldAnimate = remember { consumeCrownEntranceAnimation() }
                                val crownProgress = remember { Animatable(if (shouldAnimate) 0f else 1f) }
                                LaunchedEffect(Unit) {
                                    if (shouldAnimate) crownProgress.animateTo(1f, tween(1500))
                                }
                                val width = TeamCardImageSize * 1.33f * fontScale
                                val height = width * 0.7454545f
                                Image(
                                    painter = painterResource(Res.drawable.crown),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .width(width)
                                        .height(height * crownProgress.value)
                                        .alpha(crownProgress.value),
                                )
                            }
                            is LeaderboardEntry.TeamEntry -> TeamCard(
                                team = entry.team,
                                fontScale = fontScale,
                                onClick = { onTeamClick(entry.team) },
                                modifier = Modifier.animateItem(
                                    placementSpec = tween(1500)
                                )
                            )
                        }
                    }
                }
                EndVerticalScrollbar(rememberScrollbarAdapter(gridState))
            }
        }
    }
}

/**
 * Read-only, unfiltered event feed shown in [LeaderboardScreen]'s optional
 * bottom pane (see [LeaderboardConfig.showHistory]) — same row rendering as
 * [HistoryScreen] ([EventRow]), but never a delete button and no "load more":
 * this is a glanceable "what just happened" feed, not a full history browser.
 */
@Composable
private fun LeaderboardHistoryPane(feed: EventsFeed) {
    val events by feed.events.collectAsState()
    val errorMessage by feed.errorMessage.collectAsState()

    Box(Modifier.fillMaxSize()) {
        val currentEvents = events
        val currentError = errorMessage
        when {
            // Never loaded successfully yet and it just failed — nothing else
            // to show, so this is the one case that still surfaces an error.
            currentEvents == null && currentError != null -> Text(
                stringResource(Res.string.public_load_error, currentError),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )

            // First load still in flight: the top bar already shows a
            // spinner — just nothing to render yet.
            currentEvents == null -> Unit

            currentEvents.isEmpty() -> Text(stringResource(Res.string.history_empty), modifier = Modifier.align(Alignment.Center))

            else -> {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(currentEvents, key = { it.id }) { event ->
                        EventRow(event, onDelete = null, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                EndVerticalScrollbar(rememberScrollbarAdapter(listState))
            }
        }
    }
}

@Composable
private fun TeamCard(team: Team, fontScale: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TeamImage(
                imageUrl = team.imageUrl(),
                size = TeamCardImageSize * fontScale,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = team.name,
                style = MaterialTheme.typography.titleLarge,
                fontSize = MaterialTheme.typography.titleLarge.fontSize * fontScale,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.team_points, team.totalPoints),
                style = MaterialTheme.typography.headlineMedium,
                fontSize = MaterialTheme.typography.headlineMedium.fontSize * fontScale,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One entry of [LeaderboardScreen]'s grid: a real team, the crown that
 * always sits above the first team, or a blank cell used to center either of
 * those on its own row (see [buildLeaderboardEntries]). `key` feeds
 * `items(entries, key = { it.key })` — team keys are the (Int) team id, the
 * crown and placeholder keys are distinct Strings, so all three coexist
 * safely as `LazyVerticalGrid` item keys.
 */
private sealed interface LeaderboardEntry {
    val key: Any

    /**
     * A constant key at a list position determined only by [columns] (never
     * by which team is #1) is what makes the crown never move on a leader
     * change — see [buildLeaderboardEntries].
     */
    data object Crown : LeaderboardEntry {
        override val key: Any get() = "crown"
    }

    data class TeamEntry(val team: Team) : LeaderboardEntry {
        override val key: Any get() = team.id
    }

    data class Placeholder(override val key: String) : LeaderboardEntry
}

/**
 * Lays [sortedTeams] out for the grid. When [showCrown] is off (the
 * default), this is a no-op layout-wise: every team is just a plain
 * [LeaderboardEntry.TeamEntry] in order, flowing/wrapping normally with no
 * special row-1 treatment.
 *
 * When [showCrown] is on, whoever's in first place is centered alone on row
 * 1 with a [LeaderboardEntry.Crown] fixed above them — *unless* multiple
 * teams are tied for first, in which case there's no single leader to
 * crown: the crown is simply omitted (which, since it keeps the stable key
 * `"crown"`, makes `Modifier.animateItem` fade it away rather than snapping
 * it out — see the class doc on [LeaderboardEntry.Crown]), and every tied
 * team is centered together on row 1 instead — but only if they actually
 * fit within `columns`; if not, no special row-1 treatment happens at all
 * and they simply flow into the grid like any other team.
 *
 * "Tied for first" is the leading run of [sortedTeams] sharing the same
 * value as `sortedTeams.first()` on whichever field the list is currently
 * sorted by (points, descending — the practically relevant case — or name,
 * ascending, a rare edge case since team names aren't unique). Centering
 * uses the same placeholder-cell trick throughout, generalized from a group
 * of 1 (the single-leader case) to a group of N: `columns - N`
 * [LeaderboardEntry.Placeholder] cells split evenly left/right when that's
 * even, with one extra on the right when it's odd (no exact center exists
 * then). Whichever teams aren't part of the tied group follow as ordinary
 * entries and flow/wrap normally starting row 2.
 */
private fun buildLeaderboardEntries(
    sortedTeams: List<Team>,
    columns: Int,
    sortOrder: TeamSortOrder,
    showCrown: Boolean,
): List<LeaderboardEntry> {
    if (sortedTeams.isEmpty()) return emptyList()
    if (!showCrown) return sortedTeams.map { LeaderboardEntry.TeamEntry(it) }

    val first = sortedTeams.first()
    val tiedForFirst = when (sortOrder) {
        TeamSortOrder.POINTS -> sortedTeams.takeWhile { it.totalPoints == first.totalPoints }
        TeamSortOrder.NAME -> sortedTeams.takeWhile { it.name == first.name }
    }
    val remaining = sortedTeams.drop(tiedForFirst.size)

    return buildList {
        when {
            // Sole leader: today's crown + single-centered-team behavior.
            tiedForFirst.size == 1 -> {
                val leftCount = (columns - 1) / 2
                val rightCount = (columns - 1) - leftCount
                repeat(leftCount) { add(LeaderboardEntry.Placeholder("crown-leading-$it")) }
                add(LeaderboardEntry.Crown)
                repeat(rightCount) { add(LeaderboardEntry.Placeholder("crown-trailing-$it")) }
                repeat(leftCount) { add(LeaderboardEntry.Placeholder("placeholder-leading-$it")) }
                add(LeaderboardEntry.TeamEntry(first))
                repeat(rightCount) { add(LeaderboardEntry.Placeholder("placeholder-trailing-$it")) }
            }
            // Tie that fits on one line: no crown, whole tied group centered together.
            tiedForFirst.size <= columns -> {
                val leftCount = (columns - tiedForFirst.size) / 2
                val rightCount = (columns - tiedForFirst.size) - leftCount
                repeat(leftCount) { add(LeaderboardEntry.Placeholder("placeholder-leading-$it")) }
                tiedForFirst.forEach { add(LeaderboardEntry.TeamEntry(it)) }
                repeat(rightCount) { add(LeaderboardEntry.Placeholder("placeholder-trailing-$it")) }
            }
            // Tie doesn't fit on one line: no crown, no special centering — flows normally.
            else -> tiedForFirst.forEach { add(LeaderboardEntry.TeamEntry(it)) }
        }
        remaining.forEach { add(LeaderboardEntry.TeamEntry(it)) }
    }
}

/**
 * Sort/columns/font-size controls for [LeaderboardScreen], rendered by
 * [AppRoot] inside the shared top bar (see the class doc on [LeaderboardConfig]).
 */
@Composable
fun LeaderboardTopBarActions() {
    val di = localDI()
    val config = di.direct.instance<LeaderboardConfig>()
    val sortOrder by config.sortOrder.collectAsState()
    val columns by config.columns.collectAsState()
    val fontScale by config.fontScale.collectAsState()
    val showCrown by config.showCrown.collectAsState()
    val showHistory by config.showHistory.collectAsState()
    val isLoading by config.isLoading.collectAsState()
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { sortMenuExpanded = true }) {
            Icon(Icons.Filled.Sort, contentDescription = stringResource(Res.string.public_display_sort_description))
        }
        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.public_display_sort_by_name)) },
                leadingIcon = if (sortOrder == TeamSortOrder.NAME) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    config.setSortOrder(TeamSortOrder.NAME)
                    sortMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.public_display_sort_by_points)) },
                leadingIcon = if (sortOrder == TeamSortOrder.POINTS) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    config.setSortOrder(TeamSortOrder.POINTS)
                    sortMenuExpanded = false
                },
            )
        }
    }
    if (isLoading) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    } else {
        IconButton(onClick = { config.requestRefresh() }) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(Res.string.public_display_refresh_description))
        }
    }
    Box {
        IconButton(onClick = { settingsExpanded = true }) {
            Icon(Icons.Filled.Tune, contentDescription = stringResource(Res.string.public_display_settings_show))
        }
        // A real popup (DropdownMenu renders via Popup — a separate overlay
        // layer, elevated above the top app bar) rather than inline actions,
        // so it floats over the bar instead of stretching it.
        DropdownMenu(expanded = settingsExpanded, onDismissRequest = { settingsExpanded = false }) {
            Row {
                IconButton(onClick = config::decrementColumns, enabled = columns > LeaderboardConfig.MIN_COLUMNS) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(Res.string.public_display_columns_decrease))
                }
                IconButton(onClick = config::incrementColumns) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.public_display_columns_increase))
                }
                IconButton(onClick = config::decreaseFontScale, enabled = fontScale > LeaderboardConfig.MIN_SCALE) {
                    Icon(Icons.Filled.TextDecrease, contentDescription = stringResource(Res.string.public_display_font_decrease))
                }
                IconButton(onClick = config::increaseFontScale) {
                    Icon(Icons.Filled.TextIncrease, contentDescription = stringResource(Res.string.public_display_font_increase))
                }
                IconButton(onClick = config::toggleCrown) {
                    Icon(
                        Icons.Filled.Diamond,
                        contentDescription = stringResource(Res.string.public_display_crown_toggle),
                        modifier = Modifier.alpha(if (showCrown) 1f else 0.38f),
                    )
                }
                IconButton(onClick = config::toggleHistory) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = stringResource(Res.string.public_display_history_toggle),
                        modifier = Modifier.alpha(if (showHistory) 1f else 0.38f),
                    )
                }
            }
        }
    }
}
