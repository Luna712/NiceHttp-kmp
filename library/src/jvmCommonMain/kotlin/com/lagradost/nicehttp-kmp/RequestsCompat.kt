package com.lagradost.nicehttp.kmp

import java.util.concurrent.TimeUnit

/**
 * Accepts okhttp3.Interceptor, okhttp3.RequestBody, and java.util.concurrent.TimeUnit
 * for full source compatibility with the original NiceHttp library.
 * This will eventually be removed.
 */

open class OkHttpRequests(
    baseClient: io.ktor.client.HttpClient = defaultHttpClient(),
    defaultHeaders: Map<String, String> = mapOf("user-agent" to "NiceHttp"),
    defaultReferer: String? = null,
    defaultData: Map<String, String> = emptyMap(),
    defaultCookies: Map<String, String> = emptyMap(),
    defaultCacheTime: Int = 0,
    defaultCacheUnit: kotlin.time.DurationUnit = kotlin.time.DurationUnit.MINUTES,
    defaultTimeOut: Long = 0L,
    responseParser: ResponseParser? = null,
    interceptors: MutableList<Interceptor> = mutableListOf(),
) : Requests(
    baseClient, defaultHeaders, defaultReferer, defaultData, defaultCookies,
    defaultCacheTime, defaultCacheUnit, defaultTimeOut, responseParser, interceptors,
) {
    private fun TimeUnit.toKotlinDurationUnit(): kotlin.time.DurationUnit = when (this) {
        TimeUnit.NANOSECONDS  -> kotlin.time.DurationUnit.NANOSECONDS
        TimeUnit.MICROSECONDS -> kotlin.time.DurationUnit.MICROSECONDS
        TimeUnit.MILLISECONDS -> kotlin.time.DurationUnit.MILLISECONDS
        TimeUnit.SECONDS      -> kotlin.time.DurationUnit.SECONDS
        TimeUnit.MINUTES      -> kotlin.time.DurationUnit.MINUTES
        TimeUnit.HOURS        -> kotlin.time.DurationUnit.HOURS
        TimeUnit.DAYS         -> kotlin.time.DurationUnit.DAYS
    }

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = TimeUnit.MINUTES,
        timeout: Long = defaultTimeOut,
        interceptor: okhttp3.Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = get(
        url, headers, referer, params, cookies, allowRedirects,
        cacheTime, cacheUnit.toKotlinDurationUnit(), timeout,
        interceptor?.toNiceInterceptor(), verify, responseParser,
    )

    suspend fun post(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: okhttp3.RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = TimeUnit.MINUTES,
        timeout: Long = defaultTimeOut,
        interceptor: okhttp3.Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = post(
        url, headers, referer, params, cookies, data, files, json,
        requestBody?.toNiceRequestBody(),
        allowRedirects, cacheTime, cacheUnit.toKotlinDurationUnit(), timeout,
        interceptor?.toNiceInterceptor(), verify, responseParser,
    )

    suspend fun put(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: okhttp3.RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = TimeUnit.MINUTES,
        timeout: Long = defaultTimeOut,
        interceptor: okhttp3.Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = put(
        url, headers, referer, params, cookies, data, files, json,
        requestBody?.toNiceRequestBody(),
        allowRedirects, cacheTime, cacheUnit.toKotlinDurationUnit(), timeout,
        interceptor?.toNiceInterceptor(), verify, responseParser,
    )

    suspend fun delete(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: okhttp3.RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = TimeUnit.MINUTES,
        timeout: Long = defaultTimeOut,
        interceptor: okhttp3.Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = delete(
        url, headers, referer, params, cookies, data, files, json,
        requestBody?.toNiceRequestBody(),
        allowRedirects, cacheTime, cacheUnit.toKotlinDurationUnit(), timeout,
        interceptor?.toNiceInterceptor(), verify, responseParser,
    )

    suspend fun head(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = TimeUnit.MINUTES,
        timeout: Long = defaultTimeOut,
        interceptor: okhttp3.Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = head(
        url, headers, referer, params, cookies, allowRedirects,
        cacheTime, cacheUnit.toKotlinDurationUnit(), timeout,
        interceptor?.toNiceInterceptor(), verify, responseParser,
    )

    suspend fun patch(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: okhttp3.RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = TimeUnit.MINUTES,
        timeout: Long = defaultTimeOut,
        interceptor: okhttp3.Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = patch(
        url, headers, referer, params, cookies, data, files, json,
        requestBody?.toNiceRequestBody(),
        allowRedirects, cacheTime, cacheUnit.toKotlinDurationUnit(), timeout,
        interceptor?.toNiceInterceptor(), verify, responseParser,
    )

    suspend fun options(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: okhttp3.RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = TimeUnit.MINUTES,
        timeout: Long = defaultTimeOut,
        interceptor: okhttp3.Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = options(
        url, headers, referer, params, cookies, data, files, json,
        requestBody?.toNiceRequestBody(),
        allowRedirects, cacheTime, cacheUnit.toKotlinDurationUnit(), timeout,
        interceptor?.toNiceInterceptor(), verify, responseParser,
    )
}

typealias Requests = OkHttpRequests
