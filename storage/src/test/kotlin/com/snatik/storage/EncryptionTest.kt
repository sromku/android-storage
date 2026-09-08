package com.snatik.storage

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncryptionTest {

    private val plain = "The quick brown fox".toByteArray()

    @Test
    fun roundTripWithRawKey() {
        val e = Encryption.fromKey(Encryption.generateKey())
        assertContentEquals(plain, e.decrypt(e.encrypt(plain)))
    }

    @Test
    fun roundTripWithPassphrase() {
        val salt = Encryption.generateSalt()
        val writer = Encryption.fromPassphrase("correct horse".toCharArray(), salt, iterations = 1000)
        val reader = Encryption.fromPassphrase("correct horse".toCharArray(), salt, iterations = 1000)
        assertContentEquals(plain, reader.decrypt(writer.encrypt(plain)))
    }

    @Test
    fun wrongPassphraseFails() {
        val salt = Encryption.generateSalt()
        val writer = Encryption.fromPassphrase("one".toCharArray(), salt, iterations = 1000)
        val reader = Encryption.fromPassphrase("two".toCharArray(), salt, iterations = 1000)
        assertFailsWith<StorageException.Crypto> { reader.decrypt(writer.encrypt(plain)) }
    }

    @Test
    fun differentSaltFails() {
        val writer = Encryption.fromPassphrase("pw".toCharArray(), Encryption.generateSalt(), iterations = 1000)
        val reader = Encryption.fromPassphrase("pw".toCharArray(), Encryption.generateSalt(), iterations = 1000)
        assertFailsWith<StorageException.Crypto> { reader.decrypt(writer.encrypt(plain)) }
    }

    @Test
    fun outputIsRandomisedPerCall() {
        val e = Encryption.fromKey(Encryption.generateKey())
        assertFalse(e.encrypt(plain).contentEquals(e.encrypt(plain)))
    }

    @Test
    fun tamperedDataFails() {
        val e = Encryption.fromKey(Encryption.generateKey())
        val data = e.encrypt(plain)
        data[data.size - 1] = (data[data.size - 1].toInt() xor 0x01).toByte()
        assertFailsWith<StorageException.Crypto> { e.decrypt(data) }
    }

    @Test
    fun shortOrUnknownFormatFails() {
        val e = Encryption.fromKey(Encryption.generateKey())
        assertFailsWith<StorageException.Crypto> { e.decrypt(ByteArray(5)) }
        val data = e.encrypt(plain)
        data[0] = 9
        assertFailsWith<StorageException.Crypto> { e.decrypt(data) }
    }

    @Test
    fun emptyPayloadRoundTrips() {
        val e = Encryption.fromKey(Encryption.generateKey())
        assertContentEquals(ByteArray(0), e.decrypt(e.encrypt(ByteArray(0))))
    }

    @Test
    fun keySizeIsValidated() {
        assertFailsWith<IllegalArgumentException> { Encryption.fromKey(ByteArray(10)) }
        assertTrue(Encryption.generateKey().size == 32)
        assertEquals(16, Encryption.generateSalt().size)
    }
}
