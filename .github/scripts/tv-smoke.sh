#!/usr/bin/env bash
# Android TV smoke test: installs the app, walks the main screens with the D-pad
# like the remote would, saves a screenshot + UI tree per step, and fails if the
# app crashes, stops responding or never reaches the home screen.
set -u
APK="$1"
OUT="$2"
PKG=com.neontokyo.tv
ACTIVITY=com.lumera.app.MainActivity
mkdir -p "$OUT"
: > "$OUT/FAILURES.txt"
step=0

fail() { echo "FAIL: $*" | tee -a "$OUT/FAILURES.txt"; }

alive() {
  if ! adb shell pidof "$PKG" >/dev/null 2>&1; then
    fail "app is not running after step $1"
  fi
}

# shot <name>: screenshot + UI tree (texts help reading the screen without the image)
shot() {
  step=$((step + 1))
  local name
  name=$(printf "%02d-%s" "$step" "$1")
  adb exec-out screencap -p > "$OUT/$name.png"
  if timeout 20 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; then
    adb pull /sdcard/ui.xml "$OUT/$name.xml" >/dev/null 2>&1
  fi
  alive "$name"
  echo "step $name"
}

key() {
  for k in "$@"; do
    adb shell input keyevent "$k"
    sleep 0.8
  done
}

# screen_has <text>: true when the current UI tree contains the text
screen_has() {
  timeout 20 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
  adb shell cat /sdcard/ui.xml 2>/dev/null | grep -qi -- "$1"
}

# wait_for <text> <seconds>
wait_for() {
  local t=0
  while [ "$t" -lt "$2" ]; do
    if screen_has "$1"; then return 0; fi
    sleep 3
    t=$((t + 3))
  done
  return 1
}

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP
adb shell settings put system screen_off_timeout 1800000
adb shell getprop ro.build.version.release > "$OUT/android-version.txt"

if ! adb install -r -g "$APK" > "$OUT/install.txt" 2>&1; then
  fail "install failed: $(cat "$OUT/install.txt")"
  exit 1
fi

adb logcat -c
adb logcat -v time > "$OUT/logcat-full.txt" 2>&1 &
LOGCAT_PID=$!

# nt_ci turns off the update popup for this session
adb shell am start -n "$PKG/$ACTIVITY" --ez nt_ci true
sleep 12
shot launch

# --- First run: create a profile -------------------------------------------
if screen_has "Create First Profile"; then
  key DPAD_CENTER
  sleep 2
  shot name-step
  adb shell input text "Adrian"
  sleep 1
  key ENTER
  sleep 2
  if screen_has "Enter Name"; then key ENTER; sleep 2; fi
  shot avatar-step
  key DPAD_CENTER
  sleep 2
  shot theme-step
  key DPAD_CENTER
  sleep 4
  shot profiles
fi

key DPAD_CENTER
# Fresh profile installs the default addons, give it time
if ! wait_for "Continue\|Popular\|Trending\|Top" 60; then
  fail "home screen never showed catalog rows"
fi
sleep 8
shot home

# --- Browse rows ---------------------------------------------------------
key DPAD_DOWN
sleep 2
shot home-row2
key DPAD_DOWN
sleep 2
shot home-row3
key DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT
sleep 2
shot home-row3-right
key DPAD_DOWN DPAD_DOWN
sleep 2
shot home-row5
key DPAD_DOWN DPAD_DOWN DPAD_DOWN
sleep 3
shot home-deep

# --- Details page ---------------------------------------------------------
key DPAD_CENTER
sleep 10
shot details
key DPAD_DOWN
sleep 2
shot details-down
key DPAD_DOWN DPAD_DOWN
sleep 2
shot details-more
key BACK
sleep 3
shot back-to-home

# --- Side menu and tabs ---------------------------------------------------
key DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT
sleep 2
shot menu-open
key DPAD_DOWN DPAD_CENTER
sleep 10
shot movies-tab
key DPAD_DOWN DPAD_DOWN
sleep 2
shot movies-rows
key DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT
key DPAD_DOWN DPAD_CENTER
sleep 10
shot series-tab
key DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT
key DPAD_UP DPAD_UP DPAD_UP DPAD_CENTER
sleep 6
shot search-tab
adb shell input text "dune"
sleep 1
key ENTER
sleep 10
shot search-results
key BACK
sleep 2
key DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT
key DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_CENTER
sleep 5
shot settings
key DPAD_RIGHT
sleep 2
shot settings-content
key DPAD_DOWN DPAD_DOWN
sleep 2
shot settings-down

# --- Fast scrolling stress: hold-down equivalent ------------------------------
key DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT
key DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_CENTER
sleep 8
for i in $(seq 1 25); do adb shell input keyevent DPAD_DOWN; done
for i in $(seq 1 40); do adb shell input keyevent DPAD_RIGHT; done
sleep 4
shot stress-scroll

# --- Results ----------------------------------------------------------------
adb shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo.txt" 2>&1
adb shell dumpsys meminfo "$PKG" > "$OUT/meminfo.txt" 2>&1
kill "$LOGCAT_PID" 2>/dev/null
sleep 1

APP_PID_LINES=$(grep -n "FATAL EXCEPTION\|ANR in $PKG\|Application Not Responding" "$OUT/logcat-full.txt" || true)
if [ -n "$APP_PID_LINES" ]; then
  fail "crash or ANR in logcat: $APP_PID_LINES"
  grep -A40 "FATAL EXCEPTION" "$OUT/logcat-full.txt" > "$OUT/crash.txt" || true
fi
grep -i "neontokyo\|lumera\|AndroidRuntime\|System.err" "$OUT/logcat-full.txt" > "$OUT/logcat-app.txt" || true

if [ -s "$OUT/FAILURES.txt" ]; then
  cat "$OUT/FAILURES.txt"
  exit 1
fi
echo "TV smoke test passed ($step steps)"
