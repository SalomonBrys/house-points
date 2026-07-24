import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.action_change_display_name
import house_points.front.generated.resources.action_change_password
import house_points.front.generated.resources.error_change_display_name_failed
import house_points.front.generated.resources.error_change_password_failed
import house_points.front.generated.resources.error_display_name_required
import house_points.front.generated.resources.error_password_fields_required
import house_points.front.generated.resources.error_passwords_dont_match
import house_points.front.generated.resources.label_confirm_password
import house_points.front.generated.resources.label_current_password
import house_points.front.generated.resources.label_display_name
import house_points.front.generated.resources.label_new_password
import house_points.front.generated.resources.profile_display_name_changed
import house_points.front.generated.resources.profile_password_changed
import house_points.front.generated.resources.profile_username_label
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.kodein.di.compose.localDI
import org.kodein.di.direct
import org.kodein.di.instance

sealed interface DisplayNameUiState {
    data object Idle : DisplayNameUiState
    data object Loading : DisplayNameUiState
    data object Success : DisplayNameUiState
    data class Error(val message: String) : DisplayNameUiState
}

sealed interface PasswordChangeUiState {
    data object Idle : PasswordChangeUiState
    data object Loading : PasswordChangeUiState
    data object Success : PasswordChangeUiState
    data class Error(val message: String) : PasswordChangeUiState
}

class ProfileViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _displayNameState = MutableStateFlow<DisplayNameUiState>(DisplayNameUiState.Idle)
    val displayNameState: StateFlow<DisplayNameUiState> = _displayNameState.asStateFlow()

    private val _passwordState = MutableStateFlow<PasswordChangeUiState>(PasswordChangeUiState.Idle)
    val passwordState: StateFlow<PasswordChangeUiState> = _passwordState.asStateFlow()

    fun changeDisplayName(displayName: String) {
        viewModelScope.launch {
            if (displayName.isBlank()) {
                _displayNameState.value = DisplayNameUiState.Error(getString(Res.string.error_display_name_required))
                return@launch
            }
            _displayNameState.value = DisplayNameUiState.Loading
            auth.updateDisplayName(displayName.trim())
                .onSuccess { _displayNameState.value = DisplayNameUiState.Success }
                .onFailure {
                    _displayNameState.value = DisplayNameUiState.Error(it.message ?: getString(Res.string.error_change_display_name_failed))
                }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String) {
        viewModelScope.launch {
            if (currentPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
                _passwordState.value = PasswordChangeUiState.Error(getString(Res.string.error_password_fields_required))
                return@launch
            }
            if (newPassword != confirmPassword) {
                _passwordState.value = PasswordChangeUiState.Error(getString(Res.string.error_passwords_dont_match))
                return@launch
            }
            _passwordState.value = PasswordChangeUiState.Loading
            auth.changePassword(currentPassword, newPassword)
                .onSuccess { _passwordState.value = PasswordChangeUiState.Success }
                .onFailure {
                    _passwordState.value = PasswordChangeUiState.Error(it.message ?: getString(Res.string.error_change_password_failed))
                }
        }
    }
}

/**
 * "Mon compte": shows the current username (from [Session], no API call
 * needed) and lets the user change their own display name
 * (`PATCH /api/me/display-name`) and password (`PATCH /api/me/password`).
 * Both mint a fresh access token in the same call so a successful change
 * keeps this session logged in — see [AuthRepository.updateDisplayName] /
 * [AuthRepository.changePassword]. Chrome (top bar/drawer) lives in
 * [AppRoot], this is content-only.
 */
@Composable
fun ProfileScreen() {
    val di = localDI()
    val session = di.direct.instance<Session>()
    val authState by session.state.collectAsState()
    val loggedIn = authState as? AuthState.LoggedIn
    val username = loggedIn?.username.orEmpty()

    val viewModel = viewModel { ProfileViewModel(di.direct.instance()) }
    val displayNameState by viewModel.displayNameState.collectAsState()
    val displayNameLoading = displayNameState == DisplayNameUiState.Loading

    val passwordState by viewModel.passwordState.collectAsState()
    val passwordLoading = passwordState == PasswordChangeUiState.Loading

    val originalDisplayName = loggedIn?.displayName.orEmpty()
    var displayName by remember { mutableStateOf(originalDisplayName) }
    val displayNameUnchanged = displayName.trim() == originalDisplayName
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LaunchedEffect(passwordState) {
        if (passwordState is PasswordChangeUiState.Success) {
            currentPassword = ""
            newPassword = ""
            confirmPassword = ""
        }
    }

    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.profile_username_label, username))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(Res.string.label_display_name)) },
                singleLine = true,
                enabled = !displayNameLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            val currentDisplayNameState = displayNameState
            if (currentDisplayNameState is DisplayNameUiState.Error) {
                Text(currentDisplayNameState.message, color = MaterialTheme.colorScheme.error)
            }
            if (currentDisplayNameState is DisplayNameUiState.Success) {
                Text(stringResource(Res.string.profile_display_name_changed))
            }

            Button(
                onClick = { viewModel.changeDisplayName(displayName) },
                enabled = !displayNameLoading && displayName.isNotBlank() && !displayNameUnchanged,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (displayNameLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(stringResource(Res.string.action_change_display_name))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                label = { Text(stringResource(Res.string.label_current_password)) },
                singleLine = true,
                enabled = !passwordLoading,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text(stringResource(Res.string.label_new_password)) },
                singleLine = true,
                enabled = !passwordLoading,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(stringResource(Res.string.label_confirm_password)) },
                singleLine = true,
                enabled = !passwordLoading,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            val currentPasswordState = passwordState
            if (currentPasswordState is PasswordChangeUiState.Error) {
                Text(currentPasswordState.message, color = MaterialTheme.colorScheme.error)
            }
            if (currentPasswordState is PasswordChangeUiState.Success) {
                Text(stringResource(Res.string.profile_password_changed))
            }

            Button(
                onClick = { viewModel.changePassword(currentPassword, newPassword, confirmPassword) },
                enabled = !passwordLoading && currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (passwordLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(stringResource(Res.string.action_change_password))
                }
        }
        }
        EndVerticalScrollbar(rememberScrollbarAdapter(scrollState))
    }
}
