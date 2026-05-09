package com.lagradost.nicehttp.kmp

/**
 * Restores original NiceHttp cookie extensions on okhttp3.Request and okhttp3.Response.
 * Will eventually be deprecated, this only exists to aid in migration.
 */

/**
 * Drop-in for original NiceHttp's [cookies] extension on [okhttp3.Request].
 * Delegates to [getRequestCookies] from commonMain.
 */
val okhttp3.Request.cookies: Map<String, String>
    get() = headers.toKtorHeaders().getRequestCookies()

/**
 * Drop-in for original NiceHttp's [cookies] extension on [okhttp3.Response].
 * Delegates to [getSetCookies] from commonMain.
 */
val okhttp3.Response.cookies: Map<String, String>
    get() = headers.toKtorHeaders().getSetCookies()

private fun okhttp3.Headers.toKtorHeaders(): io.ktor.http.Headers =
    io.ktor.http.Headers.build {
        forEach { (k, v) -> append(k, v) }
    }
