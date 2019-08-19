package com.snatik.storage

import android.os.Build
import android.util.Log

import java.io.FileInputStream
import java.io.UnsupportedEncodingException
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Each of the specific storage types (like External storage tool) need its own
 * configurations. This configuration class is build and used in the storage
 * classes.<br></br>
 * <br></br>
 *
 *
 * **Examples:**<br></br>
 * <br></br>
 *
 *
 * Default unsecured configuration:<br></br>
 *
 *
 * <pre>
 * `SimpleStorageConfiguration configuration = new SimpleStorageConfiguration.Builder()
 * .build()
` *
</pre> *
 *
 *
 * Secured configuration:
 *
 *
 * <pre>
 * {
 * &#064;code
 * final int CHUNK_SIZE = 16 * 1024;
 * final String IVX = &quot;1234567890123456&quot;;
 * final String SECRET_KEY = &quot;secret1234567890&quot;;
 *
 * SimpleStorageConfiguration configuration = new SimpleStorageConfiguration.Builder().setChuckSize(CHUNK_SIZE)
 * .setEncryptContent(IVX, SECRET_KEY).build();
 * }
</pre> *
 *
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 */
class EncryptConfiguration private constructor(builder: Builder) {

    /**
     * The best chunk size: *http://stackoverflow.com/a/237495/334522*
     */
    /**
     * Get chunk size. The chuck size is used while reading the file by chunks
     * [FileInputStream.read].
     *
     * @return The chunk size
     */
    val chuckSize: Int
    /**
     * Encrypt the file content.<br></br>
     *
     * @see [Block
     * cipher mode of operation](https://en.wikipedia.org/wiki/Block_cipher_modes_of_operation)
     */
    val isEncrypted: Boolean
    /**
     * Get iv parameter
     *
     * @return
     */
    val ivParameter: ByteArray?
    /**
     * Get secret key
     *
     * @return
     */
    val secretKey: ByteArray?

    init {
        chuckSize = builder._chunkSize
        isEncrypted = builder._isEncrypted
        ivParameter = builder._ivParameter
        secretKey = builder._secretKey
    }

    /**
     * Configuration Builder class. <br></br>
     * Following Builder design pattern.
     *
     * @author sromku
     */
    class Builder {
        var _chunkSize = 8192
        var _isEncrypted = false
        var _ivParameter: ByteArray? = null
        var _secretKey: ByteArray? = null

        /**
         * Build the configuration for storage.
         *
         * @return
         */
        fun build(): EncryptConfiguration {
            return EncryptConfiguration(this)
        }

        /**
         * Set chunk size. The chuck size is used while reading the file by
         * chunks [FileInputStream.read]. The preferable
         * value is 1024xN bits. While N is power of 2 (like 1,2,4,8,16,...)<br></br>
         * <br></br>
         *
         *
         * The default: **8 * 1024** = 8192 bits
         *
         * @param chunkSize The chunk size in bits
         * @return The [Builder]
         */
        fun setChuckSize(chunkSize: Int): Builder {
            _chunkSize = chunkSize
            return this
        }

        /**
         * Encrypt and descrypt the file content while writing and reading
         * to/from disc.<br></br>
         *
         * @param ivx       This is not have to be secret. It used just for better
         * randomizing the cipher. You have to use the same IV
         * parameter within the same encrypted and written files.
         * Means, if you want to have the same content after
         * descryption then the same IV must be used.<br></br>
         * <br></br>
         *
         *
         * **Important: The length must be 16 long**<br></br>
         *
         *
         * *About this parameter from wiki:
         * https://en.wikipedia.org
         * /wiki/Block_cipher_modes_of_operation
         * #Initialization_vector_.28IV.29*<br></br>
         * <br></br>
         * @param secretKey Set the secret key for encryption of file content. <br></br>
         * <br></br>
         *
         *
         * **Important: The length must be 16 long** <br></br>
         *
         *
         * *Uses SHA-256 to generate a hash from your key and trim
         * the result to 128 bit (16 bytes)*<br></br>
         * <br></br>
         * @see [Block
         * cipher mode of operation](https://en.wikipedia.org/wiki/Block_cipher_modes_of_operation)
         */
        fun setEncryptContent(ivx: String, secretKey: String, salt: ByteArray): Builder {
            _isEncrypted = true

            // Set IV parameter
            try {
                _ivParameter = ivx.toByteArray(charset(UTF_8))
            } catch (e: UnsupportedEncodingException) {
                Log.e(TAG, "UnsupportedEncodingException", e)
            }

            // Set secret key
            try {
                /*
				 * We generate random salt and then use 1000 iterations to
				 * initialize secret key factory which in-turn generates key.
				 */
                val iterationCount = 1000 // recommended by PKCS#5
                val keyLength = 128

                val keySpec = PBEKeySpec(secretKey.toCharArray(), salt, iterationCount, keyLength)
                var keyFactory: SecretKeyFactory? = null
                if (Build.VERSION.SDK_INT >= 19) {
                    // see:
                    // http://android-developers.blogspot.co.il/2013/12/changes-to-secretkeyfactory-api-in.html
                    // Use compatibility key factory -- only uses lower 8-bits
                    // of passphrase chars
                    keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1And8bit")
                } else {
                    // Traditional key factory. Will use lower 8-bits of
                    // passphrase chars on
                    // older Android versions (API level 18 and lower) and all
                    // available bits
                    // on KitKat and newer (API level 19 and higher).
                    keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                }
                val keyBytes = keyFactory!!.generateSecret(keySpec).encoded

                _secretKey = keyBytes

            } catch (e: InvalidKeySpecException) {
                Log.e(TAG, "InvalidKeySpecException", e)
            } catch (e: NoSuchAlgorithmException) {
                Log.e(TAG, "NoSuchAlgorithmException", e)
            }

            return this
        }

        companion object {

            private val UTF_8 = "UTF-8"
        }

    }

    companion object {

        private val TAG = "EncryptConfiguration"
    }

}
