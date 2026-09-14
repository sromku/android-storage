# android-storage

A tiny file library from 2017, loved and then archived — brought back not as another library, but as
one of the most capable **storage and device-inspection apps on Android**. Browse a phone like a file
manager, then keep going: inspect apps and APKs, read content providers and databases, watch the
intents and broadcasts moving through the system, reclaim space, and hand the whole thing to an AI
agent over HTTP and MCP.

<p align="center">
  <img src="assets/home.png" width="30%" alt="Home: volumes, usage and the app's own directories" />
  <img src="assets/browser.png" width="30%" alt="File browser with breadcrumbs, thumbnails and multi-select" />
  <img src="assets/hex.png" width="30%" alt="Binary inspector: hex, extracted strings and type detection" />
</p>

The app is Kotlin and Jetpack Compose. The old library still ships underneath as `com.snatik:storage`,
rewritten in Kotlin — see [the library](#the-library) below and [CHANGELOG.md](CHANGELOG.md).

## The story

**2017.** `android-storage` was a small Java library — a friendly wrapper over `java.io.File` that made
creating, reading, copying, listing, measuring and encrypting files a one-liner. It found its way into
a lot of apps and a lot of stars.

**Then it stopped.** Maintenance ended, jcenter died, and the repository was archived while Android
moved on by a decade of API levels.

**Then the world changed.** Today you can just ask an assistant to save a file, read a file, or do
almost any storage chore for you. A thin convenience wrapper is no longer the thing that's missing.

**2026 — the revival.** So this comes back as something new: an app that goes from the simplest need —
browsing and managing your files — all the way to heavy, privileged, forensic work, and doubles as a
companion for building and validating your *own* apps. The library stays underneath, rewritten in
Kotlin, but the point now is the app, and it's built to live in the AI world: everything it can do is
exposed over an HTTP and [MCP](https://modelcontextprotocol.io) API, so Claude Code and other agents
can drive the device directly.

## What it does

Depending on how much access you grant it (see [how far it reaches](#how-far-it-reaches)), the app
spans a lot of ground.

### Browse & view

- Volumes overview with usage, plus the app's own directories.
- File browser with breadcrumbs, search, sort by name/date/size/type, and a hidden-files toggle.
- Thumbnails for images and video, typed icons for everything else; multi-select copy / move / delete /
  share, rename, new folder and file, with background operations that show progress and cancel.
- A details sheet with path, size, permissions and on-demand SHA-256.
- Viewers per type: syntax-highlighted code and text, JSON and XML trees, a binary inspector (hex,
  extracted strings, magic-byte type detection, schema-less protobuf), images with pinch-zoom and an
  EXIF sheet, an in-app video and audio player (Media3), and a zip archive browser.
- Renders Android vector drawables (`<vector>`) on a canvas — paths, gradients, clip-paths, trim
  strokes — with pinch-zoom, both from disk and decoded out of an APK.
- Global search: find files by name or grep their text content anywhere the current tier can reach.

### Measure & reclaim

- Per-app app / data / cache sizes, and a drillable **sunburst** and **treemap** of where space goes.
- **Storage insights**: duplicate files (grouped by size, confirmed by SHA-256), empty directories,
  zero-byte files, and ghost footprints left under `Android/data` and `Android/obb` by uninstalled apps.
- **App storage breakdown**: base APK, split APKs, OAT/ART artifacts, native libs, and private data,
  cache and external data as a stacked bar.
- A storage **benchmark** (sequential and random read/write throughput and IOPS) and a **Time Machine**
  that records storage on a schedule, charts free space over time and forecasts when the device fills.

### Apps & APKs

- Every package with app / data / cache sizes, filters for user, system and debuggable, and sort.
- App detail: storage breakdown, install facts, signing certificate, the decoded and searchable
  `AndroidManifest.xml`, components with exported flags, and requested permissions with grant state.
  With a shell: force stop, clear cache or data, uninstall, and grant or revoke runtime permissions.
- **APK deep inspector**: open any package file without installing it — Overview (SDKs, component and
  resource counts, DEX list, native ABIs), the full decoded manifest, Resources grouped by type,
  Contents (every zip entry), and Signing (v1/v2/v3 with certificate hashes). Every entry is openable.
- **APK Analyse**: a drillable sunburst over Code / Native libs / Resources / Assets / Signing / Other.
- **Decompile** an APK's dex to readable Java (jadx), one class at a time, off the UI thread.
- **ELF inspector**: architecture, stripped state, SONAME, build-id, per-section entropy, dependency
  list, and flags for known packers or unusually high entropy. Opens automatically for `.so` files.
- **App behavior**: what an app actually did — location, Bluetooth/Wi-Fi scans, camera, mic, clipboard
  and contacts access with timestamps, running services, recently changed files, and a suspicion score.
- **ART compilation**: recompile an app's dex to a chosen filter (speed, profile, verify, reset).

### Read the device's data

- Every content provider as a table, with projection, selection, sort and paging, exportable as CSV or
  JSON; providers the app may not read directly are queried as the shell user when Shizuku is connected.
- **SQLite** browser: tables with row counts, schema and foreign-key relationships, sortable paged rows,
  a SQL console, and one-tap VACUUM and integrity check. Databases that can't be opened in place are
  copied and can be saved back.
- **Shared preferences**: open the XML of any app you can reach, edit typed values, add or delete keys.
- **Permission matrix** (every app against the dangerous permissions), **permission-vs-footprint**
  ranking, and an **app-ops timeline** of recent sensitive access device-wide.
- **Network**: a live view of which apps are talking to the network, remote host, port, state and byte
  totals, read from the kernel connection tables — no VPN.
- **Device dashboard** and **System** view: model, build, kernel, SELinux, uptime, RAM, battery,
  wakelocks, mounts, partitions, swap and ZRAM (some figures need root).

### Watch & record

- **Intents**: build any activity, broadcast or service intent with typed extras and flags, see which
  components would receive it, send it or save it as a preset.
- **Broadcast monitor** (live, in the background) with the system's recent broadcast history from
  `dumpsys`, and an **intent monitor** of activity launches across the device.
- **Deep-link tester**: discovers the real web App Links (with paths) and custom schemes the device
  advertises, and mines each scheme's example URIs from the handling app's own code — nothing guessed.
- **Notification** and **clipboard** monitors, and a **provider-change** watch.
- **Capture**: freeze a folder into a snapshot and diff two snapshots (added / removed / modified /
  moved, with a text diff); recording sessions capture logcat, broadcasts, file changes and optionally
  the screen as a foreground service, and export as a zip.

### For developers & agents

- Read your **own debuggable app's** private files, databases and prefs via `run-as` — the dev loop.
- **Receive files** from a browser (drop page with a pairing code and QR) or another phone, over Wi-Fi
  or `adb forward`, with resume.
- **Agent API**: the same engine exposed as 27 operations over REST (`/api/v1/{op}`) and Model Context
  Protocol (`/mcp`) — list apps, read files, decode a manifest, query a provider, run SQL, take a
  snapshot, run a shell command, read device stats and more. Behind a bearer token and two safety gates
  (shell tools and device changes), both off by default, with an audit log.

```bash
adb forward tcp:8484 tcp:8484
curl -s localhost:8484/api/v1/apps -H "Authorization: Bearer $TOKEN"
```

## How far it reaches

Nothing is faked. Each capability is honest about what it needs, and the app works at every tier — from
no permissions at all, up to root when a device has it.

| Capability | Needs |
|---|---|
| Browse your own app dirs, inspect any APK, list apps & components | **none** |
| Browse all of shared storage, per-app storage sizes | **permission** (`MANAGE_EXTERNAL_STORAGE`, usage access) |
| Other apps' `Android/data`, your debuggable app's private files, logcat, `pm`/`am`/`appops`, dumpsys history | **[Shizuku](https://shizuku.rikka.app/)** (shell UID over ADB / wireless debugging) |
| Any app's private `/data/data`, non-exported providers, partitions & swap | **root** |

Shizuku gives the app a shell UID without root, which unlocks most of the powerful features.

## The library

`com.snatik:storage` lives on as a thin, typed Kotlin module. Every operation returns `Result<T>`;
a failure is always a `StorageException` (`NotFound`, `AlreadyExists`, `NotADirectory`/`NotAFile`,
`Io`, `Crypto`, `Unsupported`). Calls block the calling thread — wrap them in `withContext(Dispatchers.IO)`.

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

Directories, files, copy/move/rename, sizes and listings:

```kotlin
storage.createDirectory(path, override = true)      // wipes an existing directory first
storage.listFiles(path, nameMatches = Regex(".*\\.txt"), order = FileOrder.NEWEST_FIRST)
storage.listFilesRecursively(path)
storage.directorySize(path)                          // Result<Long>

storage.createFile(path, bitmap, Bitmap.CompressFormat.JPEG, quality = 90)
storage.appendLine(path, "a line")
storage.copy(from, to)      // file or whole directory tree
storage.move(from, to)      // rename when possible, else copy then delete
storage.readableSize(path)  // "1.5 MB"
```

Encryption is AES-256-GCM with a fresh random nonce per file, so identical content never produces
identical bytes and tampering is detected:

```kotlin
// Hardware-backed key that never leaves the device
val secure = Storage(context, Encryption.fromKeystore("my-files"))

// Or a passphrase — generate the salt once and keep it next to your data
val salt = Encryption.generateSalt()
val secure = Storage(context, Encryption.fromPassphrase("correct horse".toCharArray(), salt))

secure.createFile(path, "secret")
secure.readTextFile(path)               // "secret"
Storage(context).readTextFile(path)     // ciphertext, not readable
```

`appendFile` is not available on encrypted storage, since an encrypted file is one authenticated
message. Files written by the 2.x library (AES-CBC) cannot be read by 3.0. Requires `minSdk 28`.

## Architecture

Everything the UI can do is reachable from `core`, which is what the HTTP and MCP API calls:

- `core/fs` — the file-system abstraction, volumes, operations and the disk scanner
- `core/apps` — package inspection and the binary XML decoder
- `core/data` — provider queries, the SQLite inspector and the preferences codec
- `core/intents` — the intent model, sender, broadcast monitor, history and deep-link parsers
- `core/capture` — the Room database, snapshots, diffs and the recording engine
- `core/net` — the Ktor server, peer discovery and the transfer client
- `core/shell` — the privilege layer and a `ShellExecutor` with plain, Shizuku and root backends

The Shizuku side is a hand-written Kotlin `Binder`, so there's no AIDL and no generated Java. `app` is
Compose only, with Navigation 3, Koin and Coil. The roadmap is in [docs/PLAN.md](docs/PLAN.md).

## Building

JDK 17 or newer and the Android SDK with platform 37.

```bash
./gradlew build
./gradlew :app:installDebug
```

## License

Apache 2.0, see [LICENSE](LICENSE).
