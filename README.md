android-storage
======================

A small Kotlin library for files on Android: create, read, append, copy, move, delete, list, measure and encrypt on internal or external storage. Plus an app, being built on top of it, for exploring everything a device stores.

The library is `com.snatik:storage`. Version 3.0 is a Kotlin rewrite of the 2017 Java library; see [CHANGELOG.md](CHANGELOG.md) for what changed and [docs/PLAN.md](docs/PLAN.md) for where the project is going.

## Install

Maven Central publishing for 3.0.0 is being set up. Until then, include the `:storage` module from this repository.

```kotlin
dependencies {
    implementation("com.snatik:storage:3.0.0")
}
```

Requires minSdk 28.

## Usage

```kotlin
val storage = Storage(context)

val dir = storage.externalStorageDirectory.resolve("My Sample Directory").absolutePath
storage.createDirectory(dir)

val file = "$dir/notes.txt"
storage.createFile(file, "first line")
storage.appendLine(file, "second line")

storage.readTextFile(file)
    .onSuccess { println(it) }
    .onFailure { error -> println("could not read: $error") }
```

Every operation returns `Result<T>`. A failure is always a `StorageException`:

| Exception | Meaning |
|---|---|
| `NotFound` | the path does not exist |
| `AlreadyExists` | the path exists and the operation refuses to overwrite |
| `NotADirectory` / `NotAFile` | wrong kind of path |
| `Io` | the file system refused; the cause is attached |
| `Crypto` | wrong key, tampered data or unknown format |
| `Unsupported` | not available in this configuration |

Calls block the calling thread. Wrap them in `withContext(Dispatchers.IO)` from a coroutine.

### Locations

```kotlin
storage.externalStorageDirectory            // /storage/emulated/0
storage.externalPublicDirectory(Environment.DIRECTORY_PICTURES)
storage.externalFilesDirectory              // Android/data/<pkg>/files, or null
storage.internalFilesDirectory              // /data/user/0/<pkg>/files
storage.internalCacheDirectory
storage.isExternalWritable
```

Browsing shared storage on Android 11 and later needs `MANAGE_EXTERNAL_STORAGE` or the Storage Access Framework. Your own directories need nothing.

### Directories

```kotlin
storage.createDirectory(path)                       // parents created, fails if it exists
storage.createDirectory(path, override = true)      // wipes an existing directory first
storage.deleteDirectory(path)
storage.listFiles(path)
storage.listFiles(path, nameMatches = Regex(".*\\.txt"), order = FileOrder.NEWEST_FIRST)
storage.listFilesRecursively(path)                  // every regular file below path
storage.directorySize(path)                         // Result<Long>
```

`FileOrder`: `NAME`, `NEWEST_FIRST`, `SMALLEST_FIRST`, `LARGEST_FIRST`, `DIRECTORIES_FIRST`.

### Files

```kotlin
storage.createFile(path, "text")
storage.createFile(path, byteArray)
storage.createFile(path, bitmap, Bitmap.CompressFormat.JPEG, quality = 90)
storage.createFile(path, Storable { myObject.serialize() })
storage.readFile(path)          // Result<ByteArray>
storage.readTextFile(path)      // Result<String>
storage.appendFile(path, bytes)
storage.appendLine(path, "a line")
storage.deleteFile(path)
storage.exists(path); storage.isFile(path); storage.isDirectory(path)
```

### Copy, move, rename

```kotlin
storage.copy(from, to)      // file or whole directory tree, replaces existing files
storage.move(from, to)      // rename when possible, otherwise copy then delete
storage.rename(from, to)    // fails if `to` exists
```

### Sizes

```kotlin
storage.size(path, SizeUnit.MB)         // Double
storage.readableSize(path)              // "1.5 MB"
storage.freeSpace(path, SizeUnit.GB)
storage.usedSpace(path)
storage.totalSpace(path)
1536L.toReadableSize()                  // "1.5 KB"
```

### Encryption

Pass an `Encryption` to `Storage` and every `createFile` encrypts, every `readFile` decrypts. The scheme is AES-256-GCM with a fresh random nonce per file, so identical content never produces identical bytes and any tampering is detected.

```kotlin
// Hardware-backed key that never leaves the device
val secure = Storage(context, Encryption.fromKeystore("my-files"))

// Or a passphrase. Generate the salt once and keep it next to your data.
val salt = Encryption.generateSalt()
val secure = Storage(context, Encryption.fromPassphrase("correct horse".toCharArray(), salt))

// Or a raw 16, 24 or 32 byte key
val secure = Storage(context, Encryption.fromKey(keyBytes))

secure.createFile(path, "secret")
secure.readTextFile(path)               // "secret"
Storage(context).readTextFile(path)     // ciphertext, not readable
```

`appendFile` is not available on encrypted storage, since an encrypted file is one authenticated message. Files written by the 2.x library (AES-CBC) cannot be read by 3.0.

## The app

The `app` module is a Jetpack Compose file explorer and the foundation of a device inspection tool: storage statistics, app and manifest inspection, content provider and database browsing, intent tooling, snapshots, transfers and an HTTP and MCP API for agents. The roadmap is in [docs/PLAN.md](docs/PLAN.md).

What it does today:

- Volumes overview with usage, plus the app's own directories.
- Browse any directory with breadcrumbs, search, sort by name, date, size or type, and hidden files toggle.
- Thumbnails for images and videos, typed icons for everything else.
- Multi-select with copy, move, delete and share. Rename, new folder, new file. Operations run in the background with progress and cancel.
- Details sheet with path, size, permissions and on-demand SHA-256.
- Viewers for text and code with line numbers and JSON pretty-print, hex with ASCII column, and images with pinch zoom.

<p>
<img src="assets/home.png" width="230"/>
<img src="assets/browser.png" width="230"/>
<img src="assets/hex.png" width="230"/>
</p>

Architecture: `core/fs` holds the file system abstraction, volumes and operations with no UI dependency; `app` is Compose only with Navigation 3, Koin and Coil. Everything the UI can do is reachable from `core`, which is what the HTTP and MCP API will call later.

## Building

JDK 17 or newer and the Android SDK with platform 37.

```
./gradlew build
./gradlew :app:installDebug
```

## License

Apache 2.0, see [LICENSE](LICENSE).
