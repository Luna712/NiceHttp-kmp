package com.lagradost.nicehttp

expect class NiceDocument
internal expect fun parseDocument(html: String): NiceDocument
