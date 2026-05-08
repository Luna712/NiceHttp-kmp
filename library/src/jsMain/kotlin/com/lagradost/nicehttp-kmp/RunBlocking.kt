package com.lagradost.nicehttp.kmp

internal actual fun <T> runBlockingCompat(block: suspend () -> T): T =
    throw UnsupportedOperationException("Use suspend text() on JS. runBlocking is not supported.")