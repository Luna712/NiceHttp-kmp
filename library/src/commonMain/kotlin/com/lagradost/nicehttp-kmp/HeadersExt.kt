@file:JvmName("HeadersExt")
package io.ktor.http

/** Converts Ktor [Headers] to a plain Map, keeping the last value for duplicate keys. */
fun Headers.toMap(): Map<String, String> =
    entries().associate { (key, values) -> key to values.last() }

/** Converts Ktor [Headers] to a Map with all values per key. */
fun Headers.toMultiMap(): Map<String, List<String>> =
    entries().associate { (key, values) -> key to values }
