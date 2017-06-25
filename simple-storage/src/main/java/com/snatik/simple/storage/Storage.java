package com.sromku.simple.storage;

import android.graphics.Bitmap;

import com.sromku.simple.storage.SimpleStorage.StorageType;
import com.sromku.simple.storage.helpers.OrderType;
import com.sromku.simple.storage.helpers.SizeUnit;

import java.io.File;
import java.util.List;

/**
 * Interface of CRUD methods on the file system
 * 
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 */
public interface Storage {

	/**
	 * Get the type of the storage
	 * 
	 * @return {@link StorageType}
	 */
	StorageType getStorageType();

	/**
	 * Create directory with given path. <br>
	 * If the directory with given name <b>exists</b>, then
	 * {@link StorageException} will be thrown. <br>
	 * <br>
	 * 
	 * <b>For External Storage:</b> The name should be as following format:
	 * Directory_Name_0/Directory_Name_1/Directory_Name_2<br>
	 * <br>
	 * <b>For Internal Storage:</b> No separators are acceptable
	 * 
	 * @param name
	 *            The name of the directory.
	 * @return <code>True</code> if directory was created, otherwise return
	 *         <code>False</code>
	 * @throws StorageException
	 */
	boolean createDirectory(String name);

	/**
	 * Create directory with given path. <br>
	 * If the directory with given name exists and the <code>override</code>
	 * parameter is <code>True</code> then it will be removed and a new
	 * directory will be created instead. <br>
	 * <br>
	 * 
	 * <b>Note:</b> if <code>override=false</code>, then it do nothing and
	 * return true.
	 * 
	 * @param name
	 *            The name of the directory.
	 * @param override
	 *            Set <code>True</code> if you want to override the directory if
	 *            such exists. The default is <code>False</code>.<br>
	 *            Set <code>False</code> then it checks if directory already
	 *            exist, if yes then do nothing and return true, otherwise it
	 *            creates a new directory
	 * @return <code>True</code> if directory was created, otherwise return
	 *         <code>False</code>.
	 * 
	 * @throws StorageException
	 */
	boolean createDirectory(String name, boolean override);

	/**
	 * Delete the directory and all sub content.
	 * 
	 * @param name
	 *            The name of the directory.
	 * @return <code>True</code> if the directory was deleted, otherwise return
	 *         <code>False</code>
	 */
	boolean deleteDirectory(String name);

	/**
	 * Check if the directory is already exist.
	 * 
	 * @param name
	 *            The name of the directory.
	 * @return <code>True</code> if exists, otherwise return <code>False</code>
	 */
	boolean isDirectoryExists(String name);

	/**
	 * Creating file with given name and with content in string format. <br>
	 * 
	 * @param directoryName
	 *            The directory name
	 * @param fileName
	 *            The file name
	 * @param content
	 *            The content which will filled the file
	 */
	boolean createFile(String directoryName, String fileName, String content);

	/**
	 * Creating file with given name and by using Storable format. <br>
	 * 
	 * @param directoryName
	 *            The directory name
	 * @param fileName
	 *            The file name
	 * @param content
	 *            The content which will filled the file
	 */
	boolean createFile(String directoryName, String fileName, Storable storable);

	/**
	 * Creating file with given name and by using Bitmap format. <br>
	 * 
	 * @param directoryName
	 *            The directory name
	 * @param fileName
	 *            The file name
	 * @param content
	 *            The content which will filled the file
	 */
	boolean createFile(String directoryName, String fileName, Bitmap bitmap);

	/**
	 * Creating the file with given name and with content in byte array format.<br>
	 * 
	 * @param directoryName
	 *            The directory name
	 * @param fileName
	 *            The file name
	 * @param content
	 *            The content which will filled the file
	 */
	boolean createFile(String directoryName, String fileName, byte[] content);

	/**
	 * Delete file
	 * 
	 * @param directoryName
	 *            The directory name
	 * @param fileName
	 *            The file name
	 * @return
	 */
	boolean deleteFile(String directoryName, String fileName);

	/**
	 * Is file exists
	 * 
	 * @param directoryName
	 *            The directory name
	 * @param fileName
	 *            The file name
	 * @return
	 */
	boolean isFileExist(String directoryName, String fileName);

	/**
	 * Read file from storage
	 * 
	 * @param directoryName
	 *            The directory name
	 * @param fileName
	 *            The file name
	 * @return
	 */
	byte[] readFile(String directoryName, String fileName);

	/**
	 * Read string from external storage
	 * 
	 * @param directoryName
	 *            The directory name
	 * @param fileName
	 *            The file name
	 * @return
	 */
	String readTextFile(String directoryName, String fileName);

	/**
	 * Append content to the existing file
	 * 
	 * @param directoryName
	 *            The directory name
	 * @param fileName
	 *            The file name
	 * @param content
	 */
	void appendFile(String directoryName, String fileName, String content);

	/**
	 * Append content to the existing file
	 * 
	 * @param directoryName
	 *            The directory name
	 * @param fileName
	 *            The file name
	 * @param bytes
	 */
	void appendFile(String directoryName, String fileName, byte[] bytes);

	/**
	 * Get list of all nested files only (without directories) under the
	 * directories.
	 */
	List<File> getNestedFiles(String directoryName);
	
	/**
	 * Get all files and directories under the directory that match the regex pattern on their full names.<br><br>
	 * For example, we want to get only image files. And this our directory status:
	 * <pre>
	 * my_dir 
	 *    |- image1.jpg
	 *    |- image2.png
	 *    |- not_image1.txt
	 *    |- not_image2.psd
	 *    |- dir1
	 *    |- image3.gif
	 * </pre> 
	 * The code:
	 * <pre>
	 * String IMAGE_PATTERN = "([^\\s]+(\\.(?i)(jpg|png|gif|bmp))$)";
	 * List{@code <}File> files = storage.getFiles("my_dir", IMAGE_PATTERN);
	 * </pre>
	 * The result:
	 * <pre>
	 * my_dir 
	 *    |- image1.jpg
	 *    |- image2.png
	 *    |- image3.gif
	 * </pre>
	 * 
	 * @param directoryName
	 * @param matchRegex Set regular expression to match files you need. 
	 * 		Or set <code>null</code> to get all files.
	 */
	List<File> getFiles(String directoryName, String matchRegex);
	
	/**
	 * Get files from directory ordered.
	 * @param directoryName
	 * @param orderType
	 * @return
	 */
	List<File> getFiles(String directoryName, OrderType orderType);

	/**
	 * Get {@link File} object by name of directory or file
	 * 
	 * @param name
	 * @return
	 */
	File getFile(String name);

	/**
	 * Get {@link File}
	 * 
	 * @param directoryName
	 * @param fileName
	 * @return
	 */
	File getFile(String directoryName, String fileName);

	/**
	 * Rename file. Get the file you want to change.
	 * 
	 * @param file
	 *            The file you want to change. You can get the {@link File} by
	 *            calling to one of the {@link #getFile(String)} methods
	 * @param newName
	 */
	void rename(File file, String newName);

	/**
	 * Get size of the file in units you need.
	 * 
	 * @param file
	 * @param unit
	 * @return
	 */
	double getSize(File file, SizeUnit unit);

	/**
	 * Get free space on disk.
	 * 
	 * @param sizeUnit
	 *            The units you want the returned value to be.
	 * @return The free space in units you selected.
	 */
	long getFreeSpace(SizeUnit sizeUnit);

	/**
	 * Get already used space on disk.
	 * 
	 * @param sizeUnit
	 *            The units you want the returned value to be.
	 * @return The used space in units you selected.
	 */
	long getUsedSpace(SizeUnit sizeUnit);

	/**
	 * Copy file (only) to another destination.
	 * 
	 * @param file
	 *            The file you want to copy
	 * @param directoryName
	 *            The destination directory
	 * @param fileName
	 *            The destination file name
	 * @throws StorageException
	 */
	void copy(File file, String directoryName, String fileName);

	/**
	 * Move file to another destination.
	 * 
	 * @param file
	 *            The file you want to move
	 * @param directoryName
	 *            The destination directory
	 * @param fileName
	 *            The destination file name
	 * @throws StorageException
	 */
	void move(File file, String directoryName, String fileName);
}
