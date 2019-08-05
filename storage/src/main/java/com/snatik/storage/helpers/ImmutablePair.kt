package com.snatik.storage.helpers

import java.io.Serializable

/**
 * @param <T>
 * @param <S>
</S></T> */
class ImmutablePair<T, S>(element1: T, element2: S) : Serializable {

    val element1: T? = element1
    val element2: S? = element2

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
