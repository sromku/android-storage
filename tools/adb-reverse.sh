#!/usr/bin/env bash
#
# Set up the adb reverse tunnel so the phone can reach the collector at
# http://127.0.0.1:<port> over USB — handy when Wi-Fi blocks device-to-device
# connections. Handles the "more than one device/emulator" case by asking which
# device to use.
#
# Usage:
#   ./tools/adb-reverse.sh                # port 8899, choose device if several
#   ./tools/adb-reverse.sh 9000           # custom port
#   ADB_SERIAL=57221FDCH009VC ./tools/adb-reverse.sh   # skip the prompt
#
set -euo pipefail

PORT="${1:-8899}"

# Find adb: PATH first, then the usual SDK locations.
ADB="${ADB:-adb}"
if ! command -v "$ADB" >/dev/null 2>&1; then
  for c in \
    "$HOME/Library/Android/sdk/platform-tools/adb" \
    "${ANDROID_HOME:-}/platform-tools/adb" \
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb"; do
    if [ -x "$c" ]; then ADB="$c"; break; fi
  done
fi
if ! command -v "$ADB" >/dev/null 2>&1 && [ ! -x "$ADB" ]; then
  echo "adb not found. Install Android platform-tools or set ADB=/path/to/adb." >&2
  exit 1
fi

# Collect online devices (state == "device").
DEVICES=()
while IFS= read -r line; do
  [ -n "$line" ] && DEVICES+=("$line")
done < <("$ADB" devices | awk 'NR>1 && $2=="device"{print $1}')

count=${#DEVICES[@]}
if [ "$count" -eq 0 ]; then
  echo "No connected device. Plug one in (and enable USB debugging) and retry." >&2
  exit 1
fi

# Pick a device.
SERIAL=""
if [ -n "${ADB_SERIAL:-}" ]; then
  SERIAL="$ADB_SERIAL"
elif [ "$count" -eq 1 ]; then
  SERIAL="${DEVICES[0]}"
else
  echo "Multiple devices connected — choose one:"
  i=1
  for s in "${DEVICES[@]}"; do
    model="$("$ADB" -s "$s" shell getprop ro.product.model 2>/dev/null </dev/null | tr -d '\r')"
    printf "  %s) %s  (%s)\n" "$i" "$s" "$model"
    i=$((i + 1))
  done
  while [ -z "$SERIAL" ]; do
    printf "Device # [1-%s]: " "$count"
    read -r choice || { echo "No choice given." >&2; exit 1; }
    if [ "$choice" -ge 1 ] 2>/dev/null && [ "$choice" -le "$count" ] 2>/dev/null; then
      SERIAL="${DEVICES[$((choice - 1))]}"
    else
      echo "Enter a number between 1 and $count."
    fi
  done
fi

model="$("$ADB" -s "$SERIAL" shell getprop ro.product.model 2>/dev/null </dev/null | tr -d '\r')"
"$ADB" -s "$SERIAL" reverse tcp:"$PORT" tcp:"$PORT"

echo
echo "Tunnel ready on $SERIAL ($model)."
echo "In the app: Settings → External collector → set the URL to"
echo "    http://127.0.0.1:$PORT"
echo
echo "Remove it later with:  $ADB -s $SERIAL reverse --remove tcp:$PORT"
