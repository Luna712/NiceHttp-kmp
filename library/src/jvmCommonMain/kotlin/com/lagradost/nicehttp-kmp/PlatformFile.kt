package com.lagradost.nicehttp

actual typealias PlatformFile = java.io.File

internal actual fun PlatformFile.toBytes(): ByteArray = readBytes()
internal actual fun PlatformFile.toFileName(): String = name
