package com.lagradost.nicehttp

actual typealias NiceDocument = org.jsoup.nodes.Document
internal actual fun parseDocument(html: String): NiceDocument = org.jsoup.Jsoup.parse(html)
