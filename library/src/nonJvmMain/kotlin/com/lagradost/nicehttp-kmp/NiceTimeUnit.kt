package com.lagradost.nicehttp

actual class NiceTimeUnit private constructor(private val name: String) {
    actual companion object {
        actual val NANOSECONDS  = NiceTimeUnit("NANOSECONDS")
        actual val MICROSECONDS = NiceTimeUnit("MICROSECONDS")
        actual val MILLISECONDS = NiceTimeUnit("MILLISECONDS")
        actual val SECONDS      = NiceTimeUnit("SECONDS")
        actual val MINUTES      = NiceTimeUnit("MINUTES")
        actual val HOURS        = NiceTimeUnit("HOURS")
        actual val DAYS         = NiceTimeUnit("DAYS")
    }
}

internal actual fun NiceTimeUnit.toDurationUnit(): kotlin.time.DurationUnit = when (this) {
    NiceTimeUnit.NANOSECONDS  -> kotlin.time.DurationUnit.NANOSECONDS
    NiceTimeUnit.MICROSECONDS -> kotlin.time.DurationUnit.MICROSECONDS
    NiceTimeUnit.MILLISECONDS -> kotlin.time.DurationUnit.MILLISECONDS
    NiceTimeUnit.SECONDS      -> kotlin.time.DurationUnit.SECONDS
    NiceTimeUnit.MINUTES      -> kotlin.time.DurationUnit.MINUTES
    NiceTimeUnit.HOURS        -> kotlin.time.DurationUnit.HOURS
    NiceTimeUnit.DAYS         -> kotlin.time.DurationUnit.DAYS
    else                      -> kotlin.time.DurationUnit.MINUTES
}
