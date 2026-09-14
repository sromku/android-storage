#!/usr/bin/env python3
"""
External collector for the Storage app's monitoring tools.

Runs a tiny HTTP endpoint on your computer that the phone streams to while a
monitor is recording. Every record is appended to a local SQLite database with
no row limit, so you can leave a device recording for days and keep everything.

Usage:
    python3 tools/collector.py                 # 0.0.0.0:8899, db tools/collector-data/monitor.db
    python3 tools/collector.py --port 9000 --db ~/captures/run1.db

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
import os
import socket
import sqlite3
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

_lock = threading.Lock()
_seen_tables = set()

WEB_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web")


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


def _ensure_table(db, tool, sample):
    """Create a table for a tool on first sight, with columns from the record keys."""
    if tool in _seen_tables:
        return
    cols = [k for k in sample.keys() if k != "tool"]
    coldefs = ", ".join(f'"{c}"' for c in cols)
    db.execute(
        f'CREATE TABLE IF NOT EXISTS "{tool}" '
        f'(_id INTEGER PRIMARY KEY AUTOINCREMENT, received_at INTEGER, {coldefs})'
    )
    # A unique index on "key" (if present) makes re-sent rows idempotent.
    if "key" in cols:
        db.execute(f'CREATE UNIQUE INDEX IF NOT EXISTS "{tool}_key" ON "{tool}" ("key")')
    db.commit()
    _seen_tables.add(tool)


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
            with _lock:
                tools = list_tables(self.server.db)
            self._json({
                "ok": True,
                "db": self.server.db_path,
                "lan_ip": self.server.lan_ip,
                "port": self.server.port,
                "tools": tools,
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
            self.wfile.write(b"web/index.html not found next to collector.py\n")
            return
        with open(index, "rb") as f:
            body = f.read()
        self.send_response(200)
        self._cors()
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

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
                _ensure_table(db, tool, rec)
                _insert(db, tool, rec, now)
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
    db = sqlite3.connect(args.db, check_same_thread=False)
    db.execute("PRAGMA journal_mode=WAL")
    server = ThreadingHTTPServer((args.host, args.port), Handler)
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
