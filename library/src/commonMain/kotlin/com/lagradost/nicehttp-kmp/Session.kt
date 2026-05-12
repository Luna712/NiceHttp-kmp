package com.lagradost.nicehttp

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*

/**
 * A [Requests] subclass that automatically persists cookies across all requests,
 * mirroring the original NiceHttp `Session`.
 *
 * Cookies are stored in an [AcceptAllCookiesStorage] (in-memory) and are sent back
 * to the server on subsequent calls.
 *
 * Example:
 * ```kotlin
 * val session = Session()
 * session.post("https://example.com/login", data = mapOf("user" to "alice", "pass" to "secret"))
 * val profile = session.get("https://example.com/profile") // session cookie is sent automatically
 * ```
 */
class Session(
    client: HttpClient = defaultHttpClient(),
    defaultHeaders: Map<String, String> = mapOf(HttpHeaders.UserAgent to "NiceHttp"),
) : Requests(
    baseClient = client.config {
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
    },
    defaultHeaders = defaultHeaders,
)
