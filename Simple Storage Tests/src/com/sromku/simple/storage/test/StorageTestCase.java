package com.sromku.simple.storage.test;

import android.content.Context;
import android.test.InstrumentationTestCase;

import com.sromku.simple.storage.SimpleStorage;
import com.sromku.simple.storage.SimpleStorageConfiguration;
import com.sromku.simple.storage.Storage;

public class StorageTestCase extends InstrumentationTestCase {

	private Context mContext;
	private Storage mStorage;

	private final static String DIR_NAME = "Storage Test";
	private final static String FILE_NAME = "test.txt";
	private final static String FILE_CONTENT = "some file content";

	private final static String FILE_SECURE_NAME = "test_secure.txt";
	private final static String FILE_SECURE_CONTENT = "something very secret";

	@Override
	protected void setUp() throws Exception {
		mContext = getInstrumentation().getContext();

		// set a storage
		mStorage = null;
		if (SimpleStorage.isExternalStorageWritable()) {
			mStorage = SimpleStorage.getExternalStorage();
		}
		else {
			mStorage = SimpleStorage.getInternalStorage(mContext);
		}
	}

	@Override
	protected void tearDown() throws Exception {

		// delete dir if exists
		// mStorage.deleteDirectory(DIR_NAME);

		super.tearDown();
	}

	/**
	 * Create directory and check that the directory was created
	 */
	public void testCreateDirectory() {

		// TEST: create dir
		boolean wasCreated = mStorage.createDirectory(DIR_NAME, true);
		assertEquals(true, wasCreated);

	}

	/**
	 * Create directory and check that the directory was created
	 */
	public void testCreateFile() {

		// create dir
		testCreateDirectory();

		// TEST: create file
		boolean wasCreated = mStorage.createFile(DIR_NAME, FILE_NAME, FILE_CONTENT);
		assertEquals(true, wasCreated);

	}

	/**
	 * Create directory and check that the directory was created
	 */
	public void testReadFile() {

		// create file with content
		testCreateFile();

		// TEST: read the content and test
		String content = mStorage.readTextFile(DIR_NAME, FILE_NAME);
		assertEquals(FILE_CONTENT, content);

	}

	/**
	 * Create directory and check that the directory was created
	 */
	public void testAppendFile() {

		// create file with content
		testCreateFile();

		String newData = "new added data";

		// TEST: append new data and test
		mStorage.appendFile(DIR_NAME, FILE_NAME, newData);
		String content = mStorage.readTextFile(DIR_NAME, FILE_NAME);
		assertTrue(content.contains(newData));
	}

	/**
	 * Create file with encrypted data
	 */
	public void testEncryptContent() {

		// create dir
		testCreateDirectory();

		// set encryption
		final String IVX = "abcdefghijklmnop";
		final String SECRET_KEY = "secret1234567890";

		SimpleStorageConfiguration configuration = new SimpleStorageConfiguration.Builder()
			.setEncryptContent(IVX, SECRET_KEY)
			.build();
		SimpleStorage.updateConfiguration(configuration);

		// create file
		mStorage.createFile(DIR_NAME, FILE_SECURE_NAME, FILE_SECURE_CONTENT);

		// TEST: check the content of the file to be encrypted
		String content = mStorage.readTextFile(DIR_NAME, FILE_SECURE_NAME);
		assertEquals(FILE_SECURE_CONTENT, content);

		// TEST: check after reseting the configuration to default
		SimpleStorage.resetConfiguration();
		content = mStorage.readTextFile(DIR_NAME, FILE_SECURE_NAME);
		assertNotSame(FILE_SECURE_CONTENT, content);
	}
}
