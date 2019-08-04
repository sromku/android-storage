package com.snatik.storage.security

/**
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 */
enum class CipherPaddingType private constructor(
        /**
         * Get the algorithm name of the enum value.
         *
         * @return The algorithm name
         */
        val algorithmName: String) {
    NoPadding("NoPadding"),
    PKCS5Padding("PKCS5Padding"),
    PKCS1Padding("PKCS1Padding"),
    OAEPWithSHA_1AndMGF1Padding("OAEPWithSHA-1AndMGF1Padding"),
    OAEPWithSHA_256AndMGF1Padding("OAEPWithSHA-256AndMGF1Padding")
}
