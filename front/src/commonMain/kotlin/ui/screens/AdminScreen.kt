import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.action_add
import house_points.front.generated.resources.action_cancel
import house_points.front.generated.resources.action_delete
import house_points.front.generated.resources.action_retry
import house_points.front.generated.resources.admin_confirm_delete_house_message
import house_points.front.generated.resources.admin_confirm_delete_house_title
import house_points.front.generated.resources.admin_confirm_delete_teacher_message
import house_points.front.generated.resources.admin_confirm_delete_teacher_title
import house_points.front.generated.resources.admin_house_name
import house_points.front.generated.resources.admin_houses_section
import house_points.front.generated.resources.admin_no_teachers
import house_points.front.generated.resources.admin_teachers_section
import house_points.front.generated.resources.error_load_houses
import house_points.front.generated.resources.label_display_name
import house_points.front.generated.resources.label_password
import house_points.front.generated.resources.label_username
import house_points.front.generated.resources.public_no_houses
import house_points.front.generated.resources.public_load_error
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

sealed interface AdminUiState {
    data object Loading : AdminUiState
    data class Success(val houses: List<House>, val teachers: List<Teacher>) : AdminUiState
    data class Error(val message: String) : AdminUiState
}

class AdminViewModel(
    private val houses: HousesRepository,
    private val users: UsersRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<AdminUiState>(AdminUiState.Loading)
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = AdminUiState.Loading
            _state.value = try {
                AdminUiState.Success(houses.listActive(), users.listTeachers())
            } catch (e: Exception) {
                AdminUiState.Error(e.message ?: getString(Res.string.error_load_houses))
            }
        }
    }

    fun addHouse(name: String) = runAction { houses.create(name) }

    fun removeHouse(id: Int) = runAction { houses.deactivate(id) }

    fun addTeacher(displayName: String, username: String, password: String) = runAction {
        users.create(username, password, "teacher", displayName)
    }

    fun removeTeacher(id: Int) = runAction { users.deactivate(id) }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                block()
                refresh()
            } catch (e: Exception) {
                _actionError.value = e.message
            }
        }
    }
}

private sealed interface PendingDelete {
    data class HouseDelete(val house: House) : PendingDelete
    data class TeacherDelete(val teacher: Teacher) : PendingDelete
}

/**
 * Admin home: manage houses and teacher accounts (`SPECS.md §6`). Chrome
 * (top bar/drawer) lives in [AppRoot], this is content-only.
 */
@Composable
fun AdminScreen() {
    val di = localDI()
    val session = di.direct.instance<Session>()
    val authState by session.state.collectAsState()
    val username = (authState as? AuthState.LoggedIn)?.username.orEmpty()

    val viewModel = viewModel { AdminViewModel(di.direct.instance(), di.direct.instance()) }
    val state by viewModel.state.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.welcome_message, username))

            actionError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }

            when (val current = state) {
                AdminUiState.Loading -> CircularProgressIndicator()

                is AdminUiState.Error -> Column {
                    Text(
                        stringResource(Res.string.public_load_error, current.message),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = { viewModel.refresh() }) { Text(stringResource(Res.string.action_retry)) }
                }

                is AdminUiState.Success -> {
                    HousesSection(
                        houses = current.houses,
                        onAdd = { name -> viewModel.addHouse(name) },
                        onDeleteRequested = { house -> pendingDelete = PendingDelete.HouseDelete(house) },
                    )
                    Spacer(Modifier.height(16.dp))
                    TeachersSection(
                        teachers = current.teachers,
                        onAdd = { displayName, teacherUsername, password ->
                            viewModel.addTeacher(displayName, teacherUsername, password)
                        },
                        onDeleteRequested = { teacher -> pendingDelete = PendingDelete.TeacherDelete(teacher) },
                    )
                }
            }
        }
        EndVerticalScrollbar(rememberScrollbarAdapter(scrollState))
    }

    when (val pending = pendingDelete) {
        is PendingDelete.HouseDelete -> ConfirmDeleteDialog(
            title = stringResource(Res.string.admin_confirm_delete_house_title),
            message = stringResource(Res.string.admin_confirm_delete_house_message, pending.house.name),
            onConfirm = {
                viewModel.removeHouse(pending.house.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )

        is PendingDelete.TeacherDelete -> ConfirmDeleteDialog(
            title = stringResource(Res.string.admin_confirm_delete_teacher_title),
            message = stringResource(Res.string.admin_confirm_delete_teacher_message, pending.teacher.displayName),
            onConfirm = {
                viewModel.removeTeacher(pending.teacher.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )

        null -> Unit
    }
}

@Composable
private fun HousesSection(
    houses: List<House>,
    onAdd: (name: String) -> Unit,
    onDeleteRequested: (House) -> Unit,
) {
    Column {
        Text(stringResource(Res.string.admin_houses_section), style = MaterialTheme.typography.titleMedium)

        if (houses.isEmpty()) {
            Text(stringResource(Res.string.public_no_houses))
        } else {
            Column {
                houses.forEach { house ->
                    ListItem(
                        headlineContent = { Text(house.name) },
                        trailingContent = {
                            IconButton(onClick = { onDeleteRequested(house) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.action_delete))
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }

        var name by remember { mutableStateOf("") }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.admin_house_name)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onAdd(name.trim())
                    name = ""
                },
            ) { Text(stringResource(Res.string.action_add)) }
        }
    }
}

@Composable
private fun TeachersSection(
    teachers: List<Teacher>,
    onAdd: (displayName: String, username: String, password: String) -> Unit,
    onDeleteRequested: (Teacher) -> Unit,
) {
    Column {
        Text(stringResource(Res.string.admin_teachers_section), style = MaterialTheme.typography.titleMedium)

        if (teachers.isEmpty()) {
            Text(stringResource(Res.string.admin_no_teachers))
        } else {
            Column {
                teachers.forEach { teacher ->
                    ListItem(
                        headlineContent = { Text(teacher.displayName) },
                        supportingContent = { Text(teacher.username) },
                        trailingContent = {
                            IconButton(onClick = { onDeleteRequested(teacher) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.action_delete))
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }

        var displayName by remember { mutableStateOf("") }
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(Res.string.label_display_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(Res.string.label_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(Res.string.label_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = displayName.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                onClick = {
                    onAdd(displayName.trim(), username.trim(), password)
                    displayName = ""
                    username = ""
                    password = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.action_add)) }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(Res.string.action_delete)) }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
