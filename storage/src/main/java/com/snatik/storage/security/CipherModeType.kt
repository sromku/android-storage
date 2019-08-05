package com.snatik.storage.security

/**
 * *https://en.wikipedia.org/wiki/Block_cipher_modes_of_operation*
 *
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 */
enum class CipherModeType(
        /**
         * Get the algorithm name of the enum value.
         *
         * @return The algorithm name
         */
        val algorithmName: String) {
    /**
     * Cipher Block Chaining Mode
     */
    CBC("CBC"),

    /**
     * Electronic Codebook Mode
     */
    ECB("ECB")
}
