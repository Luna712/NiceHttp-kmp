package com.lagradost.nicehttp

actual typealias NiceDocument = com.fleeksoft.ksoup.nodes.Document
internal actual fun parseDocument(html: String): NiceDocument = com.fleeksoft.ksoup.Ksoup.parse(html)
