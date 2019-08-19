package com.snatik.storage.helpers

import java.io.File
import java.util.Comparator

enum class OrderType {

    NAME,
    /**
     * Last modified is the first
     */
    DATE,
    /**
     * Smaller size will be in the first place
     */
    SIZE;

    // name
    // date
    // size
    val comparator: Comparator<File>?
        get() {
            when (ordinal) {
                0 -> return comparatorForName()
                1 -> return comparatorForDate()
                2 -> return comparatorForSize()
                else -> {
                }
            }
            return null
        }

    private fun comparatorForName(): Comparator<File> {
        return Comparator { lhs, rhs -> lhs.name.compareTo(rhs.name) }
    }

    private fun comparatorForDate(): Comparator<File> {
        return Comparator { lhs, rhs -> (rhs.lastModified() - lhs.lastModified()).toInt() }
    }

    private fun comparatorForSize(): Comparator<File> {
        return Comparator { lhs, rhs -> (lhs.length() - rhs.length()).toInt() }
    }


}
