import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.error_change_display_name_failed
import house_points.front.generated.resources.error_change_password_failed
import house_points.front.generated.resources.error_login_failed
import house_points.front.generated.resources.error_malformed_token
import org.jetbrains.compose.resources.getString

class ApiException(message: String) : Exception(message)

/**
 * Wraps the `/api/auth` endpoints, plus the account-adjacent `/api/me`
 * ones (`password`, `display-name`); owns writing to [TokenStore] and
 * [Session] on the client's behalf.
 */
class AuthRepository(
    private val client: HttpClient,
    private val tokenStore: TokenStore,
    private val session: Session,
) {
    suspend fun login(username: String, password: String): Result<Unit> = runCatching {
        val response = client.post("$API_BASE_URL/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_login_failed)))
        }
        val pair = response.body<TokenPair>()
        tokenStore.update(pair)
        val claims = decodeJwtClaims(pair.accessToken)
            ?: throw ApiException(getString(Res.string.error_malformed_token))
        session.setLoggedIn(claims, LoginSource.EXPLICIT)
    }

    /**
     * Restores [Session] from a persisted token pair at app startup, if one exists,
     * so a returning user isn't shown as logged out. Decodes the access token's
     * claims purely for routing/display, same trust model as [login] — actual
     * authorization is re-checked server-side on every request, and the bearer
     * plugin's `refreshTokens` transparently mints a new access token from the
     * stored refresh token if the access token has since expired.
     */
    fun restoreSession() {
        val tokens = tokenStore.current() ?: return
        val claims = decodeJwtClaims(tokens.accessToken) ?: return
        session.setLoggedIn(claims, LoginSource.RESTORED)
    }

    /**
     * `PATCH /api/me/password`: verifies [currentPassword] server-side, then
     * updates it. The backend revokes every refresh token for this user (all
     * devices/sessions) but mints a fresh pair for *this* one in the same
     * response, so — unlike a stale session elsewhere — this device stays
     * logged in with no re-login and no [Session] change (identity is
     * unaffected).
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = runCatching {
        val response = client.patch("$API_BASE_URL/me/password") {
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest(currentPassword, newPassword))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_change_password_failed)))
        }
        tokenStore.update(response.body<TokenPair>())
    }

    /**
     * `PATCH /api/me/display-name`. Unlike [changePassword], this isn't
     * security-sensitive — no refresh-token rotation — so the response is
     * just a fresh access token, which is merged into the currently-stored
     * pair (keeping the existing refresh token). [Session] is updated
     * directly rather than by re-decoding the token, since identity *is*
     * affected here.
     */
    suspend fun updateDisplayName(displayName: String): Result<Unit> = runCatching {
        val response = client.patch("$API_BASE_URL/me/display-name") {
            contentType(ContentType.Application.Json)
            setBody(UpdateDisplayNameRequest(displayName))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_change_display_name_failed)))
        }
        val fresh = response.body<AccessTokenResponse>()
        tokenStore.current()?.let { current ->
            tokenStore.update(current.copy(accessToken = fresh.accessToken, expiresIn = fresh.expiresIn))
        }
        session.updateDisplayName(displayName)
    }

    suspend fun logout() {
        val tokens = tokenStore.current()
        tokenStore.clear()
        session.setLoggedOut()
        if (tokens != null) {
            runCatching {
                client.post("$API_BASE_URL/auth/logout") {
                    contentType(ContentType.Application.Json)
                    setBody(LogoutRequest(tokens.refreshToken))
                }
            }
        }
    }
}
