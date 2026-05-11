# NiceHttp KMP

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

## Installation

### JitPack

```kotlin
// settings.gradle.kts
repositories {
    maven("https://jitpack.io")
}

// build.gradle.kts

// Non-KMP Android/JVM app:
dependencies {
    implementation("com.github.Luna712.NiceHttp-kmp:library-android:TAG")
}

// KMP project:
dependencies {
    implementation("com.github.Luna712.NiceHttp-kmp:library:TAG")
}
```

## Quick start

### New builder API

```kotlin
import com.lagradost.nicehttp.Requests
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

val app = Requests()

// GET
val response = app.get("https://httpbin.org/get") {
    headers = mapOf("X-Custom" to "header")
    params = mapOf("page" to "2")
    timeout = 30.seconds
    cacheTime = 1.days
    verify = false
}

println(response.code)          // 200
println(response.text)          // body as String
println(response.ksoupDocument) // parsed as ksoup Document (all platforms)
println(response.document)      // org.jsoup.Document on JVM/Android, ksoup on others

// POST with JSON
app.post("https://httpbin.org/post") {
    json = JsonAsString("""{"name":"Alice"}""")
    timeout = 10.seconds
}

// POST with form data
app.post("https://httpbin.org/post") {
    data = mapOf("key" to "value")
}

// POST with typed JSON via SerializationResponseParser
@Serializable data class Payload(val name: String)
app.post("https://httpbin.org/post") {
    json = Payload("Alice")
}

// Typed JSON response parsing
@Serializable data class Bin(val origin: String)
val bin = app.get("https://httpbin.org/get").parsed<Bin>()
println(bin.origin)
```

### Original NiceHttp API (back-compat; deprecated, JVM/Android)

All original call sites work unchanged:

```kotlin
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor

// Original API still works on JVM/Android
app.get(
    url,
    headers = mapOf("X-Custom" to "header"),
    cacheTime = 1,
    cacheUnit = TimeUnit.DAYS,
    timeout = 30L,
    interceptor = myOkHttpInterceptor,
)

app.post(
    url,
    requestBody = "{}".toRequestBody("application/json".toMediaType()),
    interceptor = myOkHttpInterceptor,
)
```

## Sessions (cookie persistence)

```kotlin
import com.lagradost.nicehttp.Session

val session = Session()
session.post("https://example.com/login") {
    data = mapOf("user" to "alice", "pass" to "s3cr3t")
}
val profile = session.get("https://example.com/profile") // cookies sent automatically
```

## Interceptors

### Built-in interceptors

```kotlin
// Retry failed requests
app.addInterceptor(RetryInterceptor(maxRetries = 3))

// Add headers to every request
app.addInterceptor(HeadersInterceptor(mapOf("Authorization" to "Bearer $token")))

// Log requests and responses
app.addInterceptor(LoggingInterceptor())

// Fallback to a different URL on failure
app.addInterceptor(FallbackUrlInterceptor(primaryUrl, fallbackUrl))

// Per-request interceptor
app.get(url) {
    interceptor = LoggingInterceptor()
}
```

### Custom KMP interceptor

```kotlin
class MyInterceptor : Interceptor {
    override suspend fun intercept(ctx: HttpSendInterceptorContext): HttpClientCall {
        ctx.request.headers.append("X-Custom", "value")
        val call = ctx.proceed()
        println("Response: ${call.response.status.value}")
        return call
    }
}
```

### OkHttp interceptor bridge (JVM/Android)

These should be considered deprecated and will eventually be removed.

```kotlin
// Convert okhttp3.Interceptor to KMP Interceptor (may not always work for complex interceptors)
val kmpInterceptor = myOkHttpInterceptor.toNiceInterceptor()

// Convert KMP Interceptor to okhttp3.Interceptor
val okHttpInterceptor = myKmpInterceptor.toOkHttpInterceptor()
```

## Multipart / file upload

```kotlin
app.post("https://httpbin.org/post") {
    files = listOf(
        NiceFile(name = "photo", fileName = "cat.png", bytes = imageBytes, fileType = "image/png"),
        NiceFile(name = "caption", value = "My cat"),
    )
}
```

## Response

```kotlin
val response = app.get(url)

response.code           // Int - HTTP status code
response.text           // String - body (cached, guarded at 5MB)
response.textLarge      // String - body without size guard
response.body.bytes()   // ByteArray
response.body.string()  // String
response.body.source()  // okio.BufferedSource on JVM, PlatformSource on others
response.cookies        // Map<String, String>
response.headers        // NiceHeaders - ktor Headers but also includes .toMap(), .toMultiMap() for back-compat
response.header("Content-Type") // String? - convenience header access for back-compat
response.isSuccessful   // Boolean - true if 2xx
response.url            // String - final URL after redirects

// HTML parsing
response.document           // org.jsoup.Document on JVM/Android, ksoup on others
response.documentLarge      // same but without size guard
response.ksoupDocument      // always com.fleeksoft.ksoup.nodes.Document
response.ksoupDocumentLarge // same but without size guard

// JSON parsing (requires SerializationResponseParser)
response.parsed<MyClass>()
response.parsedSafe<MyClass>()
response.parsedLarge<MyClass>()
response.parsedSafeLarge<MyClass>()
```

## JSON parsing

```kotlin
val app = Requests(responseParser = SerializationResponseParser())

@Serializable data class Repo(val name: String, val stargazers_count: Int)

val repo = app.get("https://api.github.com/repos/Kotlin/kotlinx.coroutines")
              .parsed<Repo>()
```

Custom parser:

```kotlin
class GsonParser : ResponseParser {
    private val gson = Gson()
    override fun <T : Any> parse(text: String, kClass: KClass<T>): T =
        gson.fromJson(text, kClass.java)
    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? =
        runCatching { gson.fromJson(text, kClass.java) }.getOrNull()
    override fun writeValueAsString(obj: Any): String = gson.toJson(obj)
}
```

## JVM/Android: OkHttp extras

```kotlin
import com.lagradost.nicehttp.addGenericDns
import io.ktor.client.engine.okhttp.*
import okhttp3.OkHttpClient

val okClient = OkHttpClient.Builder()
    .addGenericDns(
        url = "https://dns.cloudflare.com/dns-query",
        ips = listOf("1.1.1.1", "1.0.0.1"),
    )
    .build()

val app = Requests(
    baseClient = HttpClient(OkHttp) {
        engine { preconfigured = okClient }
    }
)
```

## Android: initialization with context

```kotlin
// In Application.onCreate() or similar
app.initClient(context)

// With SSL bypass (debug only)
app.initClient(context, ignoreSSL = true)
```

## What changed from the original

| Original | KMP version |
|---|---|
| `OkHttpClient` directly | `HttpClient` (Ktor) - engine chosen per platform |
| `java.util.concurrent.TimeUnit` | `kotlin.time.Duration` (new API) / `NiceTimeUnit` (compat) |
| `okhttp3.Interceptor` | KMP `Interceptor` (new API) / `NiceInterceptorCompat` (compat) |
| `okhttp3.RequestBody` | KMP `RequestBody` (new API) / `NiceRequestBodyCompat` (compat) |
| `Jsoup.parse()` | `Ksoup.parse()` on all platforms, `Jsoup.parse()` on JVM via `.document` |
| `response.text` (lazy val) | `response.text` (lazy val, same API) |
| `response.document` | `org.jsoup.Document` on JVM/Android, ksoup on others |
| `response.headers.toMap()` | `response.headers.toMap()` - no import needed via `NiceHeaders` |
| `body.byteStream()` | `body.byteStream()` - `InputStream` on JVM, `PlatformInputStream` on others |
| `body.source()` | `body.source()` - `BufferedSource` on JVM, `PlatformSource` on others |

## Back compat - will eventually be removed

All original NiceHttp call sites work unchanged on JVM/Android via compat types:
- `NiceTimeUnit` = `java.util.concurrent.TimeUnit` on JVM, `kotlin.time.DurationUnit` on others
- `NiceInterceptorCompat` = `okhttp3.Interceptor` on JVM, KMP `Interceptor` on others
- `NiceRequestBodyCompat` = `okhttp3.RequestBody` on JVM, KMP `RequestBody` on others

These will be removed in a future version once all call sites are migrated.
