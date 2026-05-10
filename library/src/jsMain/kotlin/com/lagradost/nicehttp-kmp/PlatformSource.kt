package com.lagradost.nicehttp.kmp

actual class PlatformSource internal constructor(private val data: ByteArray) {
    private var position = 0
    fun readByteArray(): ByteArray = data
    fun exhausted(): Boolean = position >= data.size
    fun close() = Unit
}

internal actual fun ByteArray.toPlatformSource(): PlatformSource = PlatformSource(this)
