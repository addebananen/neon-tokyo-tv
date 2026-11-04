#!/usr/bin/env bash
# Focused visual acceptance on a disposable Android TV emulator. Never use a physical TV.
set -eu
APK="$1"
OUT="$2"
PKG=com.neontokyo.tv
ACTIVITY=com.lumera.app.MainActivity
case "$(adb get-serialno)" in emulator-*) ;; *) echo "Requires a disposable emulator"; exit 2 ;; esac
mkdir -p "$OUT"
: > "$OUT/FAILURES.txt"
step=0
fail() { echo "FAIL: $*" | tee -a "$OUT/FAILURES.txt"; }

alive() {
  # The emulator itself gone: nothing after this can pass, so stop rather than wait out the job
  if ! timeout 10 adb get-state >/dev/null 2>&1; then
    fail "the emulator went away after step $1"
    exit 1
  fi
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
  adb shell cat /sdcard/ui.xml 2>/dev/null | focused_text
}

# focused_text: the label of the focused element in the UI tree read from stdin
focused_text() {
  python3 -c '
import re, sys
xml = sys.stdin.read()
nodes = re.findall(r"<node [^>]*>", xml)
def box(node):
    m = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node)
    return tuple(map(int, m.groups())) if m else None
focused = [box(n) for n in nodes if "focused=\"true\"" in n]
for n in nodes:
    if "focused=\"true\"" in n:
        desc = re.search(r"content-desc=\"([^\"]+)\"", n)
        if desc:
            print(desc.group(1))
            sys.exit(0)
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


assert_cinema_selected() {
  timeout 20 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml | python3 -c '
import sys, xml.etree.ElementTree as E
root=E.fromstring(sys.stdin.read())
sys.exit(0 if any(n.get("content-desc")=="Aurora Cinema" and n.get("selected")=="true" for n in root.iter("node")) else 1)
' || { fail "Aurora Cinema is not the selected profile theme"; exit 1; }
}

wait_text() {
  for _ in $(seq 1 25); do
    if screen_has "$1"; then return 0; fi
    sleep 2
  done
  fail "Screen never showed $1"
  shot unexpected-screen
  exit 1
}

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP
adb shell settings put system screen_off_timeout 1800000
adb install -r -g "$APK" > "$OUT/install.txt" 2>&1
adb shell pm clear "$PKG" >/dev/null
adb logcat -c
adb logcat -v time > "$OUT/logcat-full.txt" 2>&1 &
LOGCAT_PID=$!
finish() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    shot failed-step noalive || true
  fi
  kill "$LOGCAT_PID" 2>/dev/null || true
  exit "$status"
}
trap finish EXIT
# Keep real focus animation enabled for the review, including rapid direction reversals.
adb shell settings put global animator_duration_scale 1
adb shell am start -n "$PKG/$ACTIVITY" --ez nt_ci true
wait_text 'Create First Profile'
key DPAD_CENTER
sleep 2
adb shell input text Cinema
key ENTER
sleep 2
if screen_has 'Enter Name'; then key ENTER; fi
wait_text 'Choose your picture'
key DPAD_CENTER
wait_text 'Choose a theme'
key DPAD_RIGHT
sleep 2
shot cinema-theme-wizard
# Theme names can live on the parent semantics node of the focus target. The
# persisted selected-state assertion below verifies what the D-pad actually chose.
echo "Wizard focus: $(focused_label)"
key DPAD_CENTER
sleep 4
key DPAD_CENTER
wait_text 'Popular\|Trending\|Continue\|Home'
sleep 10
shot cinema-home
# A short motion clip, with a reset frame counter, separate from the long smoke test.
adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
adb shell screenrecord --time-limit 16 --size 1280x720 /sdcard/cinema-focus.mp4 > "$OUT/record.txt" 2>&1 &
REC_PID=$!
sleep 2
for _ in 1 2 3; do key DPAD_RIGHT DPAD_RIGHT DPAD_LEFT DPAD_LEFT; done
wait "$REC_PID" || true
adb pull /sdcard/cinema-focus.mp4 "$OUT/cinema-focus.mp4" >/dev/null 2>&1
adb shell dumpsys gfxinfo "$PKG" > "$OUT/cinema-gfxinfo.txt"
adb shell dumpsys meminfo "$PKG" > "$OUT/cinema-meminfo.txt"
menu_go Movies
sleep 5
shot cinema-movies
menu_go Settings
sleep 4
shot cinema-settings
key DPAD_RIGHT
sleep 2
# The settings sidebar starts on Personalization; Theme is the next entry.
key DPAD_DOWN DPAD_RIGHT
sleep 3
shot cinema-theme-picker
assert_cinema_selected
# Reopen the app: the profile's theme must be restored, not just previewed.
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$ACTIVITY" --ez nt_ci true
sleep 10
if screen_has "Who's watching?"; then key DPAD_CENTER; sleep 6; fi
shot cinema-reopened
menu_go Settings
sleep 3
key DPAD_RIGHT DPAD_DOWN DPAD_RIGHT
sleep 3
shot cinema-restored-theme
assert_cinema_selected
# Also capture the launcher in the same tested build, independently of the app theme.
adb shell am start -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" --es style aurora
sleep 5
shot aurora-launcher
key DPAD_UP DPAD_RIGHT
sleep 3
shot aurora-launcher-apps
if grep -q 'FATAL EXCEPTION' "$OUT/logcat-full.txt"; then fail 'Android reported a fatal exception'; fi
[ ! -s "$OUT/FAILURES.txt" ]
