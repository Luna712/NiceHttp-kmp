package com.lagradost.nicehttp

internal actual fun <T> runBlockingCompat(block: suspend () -> T): T =
    throw UnsupportedOperationException("Use suspend text() on web targets. runBlocking is not supported.")
