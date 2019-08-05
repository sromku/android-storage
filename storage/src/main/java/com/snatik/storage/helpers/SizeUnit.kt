package com.snatik.storage.helpers

import java.text.DecimalFormat

const val BYTES = 1024L
enum class SizeUnit(private val inBytes: Long) {
    B(1),
    KB(BYTES),
    MB(BYTES * BYTES),
    GB(BYTES * BYTES * BYTES),
    TB(BYTES * BYTES * BYTES * BYTES);

    fun inBytes(): Long {
        return inBytes
    }

    companion object {
        fun readableSizeUnit(bytes: Long): String {
            val df = DecimalFormat("0.00")
            return when {
                bytes < KB.inBytes() -> df.format((bytes / B.inBytes().toFloat()).toDouble()) + " B"
                bytes < MB.inBytes() -> df.format((bytes / KB.inBytes().toFloat()).toDouble()) + " KB"
                bytes < GB.inBytes() -> df.format((bytes / MB.inBytes().toFloat()).toDouble()) + " MB"
                else -> df.format((bytes / GB.inBytes().toFloat()).toDouble()) + " GB"
            }
        }
    }
}
