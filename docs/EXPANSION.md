# Expansion Plan (round two)

From a solid explorer with an agent API to a device-intelligence tool: cleaner
navigation, dedicated viewers per file type, and deep visibility into what apps
do while they run. Full visual version was shared as an artifact.

## 1 · Navigation
Bottom bar drops from five tabs to four: **Storage, Apps, Data, Tools**. The
**Tools** hub holds Intents, Capture, Transfer and the Agent API, plus every new
capability below. Each opens as a pushed screen with a back arrow.
*(Done in round A.)*

## 2 · Collapsed toolbars
One search icon and one overflow (tune) icon that carries sort, filters and
actions as a menu or sheet; content starts at the top. Shared `Toolbar` pattern,
rolled out to browser, apps, data, provider results, database tables, recordings.
*(Started on Apps in round A.)*

## 3 · Dedicated viewers per file type
XML (tree, syntax, edit), SQLite (relationships, EXPLAIN, blobs), JSON/YAML/TOML
tree, images (EXIF, histogram, strip metadata), APK/AAR/DEX inspector without
installing, archive peek (zip/tar/gz), media metadata, smart binary
(magic bytes, ELF, protobuf). Driven by a small theme-aware highlighting engine
with grammars for Kotlin, Java, XML, JSON, SQL, shell, properties.

## 4 · What apps actually do  (shell tier)
An **App Watch** dossier per app:
- Sensitive access with timestamps from `dumpsys appops` / `cmd appops get`:
  BLUETOOTH_SCAN, FINE_LOCATION, CAMERA, RECORD_AUDIO, READ_CLIPBOARD,
  SYSTEM_ALERT_WINDOW and more — answers "did it silently scan bt/wifi".
- Network endpoints per app from a local `VpnService` (no root).
- Running services/processes from `dumpsys activity services|processes`, `top`.
- Cross-app intent attempts by parsing `ActivityTaskManager: START ... from uid`
  and `dumpsys activity broadcasts`.
- Storage growth from scheduled data-dir snapshots + StorageStatsManager.
- Wakelocks, alarms, jobs, battery from `dumpsys batterystats|alarm|jobscheduler`.
A **suspicion score** rolls these up, each signal linking to its evidence.

## 5 · Stats and visualizations
Device dashboard (storage treemap across volumes, top apps by size/network/
battery, security patch, SELinux, sensors, uptime), storage growth charts,
permission heat matrix (apps × dangerous permissions), network map, app-ops
timeline.

## 6 · Ten more capabilities
Network Monitor (local VPN), App-Ops Console, Services & Processes, Permission
Matrix, Battery & Wakelocks, Clipboard & Notification Monitor, APK Inspector,
Global Search (find/grep), Content Provider Watcher, Scheduled Reports.

## Phasing
- **A (done):** navigation rework + collapsed toolbars begin.
- **B:** highlighting engine + dedicated viewers.
- **C:** App Watch dossier + suspicion score.
- **D:** Network Monitor (local VPN) + network map.
- **E:** device dashboard, permission matrix, app-ops timeline, battery.
- **F:** clipboard/notification monitor, global search, provider watcher, scheduled reports.
