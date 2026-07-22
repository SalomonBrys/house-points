/** Holds the current access/refresh token pair for the Ktor Auth plugin. */
interface TokenStore {
    fun current(): TokenPair?
    fun update(tokens: TokenPair)
    fun clear()
}

/**
 * In-memory only: a page reload logs the user out. Good enough for the
 * backbone; swapping in browser storage (e.g. `localStorage` on `webMain`,
 * behind this same interface) is a follow-up, not a structural change.
 */
class InMemoryTokenStore : TokenStore {
    private var tokens: TokenPair? = null

    override fun current(): TokenPair? = tokens

    override fun update(tokens: TokenPair) {
        this.tokens = tokens
    }

    override fun clear() {
        tokens = null
    }
}
