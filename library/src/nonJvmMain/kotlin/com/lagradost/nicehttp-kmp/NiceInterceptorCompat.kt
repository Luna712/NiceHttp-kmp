package com.lagradost.nicehttp

actual interface NiceInterceptorCompat
internal actual fun NiceInterceptorCompat.toInterceptor(): Interceptor =
    this as? Interceptor ?: throw UnsupportedOperationException(
        "NiceInterceptorCompat must be an Interceptor on non-JVM platforms"
    )

