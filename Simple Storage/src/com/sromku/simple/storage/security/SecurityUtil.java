package com.sromku.simple.storage.security;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Security utils for encryption, xor and more
 * 
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 */
public class SecurityUtil {

	/**
	 * Encrypt or Descrypt the content. <br>
	 * 
	 * @param content
	 *            The content to encrypt or descrypt.
	 * @param encryptionMode
	 *            Use: {@link Cipher#ENCRYPT_MODE} or
	 *            {@link Cipher#DECRYPT_MODE}
	 * @param secretKey
	 *            Set the secret key for encryption of file content.
	 *            <b>Important: The length must be 16 long</b>. <i>Uses SHA-256
	 *            to generate a hash from your key and trim the result to 128
	 *            bit (16 bytes)</i>
	 * @param ivx
	 *            This is not have to be secret. It used just for better
	 *            randomizing the cipher. You have to use the same IV parameter
	 *            within the same encrypted and written files. Means, if you
	 *            want to have the same content after descryption then the same
	 *            IV must be used. <i>About this parameter from wiki:
	 *            https://en.wikipedia.org/wiki/Block_cipher_modes_of_operation
	 *            #Initialization_vector_.28IV.29</i> <b>Important: The length
	 *            must be 16 long</b>
	 * @return
	 */
	public static byte[] encrypt(byte[] content, int encryptionMode, final byte[] secretKey, final byte[] ivx) {
		if (secretKey.length != 16 || ivx.length != 16) {
			throw new RuntimeException("Set the encryption parameters correctly. The must be 16 length long each");
		}

		try {
			final SecretKey secretkey = new SecretKeySpec(secretKey, CipherAlgorithmType.AES.getAlgorithmName());
			final IvParameterSpec IV = new IvParameterSpec(ivx);

			final String transformation = CipherTransformationType.AES_CBC_PKCS5Padding;
			final Cipher decipher = Cipher.getInstance(transformation);

			decipher.init(encryptionMode, secretkey, IV);
			final byte[] plainText = decipher.doFinal(content);
			return plainText;
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Failed to encrypt/descrypt - Unknown Algorithm", e);
		}
		catch (NoSuchPaddingException e) {
			throw new RuntimeException("Failed to encrypt/descrypt- Unknown Padding", e);
		}
		catch (InvalidKeyException e) {
			throw new RuntimeException("Failed to encrypt/descrypt - Invalid Key", e);
		}
		catch (InvalidAlgorithmParameterException e) {
			throw new RuntimeException("Failed to encrypt/descrypt - Invalid Algorithm Parameter", e);
		}
		catch (IllegalBlockSizeException e) {
			throw new RuntimeException("Failed to encrypt/descrypt", e);
		}
		catch (BadPaddingException e) {
			throw new RuntimeException("Failed to encrypt/descrypt", e);
		}
	}

	/**
	 * Do xor operation on the string with the key
	 * 
	 * @param str
	 *            The string to xor on
	 * @param key
	 *            The key by which the xor will work
	 * @return The string after xor
	 */
	public String xor(String msg, String key) {
		try {
			final String UTF_8 = "UTF-8";
			byte[] msgArray;

			msgArray = msg.getBytes(UTF_8);

			byte[] keyArray = key.getBytes(UTF_8);

			byte[] out = new byte[msgArray.length];
			for (int i = 0; i < msgArray.length; i++) {
				out[i] = (byte) (msgArray[i] ^ keyArray[i % keyArray.length]);
			}
			return new String(out, UTF_8);
		}
		catch (UnsupportedEncodingException e) {
		}
		return null;
	}

}
