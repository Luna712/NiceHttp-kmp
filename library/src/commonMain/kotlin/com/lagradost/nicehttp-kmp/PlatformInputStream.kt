package com.lagradost.nicehttp.kmp

expect abstract class PlatformInputStream {
    open fun read(): Int
    open fun read(buffer: ByteArray, offset: Int, length: Int): Int
    open fun close()
}

internal expect fun ByteArray.toPlatformInputStream(): PlatformInputStream
