import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import team_points.front.generated.resources.Res
import team_points.front.generated.resources.action_cancel
import team_points.front.generated.resources.action_confirm
import team_points.front.generated.resources.action_delete
import team_points.front.generated.resources.action_retry
import team_points.front.generated.resources.action_validate
import team_points.front.generated.resources.error_load_events
import team_points.front.generated.resources.error_load_teams
import team_points.front.generated.resources.error_void_event
import team_points.front.generated.resources.history_confirm_delete_message
import team_points.front.generated.resources.history_confirm_delete_title
import team_points.front.generated.resources.history_load_more
import team_points.front.generated.resources.public_display_refresh_description
import team_points.front.generated.resources.public_load_error
import team_points.front.generated.resources.public_no_teams
import team_points.front.generated.resources.teacher_confirm_title
import team_points.front.generated.resources.teacher_history_empty
import team_points.front.generated.resources.teacher_history_title
import team_points.front.generated.resources.teacher_history_unknown_team
import team_points.front.generated.resources.teacher_team_label
import team_points.front.generated.resources.teacher_points_summary
import team_points.front.generated.resources.teacher_sign_add_description
import team_points.front.generated.resources.teacher_sign_remove_description
import team_points.front.generated.resources.welcome_message
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.kodein.di.compose.localDI
import org.kodein.di.direct
import org.kodein.di.instance
import kotlin.time.Duration.Companion.minutes

/** A soft green distinct from [MaterialTheme]'s error red, for "points added" feedback. */
private val AddColor = Color(0xFF2E7D32)

/** Font size shared by the sign glyph and the amount input, per the design (SPECS.md §6). */
private val AmountFontSize = 96.sp

private enum class PointsSign { ADD, REMOVE }

sealed interface TeacherUiState {
    data object Loading : TeacherUiState
    data class Success(val teams: List<Team>) : TeacherUiState
    data class Error(val message: String) : TeacherUiState
}

/**
 * Top-bar↔screen bridge for the Teacher screen's history refresh button,
 * mirroring [LeaderboardConfig]/[HistoryFilter]: [TeacherTopBarActions] (in
 * [AppRoot]'s shared top bar) and this screen's [TeacherViewModel] are
 * siblings, not parent/child, so this DI singleton carries the
 * request-refresh / is-loading signal between them. In-memory only, for the
 * app's lifetime — no persistence needed.
 */
class TeacherHistoryConfig {
    private val _refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshRequests: SharedFlow<Unit> = _refreshRequests.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun requestRefresh() {
        _refreshRequests.tryEmit(Unit)
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
}

class TeacherViewModel(
    private val teams: TeamsRepository,
    private val events: EventsRepository,
    private val teacherId: Int?,
) : ViewModel() {
    private val _state = MutableStateFlow<TeacherUiState>(TeacherUiState.Loading)
    val state: StateFlow<TeacherUiState> = _state.asStateFlow()

    // The authenticated teacher's own transaction history — `GET
    // /api/events?teacher_id=<me>`, newest first, keyset-paginated like
    // HistoryScreen. Null means "never loaded yet"; a failed refresh does not
    // clear an already-loaded list.
    private val _transactions = MutableStateFlow<List<PointEvent>?>(null)
    val transactions: StateFlow<List<PointEvent>?> = _transactions.asStateFlow()

    private val _canLoadMore = MutableStateFlow(false)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // Drives TeacherHistoryConfig's top-bar spinner — mirrors only the
    // history refresh, not the (one-shot) team list load below.
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    // Separate from actionError (which gates the submit-points feedback right
    // above the Validate button) so a history refresh/load-more/delete
    // failure surfaces next to the history list instead of duplicating the
    // same message in both places.
    private val _historyError = MutableStateFlow<String?>(null)
    val historyError: StateFlow<String?> = _historyError.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    // Cursor for the next (older) history page, per the `next_id`/`before_id`
    // keyset pagination contract of `GET /api/events` — null once there is
    // nothing older left. Only touched by the initial load and loadMore();
    // refreshHistory()'s merge pass never moves this.
    private var nextId: Int? = null

    init {
        refresh()
        refreshHistory()
        // Left open for a while like Classement/History, so the history
        // keeps itself fresh on its own (other teachers/admins can also
        // touch these transactions). viewModelScope is cancelled when the
        // nav entry is destroyed, which stops this loop — no manual cleanup.
        viewModelScope.launch {
            while (isActive) {
                delay(RELOAD_INTERVAL)
                refreshHistory()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = TeacherUiState.Loading
            _state.value = try {
                TeacherUiState.Success(teams.listActive())
            } catch (e: Exception) {
                TeacherUiState.Error(e.message ?: getString(Res.string.error_load_teams))
            }
        }
    }

    /**
     * Initial load, or reload: if nothing has ever loaded successfully yet,
     * fetches the first page as-is. Otherwise merges newer-than-what's-shown
     * transactions onto the top of the existing list, walking forward page by
     * page to bridge a burst larger than one page — already-loaded "Load
     * more" pages and the bottom cursor ([nextId]) are left untouched, so
     * scroll position and pagination state survive a reload. No-ops (clears
     * to an empty list) if nobody is logged in, so another teacher's
     * transactions are never fetched by mistake.
     */
    fun refreshHistory() {
        val myId = teacherId
        if (myId == null) {
            _transactions.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val existing = _transactions.value
                if (existing == null) {
                    val page = events.listPaginated(teacherId = myId)
                    nextId = page.nextId
                    _transactions.value = page.events
                    _canLoadMore.value = page.nextId != null
                } else {
                    _transactions.value = mergeNewest(existing, myId)
                }
                _historyError.value = null
            } catch (e: Exception) {
                _historyError.value = e.message ?: getString(Res.string.error_load_events)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun mergeNewest(existing: List<PointEvent>, myId: Int): List<PointEvent> {
        val currentTopId = existing.firstOrNull()?.id ?: return existing
        val newEvents = mutableListOf<PointEvent>()
        var cursor: Int? = null
        while (true) {
            val page = events.listPaginated(beforeId = cursor, teacherId = myId)
            val freshInPage = page.events.filter { it.id > currentTopId }
            newEvents += freshInPage
            // Stop once this page ran into transactions we already had, or
            // there's nothing further to fetch.
            if (freshInPage.size < page.events.size || page.nextId == null) break
            cursor = page.nextId
        }
        return if (newEvents.isEmpty()) existing else newEvents + existing
    }

    fun loadMore() {
        val myId = teacherId ?: return
        val current = _transactions.value ?: return
        val cursor = nextId ?: return
        if (_isLoadingMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = events.listPaginated(beforeId = cursor, teacherId = myId)
                nextId = page.nextId
                _transactions.value = current + page.events
                _canLoadMore.value = page.nextId != null
                _historyError.value = null
            } catch (e: Exception) {
                // Keep the already-loaded transactions on screen; only
                // surface the error, rather than replacing a populated list.
                _historyError.value = e.message ?: getString(Res.string.error_load_events)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * Deletes one of the teacher's own transactions — `DELETE
     * /api/events/{id}`. The row is removed locally on success rather than
     * triggering a full reload, so scroll position and the "Load more"
     * cursor are undisturbed; a follow-up [refreshHistory] then picks up
     * whatever else may have changed elsewhere.
     */
    fun deleteTransaction(eventId: Int) {
        viewModelScope.launch {
            _historyError.value = null
            try {
                events.deleteEvent(eventId)
                _transactions.value = _transactions.value?.filterNot { it.id == eventId }
                refreshHistory()
            } catch (e: Exception) {
                _historyError.value = e.message ?: getString(Res.string.error_void_event)
            }
        }
    }

    fun submitPoints(team: Team, points: Int) {
        viewModelScope.launch {
            _actionError.value = null
            _submitting.value = true
            try {
                events.addPoints(team.id, points)
                refreshHistory()
            } catch (e: Exception) {
                _actionError.value = e.message
            } finally {
                _submitting.value = false
            }
        }
    }

    companion object {
        private val RELOAD_INTERVAL = 1.minutes
    }
}

/**
 * Refresh control for [TeacherScreen], rendered by [AppRoot] inside the
 * shared top bar (see the class doc on [TeacherHistoryConfig]). Swaps to a
 * spinner while a history refresh is in flight, like
 * [HistoryTopBarActions]/[LeaderboardTopBarActions].
 */
@Composable
fun TeacherTopBarActions() {
    val di = localDI()
    val config = di.direct.instance<TeacherHistoryConfig>()
    val isLoading by config.isLoading.collectAsState()
    if (isLoading) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    } else {
        IconButton(onClick = { config.requestRefresh() }) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(Res.string.public_display_refresh_description))
        }
    }
}

/**
 * Teacher home: award or void team points (`SPECS.md §6`). The history list
 * here is the teacher's own persisted transactions (`GET
 * /api/events?teacher_id=<me>`, no AI comment — just "Team : +X points"),
 * separate from the full, unfiltered [HistoryScreen], and deletable
 * individually via [TeacherViewModel.deleteTransaction]. Chrome (top
 * bar/drawer) lives in [AppRoot], this is content-only; its top-bar refresh
 * button/spinner is [TeacherTopBarActions].
 */
@Composable
fun TeacherScreen() {
    val di = localDI()
    val session = di.direct.instance<Session>()
    val authState by session.state.collectAsState()
    val loggedIn = authState as? AuthState.LoggedIn
    val displayName = loggedIn?.displayName ?: loggedIn?.username.orEmpty()

    val viewModel = viewModel { TeacherViewModel(di.direct.instance(), di.direct.instance(), loggedIn?.userId) }
    val state by viewModel.state.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val historyError by viewModel.historyError.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val submitting by viewModel.submitting.collectAsState()

    // Top-bar↔screen refresh bridge (see TeacherHistoryConfig) — same pattern
    // as HistoryScreen/LeaderboardScreen.
    val historyConfig = di.direct.instance<TeacherHistoryConfig>()
    LaunchedEffect(historyConfig, viewModel) {
        historyConfig.refreshRequests.collect { viewModel.refreshHistory() }
    }
    val isLoadingHistory by viewModel.isLoading.collectAsState()
    LaunchedEffect(historyConfig, isLoadingHistory) {
        historyConfig.setLoading(isLoadingHistory)
    }

    var pendingDelete by remember { mutableStateOf<PointEvent?>(null) }
    var selectedTeam by remember { mutableStateOf<Team?>(null) }
    var sign by remember { mutableStateOf(PointsSign.ADD) }
    var amountText by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }

    val amount = amountText.toIntOrNull() ?: 0
    val signedAmount = if (sign == PointsSign.ADD) amount else -amount

    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.welcome_message, displayName))

            when (val current = state) {
                TeacherUiState.Loading -> CircularProgressIndicator()

                is TeacherUiState.Error -> Column {
                    Text(
                        stringResource(Res.string.public_load_error, current.message),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = { viewModel.refresh() }) { Text(stringResource(Res.string.action_retry)) }
                }

                is TeacherUiState.Success -> {
                    // Never auto-pick a team — only clear a stale selection (e.g. the
                    // selected team was deactivated elsewhere and dropped on reload).
                    // Points must always be applied to a team the teacher explicitly chose.
                    LaunchedEffect(current.teams) {
                        if (selectedTeam != null && current.teams.none { it.id == selectedTeam?.id }) {
                            selectedTeam = null
                        }
                    }

                    val teamSelected = selectedTeam != null

                    if (current.teams.isEmpty()) {
                        Text(stringResource(Res.string.public_no_teams))
                    } else {
                        Text(stringResource(Res.string.teacher_team_label), style = MaterialTheme.typography.labelLarge)

                        TeamSelector(
                            teams = current.teams,
                            selected = selectedTeam,
                            onSelect = { selectedTeam = it },
                        )

                        SignedAmountInput(
                            sign = sign,
                            amountText = amountText,
                            enabled = teamSelected,
                            onSignClick = { sign = if (sign == PointsSign.ADD) PointsSign.REMOVE else PointsSign.ADD },
                            onAmountChange = { input -> amountText = input.filter { it.isDigit() }.take(3) },
                        )

                        actionError?.let { message ->
                            Text(message, color = MaterialTheme.colorScheme.error)
                        }

                        Button(
                            enabled = selectedTeam != null && amount > 0 && !submitting,
                            onClick = { showConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (submitting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            } else {
                                Text(stringResource(Res.string.action_validate))
                            }
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(Res.string.teacher_history_title), style = MaterialTheme.typography.titleMedium)

            historyError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }

            val teamsById = (state as? TeacherUiState.Success)?.teams?.associateBy { it.id } ?: emptyMap()
            when {
                transactions == null -> Unit // initial load still in flight — top bar already shows a spinner
                transactions.isNullOrEmpty() -> Text(stringResource(Res.string.teacher_history_empty))
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (event in transactions.orEmpty()) {
                            val teamName = teamsById[event.teamId]?.name
                                ?: stringResource(Res.string.teacher_history_unknown_team)
                            val signedText = if (event.points > 0) "+${event.points}" else "${event.points}"
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(Res.string.teacher_points_summary, teamName, signedText),
                                    color = if (event.points > 0) AddColor else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { pendingDelete = event }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.action_delete))
                                }
                            }
                        }
                    }

                    if (canLoadMore) {
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
        EndVerticalScrollbar(rememberScrollbarAdapter(scrollState))
    }

    val confirmingTeam = selectedTeam
    if (showConfirm && confirmingTeam != null) {
        val signedText = if (signedAmount > 0) "+${signedAmount}" else "${signedAmount}"
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(Res.string.teacher_confirm_title)) },
            text = { Text(stringResource(Res.string.teacher_points_summary, confirmingTeam.name, signedText)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.submitPoints(confirmingTeam, signedAmount)
                    amountText = ""
                    showConfirm = false
                }) { Text(stringResource(Res.string.action_confirm)) }
            },
            dismissButton = {
                Button(onClick = { showConfirm = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }

    pendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.history_confirm_delete_title)) },
            text = { Text(stringResource(Res.string.history_confirm_delete_message)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteTransaction(event.id)
                    pendingDelete = null
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                Button(onClick = { pendingDelete = null }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

private val TeamTileWidth = 96.dp
private val TeamTileImageSize = 72.dp
private val TeamTileShape = RoundedCornerShape(12.dp)

/**
 * Horizontal, alphabetically sorted list of team tiles (image + name). Tapping
 * a tile selects that team; the selected tile is highlighted with a primary
 * border and a subtle tinted background.
 */
@Composable
private fun TeamSelector(teams: List<Team>, selected: Team?, onSelect: (Team) -> Unit) {
    val sortedTeams = remember(teams) { teams.sortedBy { it.name.lowercase() } }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
    ) {
        items(sortedTeams, key = { it.id }) { team ->
            TeamTile(team = team, selected = team.id == selected?.id, onClick = { onSelect(team) })
        }
    }
}

@Composable
private fun TeamTile(team: Team, selected: Boolean, onClick: () -> Unit) {
    val borderModifier = if (selected) {
        Modifier
            .clip(TeamTileShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .border(2.dp, MaterialTheme.colorScheme.primary, TeamTileShape)
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .width(TeamTileWidth)
            .clip(TeamTileShape)
            .clickable(onClick = onClick)
            .then(borderModifier)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TeamImage(imageUrl = team.imageUrl(), size = TeamTileImageSize)
        Text(
            team.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The combined sign + amount control: a single big colored "+"/"−" glyph that
 * toggles the sign, immediately followed by a same-size, same-color numeric
 * field just wide enough for 3 digits. Deliberately not a separate preview —
 * this field *is* the display (no duplicate "big text" echoing it elsewhere).
 */
@Composable
private fun SignedAmountInput(
    sign: PointsSign,
    amountText: String,
    enabled: Boolean,
    onSignClick: () -> Unit,
    onAmountChange: (String) -> Unit,
) {
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val displayColor = when {
        !enabled -> disabledColor
        sign == PointsSign.ADD -> AddColor
        else -> MaterialTheme.colorScheme.error
    }
    val signDescription = stringResource(
        if (sign == PointsSign.ADD) Res.string.teacher_sign_add_description else Res.string.teacher_sign_remove_description,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Text(
            text = if (sign == PointsSign.ADD) "+" else "−",
            style = MaterialTheme.typography.displayMedium,
            fontSize = AmountFontSize,
            color = displayColor,
            modifier = Modifier
                .clickable(enabled = enabled, onClickLabel = signDescription, onClick = onSignClick),
        )
        PointsAmountField(
            amountText = amountText,
            onAmountChange = onAmountChange,
            enabled = enabled,
            displayColor = displayColor,
            placeholderColor = disabledColor,
            fontSize = AmountFontSize,
            modifier = Modifier.width(192.dp),
        )
    }
}
