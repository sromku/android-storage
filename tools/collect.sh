#!/usr/bin/env bash
#
# One command to run the whole external-collector setup:
#   1. sets up the USB adb-reverse tunnel (asks which device if several),
#   2. starts the collector + web tool.
#
# Usage:
#   ./tools/collect.sh            # port 8899
#   ./tools/collect.sh 9000       # custom port
#
# If you'll connect over Wi-Fi instead of USB, you can skip the tunnel step
# (Ctrl-C at the device prompt) — the Wi-Fi URL still works.
#
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
PORT="${1:-8899}"

echo "== USB tunnel (skip with Ctrl-C to use Wi-Fi only) =="
"$DIR/adb-reverse.sh" "$PORT" || echo "(no USB tunnel set up — the Wi-Fi URL still works)"

echo
echo "== Starting collector — open the web tool at http://localhost:$PORT =="
exec python3 "$DIR/companion.py" --port "$PORT"
