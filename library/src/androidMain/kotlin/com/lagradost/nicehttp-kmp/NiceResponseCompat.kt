package com.lagradost.nicehttp.kmp

import io.ktor.client.engine.okhttp.*

/**
 * Returns the underlying OkHttp Response if the engine is OkHttp, otherwise throws.
 * Used by WebViewResolver which needs to inspect raw OkHttp response internals.
 */
val NiceResponse.okhttpResponse: okhttp3.Response
    get() {
        val call = response.call
        return (call as? OkHttpCall)?.response
            ?: throw UnsupportedOperationException(
                "okhttpResponse is only available when using the OkHttp engine"
            )
    }
