package com.lagradost.nicehttp

expect enum class NiceTimeUnit {
    NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS
}
internal expect fun NiceTimeUnit.toDurationUnit(): kotlin.time.DurationUnit
