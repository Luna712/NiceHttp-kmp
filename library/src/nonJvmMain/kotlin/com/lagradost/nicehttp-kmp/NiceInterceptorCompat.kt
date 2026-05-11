package com.lagradost.nicehttp

actual typealias NiceInterceptorCompat = Interceptor
internal actual fun NiceInterceptorCompat.toInterceptor(): Interceptor = this
