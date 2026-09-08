package com.snatik.storage

/** Anything that can be written to a file as bytes. */
public fun interface Storable {
    public fun toBytes(): ByteArray
}
