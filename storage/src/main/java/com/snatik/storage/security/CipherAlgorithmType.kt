package com.snatik.storage.security

import javax.crypto.Cipher
import javax.crypto.CipherOutputStream

/**
 * The algorithm of the [Cipher]. Those algorithms could be used while
 * encrypting the files by using [CipherOutputStream]. <br></br>
 * <br></br>
 *
 *
 * *http://developer.android.com/reference/javax/crypto/Cipher.html*
 * *http://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html*
 * *http://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#Cipher*
 *
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 */
enum class CipherAlgorithmType(
        /**
         * Get the algorithm name of the enum value.
         *
         * @return The algorithm name
         */
        val algorithmName: String) {
    /**
     * Advanced Encryption Standard as specified by NIST in FIPS 197. Also known
     * as the Rijndael algorithm by Joan Daemen and Vincent Rijmen, AES is a
     * 128-bit block cipher supporting keys of 128, 192, and 256 bits.
     */
    AES("AES"),

    /**
     * The Digital Encryption Standard.
     */
    DES("DES"),

    /**
     * Triple DES Encryption (also known as DES-EDE, 3DES, or Triple-DES). Data
     * is encrypted using the DES algorithm three separate times. It is first
     * encrypted using the first subkey, then decrypted with the second subkey,
     * and encrypted with the third subkey.
     */
    DESede("DESede"),

    /**
     * The RSA encryption algorithm
     */
    RSA("RSA")
}
