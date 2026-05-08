package com.lagradost.nicehttp.kmp

internal expect fun <T> runBlockingCompat(block: suspend () -> T): T