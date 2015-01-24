package com.sromku.simple.storage.security;

/**
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 */
public enum CipherPaddingType {
	NoPadding("NoPadding"),
	PKCS5Padding("PKCS5Padding"),
	PKCS1Padding("PKCS1Padding"),
	OAEPWithSHA_1AndMGF1Padding("OAEPWithSHA-1AndMGF1Padding"),
	OAEPWithSHA_256AndMGF1Padding("OAEPWithSHA-256AndMGF1Padding");

	private String mName;

	private CipherPaddingType(String name) {
		mName = name;
	}

	/**
	 * Get the algorithm name of the enum value.
	 * 
	 * @return The algorithm name
	 */
	public String getAlgorithmName() {
		return mName;
	}
}
