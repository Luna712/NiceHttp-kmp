package com.lagradost.nicehttp.kmp

import okio.Buffer
import okio.BufferedSource

actual typealias PlatformSource = BufferedSource
internal actual fun ByteArray.toPlatformSource(): PlatformSource = Buffer().write(this)
