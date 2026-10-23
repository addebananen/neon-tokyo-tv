#!/usr/bin/env bash
# Runs tv-setup/neon-tv.ps1 step by step against a fake TV (fake-adb.sh), checks every change it
# makes, then checks that -Step undo puts the TV back exactly as it was.
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
WORK=$(mktemp -d)
export FAKE_ADB_STATE="$WORK/state"
export USERPROFILE="$WORK/home"
export TEMP="$WORK/tmp"
mkdir -p "$USERPROFILE" "$TEMP" "$WORK/bin"
cp "$ROOT/.github/scripts/fake-adb.sh" "$WORK/bin/adb.exe"
chmod +x "$WORK/bin/adb.exe"
export PATH="$WORK/bin:$PATH"
failures=0

run() {
  echo "== neon-tv.ps1 $*"
  pwsh -NoProfile -File "$ROOT/tv-setup/neon-tv.ps1" "$@" 2>&1 | tee -a "$WORK/out.txt"
}
check() {
  if eval "$2"; then echo "ok    $1"; else echo "FAIL  $1"; failures=$((failures + 1)); fi
}
setting() { grep -m1 "^$1=" "$FAKE_ADB_STATE/settings" | cut -d= -f2-; }

# First connection finds the TV over mDNS, later runs use the saved address
FAKE_ADB_MDNS=192.168.1.77:41234 run -Step inventory
check "found the TV over mDNS and saved its address" '[ "$(cat "$USERPROFILE/NeonTV/tv-ip.txt")" = 192.168.1.77:41234 ]'
check "saved the original Home activity" 'grep -q launcherx "$USERPROFILE/NeonTV/before/home.txt"'

run -Ip 192.168.1.77:41234 -Step debloat
check "TCL Safety Guard is off" 'grep -qx com.tcl.guard "$FAKE_ADB_STATE/disabled"'
check "Play Services were left alone" '! grep -qx com.google.android.gms "$FAKE_ADB_STATE/disabled"'
check "TV inputs were left alone" '! grep -qx com.tcl.tvinput "$FAKE_ADB_STATE/disabled"'

run -Ip 192.168.1.77:41234 -Step launcher
check "Projectivy is the Home activity" 'grep -q projengmenu "$FAKE_ADB_STATE/home"'
check "the Google TV home screen is off" 'grep -qx com.google.android.apps.tv.launcherx "$FAKE_ADB_STATE/disabled"'

run -Ip 192.168.1.77:41234 -Step tune
check "window animations at 0.5x" '[ "$(setting global/window_animation_scale)" = 0.5 ]'
check "app animations untouched" '[ "$(setting global/animator_duration_scale)" = 1.0 ]'

run -Ip 192.168.1.77:41234 -Step art
check "pushed the wallpaper, 17 banners and 17 icons" '[ "$(grep -c " push .*/sdcard/Pictures/NeonTokyo/" "$FAKE_ADB_STATE/calls")" -eq 35 ]'

run -Ip 192.168.1.77:41234 -Step dream
check "Neon Tokyo night is the screensaver" '[ "$(setting secure/screensaver_components)" = com.neontokyo.tv/com.lumera.app.dream.NeonDreamService ]'

run -Ip 192.168.1.77:41234 -Step report
check "the report ran" 'grep -q "Report" "$WORK/out.txt"'

run -Ip 192.168.1.77:41234 -Step undo
check "every package is back on" '[ ! -s "$FAKE_ADB_STATE/disabled" ]'
check "window animations restored" '[ "$(setting global/window_animation_scale)" = 1.0 ]'
check "Ambient mode is the screensaver again" '[ "$(setting secure/screensaver_components)" = com.google.android.apps.tv.dreamx/.service.Backdrop ]'
check "the Google TV home screen opens on Home again" 'grep -q launcherx "$FAKE_ADB_STATE/home"'
check "no step stopped with an error" '! grep -q "Stopped:" "$WORK/out.txt"'

if [ "$failures" -gt 0 ]; then
  echo "$failures check(s) failed"
  exit 1
fi
echo "TV setup script test passed"
