package com.snatik.storage

import java.util.Locale

/** Binary size units, 1024-based. */
public enum class SizeUnit(public val bytes: Long) {
    B(1L),
    KB(1L shl 10),
    MB(1L shl 20),
    GB(1L shl 30),
    TB(1L shl 40);

    /** Express [byteCount] in this unit. */
    public fun convert(byteCount: Long): Double = byteCount.toDouble() / bytes

    public companion object {
        /**
         * Human readable size such as `512 B`, `1.5 KB`, `20.3 MB`.
         */
        public fun readable(byteCount: Long): String {
            val unit = entries.lastOrNull { byteCount >= it.bytes } ?: B
            return if (unit == B) {
                "$byteCount B"
            } else {
                String.format(Locale.US, "%.1f %s", unit.convert(byteCount), unit.name)
            }
        }
    }
}

/** Human readable size, see [SizeUnit.readable]. */
public fun Long.toReadableSize(): String = SizeUnit.readable(this)
