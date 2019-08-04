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
                0 -> return Comparator { lhs, rhs -> lhs.name.compareTo(rhs.name) }
                1 -> return Comparator { lhs, rhs -> (rhs.lastModified() - lhs.lastModified()).toInt() }
                2 -> return Comparator { lhs, rhs -> (lhs.length() - rhs.length()).toInt() }
                else -> {
                }
            }
            return null
        }
}
