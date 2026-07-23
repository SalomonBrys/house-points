import kotlinx.browser.window

actual fun apiOrigin(): String = window.location.origin
