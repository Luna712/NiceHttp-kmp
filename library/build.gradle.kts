import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    id("maven-publish")
}

group = "com.lagradost"
version = "1.0.0"

kotlin {
    // ── JVM ──────────────────────────────────────────────────────────────────
    jvm {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(JvmTarget.JVM_1_8)
            }
        }
    }

    // ── Android ───────────────────────────────────────────────────────────────
    android {
        namespace = "com.lagradost.nicehttp.kmp"
        compileSdk = 35
        minSdk = 21
        compilerOptions { jvmTarget.set(JvmTarget.JVM_1_8) }
    }

    // ── JS (browser + Node.js) ───────────────────────────────────────────────
    js(IR) {
        browser {
            commonWebpackConfig {
                devServer = KotlinWebpackConfig.DevServer(
                    open = false
                )
            }
        }
        nodejs()
        binaries.library()
    }

    // ── WASM/JS ───────────────────────────────────────────────────────────────
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
        binaries.library()
    }

    // ── Native: Apple ─────────────────────────────────────────────────────────
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    macosArm64()
    tvosArm64()
    tvosSimulatorArm64()
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()

    // ── Native: Linux ─────────────────────────────────────────────────────────
    linuxX64()
    linuxArm64()

    // ── Native: Windows ───────────────────────────────────────────────────────
    mingwX64()

    // ── Source sets ──────────────────────────────────────────────────────────
    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.ktor.client.core)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(libs.ksoup)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        // JVM: use OkHttp engine (keeps full API parity with original NiceHttp)
        val jvmCommonMain by creating {
            dependsOn(commonMain)
            dependencies {
                api(libs.ktor.client.okhttp)
                // Expose OkHttp extras so callers can still configure DNS-over-HTTPS, etc.
                api(libs.okhttp)
                api(libs.okhttp.dnsoverhttps)
            }
        }

        val androidMain by getting {
            dependsOn(jvmCommonMain)
        }

        val jvmMain by getting {
            dependsOn(jvmCommonMain)
        }

        // JS: browser/Node.js Ktor engine
        val jsMain by getting {
            dependencies {
                api(libs.ktor.client.js)
            }
        }

        // WASM/JS: browser/Node.js Ktor engine
        val wasmJsMain by getting {
            dependencies {
                api(libs.ktor.client.js)
            }
        }

        // Apple targets (Darwin engine covers iOS/macOS/tvOS/watchOS)
        val appleMain by getting {
            dependencies {
                api(libs.ktor.client.darwin)
            }
        }

        // Linux targets
        val linuxMain by getting {
            dependencies {
                api(libs.ktor.client.curl)
            }
        }

        // Windows targets
        val mingwMain by getting {
            dependencies {
                api(libs.ktor.client.winhttp)
            }
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}
