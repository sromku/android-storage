#!/usr/bin/env python3
"""
External collector for the Storage app's monitoring tools.

Runs a tiny HTTP endpoint on your computer that the phone streams to while a
monitor is recording. Every record is appended to a local SQLite database with
no row limit, so you can leave a device recording for days and keep everything.

Usage:
    python3 tools/collector.py                 # listen on 0.0.0.0:8899, db ./monitor.db
    python3 tools/collector.py --port 9000 --db ~/captures/run1.db

Then in the app: Settings -> External collector -> enable, and set the URL to
    http://<this-computer-ip>:8899
(both devices must be on the same network). Use "Test connection" to confirm.

The phone POSTs newline-delimited JSON (one record per line). Each record has a
"tool" field (e.g. "appops"); records are written to a table named per tool,
created on demand. A record with tool="ping" is a health check and is not stored.
"""
import argparse
import json
import sqlite3
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

_lock = threading.Lock()
_seen_tables = set()


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
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(b"ok\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8899)
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--db", default="monitor.db")
    args = ap.parse_args()

    db = sqlite3.connect(args.db, check_same_thread=False)
    db.execute("PRAGMA journal_mode=WAL")
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    server.db = db
    print(f"Collector listening on http://{args.host}:{args.port}  ->  {args.db}")
    print("In the app: Settings -> External collector -> enable and set this URL.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nstopping")
    finally:
        db.close()


if __name__ == "__main__":
    main()
