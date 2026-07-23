import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.action_retry
import house_points.front.generated.resources.error_load_houses
import house_points.front.generated.resources.house_points
import house_points.front.generated.resources.house_rank
import house_points.front.generated.resources.public_load_error
import house_points.front.generated.resources.public_no_houses
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.kodein.di.compose.localDI
import org.kodein.di.direct
import org.kodein.di.instance

sealed interface PublicDisplayUiState {
    data object Loading : PublicDisplayUiState
    data class Success(val houses: List<House>) : PublicDisplayUiState
    data class Error(val message: String) : PublicDisplayUiState
}

class PublicDisplayViewModel(private val houses: HousesRepository) : ViewModel() {
    private val _state = MutableStateFlow<PublicDisplayUiState>(PublicDisplayUiState.Loading)
    val state: StateFlow<PublicDisplayUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = PublicDisplayUiState.Loading
            _state.value = try {
                PublicDisplayUiState.Success(houses.listActive())
            } catch (e: Exception) {
                PublicDisplayUiState.Error(e.message ?: getString(Res.string.error_load_houses))
            }
        }
    }
}

/**
 * Public leaderboard — `GET /api/houses`, no auth. The app's default screen,
 * labeled "Classement" (`SPECS.md §6`); chrome (top bar/drawer) lives in
 * [AppRoot], this is content-only. A fullscreen, auto-refreshing variant
 * (polling `GET /api/events/since`) remains a future follow-up.
 */
@Composable
fun PublicDisplayScreen() {
    val di = localDI()
    val viewModel = viewModel { PublicDisplayViewModel(di.direct.instance()) }
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        when (val current = state) {
            PublicDisplayUiState.Loading -> CircularProgressIndicator()

            is PublicDisplayUiState.Error -> Column {
                Text(
                    stringResource(Res.string.public_load_error, current.message),
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = { viewModel.refresh() }) { Text(stringResource(Res.string.action_retry)) }
            }

            is PublicDisplayUiState.Success -> Column {
                if (current.houses.isEmpty()) {
                    Text(stringResource(Res.string.public_no_houses))
                } else {
                    LazyColumn {
                        itemsIndexed(current.houses) { index, house ->
                            ListItem(
                                leadingContent = { Text(stringResource(Res.string.house_rank, index + 1)) },
                                headlineContent = { Text(house.name) },
                                trailingContent = { Text(stringResource(Res.string.house_points, house.totalPoints)) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
