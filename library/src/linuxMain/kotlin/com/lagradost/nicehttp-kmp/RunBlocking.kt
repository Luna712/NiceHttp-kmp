package com.lagradost.nicehttp

import kotlinx.coroutines.runBlocking

internal actual fun <T> runBlockingCompat(block: suspend () -> T): T = runBlocking { block() }