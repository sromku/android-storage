# Root-only / experimental track (do later)

These need root (or are only partial without it) and are deferred until we root a
test device. They are kept here so the main roadmap (docs/FORENSIC.md) stays honest.

## Features
- **ptrace activity tracer** — attach to a chosen app, log its syscalls, `connect`
  targets and `open` paths into a live timeline. Cross-uid ptrace is blocked by
  SELinux without root; the "clone the app into our sandbox" framing is not how
  Android runs apps, so this is a *trace of a launched app*, not a clone.
- **fanotify / global inotify staging watcher** — watch app-private storage across
  apps for cross-app data staging. FUSE shared storage never emits other apps'
  events; needs root to watch `/data`.
- **Cross-uid /proc introspection** — other apps' `/proc/[pid]/smaps`,
  `/proc/[pid]/net/unix`, open fds, shared memory (ashmem/ion). Hidden by hidepid
  on modern Android; root required.
- **Unlinked-but-open file recovery** — raw block-device reads to find deleted
  files an app keeps open. Root + raw block access.
- **iotop-style real-time I/O monitor** — per-process disk read/write rates from
  `/proc/[pid]/io`, which is restricted for other uids without root.
- **Non-exported content provider queries** and **full /data tree browse** —
  already partially covered; root removes the remaining walls.

## Notes on "collusion detection"
Even with root, proving two apps collude is not reliable. What we *can* show as
signals: shared world-readable directories written by one app and read by another,
and the same ad/analytics SDK present in two APKs (static scan). Present as signals,
never as a verdict.

## When we root a device
Add a `:core:native` ptrace/fanotify backend behind the Root privilege tier, gate
every feature on `PrivilegeTier.ROOT`, and label the whole area experimental in the UI.
