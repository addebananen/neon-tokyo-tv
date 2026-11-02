#!/usr/bin/env bash
# UI-only x86 APK: ARM torrent/subtitle/remote-native functionality is not covered.
set -euo pipefail
mkdir -p tv-review
pkg=com.neontokyo.tv.test
activity="$pkg/com.lumera.app.launcher.NeonHomeActivity"
adb install -r "$1"
adb logcat -c
trap 'adb logcat -d > tv-review/logcat.txt; adb shell dumpsys gfxinfo "$pkg" > tv-review/gfxinfo.txt' EXIT
capture() {
  adb exec-out screencap -p > "tv-review/$1.png"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml "tv-review/$1.xml" >/dev/null
  adb shell dumpsys activity activities > "tv-review/$1-activity.txt"
  grep -E 'mResumedActivity.*NeonHomeActivity' "tv-review/$1-activity.txt"
  test -n "$(adb shell pidof "$pkg" | tr -d '\r')"
}
for scenario in bluehour-frost bluehour-liquid deepblue; do
  style=${scenario%%-*}
  glass=${scenario#*-}
  adb shell am force-stop "$pkg"
  if [[ "$scenario" == deepblue ]]; then
    adb shell am start -W -n "$activity" --es style deepblue
  else
    adb shell am start -W -n "$activity" --es style "$style" --es glass "$glass" --es focus liquid
  fi
  sleep 4
  capture "$scenario-home"
  # Alternating directions interrupt focus animations before their normal settling time.
  for i in $(seq 1 20); do
    adb shell input keyevent 22
    adb shell input keyevent 21
  done
  adb shell input keyevent 20 22 22 21 19
  capture "$scenario-after-dpad"
  adb shell am start -W -a android.settings.SETTINGS
  sleep 1
  adb shell input keyevent 4
  sleep 2
  capture "$scenario-return"
done
adb logcat -b crash -d > tv-review/crashes.txt
if grep -q "$pkg" tv-review/crashes.txt; then
  cat tv-review/crashes.txt
  exit 1
fi
printf '%s\n' 'Navigation/liveness checks passed. Inspect PNG/XML evidence before visual sign-off.' > tv-review/result.txt
