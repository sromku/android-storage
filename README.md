android-simple-storage
======================

Library to create, read, delete, append, encrypt files and more, on internal or external disk spaces with a really simple API.

Many times in our apps, we need to manage files on disk. It can be on external or internal storage. Sometimes we need to create a directory, add files, append text to some file, delete files and even encrypt the data in the files. <br><br>
In my projects, I found myself reading the same Android doc - [Storage Options](http://developer.android.com/guide/topics/data/data-storage.html) many times to create the same methods. After a while, I wrote a simple library to be used in my apps. Now, it's the time to share and make it better thanks to you.

## Features
* Create directory
* Create file
* Read file
* Append to file
* Delete directory
* Delete file
* Encrypt the file content
* More...

## Setup Project

1. Clone and import this (Simple Storage) project to your workspace.

2. Add reference from **your app** to `Simple Storage` project.

3. Update the `manifest.xml` of your application and add next line:

    ``` java
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    ```

## Usage

#### Initialize options

- Prepere to work on **External Storage**.

    ```java
    Storage storage = SimpleStorage.getExternalStorage();
    ```

- Prepare to work on **Internal Storage**. In your `Activity` or `Application` or any other place where you have `Context`:

    ```java
    Storage storage = SimpleStorage.getInternalStorage(mContext);
    ```
    
- You prefer to use **External Storage**, but if it doesn't exist on the device, then use **Internal Storage**.

  ```java
  Storage storage = null;
  if (SimpleStorage.isExternalStorageWritable()) {
      storage = SimpleStorage.getExternalStorage();
  }
  else {
      storage = SimpleStorage.getInternalStorage(mContext);
  }
  ```

#### Create directory
```java
storage.createDirectory("MyDirName");
```

#### Create file
```java
storage.createFile("MyDirName", "fileName.txt", "some data");
```

#### Read file
```java
byte[] bytes = storage.readFile("MyDirName", "fileName.txt");
```

#### Append file
```java
storage.appendFile("MyDirName", "fileName.txt", "more new data");
```

#### Delete directory
```java
storage.deleteDirectory("MyDirName");
```

#### Delete file
```java
storage.deleteFile("MyDirName", "fileName.txt");
```

#### Much More APIs...
To be explained

***

[![Bitdeli Badge](https://d2weczhvl823v0.cloudfront.net/sromku/android-simple-storage/trend.png)](https://bitdeli.com/free "Bitdeli Badge")
