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
  elif ! adb shell dumpsys activity activities 2>/dev/null | grep -E "mResumedActivity|topResumedActivity" | grep -q "$PKG"; then
    fail "app left the foreground after step $1"
  fi
}

# shot <name> [noalive]: screenshot + UI tree (texts help reading the screen without the image).
# noalive skips the foreground check, for the screensaver, which is a system window.
shot() {
  step=$((step + 1))
  local name
  name=$(printf "%02d-%s" "$step" "$1")
  adb exec-out screencap -p > "$OUT/$name.png"
  if timeout 20 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; then
    adb pull /sdcard/ui.xml "$OUT/$name.xml" >/dev/null 2>&1
  fi
  [ "${2:-}" = noalive ] || alive "$name"
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

# focus_and_target <label>: prints "<focused center y> <menu entry center y>" from the UI tree
focus_and_target() {
  timeout 20 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
  adb shell cat /sdcard/ui.xml 2>/dev/null | python3 -c '
import re, sys
xml = sys.stdin.read()
label = sys.argv[1]
def center(node):
    m = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node)
    return (int(m.group(2)) + int(m.group(4))) // 2 if m else -1
nodes = re.findall(r"<node [^>]*>", xml)
focused = [n for n in nodes if "focused=\"true\"" in n]
def left(node):
    m = re.search(r"bounds=\"\[(\d+),", node)
    return int(m.group(1)) if m else 9999
# Menu entries sit at the left edge, which keeps posters with the same title out
target = [n for n in nodes if ("content-desc=\"%s\"" % label) in n and left(n) < 500]
print(center(focused[-1]) if focused else -1, center(target[0]) if target else -1)
' "$1"
}

# focused_label: the text inside the element that has the D-pad focus
focused_label() {
  timeout 20 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
  adb shell cat /sdcard/ui.xml 2>/dev/null | python3 -c '
import re, sys
xml = sys.stdin.read()
nodes = re.findall(r"<node [^>]*>", xml)
def box(node):
    m = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node)
    return tuple(map(int, m.groups())) if m else None
focused = [box(n) for n in nodes if "focused=\"true\"" in n]
if focused and focused[-1]:
    x1, y1, x2, y2 = focused[-1]
    for n in nodes:
        t = re.search(r"text=\"([^\"]*)\"", n)
        b = box(n)
        if t and t.group(1) and b and b[0] >= x1 and b[1] >= y1 and b[2] <= x2 and b[3] <= y2:
            print(t.group(1))
            break
'
}

# focus_down_to <label> <max presses>: presses Down until the focused element shows that label
focus_down_to() {
  local i=0
  while [ "$i" -le "$2" ]; do
    if [ "$(focused_label)" = "$1" ]; then return 0; fi
    adb shell input keyevent DPAD_DOWN
    sleep 0.8
    i=$((i + 1))
  done
  return 1
}

# menu_go <label>: opens the side menu and moves the D-pad focus onto that entry, then selects it
menu_go() {
  key DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT
  sleep 1
  local tries=0 backs=0 fy ty
  while [ "$tries" -lt 10 ]; do
    read -r fy ty < <(focus_and_target "$1")
    if [ "${ty:--1}" -lt 0 ] || [ "${fy:--1}" -lt 0 ]; then
      # A sub screen without the menu is still open: step back once and look again
      if [ "$backs" -lt 2 ]; then
        backs=$((backs + 1))
        key BACK
        sleep 2
        key DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT
        continue
      fi
      fail "menu entry $1 not found"
      return 1
    fi
    local diff=$((ty - fy))
    if [ "${diff#-}" -lt 30 ]; then key DPAD_CENTER; return 0; fi
    if [ "$diff" -gt 0 ]; then key DPAD_DOWN; else key DPAD_UP; fi
    tries=$((tries + 1))
  done
  fail "could not focus menu entry $1"
  return 1
}

# type_grid <word>: types on the app's 6 column on-screen keyboard with the D-pad,
# starting from the "a" key, the way someone with a remote would
type_grid() {
  local keys="abcdefghijklmnopqrstuvwxyz1234567890" row=0 col=0 i ch idx r c
  for ((i = 0; i < ${#1}; i++)); do
    ch="${1:$i:1}"
    idx=$(expr index "$keys" "$ch")
    idx=$((idx - 1))
    r=$((idx / 6)); c=$((idx % 6))
    while [ "$row" -lt "$r" ]; do adb shell input keyevent DPAD_DOWN; row=$((row + 1)); sleep 0.3; done
    while [ "$row" -gt "$r" ]; do adb shell input keyevent DPAD_UP; row=$((row - 1)); sleep 0.3; done
    while [ "$col" -lt "$c" ]; do adb shell input keyevent DPAD_RIGHT; col=$((col + 1)); sleep 0.3; done
    while [ "$col" -gt "$c" ]; do adb shell input keyevent DPAD_LEFT; col=$((col - 1)); sleep 0.3; done
    adb shell input keyevent DPAD_CENTER
    sleep 0.6
  done
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

# --- Hub folders: open the first streaming service card --------------------
key DPAD_CENTER
sleep 8
shot hub-open
key DPAD_DOWN DPAD_DOWN
sleep 2
shot hub-grid
# First Back jumps to the folder's back arrow, the second one leaves the folder
key BACK
sleep 1
key BACK
sleep 3
if ! screen_has "Streaming services"; then fail "Back did not return from the folder to home"; fi
shot hub-back

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
key DPAD_DOWN
sleep 2
shot home-row6
key DPAD_DOWN DPAD_DOWN
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
menu_go Movies
sleep 10
shot movies-tab
key DPAD_DOWN DPAD_DOWN
sleep 2
shot movies-rows
menu_go Series
sleep 10
shot series-tab
key DPAD_DOWN DPAD_DOWN DPAD_DOWN
sleep 2
shot series-rows
menu_go Search
sleep 6
shot search-tab
type_grid "dune"
sleep 10
shot search-results
key DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT
sleep 2
shot search-results-focus
menu_go Settings
sleep 5
shot settings
key DPAD_RIGHT
sleep 2
shot settings-content
key DPAD_DOWN DPAD_DOWN
sleep 2
shot settings-down

# --- Screensaver and Projectivy rows in Personalization ----------------------
if focus_down_to "Preview" 8; then
  shot settings-screensaver
  key DPAD_DOWN
  sleep 1
  shot settings-projectivy
  key DPAD_UP
  sleep 1
  key DPAD_CENTER
  sleep 5
  shot dream-preview
  sleep 8
  shot dream-preview-later
  key BACK
  sleep 3
  if ! screen_has "Screensaver"; then fail "Back did not return from the screensaver preview to settings"; fi
else
  fail "Screensaver row not found in Personalization"
fi

# --- Projectivy wallpaper provider -------------------------------------------
if ! adb shell cmd package query-services -a tv.projectivy.plugin.WALLPAPER_PROVIDER 2>/dev/null | grep -q NeonWallpaperProviderService; then
  fail "the Projectivy wallpaper provider is not registered"
fi
# Projectivy opens the settings page as ".NeonWallpaperSettings" under the package name
adb shell am start -n "$PKG/.NeonWallpaperSettings" > "$OUT/wallpaper-settings-start.txt" 2>&1
sleep 6
shot wallpaper-settings
key DPAD_DOWN
sleep 5
shot wallpaper-night-drive
key DPAD_DOWN
sleep 1
key DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT
sleep 2
shot wallpaper-swatches
key BACK
sleep 3

# --- The screensaver as the TV runs it ---------------------------------------
if ! adb shell cmd package query-services -a android.service.dreams.DreamService 2>/dev/null | grep -q NeonDreamService; then
  fail "the screensaver is not registered"
fi
adb shell settings put secure screensaver_enabled 1
adb shell settings put secure screensaver_components "$PKG/com.lumera.app.dream.NeonDreamService"
adb shell am start -n com.android.systemui/.Somnambulator > "$OUT/dream-start.txt" 2>&1
sleep 8
adb shell dumpsys dreams > "$OUT/dreams.txt" 2>&1
shot dream-system noalive
adb shell input keyevent DPAD_UP
sleep 3
adb shell settings delete secure screensaver_components

# --- Fast scrolling stress: hold-down equivalent ------------------------------
menu_go Home
sleep 8
for i in $(seq 1 25); do adb shell input keyevent DPAD_DOWN; done
for i in $(seq 1 40); do adb shell input keyevent DPAD_RIGHT; done
sleep 4
shot stress-scroll
for i in $(seq 1 30); do adb shell input keyevent DPAD_UP; done
sleep 3
shot stress-back-top

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
grep "NeonTokyoStarter\|MetahubFallback" "$OUT/logcat-full.txt" > "$OUT/starter-catalogs.txt" || true

if [ -s "$OUT/FAILURES.txt" ]; then
  cat "$OUT/FAILURES.txt"
  exit 1
fi
echo "TV smoke test passed ($step steps)"
