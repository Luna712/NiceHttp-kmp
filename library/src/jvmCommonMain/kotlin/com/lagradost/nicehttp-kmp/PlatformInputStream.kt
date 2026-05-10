package com.lagradost.nicehttp.kmp

import java.io.InputStream

actual typealias PlatformInputStream = InputStream
internal actual fun ByteArray.toPlatformInputStream(): PlatformInputStream = inputStream()
