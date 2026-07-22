import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.error_login_failed
import house_points.front.generated.resources.error_malformed_token
import org.jetbrains.compose.resources.getString

class ApiException(message: String) : Exception(message)

/** Wraps the `/api/auth` endpoints; owns writing to [TokenStore] and [Session] on the client's behalf. */
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
        session.setLoggedIn(claims)
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
