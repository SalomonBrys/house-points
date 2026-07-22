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

sealed interface PublicUiState {
    data object Loading : PublicUiState
    data class Success(val houses: List<House>) : PublicUiState
    data class Error(val message: String) : PublicUiState
}

class PublicViewModel(private val houses: HousesRepository) : ViewModel() {
    private val _state = MutableStateFlow<PublicUiState>(PublicUiState.Loading)
    val state: StateFlow<PublicUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = PublicUiState.Loading
            _state.value = try {
                PublicUiState.Success(houses.listActive())
            } catch (e: Exception) {
                PublicUiState.Error(e.message ?: getString(Res.string.error_load_houses))
            }
        }
    }
}

/**
 * Public leaderboard — `GET /api/houses`, no auth. The first proven vertical
 * slice; chrome (top bar/drawer) lives in [AppRoot], this is content-only.
 */
@Composable
fun PublicScreen() {
    val di = localDI()
    val viewModel = viewModel { PublicViewModel(di.direct.instance()) }
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        when (val current = state) {
            PublicUiState.Loading -> CircularProgressIndicator()

            is PublicUiState.Error -> Column {
                Text(
                    stringResource(Res.string.public_load_error, current.message),
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = { viewModel.refresh() }) { Text(stringResource(Res.string.action_retry)) }
            }

            is PublicUiState.Success -> Column {
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
