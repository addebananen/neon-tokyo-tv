#!/bin/bash
# Runs the Windows app's build on a Linux desktop (Xvfb, 1920 x 1080) and takes screenshots of
# the main screens, the way apple-screens.sh does for the Apple TV and iPhone. The core and the
# app must be built already (desktop/build-core.sh, gradle classes). Output: $1 (a folder).
set -uo pipefail
OUT=$(realpath -m "${1:-desktop-screens}")
mkdir -p "$OUT"
cd "$(dirname "$0")/../.."

shot() { # name, wait ms, keys, screen
  local name=$1 wait=$2 keys=$3 screen=$4
  NEON_DEMO=1 NEON_SHOT="$OUT/win-$name.png" NEON_SHOT_AFTER="$wait" NEON_KEYS="$keys" NEON_SCREEN="$screen" \
    timeout 240 xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew -p desktop run -q > "$OUT/win-$name.log" 2>&1
  [ -f "$OUT/win-$name.png" ] && echo "  win-$name.png" || { echo "  win-$name failed:"; tail -20 "$OUT/win-$name.log"; }
}

shot intro 1800 "" intro
shot home 25000 "" ""
shot home-focus 25000 "Down,Down,Right" ""
shot details 20000 "" details:movie:tt15239678
shot series 20000 "" details:series:tt0903747
shot settings 8000 "" settings
shot profiles 6000 "" profiles
ls "$OUT"
