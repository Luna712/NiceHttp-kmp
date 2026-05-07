package com.lagradost.nicehttp.kmp

import io.ktor.client.*

/**
 * Returns a default [HttpClient] with the platform's preferred engine already installed.
 *
 * | Platform      | Engine   |
 * |---------------|----------|
 * | JVM           | OkHttp   |
 * | Android       | OkHttp   |
 * | JS / WASM/JS  | Js       |
 * | iOS / macOS   | Darwin   |
 * | Linux         | Curl     |
 * | Windows       | WinHttp  |
 *
 * You can always supply your own `HttpClient` to [Requests] or [Session] instead.
 */
expect fun defaultHttpClient(): HttpClient
