import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Holds the current access/refresh token pair for the Ktor Auth plugin. */
interface TokenStore {
    fun current(): TokenPair?
    fun update(tokens: TokenPair)
    fun clear()
}

/** Per-target persistent backing: `localStorage` on web (`webMain`), `Preferences` on desktop. */
expect fun createTokenStore(): TokenStore

private val tokenStoreJson = Json { ignoreUnknownKeys = true }

fun TokenPair.toStorageString(): String = tokenStoreJson.encodeToString(this)

/** Corrupted/unrecognized stored data is treated as "no token" rather than crashing startup. */
fun String.toTokenPairOrNull(): TokenPair? = runCatching { tokenStoreJson.decodeFromString<TokenPair>(this) }.getOrNull()
