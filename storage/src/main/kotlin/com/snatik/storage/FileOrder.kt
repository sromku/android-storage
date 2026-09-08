package com.snatik.storage

import java.io.File

/** Orderings for directory listings. */
public enum class FileOrder(public val comparator: Comparator<File>) {
    /** Case-insensitive by name. */
    NAME(compareBy { it.name.lowercase() }),

    /** Most recently modified first. */
    NEWEST_FIRST(compareByDescending<File> { it.lastModified() }.thenBy { it.name.lowercase() }),

    /** Smallest first. Directories report a length of 0. */
    SMALLEST_FIRST(compareBy<File> { it.length() }.thenBy { it.name.lowercase() }),

    /** Largest first. Directories report a length of 0. */
    LARGEST_FIRST(compareByDescending<File> { it.length() }.thenBy { it.name.lowercase() }),

    /** Directories before files, each group by name. */
    DIRECTORIES_FIRST(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }),
}
