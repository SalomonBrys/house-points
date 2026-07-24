import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How a [AuthState.LoggedIn] transition came about — drives [AppRoot]'s post-auth routing. */
enum class LoginSource { EXPLICIT, RESTORED }

sealed interface AuthState {
    data object LoggedOut : AuthState
    data class LoggedIn(
        val userId: Int,
        val role: String,
        val username: String,
        val displayName: String?,
        val source: LoginSource,
    ) : AuthState
}

/**
 * Single source of truth for "who is logged in", observed by [AppRoot] to
 * drive role-gated navigation. Populated from the access token's JWT claims
 * (see [decodeJwtClaims]) since the API returns no separate identity payload.
 */
class Session {
    private val _state = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun setLoggedIn(claims: JwtClaims, source: LoginSource) {
        _state.value = AuthState.LoggedIn(claims.userId, claims.role, claims.username, claims.displayName, source)
    }

    /**
     * Patches the display name in place after a successful
     * `PATCH /api/me/display-name` (see [AuthRepository.updateDisplayName]) —
     * simpler than re-decoding the fresh access token's claims, and this
     * in-place edit doesn't fit the [LoginSource] classification (it's
     * neither a fresh login nor a session restore).
     */
    fun updateDisplayName(displayName: String) {
        (_state.value as? AuthState.LoggedIn)?.let { _state.value = it.copy(displayName = displayName) }
    }

    fun setLoggedOut() {
        _state.value = AuthState.LoggedOut
    }
}
