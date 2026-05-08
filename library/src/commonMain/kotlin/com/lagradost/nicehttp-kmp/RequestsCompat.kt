package com.lagradost.nicehttp.kmp

import kotlin.time.Duration
import kotlin.time.DurationUnit

// ── Back-compat overloads with interceptor, verify, cacheTime, timeout as Long ─

suspend fun Requests.get(
    url: String,
    headers: Map<String, String> = emptyMap(),
    referer: String? = null,
    params: Map<String, String> = emptyMap(),
    cookies: Map<String, String> = emptyMap(),
    allowRedirects: Boolean = true,
    cacheTime: Int = 0,
    cacheUnit: DurationUnit = DurationUnit.MINUTES,
    timeout: Long = 0L,
    interceptor: Interceptor? = null,
    verify: Boolean = true,
    responseParser: ResponseParser? = this.responseParser,
) = custom(
    "GET", url, headers, referer, params, cookies,
    null, null, null, null, allowRedirects,
    if (timeout > 0) timeout.toDuration(DurationUnit.SECONDS) else Duration.ZERO,
    interceptor, responseParser,
)

suspend fun Requests.post(
    url: String,
    headers: Map<String, String> = emptyMap(),
    referer: String? = null,
    params: Map<String, String> = emptyMap(),
    cookies: Map<String, String> = emptyMap(),
    data: Map<String, String>? = this.defaultData,
    files: List<NiceFile>? = null,
    json: Any? = null,
    requestBody: RequestBody? = null,
    allowRedirects: Boolean = true,
    cacheTime: Int = 0,
    cacheUnit: DurationUnit = DurationUnit.MINUTES,
    timeout: Long = 0L,
    interceptor: Interceptor? = null,
    verify: Boolean = true,
    responseParser: ResponseParser? = this.responseParser,
) = custom(
    "POST", url, headers, referer, params, cookies,
    data, files, json, requestBody, allowRedirects,
    if (timeout > 0) timeout.toDuration(DurationUnit.SECONDS) else Duration.ZERO,
    interceptor, responseParser,
)

suspend fun Requests.put(
    url: String,
    headers: Map<String, String> = emptyMap(),
    referer: String? = null,
    params: Map<String, String> = emptyMap(),
    cookies: Map<String, String> = emptyMap(),
    data: Map<String, String>? = this.defaultData,
    files: List<NiceFile>? = null,
    json: Any? = null,
    requestBody: RequestBody? = null,
    allowRedirects: Boolean = true,
    cacheTime: Int = 0,
    cacheUnit: DurationUnit = DurationUnit.MINUTES,
    timeout: Long = 0L,
    interceptor: Interceptor? = null,
    verify: Boolean = true,
    responseParser: ResponseParser? = this.responseParser,
) = custom(
    "PUT", url, headers, referer, params, cookies,
    data, files, json, requestBody, allowRedirects,
    if (timeout > 0) timeout.toDuration(DurationUnit.SECONDS) else Duration.ZERO,
    interceptor, responseParser,
)

suspend fun Requests.delete(
    url: String,
    headers: Map<String, String> = emptyMap(),
    referer: String? = null,
    params: Map<String, String> = emptyMap(),
    cookies: Map<String, String> = emptyMap(),
    data: Map<String, String>? = this.defaultData,
    files: List<NiceFile>? = null,
    json: Any? = null,
    requestBody: RequestBody? = null,
    allowRedirects: Boolean = true,
    cacheTime: Int = 0,
    cacheUnit: DurationUnit = DurationUnit.MINUTES,
    timeout: Long = 0L,
    interceptor: Interceptor? = null,
    verify: Boolean = true,
    responseParser: ResponseParser? = this.responseParser,
) = custom(
    "DELETE", url, headers, referer, params, cookies,
    data, files, json, requestBody, allowRedirects,
    if (timeout > 0) timeout.toDuration(DurationUnit.SECONDS) else Duration.ZERO,
    interceptor, responseParser,
)

suspend fun Requests.head(
    url: String,
    headers: Map<String, String> = emptyMap(),
    referer: String? = null,
    params: Map<String, String> = emptyMap(),
    cookies: Map<String, String> = emptyMap(),
    allowRedirects: Boolean = true,
    cacheTime: Int = 0,
    cacheUnit: DurationUnit = DurationUnit.MINUTES,
    timeout: Long = 0L,
    interceptor: Interceptor? = null,
    verify: Boolean = true,
    responseParser: ResponseParser? = this.responseParser,
) = custom(
    "HEAD", url, headers, referer, params, cookies,
    null, null, null, null, allowRedirects,
    if (timeout > 0) timeout.toDuration(DurationUnit.SECONDS) else Duration.ZERO,
    interceptor, responseParser,
)

suspend fun Requests.patch(
    url: String,
    headers: Map<String, String> = emptyMap(),
    referer: String? = null,
    params: Map<String, String> = emptyMap(),
    cookies: Map<String, String> = emptyMap(),
    data: Map<String, String>? = this.defaultData,
    files: List<NiceFile>? = null,
    json: Any? = null,
    requestBody: RequestBody? = null,
    allowRedirects: Boolean = true,
    cacheTime: Int = 0,
    cacheUnit: DurationUnit = DurationUnit.MINUTES,
    timeout: Long = 0L,
    interceptor: Interceptor? = null,
    verify: Boolean = true,
    responseParser: ResponseParser? = this.responseParser,
) = custom(
    "PATCH", url, headers, referer, params, cookies,
    data, files, json, requestBody, allowRedirects,
    if (timeout > 0) timeout.toDuration(DurationUnit.SECONDS) else Duration.ZERO,
    interceptor, responseParser,
)

suspend fun Requests.options(
    url: String,
    headers: Map<String, String> = emptyMap(),
    referer: String? = null,
    params: Map<String, String> = emptyMap(),
    cookies: Map<String, String> = emptyMap(),
    data: Map<String, String>? = this.defaultData,
    files: List<NiceFile>? = null,
    json: Any? = null,
    requestBody: RequestBody? = null,
    allowRedirects: Boolean = true,
    cacheTime: Int = 0,
    cacheUnit: DurationUnit = DurationUnit.MINUTES,
    timeout: Long = 0L,
    interceptor: Interceptor? = null,
    verify: Boolean = true,
    responseParser: ResponseParser? = this.responseParser,
) = custom(
    "OPTIONS", url, headers, referer, params, cookies,
    data, files, json, requestBody, allowRedirects,
    if (timeout > 0) timeout.toDuration(DurationUnit.SECONDS) else Duration.ZERO,
    interceptor, responseParser,
)
