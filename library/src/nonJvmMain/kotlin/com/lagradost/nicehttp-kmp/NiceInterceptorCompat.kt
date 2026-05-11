package com.lagradost.nicehttp

actual class NiceInterceptorCompat(val interceptor: Interceptor)
internal actual fun NiceInterceptorCompat.toInterceptor(): Interceptor = interceptor
