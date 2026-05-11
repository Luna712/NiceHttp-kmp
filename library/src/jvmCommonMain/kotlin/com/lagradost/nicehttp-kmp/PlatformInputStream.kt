package com.lagradost.nicehttp

import java.io.InputStream

actual typealias PlatformInputStream = InputStream
internal actual fun ByteArray.toPlatformInputStream(): PlatformInputStream = inputStream()
