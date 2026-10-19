#!/usr/bin/env bash
# Real WebView renderer-termination qualification for a rootable emulator.
# This kills the emulator's WebView sandboxed renderer processes. Run only on a
# dedicated qualification emulator; it is not a product operation or a sandbox.
set -euo pipefail

SERIAL="${1:-emulator-5580}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "$SERIAL" != emulator-* && "${ALLOW_NON_EMULATOR:-0}" != "1" ]]; then
  echo "refusing to kill renderer processes on non-emulator $SERIAL; set ALLOW_NON_EMULATOR=1 explicitly" >&2
  exit 2
fi
if ! adb devices | awk 'NR>1 {print $1, $2}' | grep -qx "$SERIAL device"; then
  echo "adb device $SERIAL is not in the 'device' state" >&2
  exit 2
fi
APP_ID="io.github.baitian6641.cloudydata"
TEST_ID="io.github.baitian6641.cloudydata.test"
ASSET_DIR="$ROOT/qualification/evidence/artifacts"
LOG_DIR="$ROOT/qualification/evidence/android-test-results/2026-10-18"
mkdir -p "$ASSET_DIR" "$LOG_DIR"

adb -s "$SERIAL" root >/dev/null
adb -s "$SERIAL" wait-for-device
if [[ "$(adb -s "$SERIAL" shell id -u 2>/dev/null | tr -d '\r')" != "0" ]]; then
  echo "renderer-death qualification requires adb root" >&2
  exit 1
fi

API_LEVEL="$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
MODEL="$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
APP_UID="$(adb -s "$SERIAL" shell cmd package list packages -U | tr -d '\r' | awk -v app="package:$APP_ID" '$1 == app {print $2}')"
{
  echo "serial=$SERIAL api=$API_LEVEL model=$MODEL app_uid=$APP_UID"
  adb devices
} > "$LOG_DIR/renderer-death-device.txt"

adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" shell am force-stop "$APP_ID" || true
adb -s "$SERIAL" shell am force-stop "$TEST_ID" || true
adb -s "$SERIAL" shell am start -n "$APP_ID/.debug.RendererProbeActivity" \
  -e probe_kind line -e view_width_dp 640 -e view_height_dp 400 >/dev/null
sleep 5

dump_ui() {
  adb -s "$SERIAL" shell uiautomator dump /sdcard/cloudy-renderer-ui.xml >/dev/null 2>&1
  adb -s "$SERIAL" pull /sdcard/cloudy-renderer-ui.xml /tmp/cloudy-renderer-ui.xml >/dev/null
}
dump_ui
if ! grep -q "Rendered probe-line r1 · 6 points" /tmp/cloudy-renderer-ui.xml; then
  echo "initial six-point line render was not observed" >&2
  cat /tmp/cloudy-renderer-ui.xml >&2
  exit 1
fi

PIDS="$(adb -s "$SERIAL" shell ps -A -o PID,NAME \
  | grep 'sandboxed_process0' | awk '{print $1}' | tr -d '\r' || true)"
if [[ -z "$PIDS" ]]; then
  echo "no WebView sandboxed renderer process was found" >&2
  exit 1
fi
echo "killing WebView renderer pid(s): $PIDS"
for pid in $PIDS; do
  adb -s "$SERIAL" shell kill -9 "$pid"
done

for _ in $(seq 1 20); do
  sleep 1
  dump_ui
  if grep -q "recovered after 1 death(s)" /tmp/cloudy-renderer-ui.xml; then
    break
  fi
done

adb -s "$SERIAL" logcat -d -t 1200 > "$LOG_DIR/renderer-death-logcat.txt"

for required in \
  "Rendered probe-line r1 · 6 points" \
  "line=2" \
  "recovered after 1 death(s)" \
  "host=3"; do
  if ! grep -q "$required" /tmp/cloudy-renderer-ui.xml; then
    echo "missing expected UI evidence: $required" >&2
    grep -o 'text="[^"]*"' /tmp/cloudy-renderer-ui.xml | head -20 >&2
    exit 1
  fi
done
if grep -q "Renderer error" /tmp/cloudy-renderer-ui.xml; then
  echo "renderer reported an error after recovery" >&2
  exit 1
fi
for required in \
  "Renderer process" \
  "crash detected" \
  "renderer death 1; recreating"; do
  if ! grep -q "$required" "$LOG_DIR/renderer-death-logcat.txt"; then
    echo "missing expected logcat evidence: $required" >&2
    exit 1
  fi
done
APPLY_COUNT="$(grep -c 'apply probe-line r1 640.0x400.0dp' "$LOG_DIR/renderer-death-logcat.txt" || true)"
if [[ "$APPLY_COUNT" -lt 2 ]]; then
  echo "expected at least two spec applications, observed $APPLY_COUNT" >&2
  exit 1
fi

cp /tmp/cloudy-renderer-ui.xml "$ASSET_DIR/2026-10-18-renderer-death-recovery-ui.xml"
adb -s "$SERIAL" exec-out screencap -p > "$ASSET_DIR/2026-10-18-renderer-death-recovery.png"
grep -o 'text="[^"]*"' /tmp/cloudy-renderer-ui.xml | head -10
echo "renderer death/recovery qualification passed; evidence written"
