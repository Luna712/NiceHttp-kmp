package com.lagradost.nicehttp

expect class NiceInterceptorCompat
internal expect fun NiceInterceptorCompat.toInterceptor(): Interceptor
