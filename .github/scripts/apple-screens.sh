#!/bin/bash
# Runs the Apple TV and iPhone builds in their simulators and takes screenshots of the main
# screens, the way tv-smoke.sh does for the Android app. Output: $1 (a folder).
set -uo pipefail
OUT=${1:-apple-screens}
mkdir -p "$OUT"
cd "$(dirname "$0")/../../apple"
BUNDLE=com.neontokyo.tv

device() { # newest available simulator whose name starts with $1
  xcrun simctl list devices available -j | python3 -c "
import json, sys
want = sys.argv[1]
found = []
for runtime, devs in json.load(sys.stdin)['devices'].items():
    for d in devs:
        if d['name'].startswith(want):
            found.append((runtime, d['name'], d['udid']))
found.sort()
print(found[-1][2] if found else '')
" "$1"
}

shoot() { # platform udid app
  local p=$1 udid=$2 app=$3
  xcrun simctl boot "$udid" 2>/dev/null
  xcrun simctl bootstatus "$udid" -b > /dev/null
  xcrun simctl install "$udid" "$app"
  run() { # name, wait, args...
    local name=$1 wait=$2; shift 2
    xcrun simctl terminate "$udid" "$BUNDLE" 2>/dev/null
    xcrun simctl launch "$udid" "$BUNDLE" -NeonDemo YES "$@" > /dev/null
    sleep "$wait"
    xcrun simctl io "$udid" screenshot "$OUT/$p-$name.png" > /dev/null 2>&1 && echo "  $p-$name.png"
  }
  run intro 1
  run home 25
  run details 20 -NeonScreen details:movie:tt15239678
  run series 20 -NeonScreen details:series:tt0903747
  run settings 8 -NeonScreen settings
  xcrun simctl spawn "$udid" log show --last 3m --predicate "process == 'NeonTokyo'" --style compact > "$OUT/$p-log.txt" 2>/dev/null
  xcrun simctl shutdown "$udid"
}

TV=$(device "Apple TV 4K")
PHONE=$(device "iPhone 16 Pro")
[ -z "$PHONE" ] && PHONE=$(device "iPhone")
echo "Apple TV simulator: $TV"
echo "iPhone simulator: $PHONE"
TV_APP=$(find build/sim-tvOS/Build/Products -maxdepth 2 -name NeonTokyo.app | head -1)
PHONE_APP=$(find build/sim-iOS/Build/Products -maxdepth 2 -name NeonTokyo.app | head -1)
[ -n "$TV" ] && [ -n "$TV_APP" ] && shoot tvOS "$TV" "$TV_APP"
[ -n "$PHONE" ] && [ -n "$PHONE_APP" ] && shoot iOS "$PHONE" "$PHONE_APP"
ls "$OUT"
