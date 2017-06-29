package com.snatik.storage.security;

/**
 * Supported types:
 * http://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html<br>
 * <br>
 *
 * Every implementation of the Java platform is required to support the
 * following standard Cipher transformations with the keysizes in parentheses:
 * <ul>
 * <li>AES/CBC/NoPadding (128)</li>
 * <li>AES/CBC/PKCS5Padding (128)</li>
 * <li>AES/ECB/NoPadding (128)</li>
 * <li>AES/ECB/PKCS5Padding (128)
 * <li>DES/CBC/NoPadding (56)</li>
 * <li>DES/CBC/PKCS5Padding (56)</li>
 * <li>DES/ECB/NoPadding (56)
 * <li>DES/ECB/PKCS5Padding (56)</li>
 * <li>DESede/CBC/NoPadding (168)</li>
 * <li>DESede/CBC/PKCS5Padding (168)</li>
 * <li>DESede/ECB/NoPadding (168)</li>
 * <li>DESede/ECB/PKCS5Padding (168)</li>
 * <li>RSA/ECB/PKCS1Padding (1024, 2048)</li>
 * <li>RSA/ECB/OAEPWithSHA-1AndMGF1Padding (1024, 2048)</li>
 * <li>RSA/ECB/OAEPWithSHA-256AndMGF1Padding (1024, 2048)</li>
 * </ul>
 *
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 *
 */
public class CipherTransformationType {
	private static final String _ = "/";

	public static final String AES_CBC_NoPadding = CipherAlgorithmType.AES + _ + CipherModeType.CBC + _ + CipherPaddingType.NoPadding;
	public static final String AES_CBC_PKCS5Padding = CipherAlgorithmType.AES + _ + CipherModeType.CBC + _ + CipherPaddingType.PKCS5Padding;
	public static final String AES_ECB_NoPadding = CipherAlgorithmType.AES + _ + CipherModeType.ECB + _ + CipherPaddingType.NoPadding;
	public static final String AES_ECB_PKCS5Padding = CipherAlgorithmType.AES + _ + CipherModeType.ECB + _ + CipherPaddingType.PKCS5Padding;

	public static final String DES_CBC_NoPadding = CipherAlgorithmType.DES + _ + CipherModeType.CBC + _ + CipherPaddingType.NoPadding;
	public static final String DES_CBC_PKCS5Padding = CipherAlgorithmType.DES + _ + CipherModeType.CBC + _ + CipherPaddingType.PKCS5Padding;
	public static final String DES_ECB_NoPadding = CipherAlgorithmType.DES + _ + CipherModeType.ECB + _ + CipherPaddingType.NoPadding;
	public static final String DES_ECB_PKCS5Padding = CipherAlgorithmType.DES + _ + CipherModeType.ECB + _ + CipherPaddingType.PKCS5Padding;

	public static final String DESede_CBC_NoPadding = CipherAlgorithmType.DESede + _ + CipherModeType.CBC + _ + CipherPaddingType.NoPadding;
	public static final String DESede_CBC_PKCS5Padding = CipherAlgorithmType.DESede + _ + CipherModeType.CBC + _ + CipherPaddingType.PKCS5Padding;
	public static final String DESede_ECB_NoPadding = CipherAlgorithmType.DESede + _ + CipherModeType.ECB + _ + CipherPaddingType.NoPadding;
	public static final String DESede_ECB_PKCS5Padding = CipherAlgorithmType.DESede + _ + CipherModeType.ECB + _ + CipherPaddingType.PKCS5Padding;

	public static final String RSA_ECB_PKCS1Padding = CipherAlgorithmType.RSA + _ + CipherModeType.ECB + _ + CipherPaddingType.PKCS1Padding;
	public static final String RSA_ECB_OAEPWithSHA_1AndMGF1Padding = CipherAlgorithmType.RSA + _ + CipherModeType.ECB + _ + CipherPaddingType.OAEPWithSHA_1AndMGF1Padding;
	public static final String RSA_ECB_OAEPWithSHA_256AndMGF1Padding = CipherAlgorithmType.RSA + _ + CipherModeType.ECB + _ + CipherPaddingType.OAEPWithSHA_256AndMGF1Padding;

}
