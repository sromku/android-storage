# Changelog
All notable changes to this project will be documented in this file.

## [3.0.0] - Unreleased
### Changed
- Rewritten in Kotlin. The whole repository is Kotlin only, no Java remains.
- Build moved to Gradle 9, AGP 9, Kotlin 2.4, Kotlin DSL and a version catalog. Published to Maven Central instead of the defunct bintray.
- minSdk raised from 14 to 28.
- Every operation returns `Result<T>`. Failures are typed `StorageException` subclasses (`NotFound`, `AlreadyExists`, `NotADirectory`, `NotAFile`, `Io`, `Crypto`, `Unsupported`) instead of boolean false or null.
- `getFiles` is now `listFiles(dir, nameMatches, order)`, `getNestedFiles` is `listFilesRecursively`, `getSize` is `size`, `getReadableSize` is `readableSize`. Directory locations are properties returning `File`.
- `copy` and `move` handle directories as well as files. `move` renames when possible and falls back to copy and delete.
- `appendFile` no longer adds a line separator. Use `appendLine` for that.
- `OrderType` replaced by `FileOrder` with five orderings. `SizeUnit` gained `convert` and `Long.toReadableSize()`.

### Added
- `Encryption`: AES-256-GCM with a random nonce per file, keys from a raw key, a passphrase (PBKDF2-HMAC-SHA256) or the Android Keystore (`Encryption.fromKeystore`).
- `directorySize`, `totalSpace`, `externalFilesDirectory`.
- Robolectric unit tests covering the public API.
- Sample app replaced by a Jetpack Compose explorer, the seed of the device inspection app described in docs/PLAN.md.

### Removed
- `EncryptConfiguration` and the AES-CBC scheme. Files encrypted with 2.x cannot be read by 3.0.
- `Storage.getInternalRootDirectory` (it returned `/system`).

## [2.1.0] - 2017-07-01
### Added
- Sample app

### Changed
- Refactored and changed package name to com.snatik.storage.
- Configuration class changed to by security configuration.
- All methods will work with absolute file and directory paths only.

### Removed
- Internal and External classes implementations.
- Test and library is used in sample app.
- No more RuntimeException, but added logs and return boolean true/false upon execution.

### Fixed
- Random salt is generated on each app session.

## [1.2.1] - 2017-05-22
### Added
- Support for public directories.
