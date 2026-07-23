import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
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
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.action_retry
import house_points.front.generated.resources.error_load_events
import house_points.front.generated.resources.history_empty
import house_points.front.generated.resources.history_filter_all
import house_points.front.generated.resources.history_filter_description
import house_points.front.generated.resources.history_filter_houses_section
import house_points.front.generated.resources.history_filter_teachers_section
import house_points.front.generated.resources.history_load_more
import house_points.front.generated.resources.public_load_error
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.kodein.di.compose.localDI
import org.kodein.di.direct
import org.kodein.di.instance

/** Which single dimension, if any, the history list is currently narrowed to. */
sealed interface HistoryFilterSelection {
    data object All : HistoryFilterSelection
    data class ByHouse(val house: House) : HistoryFilterSelection
    data class ByTeacher(val teacher: Teacher) : HistoryFilterSelection
}

/**
 * Current house/teacher filter for [HistoryScreen], shared with its top-bar
 * control ([HistoryTopBarActions]) via a DI singleton — the two composables
 * are siblings under [AppRoot] (which also reads this to append the filtered
 * name to the shared top bar's title), not parent/child, so nav-entry scoped
 * `ViewModel` state can't reach both. In-memory only, for the app's lifetime,
 * like [LeaderboardConfig] — no persistence needed.
 */
class HistoryFilter {
    private val _selection = MutableStateFlow<HistoryFilterSelection>(HistoryFilterSelection.All)
    val selection: StateFlow<HistoryFilterSelection> = _selection.asStateFlow()

    fun set(selection: HistoryFilterSelection) {
        _selection.value = selection
    }
}

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Success(val events: List<PointEvent>, val canLoadMore: Boolean) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

class HistoryViewModel(
    private val events: EventsRepository,
    private val filter: HistoryFilter,
) : ViewModel() {
    private val _state = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // Cursor for the next (older) page, per the `next_id`/`before_id` keyset
    // pagination contract of `GET /api/events` — null once there is nothing older left.
    private var nextId: Int? = null
    private var currentSelection: HistoryFilterSelection = HistoryFilterSelection.All

    init {
        // A StateFlow replays its current value to a new collector, so this
        // both performs the initial load and reloads from scratch whenever
        // the shared filter changes.
        viewModelScope.launch {
            filter.selection.collect { selection ->
                currentSelection = selection
                reloadFromStart()
            }
        }
    }

    private suspend fun reloadFromStart() {
        _state.value = HistoryUiState.Loading
        nextId = null
        _state.value = try {
            val page = fetchPage(beforeId = null)
            nextId = page.nextId
            HistoryUiState.Success(page.events, canLoadMore = page.nextId != null)
        } catch (e: Exception) {
            HistoryUiState.Error(e.message ?: getString(Res.string.error_load_events))
        }
    }

    fun loadMore() {
        val current = _state.value as? HistoryUiState.Success ?: return
        val cursor = nextId ?: return
        if (_isLoadingMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = fetchPage(beforeId = cursor)
                nextId = page.nextId
                _state.value = HistoryUiState.Success(
                    events = current.events + page.events,
                    canLoadMore = page.nextId != null,
                )
            } catch (e: Exception) {
                // Keep the already-loaded events on screen; only surface the
                // error, rather than replacing a populated list with Error.
                _state.value = HistoryUiState.Error(e.message ?: getString(Res.string.error_load_events))
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun retry() {
        viewModelScope.launch { reloadFromStart() }
    }

    private suspend fun fetchPage(beforeId: Int?) = when (val selection = currentSelection) {
        HistoryFilterSelection.All -> events.listPaginated(beforeId = beforeId)
        is HistoryFilterSelection.ByHouse -> events.listPaginated(beforeId = beforeId, houseId = selection.house.id)
        is HistoryFilterSelection.ByTeacher -> events.listPaginated(beforeId = beforeId, teacherId = selection.teacher.id)
    }
}

/**
 * Public event history — `GET /api/events`, no auth (`SPECS.md §6`). Newest
 * first, keyset-paginated via a "load more" button, optionally narrowed to a
 * single house or teacher via [HistoryFilter]/[HistoryTopBarActions]. Chrome
 * (top bar/drawer) lives in [AppRoot], this is content-only.
 */
@Composable
fun HistoryScreen() {
    val di = localDI()
    val viewModel = viewModel { HistoryViewModel(di.direct.instance(), di.direct.instance()) }
    val state by viewModel.state.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            HistoryUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            is HistoryUiState.Error -> Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                Text(
                    stringResource(Res.string.public_load_error, current.message),
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = { viewModel.retry() }) { Text(stringResource(Res.string.action_retry)) }
            }

            is HistoryUiState.Success -> if (current.events.isEmpty()) {
                Text(stringResource(Res.string.history_empty), modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(current.events, key = { it.id }) { event ->
                        EventRow(event, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    if (current.canLoadMore) {
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
            }
        }
    }
}

@Composable
private fun EventRow(event: PointEvent, modifier: Modifier = Modifier) {
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
        Text(event.comment, modifier = Modifier.padding(16.dp))
    }
}

/**
 * Filter control for [HistoryScreen], rendered by [AppRoot] inside the shared
 * top bar (see the class doc on [HistoryFilter]). Fetches the active houses
 * and teachers itself, purely to populate the dropdown's options.
 */
@Composable
fun HistoryTopBarActions() {
    val di = localDI()
    val filter = di.direct.instance<HistoryFilter>()
    val selection by filter.selection.collectAsState()
    val housesRepository = di.direct.instance<HousesRepository>()
    val usersRepository = di.direct.instance<UsersRepository>()

    var houses by remember { mutableStateOf<List<House>>(emptyList()) }
    var teachers by remember { mutableStateOf<List<Teacher>>(emptyList()) }
    LaunchedEffect(Unit) {
        // Best-effort: if either fetch fails, its section is simply left
        // empty — "Tout afficher" remains available regardless.
        runCatching { houses = housesRepository.listActive() }
        runCatching { teachers = usersRepository.listTeachers() }
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
                    filter.set(HistoryFilterSelection.All)
                    expanded = false
                },
            )
            if (houses.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    stringResource(Res.string.history_filter_houses_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                houses.forEach { house ->
                    DropdownMenuItem(
                        text = { Text(house.name) },
                        leadingIcon = if (selection == HistoryFilterSelection.ByHouse(house)) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            filter.set(HistoryFilterSelection.ByHouse(house))
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
                        leadingIcon = if (selection == HistoryFilterSelection.ByTeacher(teacher)) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            filter.set(HistoryFilterSelection.ByTeacher(teacher))
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
