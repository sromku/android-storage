package com.sromku.simple.storage;

import android.content.Context;

/**
 * Singleton class that supply all possible storage options.<br>
 * <br>
 * <b>Permissions:</b>
 * <ul>
 * <li>android.permission.WRITE_EXTERNAL_STORAGE</li>
 * <li>android.permission.READ_EXTERNAL_STORAGE</li>
 * </ul>
 * 
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 */
public class SimpleStorage {

	private static InternalStorage mInternalStorage = null;
	private static ExternalStorage mExternalStorage = null;

	private static SimpleStorage mInstance = null;
	private static SimpleStorageConfiguration mSimpleStorageConfiguration;

	private SimpleStorage() {
		// set default configuration
		mSimpleStorageConfiguration = new SimpleStorageConfiguration.Builder().build();

		mInternalStorage = new InternalStorage();
		mExternalStorage = new ExternalStorage();
	}

	private static SimpleStorage init() {
		if (mInstance == null) {
			mInstance = new SimpleStorage();
		}
		return mInstance;
	}

	/**
	 * The type of the storage. <br>
	 * Possible options:
	 * <ul>
	 * <li>{@link StorageType#INTERNAL}</li>
	 * <li>{@link StorageType#EXTERNAL}</li>
	 * </ul>
	 * 
	 * @author sromku
	 * 
	 */
	public enum StorageType {
		INTERNAL,
		EXTERNAL
	}

	/**
	 * Get internal storage. The files and folders will be persisted on device
	 * memory. The internal storage is good for saving <b>private and secure</b>
	 * data.<br>
	 * <br>
	 * <b>Important:
	 * <ul>
	 * <li>When the device is low on internal storage space, Android may delete
	 * these cache files to recover space.</li>
	 * <li>You should always maintain the cache files yourself and stay within a
	 * reasonable limit of space consumed, such as 1MB.</li>
	 * <li>When the user uninstalls your application, these files are removed.</li>
	 * </b>
	 * </ul>
	 * <i>http://developer.android.com/guide/topics/data/data-storage.html#
	 * filesInternal</i>
	 * 
	 * @return {@link InternalStorage}
	 * 
	 */
	public static InternalStorage getInternalStorage(Context context) {
		init();
		mInternalStorage.initActivity(context);
		return mInternalStorage;
	}

	/**
	 * Get external storage. <br>
	 * 
	 * @return {@link ExternalStorage}
	 */
	public static ExternalStorage getExternalStorage() {
		init();
		return mExternalStorage;
	}

	/**
	 * Check whereas the external storage is writable. <br>
	 * 
	 * @return <code>True</code> if external storage writable, otherwise return
	 *         <code>False</code>
	 */
	public static boolean isExternalStorageWritable() {
		init();
		return mExternalStorage.isWritable();
	}

	public static SimpleStorageConfiguration getConfiguration() {
		return mSimpleStorageConfiguration;
	}

	/**
	 * Set and update the storage configuration
	 * 
	 * @param configuration
	 */
	public static void updateConfiguration(SimpleStorageConfiguration configuration) {
		if (mInstance == null) {
			throw new RuntimeException("First instantiate the Storage and then you can update the configuration");
		}
		mSimpleStorageConfiguration = configuration;
	}

	/**
	 * Set the configuration to default
	 */
	public static void resetConfiguration() {
		SimpleStorageConfiguration configuration = new SimpleStorageConfiguration.Builder().build();
		mSimpleStorageConfiguration = configuration;
	}

}
