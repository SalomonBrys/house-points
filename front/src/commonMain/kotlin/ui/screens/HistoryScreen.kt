import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import team_points.front.generated.resources.Res
import team_points.front.generated.resources.action_cancel
import team_points.front.generated.resources.action_delete
import team_points.front.generated.resources.action_retry
import team_points.front.generated.resources.date_day_names
import team_points.front.generated.resources.date_month_names
import team_points.front.generated.resources.error_load_events
import team_points.front.generated.resources.error_void_event
import team_points.front.generated.resources.history_confirm_delete_message
import team_points.front.generated.resources.history_confirm_delete_title
import team_points.front.generated.resources.history_empty
import team_points.front.generated.resources.history_event_datetime
import team_points.front.generated.resources.history_filter_all
import team_points.front.generated.resources.history_filter_description
import team_points.front.generated.resources.history_filter_teams_section
import team_points.front.generated.resources.history_filter_teachers_section
import team_points.front.generated.resources.history_load_more
import team_points.front.generated.resources.public_display_refresh_description
import team_points.front.generated.resources.public_load_error
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.char
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.kodein.di.compose.localDI
import org.kodein.di.direct
import org.kodein.di.instance
import kotlin.time.Duration.Companion.minutes

/**
 * Which single dimension, if any, the history list is currently narrowed to.
 * Holds ids rather than the full [Team]/[Teacher] objects: this selection is
 * carried on the [History] nav key itself and encoded in the browser URL on
 * web (`BrowserNavigationSync`), whose `restoreKey` is synchronous and so
 * can't fetch an object over the network while parsing — only an id
 * round-trips without I/O. The display name (for the top-bar title) is
 * resolved separately; see [HistoryFilter.selectionName].
 */
@Serializable
sealed interface HistoryFilterSelection {
    @Serializable
    data object All : HistoryFilterSelection

    @Serializable
    data class ByTeam(val teamId: Int) : HistoryFilterSelection

    @Serializable
    data class ByTeacher(val teacherId: Int) : HistoryFilterSelection
}

/**
 * Resolves the display name for [HistoryScreen]'s current team/teacher filter
 * (carried on the [History] nav key), shared with its top-bar control
 * ([HistoryTopBarActions]) via a DI singleton — the two composables are
 * siblings under [AppRoot] (which also reads this to append the filtered name
 * to the shared top bar's title), not parent/child, so nav-entry scoped
 * `ViewModel` state can't reach both. In-memory only, for the app's lifetime,
 * like [LeaderboardConfig] — no persistence needed.
 *
 * Also doubles as the reload bridge between [HistoryTopBarActions] (the
 * Refresh button) and [HistoryScreen]'s [HistoryViewModel], for the same
 * sibling-composable reason — see [LeaderboardConfig.refreshRequests] /
 * [LeaderboardConfig.isLoading] for the pattern this mirrors.
 */
class HistoryFilter {
    // Resolved display name for the current selection — null for [HistoryFilterSelection.All],
    // or briefly null right after a URL-restored by-id selection until
    // [HistoryTopBarActions] (which already fetches the active teams/teachers
    // lists for its dropdown) resolves it. AppRoot's title reads this instead
    // of duplicating that fetch.
    private val _selectionName = MutableStateFlow<String?>(null)
    val selectionName: StateFlow<String?> = _selectionName.asStateFlow()

    fun setSelectionName(name: String?) {
        _selectionName.value = name
    }

    // One-shot event (not state): buffered so a click isn't lost if the
    // screen composable hasn't (re)subscribed yet.
    private val _refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshRequests: SharedFlow<Unit> = _refreshRequests.asSharedFlow()

    // Mirrors the ViewModel's loading state so the top bar can swap the
    // reload button for a spinner.
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun requestRefresh() {
        _refreshRequests.tryEmit(Unit)
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
}

class HistoryViewModel(
    private val eventsRepository: EventsRepository,
    private val selection: HistoryFilterSelection,
) : ViewModel() {
    // Null means "never successfully loaded yet" — once populated, a failed
    // refresh does NOT clear it, so the last good list stays on screen
    // undisturbed until a new fetch actually succeeds. Newest-first, per the
    // `GET /api/events` contract.
    private val _events = MutableStateFlow<List<PointEvent>?>(null)
    val events: StateFlow<List<PointEvent>?> = _events.asStateFlow()

    private val _canLoadMore = MutableStateFlow(false)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // True while a full refresh (initial load or reload) is in flight — drives
    // the top bar's spinner, mirrored up into HistoryFilter.isLoading.
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // Surfaces a failed deleteEvent() without touching errorMessage (which
    // gates the full-screen error state) or the list itself.
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    // Cursor for the next (older) page, per the `next_id`/`before_id` keyset
    // pagination contract of `GET /api/events` — null once there is nothing older left.
    // Only touched by the initial load and loadMore(); refresh()'s merge pass
    // never moves this, so already-loaded pages stay put.
    private var nextId: Int? = null

    init {
        // The filter is now part of the nav key (see History.filter), so a
        // filter change creates a new back-stack entry and thus a fresh
        // ViewModel instance — no need to observe the filter for later changes.
        refresh()
        // History is meant to be left open for a while (like Classement), so
        // it keeps itself fresh on its own. viewModelScope is cancelled when
        // the nav entry is destroyed, which stops this loop — no manual
        // cleanup needed.
        viewModelScope.launch {
            while (isActive) {
                delay(RELOAD_INTERVAL)
                refresh()
            }
        }
    }

    /**
     * Initial load, or reload: if nothing has ever loaded successfully yet,
     * fetches the first page as-is. Otherwise merges newer-than-what's-shown
     * events onto the top of the existing list, walking forward page by page
     * to bridge a burst larger than one page — the already-loaded "Load more"
     * pages and the bottom cursor ([nextId]) are left untouched, so scroll
     * position and pagination state survive a reload.
     */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val existing = _events.value
                if (existing == null) {
                    val page = fetchPage(beforeId = null)
                    nextId = page.nextId
                    _events.value = page.events
                    _canLoadMore.value = page.nextId != null
                } else {
                    _events.value = mergeNewest(existing)
                }
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: getString(Res.string.error_load_events)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun mergeNewest(existing: List<PointEvent>): List<PointEvent> {
        val currentTopId = existing.firstOrNull()?.id ?: return existing
        val newEvents = mutableListOf<PointEvent>()
        var cursor: Int? = null
        while (true) {
            val page = fetchPage(beforeId = cursor)
            val freshInPage = page.events.filter { it.id > currentTopId }
            newEvents += freshInPage
            // Stop once this page ran into events we already had, or there's
            // nothing further to fetch.
            if (freshInPage.size < page.events.size || page.nextId == null) break
            cursor = page.nextId
        }
        return if (newEvents.isEmpty()) existing else newEvents + existing
    }

    fun loadMore() {
        val current = _events.value ?: return
        val cursor = nextId ?: return
        if (_isLoadingMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = fetchPage(beforeId = cursor)
                nextId = page.nextId
                _events.value = current + page.events
                _canLoadMore.value = page.nextId != null
                _errorMessage.value = null
            } catch (e: Exception) {
                // Keep the already-loaded events on screen; only surface the
                // error, rather than replacing a populated list with Error.
                _errorMessage.value = e.message ?: getString(Res.string.error_load_events)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun retry() {
        refresh()
    }

    /**
     * Deletes an event the caller owns (or is admin for) — `DELETE
     * /api/events/{id}`, enforced server-side (`PointEventsController::destroy`:
     * 403 if neither owner nor admin). The row is removed locally on success
     * rather than triggering a full reload, so scroll position and the
     * "Load more" cursor are undisturbed.
     */
    fun deleteEvent(eventId: Int) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                eventsRepository.deleteEvent(eventId)
                _events.value = _events.value?.filterNot { it.id == eventId }
            } catch (e: Exception) {
                _actionError.value = e.message ?: getString(Res.string.error_void_event)
            }
        }
    }

    private suspend fun fetchPage(beforeId: Int?) = when (val current = selection) {
        HistoryFilterSelection.All -> eventsRepository.listPaginated(beforeId = beforeId)
        is HistoryFilterSelection.ByTeam -> eventsRepository.listPaginated(beforeId = beforeId, teamId = current.teamId)
        is HistoryFilterSelection.ByTeacher -> eventsRepository.listPaginated(beforeId = beforeId, teacherId = current.teacherId)
    }

    companion object {
        private val RELOAD_INTERVAL = 1.minutes
    }
}

/**
 * Public event history — `GET /api/events`, no auth (`SPECS.md §6`). Newest
 * first, keyset-paginated via a "load more" button, optionally narrowed to a
 * single team or teacher via [HistoryFilter]/[HistoryTopBarActions]. Chrome
 * (top bar/drawer) lives in [AppRoot], this is content-only.
 */
@Composable
fun HistoryScreen(selection: HistoryFilterSelection) {
    val di = localDI()
    val viewModel = viewModel { HistoryViewModel(di.direct.instance(), selection) }
    val events by viewModel.events.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val filter = di.direct.instance<HistoryFilter>()

    // Who's logged in, if anyone — gates the delete button to the caller's
    // own events (History is a public route, so myId is often null).
    val session = di.direct.instance<Session>()
    val authState by session.state.collectAsState()
    val myId = (authState as? AuthState.LoggedIn)?.userId
    var pendingDelete by remember { mutableStateOf<PointEvent?>(null) }

    LaunchedEffect(filter, viewModel) {
        filter.refreshRequests.collect { viewModel.refresh() }
    }
    LaunchedEffect(filter, isLoading) {
        filter.setLoading(isLoading)
    }

    Box(Modifier.fillMaxSize()) {
        val currentEvents = events
        val currentError = errorMessage
        when {
            // Never loaded successfully yet and it just failed — nothing else
            // to show, so this is the one case that still surfaces an error.
            currentEvents == null && currentError != null -> Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                Text(
                    stringResource(Res.string.public_load_error, currentError),
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = { viewModel.retry() }) { Text(stringResource(Res.string.action_retry)) }
            }

            // First load still in flight: no centered spinner (the top bar
            // already shows one) — just nothing to render yet.
            currentEvents == null -> Unit

            currentEvents.isEmpty() -> Text(stringResource(Res.string.history_empty), modifier = Modifier.align(Alignment.Center))

            else -> {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    actionError?.let { message ->
                        item {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    items(currentEvents, key = { it.id }) { event ->
                        EventRow(
                            event,
                            onDelete = if (myId != null && event.teacherId == myId) {
                                { pendingDelete = event }
                            } else {
                                null
                            },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (canLoadMore) {
                        item {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(Modifier.padding(16.dp))
                                } else {
                                    Button(onClick = { viewModel.loadMore() }) {
                                        Text(stringResource(Res.string.history_load_more))
                                    }
                                }
                            }
                        }
                    }
                }
                EndVerticalScrollbar(rememberScrollbarAdapter(listState))
            }
        }
    }

    pendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.history_confirm_delete_title)) },
            text = { Text(stringResource(Res.string.history_confirm_delete_message)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteEvent(event.id)
                    pendingDelete = null
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                Button(onClick = { pendingDelete = null }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun EventRow(event: PointEvent, onDelete: (() -> Unit)?, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val containerColor = when {
        event.points > 0 && isDark -> Color(0xFF23361F)
        event.points > 0 -> Color(0xFFD7F2D9)
        isDark -> Color(0xFF3A2222)
        else -> Color(0xFFF7D9D9)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    formatEventTimestamp(event.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(event.comment)
            }
            // Only the event's own author (or an admin, per the backend) can
            // delete it; HistoryScreen passes null when the current user
            // doesn't own this event, so no button renders for anyone else.
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.action_delete))
                }
            }
        }
    }
}

// Backend sends MySQL DATETIME, e.g. "2026-07-23 14:02:11".
private val EVENT_TIMESTAMP_FORMAT = LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); dayOfMonth()
    char(' '); hour(); char(':'); minute(); char(':'); second()
}

/**
 * Renders an event's `created_at` as e.g. "Vendredi 24 juillet à 10h45". Only
 * numeric parsing is done in code; the day/month names and the assembly
 * template are compose resources (`date_day_names`/`date_month_names`/
 * `history_event_datetime`), per this app's all-strings-are-resources
 * convention (`front/ARCHITECTURE.md`).
 */
@Composable
private fun formatEventTimestamp(raw: String): String {
    val parsed = remember(raw) { runCatching { EVENT_TIMESTAMP_FORMAT.parse(raw) }.getOrNull() }
        ?: return raw // unexpected format -> show the raw string rather than crash
    val dayNames = stringArrayResource(Res.array.date_day_names)
    val monthNames = stringArrayResource(Res.array.date_month_names)
    // stringArrayResource can transiently return an empty list while the
    // resource is still loading (observed live) — fall back to the raw
    // string rather than crash on an out-of-bounds index; a recomposition
    // once the array is actually loaded renders the formatted version.
    val dayName = dayNames.getOrNull(parsed.dayOfWeek.isoDayNumber - 1) ?: return raw // isoDayNumber: 1=Mon..7=Sun
    val monthName = monthNames.getOrNull(parsed.monthNumber - 1) ?: return raw
    return stringResource(
        Res.string.history_event_datetime,
        dayName,
        parsed.dayOfMonth,
        monthName,
        parsed.hour,
        parsed.minute.toString().padStart(2, '0'),
    )
}

/**
 * Filter control for [HistoryScreen], rendered by [AppRoot] inside the shared
 * top bar (see the class doc on [HistoryFilter]). [selection] is the current
 * filter — carried on the [History] nav key, owned by [AppRoot] — and
 * [onSelect] requests a new one; picking a filter navigates to a new
 * [History] entry rather than mutating shared state, so the change is
 * reflected in the browser URL. Fetches the active teams and teachers itself,
 * purely to populate the dropdown's options.
 */
@Composable
fun HistoryTopBarActions(selection: HistoryFilterSelection, onSelect: (HistoryFilterSelection) -> Unit) {
    val di = localDI()
    val filter = di.direct.instance<HistoryFilter>()
    val teamsRepository = di.direct.instance<TeamsRepository>()
    val usersRepository = di.direct.instance<UsersRepository>()

    var teams by remember { mutableStateOf<List<Team>>(emptyList()) }
    var teachers by remember { mutableStateOf<List<Teacher>>(emptyList()) }
    LaunchedEffect(Unit) {
        // Best-effort: if either fetch fails, its section is simply left
        // empty — "Tout afficher" remains available regardless.
        runCatching { teams = teamsRepository.listActive() }
        runCatching { teachers = usersRepository.listTeachers() }
    }

    // Publishes the resolved display name for AppRoot's title (see
    // HistoryFilter.selectionName) once both the current selection and the
    // fetched lists are available. Re-runs if either changes.
    LaunchedEffect(selection, teams, teachers) {
        filter.setSelectionName(
            when (val current = selection) {
                HistoryFilterSelection.All -> null
                is HistoryFilterSelection.ByTeam -> teams.firstOrNull { it.id == current.teamId }?.name
                is HistoryFilterSelection.ByTeacher -> teachers.firstOrNull { it.id == current.teacherId }?.displayName
            }
        )
    }

    val isLoading by filter.isLoading.collectAsState()
    if (isLoading) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    } else {
        IconButton(onClick = { filter.requestRefresh() }) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(Res.string.public_display_refresh_description))
        }
    }

    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.FilterList, contentDescription = stringResource(Res.string.history_filter_description))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.history_filter_all)) },
                leadingIcon = if (selection == HistoryFilterSelection.All) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    onSelect(HistoryFilterSelection.All)
                    expanded = false
                },
            )
            if (teams.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    stringResource(Res.string.history_filter_teams_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                teams.forEach { team ->
                    DropdownMenuItem(
                        text = { Text(team.name) },
                        leadingIcon = if (selection == HistoryFilterSelection.ByTeam(team.id)) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            onSelect(HistoryFilterSelection.ByTeam(team.id))
                            expanded = false
                        },
                    )
                }
            }
            if (teachers.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    stringResource(Res.string.history_filter_teachers_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                teachers.forEach { teacher ->
                    DropdownMenuItem(
                        text = { Text(teacher.displayName) },
                        leadingIcon = if (selection == HistoryFilterSelection.ByTeacher(teacher.id)) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            onSelect(HistoryFilterSelection.ByTeacher(teacher.id))
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
