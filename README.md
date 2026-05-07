# NiceHttp — Kotlin Multiplatform Edition

A full KMP port of [NiceHttp](https://github.com/Blatzar/NiceHttp) that runs on every
Kotlin target: **JVM, Android, JS (browser + Node.js), WASM/JS, iOS, macOS, tvOS, watchOS,
Linux, and Windows**.

## Platform → Engine mapping

| Target(s)                              | Ktor engine |
|----------------------------------------|-------------|
| JVM                                    | OkHttp      |
| Android                                | OkHttp      |
| JS (browser + Node.js)                 | Js          |
| WASM/JS (browser + Node.js)            | Js          |
| iOS / macOS / tvOS / watchOS (Apple)   | Darwin      |
| Linux (x64, arm64)                     | Curl        |
| Windows (mingwX64)                     | WinHttp     |

## What changed from the original

| Original                      | KMP version                                                  |
|-------------------------------|--------------------------------------------------------------|
| `OkHttpClient` directly       | `HttpClient` (Ktor) — engine chosen per platform             |
| `java.util.concurrent.TimeUnit` | `kotlin.time.Duration`                                     |
| `Jsoup.parse()`               | `Ksoup.parse()` (KMP-compatible ksoup library)              |
| `org.json.JSONObject/Array`   | `kotlinx-serialization` + `SerializationResponseParser`     |
| `java.io.File` in `NiceFile`  | `ByteArray` (works everywhere)                               |
| `.text` / `.document` (lazy val) | `suspend fun text()` / `suspend fun document()`          |
| `javax.net.ssl` SSL bypass    | JVM/Android only via `OkHttpExtras.kt`                      |
| DNS-over-HTTPS                | JVM/Android only via `OkHttpExtras.kt`                      |

## Installation

### JitPack (recommended for Android/JVM projects)

```kotlin
// settings.gradle.kts
repositories {
    maven("https://jitpack.io")
}

// build.gradle.kts
dependencies {
    implementation("com.github.YourFork:NiceHttp:<tag>")
}
```

### Local Maven

```bash
./gradlew :library:publishToMavenLocal
```

Then in your project:

```kotlin
dependencies {
    implementation("com.lagradost:nicehttp:1.0.0")
}
```

## Quick start

```kotlin
import com.lagradost.nicehttp.kmp.Requests
import com.lagradost.nicehttp.kmp.SerializationResponseParser
import kotlinx.serialization.Serializable

val app = Requests(responseParser = SerializationResponseParser())

// Simple GET
val response = app.get("https://httpbin.org/get")
println(response.code)           // 200
println(response.text())         // body as String
println(response.document())     // parsed as HTML Document (ksoup)

// Typed JSON parsing
@Serializable data class Bin(val origin: String)
val bin = app.get("https://httpbin.org/get").parsed<Bin>()
println(bin.origin)

// POST with form data
app.post("https://httpbin.org/post", data = mapOf("key" to "value"))

// POST with JSON
@Serializable data class Payload(val name: String)
app.post("https://httpbin.org/post", json = Payload("Alice"))

// Custom headers + query params
app.get(
    "https://httpbin.org/get",
    headers = mapOf("X-Custom" to "header"),
    params  = mapOf("page" to "2"),
)

// With timeout (kotlin.time.Duration)
import kotlin.time.Duration.Companion.seconds
app.get("https://httpbin.org/delay/5", timeout = 10.seconds)
```

## Sessions (cookie persistence)

```kotlin
import com.lagradost.nicehttp.kmp.Session

val session = Session()
session.post("https://example.com/login", data = mapOf("user" to "alice", "pass" to "s3cr3t"))
val profile = session.get("https://example.com/profile") // cookies sent automatically
```

## Multipart / file upload

```kotlin
import com.lagradost.nicehttp.kmp.NiceFile

val imageBytes: ByteArray = /* ... */
app.post(
    "https://httpbin.org/post",
    files = listOf(
        NiceFile(name = "photo", fileName = "cat.png", bytes = imageBytes, fileType = "image/png"),
        NiceFile(name = "caption", value = "My cat"),
    )
)
```

## JVM/Android: OkHttp extras

The JVM and Android source sets expose helpers that work with the underlying OkHttp client:

```kotlin
import com.lagradost.nicehttp.kmp.addGenericDns
import com.lagradost.nicehttp.kmp.defaultHttpClient
import io.ktor.client.engine.okhttp.*
import okhttp3.OkHttpClient

val okClient = OkHttpClient.Builder()
    .addGenericDns(
        url  = "https://dns.cloudflare.com/dns-query",
        ips  = listOf("1.1.1.1", "1.0.0.1"),
    )
    .build()

val app = Requests(
    baseClient = HttpClient(OkHttp) {
        engine { preconfigured = okClient }
    }
)
```

## Bringing your own parser

```kotlin
class GsonParser : ResponseParser {
    private val gson = Gson()
    override fun <T : Any> parse(text: String, kClass: KClass<T>): T =
        gson.fromJson(text, kClass.java)
    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? =
        runCatching { gson.fromJson(text, kClass.java) }.getOrNull()
    override fun writeValueAsString(obj: Any): String =
        gson.toJson(obj)
}

val app = Requests(responseParser = GsonParser())
```

## Key API differences from the original

### Body reading is now `suspend`

The original library used lazy `val` properties backed by OkHttp's synchronous APIs.
In KMP, Ktor body reading is always suspending:

```kotlin
// original
val body = response.text

// KMP
val body = response.text()   // must be called inside a coroutine
```

### `Duration` instead of `TimeUnit`

```kotlin
// original
app.get(url, timeout = 30L, cacheUnit = TimeUnit.SECONDS)

// KMP
import kotlin.time.Duration.Companion.seconds
app.get(url, timeout = 30.seconds)
```

### No `verify = false` on non-JVM

SSL bypass (`ignoreAllSSLErrors`) relies on `javax.net.ssl` and is only available on
JVM/Android.  On other platforms configure the engine directly (e.g. Darwin lets you
set `handleChallenge` in its config block).

## Dependencies

| Library                              | Version  | Purpose                          |
|--------------------------------------|----------|----------------------------------|
| `io.ktor:ktor-client-core`           | 3.1.x    | HTTP client core                 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.x | Coroutines         |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.8.x | JSON (optional) |
| `com.fleeksoft.ksoup:ksoup`          | 0.4.x    | HTML parsing (KMP Jsoup port)    |
| `com.squareup.okhttp3:okhttp`        | 5.x      | JVM/Android engine               |
| `com.squareup.okhttp3:okhttp-dnsoverhttps` | 5.x | JVM/Android DNS-over-HTTPS  |
