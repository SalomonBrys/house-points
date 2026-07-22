import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The claims the backend embeds in the access token JWT (`sub`, `role`,
 * `username` — see `back/ARCHITECTURE.md §4`). The login/refresh responses
 * carry no separate user-identity payload, so this is the only source of
 * "who am I" on the client.
 */
data class JwtClaims(
    val userId: Int,
    val role: String,
    val username: String,
)

/**
 * Decodes the (unverified) payload segment of a JWT. Verification is the
 * backend's job on every request; the client only needs the claims to drive
 * routing/display, so signature checking is deliberately skipped here.
 */
@OptIn(ExperimentalEncodingApi::class)
fun decodeJwtClaims(token: String): JwtClaims? {
    val parts = token.split(".")
    if (parts.size != 3) return null

    val payloadJson = try {
        Base64.UrlSafe.decode(parts[1].withBase64Padding()).decodeToString()
    } catch (e: IllegalArgumentException) {
        return null
    }

    return try {
        val obj = Json.parseToJsonElement(payloadJson).jsonObject
        val userId = obj["sub"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val role = obj["role"]?.jsonPrimitive?.content ?: return null
        val username = obj["username"]?.jsonPrimitive?.content ?: return null
        JwtClaims(userId, role, username)
    } catch (e: Exception) {
        null
    }
}

private fun String.withBase64Padding(): String {
    val remainder = length % 4
    return if (remainder == 0) this else this + "=".repeat(4 - remainder)
}
