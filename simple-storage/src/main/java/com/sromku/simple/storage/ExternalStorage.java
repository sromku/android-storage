package com.sromku.simple.storage;

import android.os.Environment;

import com.sromku.simple.storage.SimpleStorage.StorageType;

import java.io.File;

/**
 * Storage that responsible for storing on external storage like SD card.<br>
 * This class can be initiated <b>only</b> by {@link SimpleStorage} singleton
 * class.
 * 
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 * 
 */
public class ExternalStorage extends AbstractDiskStorage {

	/**
	 * Contractor as friend. Means, only classes from the same package can
	 * initiate this class.<br>
	 * <br>
	 * 
	 * <b>DO NOT CHANGE</b> to -> public, private or protected
	 */
	ExternalStorage() {
		super();
	}

	@Override
	public StorageType getStorageType() {
		return StorageType.EXTERNAL;
	}

	/**
	 * Checks if external storage is available for read and write. <br>
	 * 
	 * @return <code>True</code> if external storage writable, otherwise return
	 *         <code>False</code>
	 */
	public boolean isWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			return true;
		}
		return false;
	}
	
	@Override
	protected String buildAbsolutePath() {
		return Environment.getExternalStorageDirectory().getAbsolutePath();
	}

	/**
	 * Build path of folder or file on the external storage location. <br>
	 * <b>Note: </b> <li>For directory use regular name</li> <li>For file name
	 * use name with .extension like <i>abc.png</i></li><br>
	 * <br>
	 * 
	 * @param name
	 *            The name of the directory
	 * @param storageLocation
	 * @return
	 */
	protected String buildPath(String name) {
		String path = buildAbsolutePath();
		path = path + File.separator + name;
		return path;
	}

	/**
	 * Build folder + file on the external storage location. <br>
	 * <b>Note: </b> <li>For directory use regular name</li> <li>For file name
	 * use name with .extension like <i>abc.png</i></li><br>
	 * <br>
	 * 
	 * @param directoryName
	 *            The directory name
	 * @param fileName
	 *            The file name
	 * @return
	 */
	protected String buildPath(String directoryName, String fileName) {
		String path = Environment.getExternalStorageDirectory().getAbsolutePath();
		path = path + File.separator + directoryName + File.separator + fileName;
		return path;
	}

}
