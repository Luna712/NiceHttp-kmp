package com.lagradost.nicehttp

actual typealias NiceInterceptorCompat = okhttp3.Interceptor
internal actual fun NiceInterceptorCompat.toInterceptor(): Interceptor = toNiceInterceptor()
