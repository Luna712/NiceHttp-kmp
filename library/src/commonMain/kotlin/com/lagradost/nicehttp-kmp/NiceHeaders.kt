package com.lagradost.nicehttp.kmp

import io.ktor.http.*

class NiceHeaders(private val delegate: Headers) : Headers by delegate {

    /** Converts headers to a plain Map, keeping the last value for duplicate keys. */
    fun toMap(): Map<String, String> =
        entries().associate { (key, values) -> key to values.last() }

    /** Converts headers to a Map with all values per key. */
    fun toMultiMap(): Map<String, List<String>> =
        entries().associate { (key, values) -> key to values }
}
