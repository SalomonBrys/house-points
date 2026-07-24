import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.action_cancel
import house_points.front.generated.resources.action_confirm
import house_points.front.generated.resources.action_retry
import house_points.front.generated.resources.action_validate
import house_points.front.generated.resources.error_load_houses
import house_points.front.generated.resources.public_load_error
import house_points.front.generated.resources.public_no_houses
import house_points.front.generated.resources.teacher_confirm_title
import house_points.front.generated.resources.teacher_history_empty
import house_points.front.generated.resources.teacher_history_title
import house_points.front.generated.resources.teacher_house_label
import house_points.front.generated.resources.teacher_points_summary
import house_points.front.generated.resources.teacher_sign_add_description
import house_points.front.generated.resources.teacher_sign_remove_description
import house_points.front.generated.resources.welcome_message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.kodein.di.compose.localDI
import org.kodein.di.direct
import org.kodein.di.instance

/** A soft green distinct from [MaterialTheme]'s error red, for "points added" feedback. */
private val AddColor = Color(0xFF2E7D32)

/** Font size shared by the sign glyph and the amount input, per the design (SPECS.md §6). */
private val AmountFontSize = 96.sp

private enum class PointsSign { ADD, REMOVE }

data class HistoryEntry(val houseName: String, val points: Int)

sealed interface TeacherUiState {
    data object Loading : TeacherUiState
    data class Success(val houses: List<House>) : TeacherUiState
    data class Error(val message: String) : TeacherUiState
}

class TeacherViewModel(
    private val houses: HousesRepository,
    private val events: EventsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<TeacherUiState>(TeacherUiState.Loading)
    val state: StateFlow<TeacherUiState> = _state.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = TeacherUiState.Loading
            _state.value = try {
                TeacherUiState.Success(houses.listActive())
            } catch (e: Exception) {
                TeacherUiState.Error(e.message ?: getString(Res.string.error_load_houses))
            }
        }
    }

    fun submitPoints(house: House, points: Int) {
        viewModelScope.launch {
            _actionError.value = null
            _submitting.value = true
            try {
                events.addPoints(house.id, points)
                _history.value = listOf(HistoryEntry(house.name, points)) + _history.value
            } catch (e: Exception) {
                _actionError.value = e.message
            } finally {
                _submitting.value = false
            }
        }
    }
}

/**
 * Teacher home: award or void house points (`SPECS.md §6`). The transaction
 * list here is an in-memory, per-[ViewModel] session log only — it is not the
 * persisted history (see [HistoryScreen]) and is lost when the teacher
 * navigates away, by design. Chrome (top bar/drawer) lives in [AppRoot], this
 * is content-only.
 */
@Composable
fun TeacherScreen() {
    val di = localDI()
    val session = di.direct.instance<Session>()
    val authState by session.state.collectAsState()
    val loggedIn = authState as? AuthState.LoggedIn
    val displayName = loggedIn?.displayName ?: loggedIn?.username.orEmpty()

    val viewModel = viewModel { TeacherViewModel(di.direct.instance(), di.direct.instance()) }
    val state by viewModel.state.collectAsState()
    val history by viewModel.history.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val submitting by viewModel.submitting.collectAsState()

    var selectedHouse by remember { mutableStateOf<House?>(null) }
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
                    // Never auto-pick a house — only clear a stale selection (e.g. the
                    // selected house was deactivated elsewhere and dropped on reload).
                    // Points must always be applied to a house the teacher explicitly chose.
                    LaunchedEffect(current.houses) {
                        if (selectedHouse != null && current.houses.none { it.id == selectedHouse?.id }) {
                            selectedHouse = null
                        }
                    }

                    val houseSelected = selectedHouse != null

                    if (current.houses.isEmpty()) {
                        Text(stringResource(Res.string.public_no_houses))
                    } else {
                        HouseSelector(
                            houses = current.houses,
                            selected = selectedHouse,
                            onSelect = { selectedHouse = it },
                        )

                        SignedAmountInput(
                            sign = sign,
                            amountText = amountText,
                            enabled = houseSelected,
                            onSignClick = { sign = if (sign == PointsSign.ADD) PointsSign.REMOVE else PointsSign.ADD },
                            onAmountChange = { input -> amountText = input.filter { it.isDigit() }.take(3) },
                        )

                        actionError?.let { message ->
                            Text(message, color = MaterialTheme.colorScheme.error)
                        }

                        Button(
                            enabled = selectedHouse != null && amount > 0 && !submitting,
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

            if (history.isEmpty()) {
                Text(stringResource(Res.string.teacher_history_empty))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (entry in history) {
                        val signedText = if (entry.points > 0) "+${entry.points}" else "${entry.points}"
                        Text(
                            stringResource(Res.string.teacher_points_summary, entry.houseName, signedText),
                            color = if (entry.points > 0) AddColor else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        EndVerticalScrollbar(rememberScrollbarAdapter(scrollState))
    }

    val confirmingHouse = selectedHouse
    if (showConfirm && confirmingHouse != null) {
        val signedText = if (signedAmount > 0) "+${signedAmount}" else "${signedAmount}"
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(Res.string.teacher_confirm_title)) },
            text = { Text(stringResource(Res.string.teacher_points_summary, confirmingHouse.name, signedText)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.submitPoints(confirmingHouse, signedAmount)
                    amountText = ""
                    showConfirm = false
                }) { Text(stringResource(Res.string.action_confirm)) }
            },
            dismissButton = {
                Button(onClick = { showConfirm = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun HouseSelector(houses: List<House>, selected: House?, onSelect: (House) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.name ?: stringResource(Res.string.teacher_house_label))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            houses.forEach { house ->
                DropdownMenuItem(
                    text = { Text(house.name) },
                    onClick = {
                        onSelect(house)
                        expanded = false
                    },
                )
            }
        }
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
        BasicTextField(
            value = amountText,
            onValueChange = onAmountChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(fontSize = AmountFontSize, color = displayColor, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(displayColor),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(192.dp),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (amountText.isEmpty()) {
                        Text(
                            text = "0",
                            style = MaterialTheme.typography.displayMedium,
                            fontSize = AmountFontSize,
                            color = disabledColor,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
