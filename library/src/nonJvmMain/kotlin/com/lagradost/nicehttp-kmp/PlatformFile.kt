package com.lagradost.nicehttp

actual class PlatformFile(val bytes: ByteArray, val name: String)

internal actual fun PlatformFile.toBytes(): ByteArray = bytes
internal actual fun PlatformFile.toFileName(): String = name
