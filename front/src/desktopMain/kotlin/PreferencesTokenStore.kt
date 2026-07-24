import java.util.prefs.Preferences

actual fun createTokenStore(): TokenStore = PreferencesTokenStore(Preferences.userRoot().node("house-points"))

private class PreferencesTokenStore(private val prefs: Preferences) : TokenStore {
    override fun current(): TokenPair? = prefs.get(KEY, null)?.toTokenPairOrNull()

    override fun update(tokens: TokenPair) {
        prefs.put(KEY, tokens.toStorageString())
        runCatching { prefs.flush() } // best-effort: force durability now rather than on JVM exit
    }

    override fun clear() {
        prefs.remove(KEY)
        runCatching { prefs.flush() }
    }

    private companion object {
        const val KEY = "tokens"
    }
}
