package com.lagradost.nicehttp.kmp

import io.ktor.http.*

// ── URL helpers ───────────────────────────────────────────────────────────────

/**
 * Appends query-string parameters to [url].
 *
 * Handles both URLs that already have a query string and those that do not.
 */
internal fun addParamsToUrl(url: String, params: Map<String, String?>): String {
    if (params.isEmpty()) return url
    val builder = URLBuilder(url)
    params.forEach { (key, value) ->
        if (value != null) builder.parameters.append(key, value)
    }
    return builder.buildString()
}

// ── Header helpers ────────────────────────────────────────────────────────────

/**
 * Builds the final [Headers] object for a request.
 *
 * Priority (highest to lowest):
 *  1. Explicit per-request [headers]
 *  2. Cookie header derived from [cookie]
 *  3. Referer header derived from [referer]
 *  4. Default headers (applied by the caller before this point)
 *
 * Header names are normalised to lowercase so lookups are case-insensitive.
 */
fun buildHeaders(
    headers: Map<String, String>,
    referer: String?,
    cookie: Map<String, String>,
): Headers = Headers.build {
    referer?.let { append("referer", it) }
    if (cookie.isNotEmpty()) {
        append("Cookie", cookie.entries.joinToString("; ") { "${it.key}=${it.value}" })
    }
    headers.forEach { (k, v) -> append(k, v) }
}
