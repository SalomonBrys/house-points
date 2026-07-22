import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.4.10"
    kotlin("plugin.compose") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("org.jetbrains.compose") version "1.11.1"
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
            implementation("io.ktor:ktor-client-okhttp:${ktor}")
        }

        webMain.dependencies {
            implementation("io.ktor:ktor-client-js:${ktor}")
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
