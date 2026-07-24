import kotlinx.browser.window

actual fun createTokenStore(): TokenStore = LocalStorageTokenStore()

private class LocalStorageTokenStore : TokenStore {
    override fun current(): TokenPair? = window.localStorage.getItem(KEY)?.toTokenPairOrNull()
    override fun update(tokens: TokenPair) = window.localStorage.setItem(KEY, tokens.toStorageString())
    override fun clear() = window.localStorage.removeItem(KEY)

    private companion object {
        const val KEY = "tokens"
    }
}
