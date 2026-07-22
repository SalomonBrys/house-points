import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Per-target Ktor engine: `Js` on web (`webMain`), `OkHttp` on desktop. */
expect val httpClientEngineFactory: HttpClientEngineFactory<*>

/**
 * The single Ktor [HttpClient] used by every repository. The `Auth` bearer
 * plugin holds the 15-minute access token and transparently rotates it via
 * `POST /api/auth/refresh` on 401 (`back/ARCHITECTURE.md §4`); on refresh
 * failure it clears the session so [AppRoot] routes back to a logged-out view.
 */
fun createHttpClient(tokenStore: TokenStore, session: Session): HttpClient =
    HttpClient(httpClientEngineFactory) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
        install(Auth) {
            bearer {
                loadTokens {
                    tokenStore.current()?.let { BearerTokens(it.accessToken, it.refreshToken) }
                }
                refreshTokens {
                    val current = tokenStore.current()
                    if (current == null) {
                        null
                    } else {
                        try {
                            // `client` here is a bare client (no Auth plugin), avoiding
                            // an infinite refresh loop on a failing refresh call.
                            val response: HttpResponse = client.post("$API_BASE_URL/auth/refresh") {
                                markAsRefreshTokenRequest()
                                contentType(ContentType.Application.Json)
                                setBody(RefreshRequest(current.refreshToken))
                            }
                            if (response.status.isSuccess()) {
                                val pair = response.body<TokenPair>()
                                tokenStore.update(pair)
                                BearerTokens(pair.accessToken, pair.refreshToken)
                            } else {
                                tokenStore.clear()
                                session.setLoggedOut()
                                null
                            }
                        } catch (e: Exception) {
                            tokenStore.clear()
                            session.setLoggedOut()
                            null
                        }
                    }
                }
            }
        }
    }

/** Best-effort read of the backend's `{"error": "..."}` body; falls back if absent/malformed. */
suspend fun HttpResponse.errorMessage(fallback: String): String =
    runCatching { body<ApiError>().error }.getOrDefault(fallback)
