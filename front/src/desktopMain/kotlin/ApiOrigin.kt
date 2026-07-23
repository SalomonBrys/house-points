actual fun apiOrigin(): String = error(
    "No API_URL configured for this desktop build. Desktop has no " +
        "\"current page\" to derive one from (that's what -PApiUrl=by-url " +
        "means on web) — pass -PApiUrl=<url> explicitly when building.",
)
