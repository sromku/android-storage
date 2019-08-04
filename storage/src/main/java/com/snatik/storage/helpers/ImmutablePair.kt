package com.snatik.storage.helpers

import java.io.Serializable

/**
 * @param <T>
 * @param <S>
</S></T> */
class ImmutablePair<T, S> : Serializable {

    val element1: T?
    val element2: S?

    constructor() {
        element1 = null
        element2 = null
    }

    constructor(element1: T, element2: S) {
        this.element1 = element1
        this.element2 = element2
    }

    override fun equals(`object`: Any?): Boolean {
        if (`object` !is ImmutablePair<*, *>) {
            return false
        }

        val object1 = `object`.element1
        val object2 = `object`.element2

        return element1 == object1 && element2 == object2
    }

    override fun hashCode(): Int {
        return element1!!.hashCode() shl 16 + element2!!.hashCode()
    }

    companion object {
        private const val serialVersionUID: Long = 40
    }
}
