package com.snatik.storage.security

/**
 * Supported types:
 * http://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html<br></br>
 * <br></br>
 *
 * Every implementation of the Java platform is required to support the
 * following standard Cipher transformations with the keysizes in parentheses:
 *
 *  * AES/CBC/NoPadding (128)
 *  * AES/CBC/PKCS5Padding (128)
 *  * AES/ECB/NoPadding (128)
 *  * AES/ECB/PKCS5Padding (128)
 *  * DES/CBC/NoPadding (56)
 *  * DES/CBC/PKCS5Padding (56)
 *  * DES/ECB/NoPadding (56)
 *  * DES/ECB/PKCS5Padding (56)
 *  * DESede/CBC/NoPadding (168)
 *  * DESede/CBC/PKCS5Padding (168)
 *  * DESede/ECB/NoPadding (168)
 *  * DESede/ECB/PKCS5Padding (168)
 *  * RSA/ECB/PKCS1Padding (1024, 2048)
 *  * RSA/ECB/OAEPWithSHA-1AndMGF1Padding (1024, 2048)
 *  * RSA/ECB/OAEPWithSHA-256AndMGF1Padding (1024, 2048)
 *
 *
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 */
object CipherTransformationType {
    private val SLASH = "/"

    val AES_CBC_NoPadding = CipherAlgorithmType.AES.toString() + SLASH + CipherModeType.CBC + SLASH + CipherPaddingType.NoPadding
    val AES_CBC_PKCS5Padding = CipherAlgorithmType.AES.toString() + SLASH + CipherModeType.CBC + SLASH + CipherPaddingType.PKCS5Padding
    val AES_ECB_NoPadding = CipherAlgorithmType.AES.toString() + SLASH + CipherModeType.ECB + SLASH + CipherPaddingType.NoPadding
    val AES_ECB_PKCS5Padding = CipherAlgorithmType.AES.toString() + SLASH + CipherModeType.ECB + SLASH + CipherPaddingType.PKCS5Padding

    val DES_CBC_NoPadding = CipherAlgorithmType.DES.toString() + SLASH + CipherModeType.CBC + SLASH + CipherPaddingType.NoPadding
    val DES_CBC_PKCS5Padding = CipherAlgorithmType.DES.toString() + SLASH + CipherModeType.CBC + SLASH + CipherPaddingType.PKCS5Padding
    val DES_ECB_NoPadding = CipherAlgorithmType.DES.toString() + SLASH + CipherModeType.ECB + SLASH + CipherPaddingType.NoPadding
    val DES_ECB_PKCS5Padding = CipherAlgorithmType.DES.toString() + SLASH + CipherModeType.ECB + SLASH + CipherPaddingType.PKCS5Padding

    val DESede_CBC_NoPadding = CipherAlgorithmType.DESede.toString() + SLASH + CipherModeType.CBC + SLASH + CipherPaddingType.NoPadding
    val DESede_CBC_PKCS5Padding = CipherAlgorithmType.DESede.toString() + SLASH + CipherModeType.CBC + SLASH + CipherPaddingType.PKCS5Padding
    val DESede_ECB_NoPadding = CipherAlgorithmType.DESede.toString() + SLASH + CipherModeType.ECB + SLASH + CipherPaddingType.NoPadding
    val DESede_ECB_PKCS5Padding = CipherAlgorithmType.DESede.toString() + SLASH + CipherModeType.ECB + SLASH + CipherPaddingType.PKCS5Padding

    val RSA_ECB_PKCS1Padding = CipherAlgorithmType.RSA.toString() + SLASH + CipherModeType.ECB + SLASH + CipherPaddingType.PKCS1Padding
    val RSA_ECB_OAEPWithSHA_1AndMGF1Padding = CipherAlgorithmType.RSA.toString() + SLASH + CipherModeType.ECB + SLASH + CipherPaddingType.OAEPWithSHA_1AndMGF1Padding
    val RSA_ECB_OAEPWithSHA_256AndMGF1Padding = CipherAlgorithmType.RSA.toString() + SLASH + CipherModeType.ECB + SLASH + CipherPaddingType.OAEPWithSHA_256AndMGF1Padding

}
