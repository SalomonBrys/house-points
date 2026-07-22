import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthState {
    data object LoggedOut : AuthState
    data class LoggedIn(val userId: Int, val role: String, val username: String) : AuthState
}

/**
 * Single source of truth for "who is logged in", observed by [AppRoot] to
 * drive role-gated navigation. Populated from the access token's JWT claims
 * (see [decodeJwtClaims]) since the API returns no separate identity payload.
 */
class Session {
    private val _state = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun setLoggedIn(claims: JwtClaims) {
        _state.value = AuthState.LoggedIn(claims.userId, claims.role, claims.username)
    }

    fun setLoggedOut() {
        _state.value = AuthState.LoggedOut
    }
}
