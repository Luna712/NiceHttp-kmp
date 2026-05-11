package com.lagradost.nicehttp

internal expect fun <T> runBlockingCompat(block: suspend () -> T): T