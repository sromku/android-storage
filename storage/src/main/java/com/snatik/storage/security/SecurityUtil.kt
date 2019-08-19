package com.snatik.storage.security

import android.util.Log

import java.io.UnsupportedEncodingException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException

import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

/**
 * Security utils for encryption, xor and more
 *
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 */
class SecurityUtil {

    /**
     * Do xor operation on the string with the key
     *
     * @param msg The string to xor on
     * @param key The key by which the xor will work
     * @return The string after xor
     */
    fun xor(msg: String, key: String): String? {
        try {
            val UTF_8 = "UTF-8"
            val msgArray: ByteArray

            msgArray = msg.toByteArray(charset(UTF_8))

            val keyArray = key.toByteArray(charset(UTF_8))

            val out = ByteArray(msgArray.size)
            for (i in msgArray.indices) {
                out[i] = (msgArray[i] xor keyArray[i % keyArray.size]).toByte()
            }
            return String(out, Charsets.UTF_8)
        } catch (e: UnsupportedEncodingException) {
        }

        return null
    }

    companion object {

        private val TAG = "SecurityUtil"

        /**
         * Encrypt or Descrypt the content. <br></br>
         *
         * @param content        The content to encrypt or descrypt.
         * @param encryptionMode Use: [Cipher.ENCRYPT_MODE] or
         * [Cipher.DECRYPT_MODE]
         * @param secretKey      Set the secret key for encryption of file content.
         * **Important: The length must be 16 long**. *Uses SHA-256
         * to generate a hash from your key and trim the result to 128
         * bit (16 bytes)*
         * @param ivx            This is not have to be secret. It used just for better
         * randomizing the cipher. You have to use the same IV parameter
         * within the same encrypted and written files. Means, if you
         * want to have the same content after descryption then the same
         * IV must be used. *About this parameter from wiki:
         * https://en.wikipedia.org/wiki/Block_cipher_modes_of_operation
         * #Initialization_vector_.28IV.29* **Important: The length
         * must be 16 long**
         * @return
         */
        fun encrypt(content: ByteArray?, encryptionMode: Int, secretKey: ByteArray, ivx: ByteArray): ByteArray? {
            if (secretKey.size != 16 || ivx.size != 16) {
                Log.w(TAG, "Set the encryption parameters correctly. The must be 16 length long each")
                return null
            }

            try {
                val secretkey = SecretKeySpec(secretKey, CipherAlgorithmType.AES.algorithmName)
                val IV = IvParameterSpec(ivx)
                val transformation = CipherTransformationType.AES_CBC_PKCS5Padding
                val decipher = Cipher.getInstance(transformation)
                decipher.init(encryptionMode, secretkey, IV)
                return decipher.doFinal(content)
            } catch (e: NoSuchAlgorithmException) {
                Log.e(TAG, "Failed to encrypt/descrypt - Unknown Algorithm", e)
                return null
            } catch (e: NoSuchPaddingException) {
                Log.e(TAG, "Failed to encrypt/descrypt- Unknown Padding", e)
                return null
            } catch (e: InvalidKeyException) {
                Log.e(TAG, "Failed to encrypt/descrypt - Invalid Key", e)
                return null
            } catch (e: InvalidAlgorithmParameterException) {
                Log.e(TAG, "Failed to encrypt/descrypt - Invalid Algorithm Parameter", e)
                return null
            } catch (e: IllegalBlockSizeException) {
                Log.e(TAG, "Failed to encrypt/descrypt", e)
                return null
            } catch (e: BadPaddingException) {
                Log.e(TAG, "Failed to encrypt/descrypt", e)
                return null
            }

        }
    }

}
