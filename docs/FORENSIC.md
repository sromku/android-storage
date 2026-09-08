# Forensic Suite Roadmap

The broad forensic vision folded into the existing rounds, with a feasibility
read on each idea. Visual version shared as an artifact.

## Already built (that the vision also asks for)
Shizuku shell; manifest/component/permission explorer; APK export + APK
inspector without install; SQLite browsing; per-app sizes + squarified disk
treemap; app-ops behavior + suspicion score; live per-app network connections;
snapshots + diffs; Ktor REST + MCP + self-documenting /api/v1/tools over
adb forward; modular Gradle; MANAGE_EXTERNAL_STORAGE, PACKAGE_USAGE_STATS,
QUERY_ALL_PACKAGES.

## Will NOT work as described (honest)
- ptrace "clone" sandbox of another app — NOT FEASIBLE (SELinux blocks cross-uid
  ptrace; apps can't be cloned into our process). Root-only and still not a clone.
- Global inotify/fanotify catching other apps' hidden writes — ROOT ONLY; FUSE
  shared storage never emits other apps' events.
- /proc/[pid]/smaps, /proc/net/unix, other uids' fds — ROOT ONLY (hidepid).
- Unlinked-but-open file recovery via raw block reads — ROOT ONLY.
- Proving cross-app collusion — heuristic only (shared world-readable dirs,
  shared ad SDKs from static APK scan); a signal, never a verdict.
These go in an experimental root-gated track (L), clearly labeled.

## Rounds
- **E (done):** device dashboard (model/OS/patch/kernel/SELinux/uptime, RAM,
  battery, ZRAM), permission heat matrix (every app vs the dangerous
  permissions), device-wide app-ops timeline, battery wakelocks. [shell]
- **F (done):** global search (shell find/grep with an unprivileged walk
  fallback), device-wide notification monitor (NotificationListenerService),
  content-provider watcher (ContentObserver on well-known URIs), clipboard
  inspector (foreground-only, per Android 10+). Scheduled reports moved to
  Round I, where the WorkManager telemetry pipeline already lives. [shell]
- **G (done):** storage deep-dive — drillable sunburst with a radial
  breadcrumb, app storage decomposition (base APK / splits / OAT / native libs
  / data / cache / external), duplicate finder (size-grouped then SHA-256),
  empty-directory and zero-byte hunters, ghost footprints (leftover
  Android/data & obb for uninstalled apps). Symlink-aware sizing folded into
  the native round (H)'s raw stat walk. [shell]
- **H (done, Kotlin path):** System screen (/proc/mounts, /proc/partitions,
  /proc/swaps, /sys/block/zram0, /proc/self/smaps rollup — the root-only ones
  degrade gracefully), ELF inspector (header, sections + per-section Shannon
  entropy, DT_NEEDED, SONAME, GNU build-id, packer signatures) as a viewer for
  .so files, and raw mode/owner/SELinux context shown via the shell (the same
  data a native lstat/lgetxattr would return). The C++/NDK module
  (getdents64 fast scan, in-process raw stat without a shell, shared .so dedup)
  is deferred: no NDK/CMake toolchain is installed on this machine and there is
  no sdkmanager to fetch one. Tracked in docs/ROOT.md.
- **I (done):** Time Machine — WorkManager periodic telemetry snapshots (Room),
  a free-space line graph, predictive exhaustion by least-squares regression,
  per-app growth deltas with version-change flags, and cache velocity. On-demand
  capture too. TelemetryForecastTest covers the regression.
- **J (done):** power tools — SQLite VACUUM + integrity check + schema/ER
  overview (table stats and foreign-key edges), storage benchmark engine
  (sequential + random read/write throughput and IOPS), ART recompile actions
  on the app detail (`cmd package compile` speed / speed-profile / verify /
  reset). Bulk ops already exist via the browser's multi-select. FFmpeg
  transcode deferred (a large native dependency), noted in docs/ROOT.md.
- **K (done):** a global command palette (fuzzy-jump to every tool and tab),
  a permission-vs-footprint ranking (apps by dangerous-permission count against
  size), and every new forensic capability exposed through the REST + MCP API
  (27 tools total: device_stats, permission_matrix, app_ops_timeline, network,
  storage_insights, app_storage, elf_inspect, system_report, telemetry_forecast,
  capture_telemetry, search_files). The app permeability node graph and asset
  ripper are deferred as visual nice-to-haves; the analytical substance
  (components, permissions, footprint) is already surfaced.
- **L (root, experimental):** ptrace tracer, fanotify staging watcher,
  iotop-style I/O monitor. Visual toys (sonification, haptics, particles, 3D,
  live wallpaper) parked as optional flourish.

## Architecture
Already modular (storage, core/fs, core/shell, core/apps, core/data,
core/intents, core/capture, core/net, app), MVVM + unidirectional state, one
operations layer feeding UI and MCP. Additions: :core:native (H) and a heavier
WorkManager telemetry pipeline (I). gRPC unnecessary; Ktor REST + MCP already
serve CLI and agents over adb forward.
