<img src="http://code.sromku.com/static/img/img_android_code.jpg" height="30" width="30"/> android-simple-storage
======================

Library to create, read, delete, append, encrypt files and more, on internal or external disk spaces with a really simple API.

Many times in our apps, we need to manage files on disk. It can be on external or internal storage. Sometimes we need to create a directory, add files, append text to some file, delete files and even encrypt the data in the files. <br><br>
In my projects, I found myself reading the same Android doc - [Storage Options](http://developer.android.com/guide/topics/data/data-storage.html) many times to create the same methods. After a while, I wrote a simple library to be used in my apps. Now, it's the time to share and make it better thanks to you.

## Features
* [Easy define Internal or External storage](#initialize)
* [Create directory](#create-directory)
* [Create file](#create-file)
* [Read file content](#read-file)
* [Append content to file](#append-content-to-file)
* [Copy](#copy)
* [Move](#move)
* [Delete directory](#delete-directory)
* [Delete file](#delete-file)
* [Get files](#get-files)
* [More options](#more)
* [Encrypt the file content](#security-configuration)

## Setup Project

1. Clone and import this (Simple Storage) project to your workspace.

2. Add reference from **your app** to `Simple Storage` project.

3. Update the `manifest.xml` of your application and add next line:

	``` java
	<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
	```

### Gradle build

To deploy the library to your local Maven repository run the following task:

```
$ ./gradlew install
```

Then, to use the library in your project add the following to your `build.gradle`:

```groovy
dependencies {
    compile 'com.sromku.simple.storage:library:1.0.0'
}
```

## Usage

### Initialize
You have the next options to initialize the simple storage:

- Prepere to work on **External Storage**.

	``` java
	Storage storage = SimpleStorage.getExternalStorage();
	```

- Prepare to work on **Internal Storage**. In your `Activity` or `Application` or any other place where you have `Context`:

	``` java
	Storage storage = SimpleStorage.getInternalStorage(mContext);
	```
    
- You prefer to use **External Storage**, but if it doesn't exist on the device, then use **Internal Storage**.

	``` java
 	Storage storage = null;
 	if (SimpleStorage.isExternalStorageWritable()) {
		storage = SimpleStorage.getExternalStorage();
	}
	else {
		storage = SimpleStorage.getInternalStorage(mContext);
	}
	```

### Create directory

- Create directory under the root path.

	``` java
	// create directory
	storage.createDirectory("MyDirName");
	```
 
- Create **sub directory**. 

	``` java
	// create directory
	storage.createDirectory("MyDirName/MySubDirectory");
	```

- Create directory and **override** the existing one. 

	``` java
	// create directory
	storage.createDirectory("MyDirName", true);
	```

### Create file
Create a new file with the content in it.
``` java
// create new file
storage.createFile("MyDirName", "fileName", "some content of the file");
```

The `content` of the file can be one of the next types:
- `String`
- `byte[]`
- `Bitmap`
- `Storable`

### Read file

Read the content of any file to byte array.
``` java
byte[] bytes = storage.readFile("MyDirName", "fileName");
```

Read the content of the file to String.
``` java
String content = storage.readTextFile("MyDirName", "my_text.txt");
```

### Append content to file
``` java
storage.appendFile("MyDirName", "fileName", "more new data");
```

You can append:
- `String`
- `byte[]`

### Copy
``` java
storage.copy(file, "MyDirName", "newFileName");
```

### Move
``` java
storage.move(file, "MyDirName", "newFileName");
```

### Delete directory
``` java
storage.deleteDirectory("MyDirName");
```

### Delete file
``` java
storage.deleteFile("MyDirName", "fileName");
```

### Get files
- Get files in ordered way by: `name`, `date`, `size`
	``` java
	List<File> files = storage.getFiles("MyDirName", OrderType.DATE);
	```

- Get files and filter by regular expression:
	``` java
	String regex = ...;
	List<File> files = storage.getFiles("MyDirName", regex);
	```

* Get all nested files (without the directories)
	``` java
	List<File> files = storage.getNestedFiles("MyDirName");
	```

### More...

* Is directory exists
	``` java
	boolean dirExists = storage.isDirectoryExists("MyDirName");
	```

* Is file exists
	``` java
	boolean fileExists = storage.isFileExist("MyDirName", "fileName");
	```

## Security configuration
You can write and read files while the content is **encrypted**. It means, that no one can read the data of your files from external or internal storage.

You will continue using the same api as before. The only thing you need to do is to configure the Simple Storage library before the you want to create/read encrypted data.

``` java
// set encryption
String IVX = "abcdefghijklmnop"; // 16 lenght - not secret
String SECRET_KEY = "secret1234567890"; // 16 lenght - secret

// build configuratio
SimpleStorageConfiguration configuration = new SimpleStorageConfiguration.Builder()
	.setEncryptContent(IVX, SECRET_KEY)
	.build();
	
// configure the simple storage
SimpleStorage.updateConfiguration(configuration);
```

Now, you can create a new file with content and the content will be automatically encrypted.<br>
You can read the file and the content will be decrypted.

**Example**

Create file with next content `"this is the secret data"`:
``` java
storage.createFile("MyDirName", "fileName", "this is the secret data");
```

If we open the file to see it's content then it we will something like this: `„f°α�ΤG†_iΐp` . It looks good :)

And now, read the file data with the same api:
``` java
String content = storage.readTextFile("MyDirName", "fileName");
```
You will see that the content will be: `"this is the secret data"`.

## Tests

Test project includes android junits which covers most of the functionality.
