/**
 * Base URL of the House Points API. Every repository builds full request
 * URLs off it. Resolved once, lazily, from `BuildConfig.API_URL` (generated
 * at build time from the `ApiUrl` Gradle property — see `front/build.gradle.kts`
 * and `front/ARCHITECTURE.md`): if that's set, it's used directly; if it's
 * null (`-PApiUrl=by-url`), [apiOrigin] resolves it at runtime instead.
 */
val API_BASE_URL: String by lazy { "${BuildConfig.API_URL ?: apiOrigin()}/api" }

/**
 * Resolves the API origin (scheme+host+port) when [BuildConfig.API_URL] is
 * null, i.e. "by-url" mode — deriving it from wherever this app is actually
 * being served from, for a same-origin prod deployment. A `fun`, not a
 * `val`: it must only run when by-url mode is actually reached (only ever
 * called from the `?:` above), since on desktop it always throws — an
 * `expect val` would evaluate eagerly at startup and crash desktop even when
 * a real API_URL *is* configured.
 */
expect fun apiOrigin(): String
