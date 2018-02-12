package com.snatik.storage;

import android.test.InstrumentationTestCase;

public class StorageTestCase extends InstrumentationTestCase {

//	private StorageDel mStorage;
//
//	private final static String DIR_NAME = "Storage Test";
//	private final static String FILE_NAME = "test.txt";
//	private final static String FILE_CONTENT = "some file content";
//
//	private final static String FILE_SECURE_NAME = "test_secure.txt";
//	private final static String FILE_SECURE_CONTENT = "something very secret";
//
//	@Override
//	protected void setUp() throws Exception {
//		Context context = getInstrumentation().getContext();
//
//		// set a storage
//		mStorage = null;
//		if (SimpleStorageDel.isExternalStorageWritable()) {
//			mStorage = SimpleStorageDel.getExternalStorage(Environment.DIRECTORY_PICTURES);
//		} else {
//			mStorage = SimpleStorageDel.getInternalStorage(context);
//		}
//	}
//
//	@Override
//	protected void tearDown() throws Exception {
//
//		// delete dir if exists
//		mStorage.deleteDirectory(DIR_NAME);
//
//		super.tearDown();
//	}
//
//	/**
//	 * Create directory and check that the directory was created
//	 */
//	public void testCreateDirectory() {
//
//		// TEST: create dir
//		boolean wasCreated = mStorage.createDirectory(DIR_NAME, true);
//		assertEquals(true, wasCreated);
//
//	}
//
//	/**
//	 * Create directory and check that the directory was created
//	 */
//	public void testCreateFile() {
//
//		// create dir
//		testCreateDirectory();
//
//		// TEST: create file
//		boolean wasCreated = mStorage.createFile(DIR_NAME, FILE_NAME, FILE_CONTENT);
//		assertEquals(true, wasCreated);
//
//	}
//
//	/**
//	 * Create directory and check that the directory was created
//	 */
//	public void testReadFile() {
//
//		// create file with content
//		testCreateFile();
//
//		// TEST: read the content and test
//		String content = mStorage.readTextFile(DIR_NAME, FILE_NAME);
//		assertEquals(FILE_CONTENT, content);
//
//	}
//
//	/**
//	 * Create directory and check that the directory was created
//	 */
//	public void testAppendFile() {
//
//		// create file with content
//		testCreateFile();
//
//		String newData = "new added data";
//
//		// TEST: append new data and test
//		mStorage.appendFile(DIR_NAME, FILE_NAME, newData);
//		String content = mStorage.readTextFile(DIR_NAME, FILE_NAME);
//		assertTrue(content.contains(newData));
//	}
//
//	/**
//	 * Create file with encrypted data
//	 */
//	public void testEncryptContent() {
//
//		// create dir
//		testCreateDirectory();
//
//		// set encryption
//		final String IVX = "abcdefghijklmnop";
//		final String SECRET_KEY = "secret1234567890";
//
//		EncryptConfiguration configuration = new EncryptConfiguration.Builder().setEncryptContent(IVX, SECRET_KEY).build();
//		SimpleStorageDel.updateConfiguration(configuration);
//
//		// create file
//		mStorage.createFile(DIR_NAME, FILE_SECURE_NAME, FILE_SECURE_CONTENT);
//
//		// TEST: check the content of the file to be encrypted
//		String content = mStorage.readTextFile(DIR_NAME, FILE_SECURE_NAME);
//		assertEquals(FILE_SECURE_CONTENT, content);
//
//		// TEST: check after reseting the configuration to default
//		SimpleStorageDel.resetConfiguration();
//		content = mStorage.readTextFile(DIR_NAME, FILE_SECURE_NAME);
//		assertNotSame(FILE_SECURE_CONTENT, content);
//	}
//
//	public void testRename() {
//
//		// create file
//		testCreateFile();
//
//		// rename
//		File file = mStorage.getFile(DIR_NAME, FILE_NAME);
//		mStorage.rename(file, "new_" + FILE_NAME);
//		boolean isExist = mStorage.isFileExist(DIR_NAME, "new_" + FILE_NAME);
//		assertEquals(true, isExist);
//	}
//
//	public void testCopy() {
//
//		// create file
//		testCreateFile();
//
//		// copy file
//		File fileSource = mStorage.getFile(DIR_NAME, FILE_NAME);
//		mStorage.copy(fileSource, DIR_NAME, FILE_NAME + "C");
//
//		// validate existence
//		boolean isExist = mStorage.isFileExist(DIR_NAME, FILE_NAME + "C");
//		assertEquals(true, isExist);
//
//		// validate content
//		assertEquals(mStorage.readTextFile(DIR_NAME, FILE_NAME), mStorage.readTextFile(DIR_NAME, FILE_NAME + "C"));
//	}
//
//	public void testMove() {
//
//		// create file
//		testCreateFile();
//
//		// copy file
//		File fileSource = mStorage.getFile(DIR_NAME, FILE_NAME);
//		mStorage.move(fileSource, DIR_NAME, FILE_NAME + "C");
//
//		// validate existence destination
//		boolean isExist = mStorage.isFileExist(DIR_NAME, FILE_NAME + "C");
//		assertEquals(true, isExist);
//
//		// validate existence source (it shouldn't exist)
//		isExist = mStorage.isFileExist(DIR_NAME, FILE_NAME);
//		assertEquals(false, isExist);
//	}
//
//	public void testGetFilesByRegex() {
//
//		// create dir
//		testCreateDirectory();
//
//		// create 5 files
//		mStorage.createFile(DIR_NAME, "file1.txt", "");
//		mStorage.createFile(DIR_NAME, "file2.txt", "");
//		mStorage.createFile(DIR_NAME, "file3.log", "");
//		mStorage.createFile(DIR_NAME, "file4.log", "");
//		mStorage.createFile(DIR_NAME, "file5.txt", "");
//
//		// get files that ends with *.txt only. should be 3 of them
//		String TXT_PATTERN = "([^\\s]+(\\.(?i)(txt))$)";
//		List<File> filesTexts = mStorage.getFiles(DIR_NAME, TXT_PATTERN);
//		assertEquals(3, filesTexts.size());
//
//		// create more log files and check for *.log. should be 4 of them
//		String LOG_PATTERN = "([^\\s]+(\\.(?i)(log))$)";
//		mStorage.createFile(DIR_NAME, "file6.log", "");
//		mStorage.createFile(DIR_NAME, "file7.log", "");
//		List<File> filesLogs = mStorage.getFiles(DIR_NAME, LOG_PATTERN);
//		assertEquals(4, filesLogs.size());
//
//		// create dir and add files to dir. check again for *.log files. should
//		// be 4 of them.
//		mStorage.createDirectory(DIR_NAME + File.separator + "New Dir");
//		mStorage.createFile(DIR_NAME + File.separator + "New Dir", "file8.log", "");
//		mStorage.createFile(DIR_NAME + File.separator + "New Dir", "file9.log", "");
//		mStorage.createFile(DIR_NAME + File.separator + "New Dir", "file10.txt", "");
//		List<File> filesLogs2 = mStorage.getFiles(DIR_NAME, LOG_PATTERN);
//		assertEquals(4, filesLogs2.size());
//
//		// check inside new dir for *.log files. should be 2 of them
//		List<File> filesLogs3 = mStorage.getFiles(DIR_NAME + File.separator + "New Dir", LOG_PATTERN);
//		assertEquals(2, filesLogs3.size());
//	}
//
//	public void testGetFilesByOrder() {
//
//		// create dir
//		testCreateDirectory();
//
//		// TEST - Order by SIZE
//		mStorage.createFile(DIR_NAME, "file1.txt", "111222333");
//		mStorage.createFile(DIR_NAME, "file2.txt", "");
//		mStorage.createFile(DIR_NAME, "file3.log", "111");
//		List<File> filesSize = mStorage.getFiles(DIR_NAME, OrderType.SIZE);
//		assertEquals("file2.txt", filesSize.get(0).getName());
//		assertEquals("file3.log", filesSize.get(1).getName());
//		assertEquals("file1.txt", filesSize.get(2).getName());
//
//		// refresh directory
//		mStorage.deleteDirectory(DIR_NAME);
//		testCreateDirectory();
//
//		// TEST - Order by NAME
//		mStorage.createFile(DIR_NAME, "bbb.txt", "111222333");
//		mStorage.createFile(DIR_NAME, "ccc.txt", "");
//		mStorage.createFile(DIR_NAME, "aaa.log", "111");
//		List<File> filesName = mStorage.getFiles(DIR_NAME, OrderType.NAME);
//		assertEquals("aaa.log", filesName.get(0).getName());
//		assertEquals("bbb.txt", filesName.get(1).getName());
//		assertEquals("ccc.txt", filesName.get(2).getName());
//
//		// refresh directory
//		mStorage.deleteDirectory(DIR_NAME);
//		testCreateDirectory();
//
//		// TEST - Order by DATE
//		mStorage.createFile(DIR_NAME, "aaa.txt", "123456789");
//		sleep(1000);
//		mStorage.createFile(DIR_NAME, "bbb.txt", "123456789");
//		sleep(1000);
//		mStorage.createFile(DIR_NAME, "ccc.log", "123456789");
//		sleep(1000);
//		mStorage.appendFile(DIR_NAME, "bbb.txt", "some new content");
//		List<File> files = mStorage.getFiles(DIR_NAME, OrderType.DATE);
//		assertEquals("bbb.txt", files.get(0).getName());
//		assertEquals("ccc.log", files.get(1).getName());
//		assertEquals("aaa.txt", files.get(2).getName());
//	}
//
//	private void sleep(long millis) {
//		try {
//			Thread.sleep(millis);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
//	}
}
