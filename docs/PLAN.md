# android-storage Revival Plan

Bring the 2017-era Java library back to life in Kotlin, then grow the sample app into a
device-inspection tool installed on every phone you own or develop on.

## Where the repo stands (Sept 2026)

| | |
|---|---|
| Last commit | 2018-04-02 |
| Gradle / AGP | 3.3 / 2.3.2 |
| compile / min / target | 25 / 14 / 25 |
| Language | Java only |
| Library | ~1,030 lines, 11 files |
| Sample app | ~1,200 lines, 12 files |
| Repos / publishing | jcenter + bintray (both dead) |
| UI | support-v7 25.3.1 |
| Tests / CI | 1 androidTest, no CI |

Nothing builds on a current machine. The library is a `Storage` facade over `java.io.File`
plus an AES layer. Two things to fix rather than translate: encryption regenerates its salt
every session (files can't be decrypted across runs unless the caller stores the salt), and
every operation swallows exceptions into a boolean.

## What we are building

The library stays as a thin, typed Kotlin module. The product is the app:

- **Explore** every volume: shared storage, app-private dirs, SD, USB, and with privilege `/data`.
- **Measure** space: per-app app/data/cache sizes, treemap, largest files, duplicates.
- **Inspect apps**: decoded manifest, components, permissions, intent filters, signing, APK export, app files/DBs/prefs.
- **Read data**: content providers as tables, SQLite with schema and queries, shared prefs, JSON/XML/image/hex viewers.
- **Watch and record**: broadcasts, incoming intents, logcat, file changes, screen. Sessions exportable.
- **Send and receive** files between devices and laptop without cables.
- **Snapshot** a directory or app data, diff two snapshots.
- **Expose an API** (HTTP + MCP) so Claude Code and other agents can drive all of it.

## Reality checks: capability vs privilege tier

Tiers: `none` | `permission` (user-granted setting) | `shizuku` (shell uid via ADB / wireless debugging) | `root`

| Capability | Needs | How |
|---|---|---|
| Browse/edit `/sdcard` | permission | `MANAGE_EXTERNAL_STORAGE` ("All files access"). Play restricts it, sideloading doesn't. |
| Other apps' `Android/data`, `Android/obb` | shizuku | Hidden from apps on Android 11+, shell can read. |
| Private data of an app you develop | shizuku | `run-as <pkg>` for debuggable builds. The dev-loop feature. |
| Private data of any app | root | `/data/data/<pkg>` is uid-owned; shell can't read it. |
| Per-app storage sizes | permission | `StorageStatsManager` + usage access. |
| App list, components, permissions | none | `QUERY_ALL_PACKAGES` in manifest. |
| Decoded manifest of any app | none | APK at `publicSourceDir` is readable; decode binary XML ourselves. |
| Query exported providers | none | `ContentResolver`, subject to provider permissions. |
| Query non-exported providers | root | Only uid 0 / system bypass the exported check. |
| Send activity/broadcast/service intents | none | Shizuku adds `am` as shell. |
| Observe system broadcasts live | none | Dynamic receivers for non-protected actions. |
| Recent broadcast/intent history | shizuku | `dumpsys activity broadcasts` needs `DUMP`. |
| Receive implicit intents meant for other apps | none | Register as VIEW/SEND/mime handler, log, offer to forward. Chooser-level interception. |
| Intercept explicit intents to another app | root | LSPosed hook. Deferred. |
| Logcat for all processes | shizuku | `READ_LOGS` is shell-level. |
| Install/uninstall/clear data/force stop/grant perms | shizuku | `pm`, `am`, `appops`. |
| Screen recording | none | `MediaProjection`. |
| HTTP/MCP API from laptop | none | Bind localhost, `adb forward`. Optional LAN + token. |

Shizuku is the backbone. Root is an optional upgrade behind the same interface. LSPosed is deferred.

## Stack

| Layer | Choice |
|---|---|
| Build | Kotlin 2.2+, JDK 17, AGP 8.x latest, Gradle 8.x/9, version catalog, KSP |
| SDK | compileSdk/targetSdk latest, minSdk 28 |
| UI | Compose, Material 3, Navigation Compose, Coil |
| Data | Coroutines/Flow, kotlinx.serialization, Room |
| Privilege | Shizuku API, libsu (root) behind one `ShellExecutor` |
| API | Ktor server (CIO): REST + MCP Streamable HTTP on one port |
| Manifest decoding | Own binary-XML (AXML) decoder, ~400 lines |
| DI | Koin |
| Tests | kotlin.test, Robolectric for the library, some Compose tests |
| Publishing | vanniktech maven-publish to Maven Central |
| CI / distribution | GitHub Actions; signed APK on tag; Obtainium-friendly releases |

## Modules

```
android-storage/
├── storage/          published library: com.snatik:storage (Kotlin, tiny, tested)
├── core/
│   ├── shell/        ShellExecutor: Plain | Shizuku | Root, streaming output, run-as
│   ├── fs/           FileSystem abstraction: LocalFs, SafFs, ShellFs + ops, hashing
│   ├── apps/         packages, StorageStats, AXML decoder, APK export
│   ├── data/         Room: snapshots, recordings, audit log, api tokens
│   ├── providers/    content provider discovery/querying, SQLite browsing
│   ├── intents/      builder, sink activity, broadcast monitor, dumpsys parsers
│   └── api/          Ktor server: REST routes, MCP tools, auth
└── app/              Compose UI only
```

Phases 0–2 touch only `storage` and `app`. Core modules are created when their phase begins.
Rule: `app` holds no logic an API caller could need; everything lives in `core`.

## Status

- 2026-09-08: Phases 0 and 1 done in one pass. Build on Gradle 9.7 / AGP 9.4 / Kotlin 2.4, library rewritten in Kotlin with 34 Robolectric and JVM tests, Java sample replaced by a Compose explorer (phase 2 seed). Maven Central publishing and the Compose explorer features are still open.

## Phases

### Phase 0 — Build resurrection
Goal: build on a 2026 machine without changing a line of Java.
- Tag current head `v2.1.0-legacy`.
- Regenerate wrapper; `settings.gradle.kts` with `google()` + `mavenCentral()`; `gradle/libs.versions.toml`; both build files to Kotlin DSL.
- `namespace`, remove `package` from manifests, drop jcenter and bintray scripts, set SDK levels.
- Rewrite support-library imports to AndroidX / Material Components. No Jetifier.
- Add Kotlin plugin now.
- GitHub Actions: assemble, unit tests, lint.

**Done when** `./gradlew assembleDebug testDebugUnitTest` is green locally and in CI, and the old sample installs and lists files.

### Phase 1 — Library to Kotlin, 3.0
- Mechanical conversion, commit, then idiomatic pass. Keep `Storage` and known method names.
- `Result` / sealed `StorageError` instead of booleans; `suspend` variants on `Dispatchers.IO`.
- `File` extension functions for common cases.
- Encryption replaced: AES-256-GCM, versioned header with salt + nonce, keys via PBKDF2 or Android Keystore. 2.x files not compatible, documented.
- Space/size helpers on `StorageManager`.
- Robolectric tests for every public function; binary-compatibility validator.
- Publish 3.0.0 to Maven Central; new README, Kotlin-first.

**Done when** no Java remains in `storage`, public API is tested, 3.0.0 resolves from Central.

### Phase 2 — App to Kotlin and Compose
- Delete old activities/dialogs/layouts; keep only the file provider.
- Onboarding for All-files access, usage access, Shizuku; shows active tier.
- Volume list; breadcrumbs, sort, filter, multi-select, long-press actions, drag to move.
- Viewers: text with syntax colour, hex, image, JSON tree, XML; open-with fallback.
- Operations as foreground work with progress and cancel, on the phase 1 library.
- SAF path where document URIs are unavoidable.

**Done when** you would use it instead of the OEM file manager daily.

### Phase 3 — Privilege layer
- `ShellExecutor`: `Plain`, `Shizuku`, `Root`; streaming stdout/stderr as `Flow`, timeouts, cancel, exit codes.
- Shizuku binding, permission request, wireless-debugging auto-start hint. libsu behind same interface.
- `ShellFs` backed by `ls -la`/`stat`/`cat`/`cp`/`mv`/`rm` (toybox parsing) so the explorer reaches `Android/data`, `/data/app`, and with root all of `/data`.
- `run-as` mode for debuggable packages.
- Audit log in Room: every privileged command, origin (UI/API), result.

**Done when** the explorer opens `/sdcard/Android/data` via Shizuku and a debuggable app's `shared_prefs` via run-as.

### Phase 4 — Apps and storage stats
- App list with app/data/cache sizes, filters, category totals.
- Disk analysis: size tree, treemap, largest/oldest files, empty dirs, duplicates by hash.
- App detail: version, uid, install source, signing certs, target SDK, data dirs, native libs, splits, APK export.
- Manifest tab: decoded `AndroidManifest.xml` with search, plus structured components, permissions, intent filters, deep links.
- Shizuku actions: force stop, clear cache/data, uninstall, grant/revoke permissions, app-ops.

**Done when** you can find why a device is full and read any app's manifest on device.

### Phase 5 — Content providers and databases
- Discovery of all `ProviderInfo`: authority, exported, permissions, path permissions, grant-URI.
- Query UI: URI builder with known paths per authority, projection, selection args, sort, paging, spreadsheet result. Export CSV/JSON.
- Shortcuts: MediaStore, Contacts, Calendar, Settings, Telephony, CallLog, Downloads.
- Insert/update/delete with confirmation where allowed.
- SQLite browser: any DB the tier can reach, copy-to-cache when needed, schema, rows, ad-hoc SQL, write-back via Shizuku/root.
- Shared preferences viewer/editor.

**Done when** you can query MediaStore and open your own app's Room DB on device.

### Phase 6 — Intents
- Builder: action, data, type, categories, component, flags, typed extras/bundles. Send as activity/broadcast/service. Presets.
- Sink: activity registered for VIEW/SEND/SEND_MULTIPLE and wide mime/scheme filters; logs full intent, offers to forward to another handler.
- Broadcast monitor: dynamic receivers for the catalogue of non-protected actions, live timeline, record to session.
- History via Shizuku: parsed `dumpsys activity broadcasts` / `activities`.
- Deep-link tester.
- Later, optional: LSPosed module for explicit-intent hooks.

**Done when** sharing a photo to this app shows every extra, and the monitor shows a charger event within a second.

### Phase 7 — Snapshots and recording
- Directory snapshot: hash tree (paths, sizes, mtimes, content hashes) in Room, optional content copy. Any path the tier can read.
- Diff view: added/removed/modified/moved, text content diff.
- Scheduled snapshots and a before/after flow.
- Recording sessions combining logcat (Shizuku), broadcast monitor, `FileObserver` watches, screen recording. One timeline, zip export.

**Done when** you can show exactly which files an app wrote during a test run.

### Phase 8 — Send and receive
- Device to device on LAN: NSD discovery, QR/short-code pairing, HTTP transfer with resume.
- Laptop to device: browser receive page on the same server, upload/download endpoints.
- Share-sheet target into a chosen folder.

**Done when** two phones exchange a folder over Wi-Fi and a laptop drops a file via browser.

### Phase 9 — External API and MCP
- Ktor foreground service, localhost by default (`adb forward tcp:PORT tcp:PORT`), optional LAN with bearer token shown as QR.
- REST routes over core modules: files, stats, apps, manifest, providers, databases, intents, snapshots, recordings, shell.
- MCP Streamable HTTP endpoint on the same server. Tools: `list_dir`, `read_file`, `write_file`, `stat`, `disk_usage`, `apps`, `app_info`, `manifest`, `query_provider`, `sql`, `send_intent`, `logcat`, `snapshot`, `diff`, `shell`.
- Safety: privileged/destructive tools off until enabled per session in UI; every call audited; kill switch in notification.
- On-device IPC: exported ContentProvider + bound service, signature/permission guarded.
- `mcp.json` snippet and a setup script in the repo.

**Done when** Claude Code on the laptop lists a device's apps and reads a manifest via MCP.

### Phase 10 — Release and upkeep
- Signed release on tag to GitHub Releases, Obtainium-compatible; F-Droid later if deps allow.
- README around the app with the library as a section; screenshots, tier table, MCP setup.
- Renovate/Dependabot for the catalog.

**Done when** a colleague installs from the release page and connects Claude Code in under five minutes.

## Decisions (recommendation in bold)

| Decision | Options | Recommendation |
|---|---|---|
| Minimum SDK | 26 / 28 / 29 / 31 | **28** |
| Library 3.0 compat | keep 2.1 signatures / break | **Break**; 2.x is unreachable anyway. Keep names, change types. |
| Maven group | `com.snatik` / `io.github.sromku` | **com.snatik** if you can verify snatik.com DNS, else `io.github.sromku` |
| Distribution | Play / GitHub Releases / both | **GitHub Releases** (all-files + query-all-packages are Play policy problems) |
| Privilege backbone | Shizuku first / root first | **Shizuku first**, root optional |
| App identity | keep `com.snatik.storage.app` / new | **New name and id** before phase 2; repo name stays |
| DI | Koin / Hilt / manual | **Koin** |
| Phase 6 hooking | LSPosed now / later / never | **Later**, only if devices end up rooted |

## First session (Phase 0, one commit per step)

1. Tag `v2.1.0-legacy` on current head, push.
2. JDK 17; `gradle wrapper` to latest 8.x; confirm daemon starts.
3. `settings.gradle.kts`, `gradle/libs.versions.toml`, both `build.gradle.kts`. Delete Groovy files and bintray lines.
4. Namespaces, SDK levels, Java 17 toolchain, Kotlin plugin, AndroidX + Material deps.
5. Fix imports in the twelve app files, build, install, open the explorer.
6. `.github/workflows/build.yml`, push, green.

Then Phase 1 starts with IDE Java-to-Kotlin conversion of `storage`, one file per commit, before any API redesign.
