package com.lagradost.nicehttp

actual typealias NiceTimeUnit = java.util.concurrent.TimeUnit

internal actual fun NiceTimeUnit.toDurationUnit(): kotlin.time.DurationUnit = when (this) {
    java.util.concurrent.TimeUnit.NANOSECONDS  -> kotlin.time.DurationUnit.NANOSECONDS
    java.util.concurrent.TimeUnit.MICROSECONDS -> kotlin.time.DurationUnit.MICROSECONDS
    java.util.concurrent.TimeUnit.MILLISECONDS -> kotlin.time.DurationUnit.MILLISECONDS
    java.util.concurrent.TimeUnit.SECONDS      -> kotlin.time.DurationUnit.SECONDS
    java.util.concurrent.TimeUnit.MINUTES      -> kotlin.time.DurationUnit.MINUTES
    java.util.concurrent.TimeUnit.HOURS        -> kotlin.time.DurationUnit.HOURS
    java.util.concurrent.TimeUnit.DAYS         -> kotlin.time.DurationUnit.DAYS
}
