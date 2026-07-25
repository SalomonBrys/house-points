import kotlinx.browser.window

// origin + the directory the app is served from, so a subdirectory deployment
// (e.g. https://whatever.com/teampoints/) still resolves the API under that
// same prefix rather than the domain root. The app is always served with a
// trailing slash (index.html's relative `front.js` requires it), so pathname
// IS the deployment directory: "/teampoints/" -> "/teampoints", "/" -> "".
actual fun apiRootUrl(): String =
    window.location.origin + window.location.pathname.substringBeforeLast('/')