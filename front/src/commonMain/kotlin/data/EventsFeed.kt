import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import team_points.front.generated.resources.Res
import team_points.front.generated.resources.error_load_events
import team_points.front.generated.resources.error_void_event

/**
 * Keyset-paginated, self-merging event feed — shared paging/refresh logic
 * behind [HistoryViewModel] and [LeaderboardViewModel]'s history pane, so
 * "newest first, reload without losing scroll position or the load-more
 * cursor" is implemented once. [fetchPage] supplies the filter (unfiltered,
 * by team, or by teacher); everything else is filter-agnostic.
 *
 * Callers drive it from their own `viewModelScope` (passed in as [scope])
 * rather than this owning a `ViewModel` itself, so it can be embedded inside
 * a `ViewModel` that also does other things (as [LeaderboardViewModel] does).
 */
class EventsFeed(
    private val scope: CoroutineScope,
    private val eventsRepository: EventsRepository,
    private val fetchPage: suspend (beforeId: Int?) -> EventsPage,
) {
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

    // True while a full refresh (initial load or reload) is in flight.
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

    /**
     * Initial load, or reload: if nothing has ever loaded successfully yet,
     * fetches the first page as-is. Otherwise merges newer-than-what's-shown
     * events onto the top of the existing list, walking forward page by page
     * to bridge a burst larger than one page — the already-loaded "Load more"
     * pages and the bottom cursor ([nextId]) are left untouched, so scroll
     * position and pagination state survive a reload.
     */
    fun refresh() {
        scope.launch {
            _isLoading.value = true
            try {
                val existing = _events.value
                if (existing == null) {
                    val page = fetchPage(null)
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
            val page = fetchPage(cursor)
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
        scope.launch {
            _isLoadingMore.value = true
            try {
                val page = fetchPage(cursor)
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

    /**
     * Deletes an event the caller owns (or is admin for) — `DELETE
     * /api/events/{id}`, enforced server-side (`PointEventsController::destroy`:
     * 403 if neither owner nor admin). The row is removed locally on success
     * rather than triggering a full reload, so scroll position and the
     * "Load more" cursor are undisturbed.
     */
    fun deleteEvent(eventId: Int) {
        scope.launch {
            _actionError.value = null
            try {
                eventsRepository.deleteEvent(eventId)
                _events.value = _events.value?.filterNot { it.id == eventId }
            } catch (e: Exception) {
                _actionError.value = e.message ?: getString(Res.string.error_void_event)
            }
        }
    }
}
