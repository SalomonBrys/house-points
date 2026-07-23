# Frontend Architecture

This document describes the frontend architecture as configured in
`front/build.gradle.kts`. See `SPECS.md` at the repo root for the functional
specification (§6 covers the web client) and `back/ARCHITECTURE.md` for the
API it talks to. Where a point is inferred from dependencies rather than
stated by the spec, it is marked as such.

## 1. Language & UI toolkit: Kotlin Multiplatform + Compose

- **Kotlin Multiplatform** (`kotlin("multiplatform")`, Kotlin `2.4.10`) with a
  shared `commonMain` holding the bulk of the code.
- **Compose Multiplatform** (`org.jetbrains.compose` `1.11.1`, plus the
  `kotlin("plugin.compose")` compiler plugin) for the entire UI, declared once
  in common code and rendered on every target.

Compose UI dependencies pulled in `commonMain`: `foundation`, `runtime`, `ui`,
`components-resources` (bundled resources), `material3` (`1.11.0-alpha07`),
and `material-icons-extended` (`1.7.3`).

## 2. Targets

Three Kotlin targets are configured:

| Target | Declaration | Notes |
|---|---|---|
| Browser (Wasm) | `wasmJs { browser() }` | `binaries.executable()`; primary shipped web target |
| Browser (JS) | `js { browser(); useCommonJs() }` | `binaries.executable()`; JS fallback for browsers without Wasm |
| Desktop (JVM) | `jvm("desktop")` | **Development only** — a Compose Hot-Reload harness, not a shipped artifact; `mainClass = "MainKt"` via the Compose `application` block |

The shipped client is **Compose for Web (Wasm & JS)** per `SPECS.md §6`,
provided by the `wasmJs` + `js` browser targets. The desktop (JVM) target
exists solely to run the UI under Compose Hot-Reload during development (fast
iteration on composables without a browser rebuild); it is not a deliverable,
so there is no conflict with the spec's "no native Desktop builds" scope.

## 3. Source-set layout

```
src/
  commonMain/kotlin   # shared UI + logic for all targets
  desktopMain/kotlin  # JVM-desktop specifics
  webMain/kotlin      # shared between wasmJs and js
```

`webMain` is an intermediate source set (from Kotlin's default hierarchy
template) that groups the two browser targets, so Wasm- and JS-common code
(e.g. the browser Ktor engine) is written once. `desktopMain` carries the
JVM-only pieces.

## 4. Navigation: Navigation3

- `androidx.navigation3:navigation3-runtime` and
  `org.jetbrains.androidx.navigation3:navigation3-ui` (`1.1.1`).
- Integrated with lifecycle via
  `lifecycle-viewmodel-navigation3`, so navigation entries can own
  `ViewModel`s.

*(Inferred)* This backs the distinct screens described in `SPECS.md §6` —
admin, teacher, public, and the public display view.

## 5. State & lifecycle: AndroidX Lifecycle (multiplatform)

`org.jetbrains.androidx.lifecycle` (`2.11.0-beta01`):
`lifecycle-viewmodel-compose`, `lifecycle-viewmodel-navigation3`, and
`lifecycle-runtime-compose`. This provides `ViewModel`-scoped state holders and
lifecycle-aware Compose integration — the presentation layer between the UI and
the networking/DI layers.

## 6. Dependency injection: Kodein-DI

`org.kodein.di:kodein-di` and `kodein-di-framework-compose` (`7.33.0`) —
constructor-style DI with Compose-aware helpers for injecting into composables
(mirroring the backend's explicit-wiring philosophy rather than reflection-heavy
containers).

## 7. Networking: Ktor client

Shared client config in `commonMain`:
`ktor-client-core`, `ktor-client-auth`, `ktor-client-content-negotiation`, and
`ktor-serialization-kotlinx-json` (Ktor `3.5.1`). Engines are per-target:

| Source set | Engine |
|---|---|
| `desktopMain` | `ktor-client-okhttp` |
| `webMain` | `ktor-client-js` |

The `ktor-client-auth` plugin is the notable piece: it maps directly onto the
backend's auth model (`back/ARCHITECTURE.md §10`) — bearer access tokens with a
refresh flow. *(Inferred)* it will hold the 15-minute JWT and transparently refresh
via `POST /api/auth/refresh` on `401`.

## 8. Serialization & date/time

- **kotlinx.serialization** (`plugin.serialization` + `kotlinx-serialization-core`
  / `-json` `1.11.0`) for typed (de)serialization of API payloads via Ktor's
  content negotiation. This is why the backend's numeric-typing consistency
  (e.g. `total_points` as an int) matters — these become typed Kotlin models.
- **kotlinx-datetime** (`0.8.0`) for parsing/formatting event timestamps
  (`created_at`) from the API.

## 9. Build & toolchain notes

- **JVM toolchain 25** (`jvmToolchain(25)`) — used for the desktop target and
  build tooling.
- The Compose `desktop.application { mainClass = "MainKt" }` block makes the
  desktop target runnable, which is what the Compose Hot-Reload development
  workflow drives (see §2).
- A local quirk in `build.gradle.kts`: the Compose plugin extension is aliased
  (`val composeDeps = compose`) before a local `val compose = "1.11.1"` version
  string shadows the `compose` name; `desktopMain` then uses
  `composeDeps.desktop.currentOs` for the desktop artifacts.
- Dependency versions (including `material3 1.11.0-alpha07`,
  `lifecycle 2.11.0-beta01`, and `material-icons-extended 1.7.3`) are the
  versions required for compatibility with this Compose Multiplatform release —
  the differing numbers are the correct per-artifact versions, not a mismatch.

## 10. Localization: Compose Multiplatform resources, French only

Per `SPECS.md §6`, the app is **French only** — no language switcher, no
English fallback. That's still implemented through the standard i18n
mechanism rather than as inline literals, so the app is a straightforward
translation job (add a `values-xx/strings.xml`) if that ever changes.

- Every displayed string — screen/window titles, button and field labels,
  drawer items, content descriptions, and error messages — is a Compose
  Multiplatform string resource, not a Kotlin string literal. They live in
  `front/src/commonMain/composeResources/values/strings.xml`, and are read via
  `stringResource(Res.string.xxx)` in `@Composable` code or the suspend
  `getString(Res.string.xxx)` in repository/`ViewModel` code that isn't
  composable (e.g. a network-error fallback message).
- The `values/` directory is **unqualified** (no `values-fr/`): since French
  is the only supported locale, it *is* the default, not an override. Adding a
  second language later means adding `values-<locale>/strings.xml` next to it
  — the lookup/fallback mechanism is already in place, nothing in the Kotlin
  code changes.
- **Not covered by this catalog:** text the backend controls — the PHP API's
  `{"error": "..."}` bodies (`back/ARCHITECTURE.md`) — is shown verbatim when
  present, and platform/library exception messages (e.g. a Ktor network
  failure) surface as-is when there's no app-level fallback string to use
  instead. Translating those is a backend-side or case-by-case concern, not
  something the client's resource catalog can cover.
- User-entered data (house names, display names, teacher comments) is never
  routed through string resources — only *our* authored UI copy is.

## 11. API URL: build-time-configurable via `com.github.gmazzo.buildconfig`

The backend URL is not hardcoded — it's resolved once at runtime from a
generated `BuildConfig.API_URL: String?` (`com.github.gmazzo.buildconfig`
plugin, `6.0.10`), itself driven by an `ApiUrl` Gradle property
(`-PApiUrl=...`), configured in `front/build.gradle.kts`:

| `-PApiUrl=...` | `BuildConfig.API_URL` | Resolved base URL |
|---|---|---|
| *(unset)* | `"http://localhost:8080"` | that value + `/api` — local-dev default |
| `by-url` | `null` | the page's own origin (`window.location.origin`) + `/api` — same-origin prod deployment |
| anything else | that value | that value + `/api` — explicit dev/CI-provided origin |

`Config.kt`'s `API_BASE_URL` is `"${BuildConfig.API_URL ?: apiOrigin()}/api"`,
computed lazily on first use. `apiOrigin()` is `expect`ed and only ever called
in the `by-url` case:
- `webMain` reads `window.location.origin` via `kotlinx-browser` (`0.5.0`) —
  the JetBrains multiplatform successor to the classic `kotlin.browser`
  package, usable from one shared source set across both `js` and `wasmJs`.
- `desktopMain` throws — there's no "current page" on desktop to derive an
  origin from, so `by-url` (or an unset `API_URL` more generally) is a build
  misconfiguration there, not a runtime state to handle gracefully.

`apiOrigin()` is a **function, not a `val`** — deliberately, unlike this
project's other `expect`/`actual` split (`httpClientEngineFactory` in
`ApiClientEngine.kt`). An `expect val` evaluates eagerly at module init on
every target; since desktop's implementation always throws, that would crash
desktop unconditionally, even when a real `API_URL` *is* configured. As a
`fun`, it's only invoked from inside the `?:` — i.e. only when `by-url` mode
is actually in play.

The generated `BuildConfig` sits in the project's root package
(`packageName("")`), matching this project's package-less convention, so no
import is needed to use it from `commonMain`.
