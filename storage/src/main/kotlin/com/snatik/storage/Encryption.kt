package com.snatik.storage

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated encryption for file contents: AES-GCM with a random nonce per message.
 *
 * Output layout: one version byte, a 12 byte nonce, then ciphertext followed by the 16 byte tag.
 * Files written by the 2.x library (AES-CBC) cannot be read with this class.
 *
 * Obtain an instance with [fromKey], [fromPassphrase] or, on Android, `Encryption.fromKeystore`.
 */
public class Encryption internal constructor(private val key: SecretKey) {

    /** Encrypt [plain]. Every call produces different output for the same input. */
    public fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val nonce = cipher.iv
        check(nonce.size == NONCE_SIZE) { "Unexpected GCM nonce size ${nonce.size}" }
        val body = cipher.doFinal(plain)
        val out = ByteArray(HEADER_SIZE + body.size)
        out[0] = VERSION
        nonce.copyInto(out, destinationOffset = 1)
        body.copyInto(out, destinationOffset = HEADER_SIZE)
        return out
    }

    /**
     * Decrypt data produced by [encrypt].
     * @throws StorageException.Crypto on a wrong key, tampered data or an unknown format.
     */
    public fun decrypt(data: ByteArray): ByteArray {
        if (data.size < HEADER_SIZE + TAG_SIZE) {
            throw StorageException.Crypto(IllegalArgumentException("Data too short to be encrypted content"))
        }
        if (data[0] != VERSION) {
            throw StorageException.Crypto(IllegalArgumentException("Unknown encryption format version ${data[0]}"))
        }
        val nonce = data.copyOfRange(1, HEADER_SIZE)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE * 8, nonce))
            cipher.doFinal(data, HEADER_SIZE, data.size - HEADER_SIZE)
        } catch (e: GeneralSecurityException) {
            throw StorageException.Crypto(e)
        }
    }

    public companion object {
        internal const val TRANSFORMATION: String = "AES/GCM/NoPadding"
        private const val VERSION: Byte = 1
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 16
        private const val HEADER_SIZE = 1 + NONCE_SIZE
        private const val DEFAULT_ITERATIONS = 120_000
        private val random = SecureRandom()

        /** Use a raw AES key of 16, 24 or 32 bytes. */
        public fun fromKey(key: ByteArray): Encryption {
            require(key.size == 16 || key.size == 24 || key.size == 32) {
                "AES key must be 16, 24 or 32 bytes, got ${key.size}"
            }
            return Encryption(SecretKeySpec(key, "AES"))
        }

        /**
         * Derive a 256 bit key from [passphrase] with PBKDF2-HMAC-SHA256.
         *
         * Keep [salt] alongside your data and pass the same value every time, see [generateSalt].
         */
        public fun fromPassphrase(
            passphrase: CharArray,
            salt: ByteArray,
            iterations: Int = DEFAULT_ITERATIONS,
        ): Encryption {
            require(salt.size >= 8) { "Salt must be at least 8 bytes" }
            require(iterations > 0) { "Iterations must be positive" }
            val spec = PBEKeySpec(passphrase, salt, iterations, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = try {
                factory.generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
            return fromKey(keyBytes)
        }

        /** A fresh random salt for [fromPassphrase]. */
        public fun generateSalt(size: Int = 16): ByteArray = ByteArray(size).also(random::nextBytes)

        /** A fresh random 256 bit key for [fromKey]. */
        public fun generateKey(): ByteArray = ByteArray(32).also(random::nextBytes)
    }
}
