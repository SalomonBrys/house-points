import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.action_retry
import house_points.front.generated.resources.error_load_houses
import house_points.front.generated.resources.house_points
import house_points.front.generated.resources.public_display_columns_decrease
import house_points.front.generated.resources.public_display_columns_increase
import house_points.front.generated.resources.public_display_font_decrease
import house_points.front.generated.resources.public_display_font_increase
import house_points.front.generated.resources.public_display_refresh_description
import house_points.front.generated.resources.public_display_settings_show
import house_points.front.generated.resources.public_display_sort_by_name
import house_points.front.generated.resources.public_display_sort_by_points
import house_points.front.generated.resources.public_display_sort_description
import house_points.front.generated.resources.public_load_error
import house_points.front.generated.resources.public_no_houses
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
import org.jetbrains.compose.resources.stringResource
import org.kodein.di.compose.localDI
import org.kodein.di.direct
import org.kodein.di.instance
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class HouseSortOrder { NAME, POINTS }

/**
 * User-adjustable display settings for [LeaderboardScreen], shared with its
 * top-bar controls ([LeaderboardTopBarActions]) via a DI singleton — the two
 * composables are siblings under [AppRoot], not parent/child, so nav-entry
 * scoped `ViewModel` state (as used elsewhere) can't reach both. In-memory
 * only, for the app's lifetime, like other lightweight client state
 * ([Session], [TokenStore]) — no persistence needed for a display preference.
 */
class LeaderboardConfig {
    private val _sortOrder = MutableStateFlow(HouseSortOrder.POINTS)
    val sortOrder: StateFlow<HouseSortOrder> = _sortOrder.asStateFlow()

    private val _columns = MutableStateFlow(MIN_COLUMNS)
    val columns: StateFlow<Int> = _columns.asStateFlow()

    private val _fontScale = MutableStateFlow(1f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

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

    fun setSortOrder(order: HouseSortOrder) {
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

    companion object {
        const val MIN_COLUMNS = 1
        const val MIN_SCALE = 0.5f
        const val SCALE_STEP = 0.25f
    }
}

class LeaderboardViewModel(private val housesRepository: HousesRepository) : ViewModel() {
    // Null means "never successfully loaded yet" — once populated, a failed
    // refresh does NOT clear it, so the last good list stays on screen
    // undisturbed until a new fetch actually succeeds.
    private val _houses = MutableStateFlow<List<House>?>(null)
    val houses: StateFlow<List<House>?> = _houses.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
                _houses.value = housesRepository.listActive()
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: getString(Res.string.error_load_houses)
            } finally {
                _isLoading.value = false
            }
        }
    }

    companion object {
        private val RELOAD_INTERVAL = 1.minutes
    }
}

/**
 * Public leaderboard — `GET /api/houses`, no auth. The app's default screen,
 * labeled "Classement" (`SPECS.md §6`); chrome (top bar/drawer) lives in
 * [AppRoot], this is content-only — including [LeaderboardTopBarActions],
 * which [AppRoot] renders inside the shared top bar only while this screen is
 * active. A fullscreen, auto-refreshing variant (polling `GET /api/events/since`)
 * remains a future follow-up.
 */
@Composable
fun LeaderboardScreen() {
    val di = localDI()
    val viewModel = viewModel { LeaderboardViewModel(di.direct.instance()) }
    val houses by viewModel.houses.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoadingVm by viewModel.isLoading.collectAsState()
    val config = di.direct.instance<LeaderboardConfig>()
    val sortOrder by config.sortOrder.collectAsState()
    val columns by config.columns.collectAsState()
    val fontScale by config.fontScale.collectAsState()

    LaunchedEffect(config, viewModel) {
        config.refreshRequests.collect { viewModel.refresh() }
    }
    LaunchedEffect(config, isLoadingVm) {
        config.setLoading(isLoadingVm)
    }

    Box(Modifier.fillMaxSize().padding(16.dp)) {
        val currentHouses = houses
        val currentError = errorMessage
        when {
            // Never loaded successfully yet and it just failed — nothing else
            // to show, so this is the one case that still surfaces an error.
            currentHouses == null && currentError != null -> Column(Modifier.align(Alignment.Center)) {
                Text(
                    stringResource(Res.string.public_load_error, currentError),
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = { viewModel.refresh() }) { Text(stringResource(Res.string.action_retry)) }
            }

            // First load still in flight: no centered spinner (the top bar
            // already shows one) — just nothing to render yet.
            currentHouses == null -> Unit

            currentHouses.isEmpty() -> Text(stringResource(Res.string.public_no_houses), modifier = Modifier.align(Alignment.Center))

            else -> {
                val sortedHouses = remember(currentHouses, sortOrder) {
                    when (sortOrder) {
                        HouseSortOrder.NAME -> currentHouses.sortedBy { it.name }
                        HouseSortOrder.POINTS -> currentHouses.sortedByDescending { it.totalPoints }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(sortedHouses, key = { it.id }) { house ->
                        HouseCard(
                            house = house,
                            fontScale = fontScale,
                            modifier = Modifier.animateItem(
                                placementSpec = tween(1500)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HouseCard(house: House, fontScale: Float, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = house.name,
                style = MaterialTheme.typography.titleLarge,
                fontSize = MaterialTheme.typography.titleLarge.fontSize * fontScale,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.house_points, house.totalPoints),
                style = MaterialTheme.typography.headlineMedium,
                fontSize = MaterialTheme.typography.headlineMedium.fontSize * fontScale,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
                leadingIcon = if (sortOrder == HouseSortOrder.NAME) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    config.setSortOrder(HouseSortOrder.NAME)
                    sortMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.public_display_sort_by_points)) },
                leadingIcon = if (sortOrder == HouseSortOrder.POINTS) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    config.setSortOrder(HouseSortOrder.POINTS)
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
            }
        }
    }
}
