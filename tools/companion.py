#!/usr/bin/env python3
"""
Storage Studio — desktop companion.

Runs a tiny HTTP server on your computer that powers the companion web tool:
  * Collector: the phone streams monitor events here while recording; every
    record is appended to a local SQLite database with no row limit, so you can
    leave a device recording for days and keep everything.
  * Files: browses and downloads the connected device's filesystem over adb.

Usage:
    python3 tools/companion.py                 # 0.0.0.0:8899, db tools/collector-data/monitor.db
    python3 tools/companion.py --port 9000 --db ~/captures/run1.db

The default database lives in tools/collector-data/ (gitignored), so captures
never show up in git status.

Then open the web tool at http://localhost:8899 for guided setup, copy-paste
URLs and live results, and in the app: Settings -> External collector -> enable,
and set the URL it shows.

The phone POSTs newline-delimited JSON (one record per line). Each record has a
"tool" field (e.g. "appops"); records are written to a table named per tool,
created on demand. A record with tool="ping" is a health check and is not stored.

The same server also serves a small web UI (GET /, from tools/web/index.html)
and a read API (GET /api/health, GET /api/rows?tool=<t>&limit=<n>) with CORS,
so the web tool can show connection status and live rows.
"""
import argparse
import json
import mimetypes
import os
import posixpath
import re
import shutil
import signal
import socket
import sqlite3
import subprocess
import sys
import tempfile
import threading
import time
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

_lock = threading.Lock()
_table_cols = {}  # tool -> set of known columns (so new fields are added on the fly)
_seen = {}        # tool -> last-activity epoch ms (data row or heartbeat); powers the live indicator
_LIVE_MS = 15000  # a tool counts as "live" if seen within this window (heartbeat is every 7s)

WEB_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web")
_SELF = os.path.basename(__file__)  # so the port-freeing self-check survives a rename


class ReuseServer(ThreadingHTTPServer):
    # Reuse the address so a quick restart isn't blocked by TIME_WAIT.
    allow_reuse_address = True


# ---------------------------------------------------------------------------
# Device file access over adb (the Files explorer in the web tool).
#
# The laptop already has adb and (for this to work) the phone connected over
# USB or wireless debugging. We browse and pull files at the adb shell tier
# (uid 2000 — the same reach as Shizuku), or more when `adb root` is available.
# ---------------------------------------------------------------------------
_ADB = None


def find_adb():
    """Locate the adb binary on PATH or in the common SDK install locations."""
    global _ADB
    if _ADB is not None:
        return _ADB
    c = shutil.which("adb")
    if not c:
        home = os.path.expanduser("~")
        for p in (f"{home}/Library/Android/sdk/platform-tools/adb",
                  f"{home}/Android/Sdk/platform-tools/adb",
                  "/usr/local/bin/adb", "/opt/homebrew/bin/adb"):
            if os.path.isfile(p) and os.access(p, os.X_OK):
                c = p
                break
    _ADB = c or ""
    return _ADB


def adb_devices():
    """List (serial, state) for attached devices; only those in 'device' state."""
    adb = find_adb()
    if not adb:
        return []
    try:
        out = subprocess.run([adb, "devices"], capture_output=True, timeout=8, text=True).stdout
    except Exception:
        return []
    devs = []
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devs.append((parts[0], parts[1]))
    return devs


def resolve_serial(explicit=None):
    if explicit:
        return explicit
    env = os.environ.get("ADB_SERIAL")
    if env:
        return env
    devs = adb_devices()
    return devs[0][0] if devs else None


def adb_base(serial=None):
    adb = find_adb()
    if not adb:
        return None
    s = resolve_serial(serial)
    return [adb] + (["-s", s] if s else [])


def dq(path):
    """Single-quote a path for the device shell (prevents command injection)."""
    return "'" + str(path).replace("'", "'\\''") + "'"


def adb_out(cmd, serial=None, timeout=25):
    """Run a device shell command via exec-out; return CompletedProcess (bytes)."""
    base = adb_base(serial)
    if not base:
        raise RuntimeError("adb not found")
    return subprocess.run(base + ["exec-out", cmd], capture_output=True, timeout=timeout)


# toybox `ls -lA`: perms links owner group size YYYY-MM-DD HH:MM[:SS] name[ -> target]
_LS_RE = re.compile(
    r'^([bcdlpsx\-][-rwxsStT]{9})[.+]?\s+\d+\s+(\S+)\s+(\S+)\s+(\d+)\s+'
    r'(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}(?::\d{2})?)\s+(.*)$')


def fs_list(path, serial=None):
    # -L dereferences symlinks so that e.g. /sdcard (a link) lists the target dir.
    r = adb_out("ls -lAL " + dq(path), serial=serial, timeout=25)
    out = r.stdout.decode("utf-8", "replace")
    err = r.stderr.decode("utf-8", "replace").strip()
    entries = []
    for line in out.splitlines():
        m = _LS_RE.match(line.strip())
        if not m:
            continue
        perms, owner, group, size, date, tm, name = m.groups()
        typ = "dir" if perms[0] == "d" else ("link" if perms[0] == "l" else "file")
        target = None
        if typ == "link" and " -> " in name:
            name, target = name.split(" -> ", 1)
        entries.append({
            "name": name, "type": typ, "size": int(size),
            "mtime": date + " " + tm, "target": target,
            "mode": perms, "owner": owner, "group": group,
            "path": posixpath.join(path, name),
        })
    denied = not entries and bool(err)
    return {"path": path, "entries": entries,
            "denied": denied, "error": err if denied else None}


def _pids_on_port(port):
    """PIDs listening on TCP port (macOS/Linux via lsof). Empty if lsof is missing."""
    try:
        out = subprocess.check_output(
            ["lsof", "-tiTCP:%d" % port, "-sTCP:LISTEN"],
            text=True, stderr=subprocess.DEVNULL,
        )
        return [int(p) for p in out.split()]
    except Exception:
        return []


def _is_collector(pid):
    """True only if the process looks like another copy of this script."""
    try:
        cmd = subprocess.check_output(["ps", "-p", str(pid), "-o", "command="], text=True)
        return _SELF in cmd
    except Exception:
        return False


def free_port_if_ours(port):
    """Silently stop a stale companion holding the port, so a fresh run just works.
    Only ever kills another copy of this script — never an unrelated process."""
    freed = False
    for pid in _pids_on_port(port):
        if pid == os.getpid() or not _is_collector(pid):
            continue
        print(f"Port {port} was held by a previous companion (pid {pid}) — restarting it.")
        for sig in (signal.SIGTERM, signal.SIGKILL):
            try:
                os.kill(pid, sig)
            except ProcessLookupError:
                break
            for _ in range(20):
                time.sleep(0.05)
                if pid not in _pids_on_port(port):
                    break
            if pid not in _pids_on_port(port):
                break
        freed = True
    if freed:
        time.sleep(0.2)
    return freed


def lan_ip():
    """Best-effort LAN address of this machine (the one the phone should reach over Wi-Fi)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


def list_tables(db):
    rows = db.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
    ).fetchall()
    out = {}
    for (name,) in rows:
        try:
            out[name] = db.execute(f'SELECT COUNT(*) FROM "{name}"').fetchone()[0]
        except sqlite3.Error:
            out[name] = 0
    return out


def table_columns(db, table):
    """Column names of a table, or [] if it doesn't exist (also validates the name)."""
    if table not in list_tables(db):
        return []
    return [r[1] for r in db.execute(f'PRAGMA table_info("{table}")').fetchall()]


def _ensure_table(db, tool, sample):
    """Create the tool's table on first sight, and add any newly-seen columns to it."""
    cols = [k for k in sample.keys() if k != "tool"]
    known = _table_cols.get(tool)
    if known is None:
        existing = set(table_columns(db, tool))
        if not existing:
            coldefs = ", ".join(f'"{c}"' for c in cols)
            db.execute(
                f'CREATE TABLE "{tool}" '
                f'(_id INTEGER PRIMARY KEY AUTOINCREMENT, received_at INTEGER, {coldefs})'
            )
            # A unique index on "key" (if present) makes re-sent rows idempotent.
            if "key" in cols:
                db.execute(f'CREATE UNIQUE INDEX IF NOT EXISTS "{tool}_key" ON "{tool}" ("key")')
            existing = set(cols) | {"_id", "received_at"}
        known = existing
        _table_cols[tool] = known
    for c in cols:
        if c not in known:
            try:
                db.execute(f'ALTER TABLE "{tool}" ADD COLUMN "{c}"')
                known.add(c)
            except sqlite3.OperationalError:
                pass
    db.commit()


def _insert(db, tool, rec, received_at):
    cols = [k for k in rec.keys() if k != "tool"]
    placeholders = ", ".join("?" for _ in cols) + ", ?"
    colnames = ", ".join(f'"{c}"' for c in cols) + ", received_at"
    values = [rec[c] for c in cols] + [received_at]
    db.execute(
        f'INSERT OR IGNORE INTO "{tool}" ({colnames}) VALUES ({placeholders})',
        values,
    )


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # quiet; we print our own summaries

    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def _json(self, obj, code=200):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self._cors()
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        if path in ("/", "/index.html"):
            self._serve_web()
            return
        if path == "/api/health":
            now = int(time.time() * 1000)
            with _lock:
                tools = list_tables(self.server.db)
                # Union of tables and anything we've heard a heartbeat from.
                names = set(tools.keys()) | set(_seen.keys())
                live = {}
                age = {}
                for t in names:
                    last = _seen.get(t, 0)
                    live[t] = (now - last) < _LIVE_MS if last else False
                    age[t] = (now - last) // 1000 if last else None
            self._json({
                "ok": True,
                "db": self.server.db_path,
                "lan_ip": self.server.lan_ip,
                "port": self.server.port,
                "tools": tools,
                "live": live,
                "age": age,
            })
            return
        if path == "/api/rows":
            q = parse_qs(parsed.query)
            tool = (q.get("tool", [""])[0])
            limit = min(int(q.get("limit", ["100"])[0] or 100), 1000)
            with _lock:
                db = self.server.db
                if tool not in list_tables(db):
                    self._json({"error": "unknown tool"}, 404)
                    return
                cur = db.execute(f'SELECT * FROM "{tool}" ORDER BY _id DESC LIMIT ?', (limit,))
                cols = [c[0] for c in cur.description]
                rows = [dict(zip(cols, r)) for r in cur.fetchall()]
            self._json({"tool": tool, "columns": cols, "rows": rows})
            return
        if path == "/api/group":
            q = parse_qs(parsed.query)
            tool = q.get("tool", [""])[0]
            by = q.get("by", [""])[0]
            limit = min(int(q.get("limit", ["12"])[0] or 12), 100)
            with _lock:
                db = self.server.db
                cols = table_columns(db, tool)
                if not cols:
                    self._json({"error": "unknown tool"}, 404); return
                if by not in cols:
                    self._json({"error": "unknown column"}, 400); return
                cur = db.execute(
                    f'SELECT "{by}" AS value, COUNT(*) AS count FROM "{tool}" '
                    f'GROUP BY "{by}" ORDER BY count DESC LIMIT ?', (limit,))
                groups = [{"value": r[0], "count": r[1]} for r in cur.fetchall()]
            self._json({"tool": tool, "by": by, "groups": groups})
            return
        if path == "/api/hist":
            q = parse_qs(parsed.query)
            tool = q.get("tool", [""])[0]
            hours = min(int(q.get("hours", ["24"])[0] or 24), 168)
            with _lock:
                db = self.server.db
                if "at_ms" not in table_columns(db, tool):
                    self._json({"tool": tool, "hours": hours, "buckets": []}); return
                now = int(time.time() * 1000)
                start = now - hours * 3600000
                cur = db.execute(
                    f'SELECT (at_ms/3600000) AS hb, COUNT(*) FROM "{tool}" '
                    f'WHERE at_ms >= ? GROUP BY hb', (start,))
                counts = {int(r[0]): r[1] for r in cur.fetchall()}
            now_h = now // 3600000
            buckets = [counts.get(now_h - (hours - 1 - i), 0) for i in range(hours)]
            self._json({"tool": tool, "hours": hours, "buckets": buckets})
            return
        if path == "/api/fs/status":
            adb = find_adb()
            devs = adb_devices()
            info = {"adb": bool(adb), "connected": bool(adb) and bool(devs),
                    "devices": [d[0] for d in devs]}
            if info["connected"]:
                try:
                    info["model"] = adb_out("getprop ro.product.model", timeout=8).stdout.decode().strip()
                except Exception:
                    info["model"] = ""
                try:
                    info["root"] = adb_out("id -u", timeout=8).stdout.decode().strip() == "0"
                except Exception:
                    info["root"] = False
                info["serial"] = resolve_serial()
            self._json(info)
            return
        if path == "/api/fs/list":
            q = parse_qs(parsed.query)
            target = q.get("path", ["/sdcard"])[0] or "/sdcard"
            serial = q.get("serial", [None])[0]
            if not find_adb():
                self._json({"error": "adb not found — install platform-tools", "entries": []}, 503)
                return
            if not adb_devices():
                self._json({"error": "no device connected over adb", "entries": []}, 503)
                return
            try:
                self._json(fs_list(target, serial=serial))
            except Exception as e:
                self._json({"error": str(e), "entries": []}, 500)
            return
        if path == "/api/fs/download":
            q = parse_qs(parsed.query)
            self.handle_download(q.get("path", [""])[0], q.get("serial", [None])[0],
                                 q.get("inline", ["0"])[0] == "1")
            return
        if path == "/api/fs/zip":
            q = parse_qs(parsed.query)
            try:
                paths = json.loads(q.get("paths", ["[]"])[0])
            except Exception:
                paths = []
            self.handle_zip(paths, q.get("serial", [None])[0])
            return
        self.send_response(404)
        self._cors()
        self.end_headers()

    def _serve_web(self):
        index = os.path.join(WEB_DIR, "index.html")
        if not os.path.isfile(index):
            self.send_response(404)
            self._cors()
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"web/index.html not found next to companion.py\n")
            return
        with open(index, "rb") as f:
            body = f.read()
        self.send_response(200)
        self._cors()
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def handle_download(self, path, serial=None, inline=False):
        """Stream a single device file to the browser (attachment, or inline for previews)."""
        base = adb_base(serial)
        if not base:
            self._json({"error": "adb / device not available"}, 503)
            return
        if not path:
            self._json({"error": "path required"}, 400)
            return
        # Refuse early if the shell user can't read it, so the browser gets an error
        # instead of a silent empty file.
        try:
            ok = adb_out("test -r " + dq(path) + " && echo ok", serial=serial, timeout=10).stdout.decode().strip()
        except Exception:
            ok = ""
        if ok != "ok":
            self._json({"error": "cannot read (permission denied or not a file): " + path}, 403)
            return
        size = None
        try:
            rs = adb_out("stat -c %s " + dq(path), serial=serial, timeout=10)
            if rs.returncode == 0:
                size = int(rs.stdout.decode().strip() or 0)
        except Exception:
            pass
        name = posixpath.basename(path) or "download"
        ctype = mimetypes.guess_type(name)[0] or "application/octet-stream"
        proc = subprocess.Popen(base + ["exec-out", "cat " + dq(path)], stdout=subprocess.PIPE)
        self.send_response(200)
        self._cors()
        self.send_header("Content-Type", ctype)
        disp = "inline" if inline else "attachment"
        self.send_header("Content-Disposition", '%s; filename="%s"' % (disp, name.replace('"', '')))
        if size is not None:
            self.send_header("Content-Length", str(size))
        self.end_headers()
        try:
            while True:
                chunk = proc.stdout.read(65536)
                if not chunk:
                    break
                self.wfile.write(chunk)
        except (BrokenPipeError, ConnectionResetError):
            pass
        finally:
            try:
                proc.stdout.close()
            except Exception:
                pass
            proc.wait()

    def handle_zip(self, paths, serial=None):
        """Stream the selected files/folders back as one zip, reading each file with
        `exec-out cat` (the same reliable path as single-file download; adb pull is
        flaky over an unstable connection). Folder selections keep their structure."""
        base = adb_base(serial)
        if not base:
            self._json({"error": "adb / device not available"}, 503)
            return
        paths = [p for p in (paths or []) if isinstance(p, str) and p.strip()]
        if not paths:
            self._json({"error": "no paths given"}, 400)
            return

        # Expand each selection into (device_path, arcname) file entries.
        entries, errors = [], []
        for p in paths:
            try:
                kind = adb_out("if [ -d " + dq(p) + " ]; then echo d; elif [ -f " + dq(p) +
                               " ]; then echo f; else echo x; fi", serial=serial, timeout=15).stdout.decode().strip()
            except Exception as e:
                errors.append(str(e)); continue
            if kind == "d":
                parent = posixpath.dirname(p.rstrip("/")) or "/"
                try:
                    out = adb_out("find " + dq(p) + " -type f", serial=serial, timeout=120).stdout.decode("utf-8", "replace")
                except Exception as e:
                    errors.append(str(e)); continue
                for f in out.splitlines():
                    f = f.strip()
                    if f:
                        entries.append((f, posixpath.relpath(f, parent)))
            elif kind == "f":
                entries.append((p, posixpath.basename(p)))
            else:
                errors.append("not found: " + p)

        if not entries:
            msg = "nothing to download" + ((" — " + errors[0]) if errors else "")
            self._json({"error": msg}, 502)
            return

        tmp = tempfile.mkdtemp(prefix="ssfs-")
        zpath = tmp + ".zip"
        added = 0
        try:
            with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED, allowZip64=True) as zf:
                for i, (devpath, arc) in enumerate(entries):
                    lp = os.path.join(tmp, "f%d" % i)
                    with open(lp, "wb") as out:
                        proc = subprocess.Popen(base + ["exec-out", "cat " + dq(devpath)], stdout=out)
                        proc.wait()
                    zf.write(lp, arc)
                    added += 1
                    os.remove(lp)
            if added == 0:
                self._json({"error": "could not read the selected files"}, 502)
                return
            size = os.path.getsize(zpath)
            self.send_response(200)
            self._cors()
            self.send_header("Content-Type", "application/zip")
            self.send_header("Content-Disposition", 'attachment; filename="storage-studio-files.zip"')
            self.send_header("Content-Length", str(size))
            self.end_headers()
            with open(zpath, "rb") as f:
                while True:
                    chunk = f.read(65536)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception as e:
            try:
                self._json({"error": str(e)}, 500)
            except Exception:
                pass
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
            try:
                os.remove(zpath)
            except Exception:
                pass

    def do_POST(self):
        import time
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8", "replace")
        stored, pings = {}, 0
        now = int(time.time() * 1000)
        with _lock:
            db = self.server.db
            for line in body.splitlines():
                line = line.strip()
                if not line:
                    continue
                try:
                    rec = json.loads(line)
                except json.JSONDecodeError:
                    continue
                tool = rec.get("tool", "unknown")
                if tool == "ping":
                    pings += 1
                    continue
                if rec.get("type") == "heartbeat":
                    # Liveness only — mark the stream live, don't store.
                    _seen[tool] = now
                    continue
                _ensure_table(db, tool, rec)
                _insert(db, tool, rec, now)
                _seen[tool] = now  # activity implies live
                stored[tool] = stored.get(tool, 0) + 1
            db.commit()
            totals = {t: db.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0] for t in stored}
        if stored:
            summary = ", ".join(f"{t} +{n} (total {totals[t]})" for t, n in stored.items())
            print(summary, flush=True)
        elif pings:
            print("ping ok", flush=True)
        self.send_response(200)
        self._cors()
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(b"ok\n")


def main():
    try:
        sys.stdout.reconfigure(line_buffering=True)  # show messages live, not on exit
    except Exception:
        pass
    default_db = os.path.join(WEB_DIR, "..", "collector-data", "monitor.db")
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8899)
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--db", default=os.path.normpath(default_db),
                    help="SQLite path (default: tools/collector-data/monitor.db, which is gitignored)")
    args = ap.parse_args()

    parent = os.path.dirname(os.path.abspath(args.db))
    if parent:
        os.makedirs(parent, exist_ok=True)

    def bind():
        return ReuseServer((args.host, args.port), Handler)

    try:
        server = bind()
    except OSError as e:
        if e.errno in (48, 98, 10048):  # EADDRINUSE on macOS / Linux / Windows
            # Free the port automatically if a stale collector is holding it, then retry.
            if free_port_if_ours(args.port):
                try:
                    server = bind()
                except OSError:
                    server = None
            else:
                server = None
            if server is None:
                print(f"Port {args.port} is in use by something that isn't a collector.")
                print(f"  Stop it, or start this on another port:  --port {args.port + 1}")
                raise SystemExit(1)
        else:
            raise

    db = sqlite3.connect(args.db, check_same_thread=False)
    db.execute("PRAGMA journal_mode=WAL")
    server.db = db
    server.db_path = os.path.abspath(args.db)
    server.lan_ip = lan_ip()
    server.port = args.port
    ip = server.lan_ip
    print(f"Collector on http://{args.host}:{args.port}  ->  {args.db}")
    print(f"  Web tool:   http://localhost:{args.port}")
    print(f"  App URL:    http://{ip}:{args.port}   (same Wi-Fi)")
    print(f"              or adb reverse tcp:{args.port} tcp:{args.port} + http://127.0.0.1:{args.port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nstopping")
    finally:
        db.close()


if __name__ == "__main__":
    main()
