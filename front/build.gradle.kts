import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.4.10"
    kotlin("plugin.compose") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("org.jetbrains.compose") version "1.11.1"
    id("com.github.gmazzo.buildconfig") version "6.0.10"
}

kotlin {
    jvm("desktop")
    jvmToolchain(25)

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    js {
        browser()
        binaries.executable()
        useCommonJs()
    }

    sourceSets {
        val composeDeps = compose

        val compose = "1.11.1"
        val coroutines = "1.11.0"
        val nav3 = "1.1.1"
        val lifecycle = "2.11.0-beta01"
        val kodein = "7.33.0"
        val ktor = "3.5.1"
        val serialization = "1.11.0"

        commonMain.dependencies {
            implementation("org.jetbrains.compose.foundation:foundation:$compose")
            implementation("org.jetbrains.compose.runtime:runtime:$compose")
            implementation("org.jetbrains.compose.ui:ui:$compose")
            implementation("org.jetbrains.compose.components:components-resources:$compose")

            implementation("org.jetbrains.compose.material3:material3:1.11.0-alpha07")
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines")

            implementation("androidx.navigation3:navigation3-runtime:$nav3")
            implementation("org.jetbrains.androidx.navigation3:navigation3-ui:$nav3")

            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycle")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3:$lifecycle")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:$lifecycle")

            implementation("org.kodein.di:kodein-di:$kodein")
            implementation("org.kodein.di:kodein-di-framework-compose:$kodein")

            implementation("io.ktor:ktor-client-core:$ktor")
            implementation("io.ktor:ktor-client-auth:$ktor")
            implementation("io.ktor:ktor-client-content-negotiation:$ktor")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")

            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization")

            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
        }

        val desktopMain by getting
        desktopMain.dependencies {
            implementation(composeDeps.desktop.currentOs)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${coroutines}")
            implementation("io.ktor:ktor-client-okhttp:${ktor}")
        }

        webMain.dependencies {
            implementation("io.ktor:ktor-client-js:${ktor}")
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
        }
    }
}

compose {
    desktop {
        application {
            mainClass = "MainKt"
        }
    }
}

// API base URL, set at build time via `-PApiUrl=<url>`:
//   - unset            -> local-dev default (http://localhost:8080)
//   - "by-url"          -> null; the app derives it from window.location at
//                         runtime instead (same-origin prod deployment)
//   - anything else     -> used verbatim
val apiUrlProperty = providers.gradleProperty("ApiUrl").orNull

val apiUrlValue: String? = when (apiUrlProperty) {
    null -> "http://localhost:8080"
    "by-url" -> null
    else -> apiUrlProperty
}

buildConfig {
    packageName("") // root package, matching this project's package-less convention
    // Using the plain (type: String, name: String, value: Serializable?) member
    // directly, rather than the `buildConfigField<String>(name, value)` sugar
    // extension — that extension failed to resolve from this script (only the
    // Action-based member overload was found), so this sidesteps it.
    buildConfigField("String?", "API_URL", apiUrlValue)
}
