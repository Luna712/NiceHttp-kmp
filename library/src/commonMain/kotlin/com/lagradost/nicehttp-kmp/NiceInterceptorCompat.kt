package com.lagradost.nicehttp

expect interface NiceInterceptorCompat
internal expect fun NiceInterceptorCompat.toInterceptor(): Interceptor
