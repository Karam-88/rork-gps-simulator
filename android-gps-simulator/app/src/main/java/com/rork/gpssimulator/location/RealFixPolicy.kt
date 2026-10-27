package com.rork.gpssimulator.location

/** Monotonic time only: wall-clock changes cannot turn cached fixes into new fixes. */
internal object RealFixPolicy {
    const val MAX_AGE_NANOS = 30_000_000_000L

    fun accepts(isMock: Boolean, fixNanos: Long, nowNanos: Long, afterNanos: Long,
                callbackGeneration: Long, currentGeneration: Long): Boolean =
        callbackGeneration == currentGeneration && !isMock && fixNanos > 0 &&
            fixNanos >= afterNanos && fixNanos <= nowNanos &&
            nowNanos - fixNanos <= MAX_AGE_NANOS
}
