#!/usr/bin/env bash
# Android TV smoke test: installs the app, walks the main screens with the D-pad
# like the remote would, saves a screenshot + UI tree per step, and fails if the
# app crashes, stops responding or never reaches the home screen.
set -u
APK="$1"
OUT="$2"
PKG=com.neontokyo.tv
ACTIVITY=com.lumera.app.MainActivity
# SMOKE_MODE=quick, for everyday builds, leaves out the second profile and the scroll stress; the build for a release runs it all
MODE="${SMOKE_MODE:-full}"
RUN="${GITHUB_RUN_NUMBER:-0}"
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

# pixel <x> <y>: prints "r g b" of a pixel on the screen now
pixel() {
  adb exec-out screencap > "$OUT/screen.raw" 2>/dev/null
  python3 - "$OUT/screen.raw" "$1" "$2" <<'PY'
import struct, sys
d = open(sys.argv[1], 'rb').read()
w, h = struct.unpack('<II', d[:8])
o = len(d) - w * h * 4 + (int(sys.argv[3]) * w + int(sys.argv[2])) * 4
print(d[o], d[o + 1], d[o + 2])
PY
  rm -f "$OUT/screen.raw"
}

# brightness: the screen's average brightness now, 0 to 255, from a grid of points
brightness() {
  adb exec-out screencap > "$OUT/screen.raw" 2>/dev/null
  python3 - "$OUT/screen.raw" <<'PY'
import struct, sys
d = open(sys.argv[1], 'rb').read()
w, h = struct.unpack('<II', d[:8])
base = len(d) - w * h * 4
total = n = 0
for gy in range(1, 10):
    for gx in range(1, 16):
        o = base + ((h * gy // 10) * w + w * gx // 16) * 4
        total += (d[o] + d[o + 1] + d[o + 2]) // 3
        n += 1
print(total // n)
PY
  rm -f "$OUT/screen.raw"
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

# focused_poster: the content description inside the focused element (a poster's title)
focused_poster() {
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
        t = re.search(r"content-desc=\"([^\"]*)\"", n)
        b = box(n)
        if t and t.group(1) and b and b[0] >= x1 - 12 and b[1] >= y1 - 12 and b[2] <= x2 + 12 and b[3] <= y2 + 12:
            print(t.group(1))
            break
'
}

# focused_width: the width in pixels of the element that has the D-pad focus
focused_width() {
  timeout 20 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
  adb shell cat /sdcard/ui.xml 2>/dev/null | python3 -c '
import re, sys
nodes = re.findall(r"<node [^>]*>", sys.stdin.read())
focused = [n for n in nodes if "focused=\"true\"" in n]
m = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", focused[-1]) if focused else None
print(int(m.group(3)) - int(m.group(1)) if m else -1)
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

# focus_right_to <label> <max presses>: presses Right until the focused element shows that label
focus_right_to() {
  local i=0
  while [ "$i" -le "$2" ]; do
    if [ "$(focused_label)" = "$1" ]; then return 0; fi
    adb shell input keyevent DPAD_RIGHT
    sleep 0.8
    i=$((i + 1))
  done
  return 1
}

# photo_address: "<ip> <port> <token>" of the page the profile photo dialog shows
photo_address() {
  timeout 20 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
  adb shell cat /sdcard/ui.xml 2>/dev/null | python3 -c '
import re, sys
m = re.search(r"text=\"http://([0-9.]+):([0-9]+)/([a-z0-9]{6})\"", sys.stdin.read())
if m:
    print(*m.groups())
'
}

# photo_from_phone: with the photo dialog open, does what a phone does after scanning the
# QR code: opens the page, sends a photo, sees the TV show it, and keeps it with OK
photo_from_phone() {
  local ip port token base code state
  read -r ip port token < <(photo_address)
  if [ -z "${token:-}" ]; then fail "the photo dialog shows no address"; return 1; fi
  echo "photo page at http://$ip:$port/$token"
  adb forward tcp:18080 tcp:"$port" >/dev/null
  base="http://127.0.0.1:18080/$token"
  if ! curl -fsS -m 10 "$base" -o "$OUT/photo-page.html"; then fail "the photo page did not open"; return 1; fi
  if grep -q "{{" "$OUT/photo-page.html"; then fail "the photo page kept its placeholders"; fi
  grep -q 'id="title"><span class="round-only">Photo</span>' "$OUT/photo-page.html" || fail "the photo page has no title"
  for asset in skyline.webp kanit-medium.ttf kanit-semibold-italic.ttf; do
    code=$(curl -s -m 10 -o /dev/null -w "%{http_code}" "$base/$asset")
    [ "$code" = 200 ] || fail "the photo page's $asset answered $code"
  done
  # Only the address in the QR code takes a photo, and only a photo
  code=$(curl -s -m 10 -H "Expect:" -o /dev/null -w "%{http_code}" -F "image=aGVsbG8=" "http://127.0.0.1:18080/zzzzzz/photo")
  [ "$code" = 404 ] || fail "a wrong address answered $code instead of 404"
  code=$(curl -s -m 10 -H "Expect:" -o /dev/null -w "%{http_code}" -F "image=aGVsbG8=" "$base/photo")
  [ "$code" = 400 ] || fail "a file that is no photo answered $code instead of 400"
  sleep 2
  screen_has "Phone connected" || fail "the TV did not notice the phone"
  shot photo-qr
  base64 -w0 .github/scripts/photo-fixture.jpg > "$OUT/photo.b64"
  code=$(curl -s -m 20 -H "Expect:" -o "$OUT/photo-post.txt" -w "%{http_code}" -F "image=<$OUT/photo.b64" "$base/photo")
  if [ "$code" != 200 ]; then fail "sending the photo answered $code: $(cat "$OUT/photo-post.txt")"; return 1; fi
  if ! wait_for "Use This Photo" 20; then fail "the photo never showed on the TV"; return 1; fi
  sleep 2
  shot photo-preview
  state=$(curl -s -m 5 "$base/status")
  echo "$state" | grep -q '"preview"' || fail "the phone was told $state instead of preview"
  # A dialog's focus does not show in the UI tree, so this only catches focus on the wrong button;
  # the phone hearing "done" after OK below is what proves Use This Photo had it
  label=$(focused_label)
  if [ -n "$label" ] && [ "$label" != "USE THIS PHOTO" ]; then fail "Use This Photo does not have the focus ($label does)"; fi
  key DPAD_CENTER
  sleep 1
  state=$(curl -s -m 5 "$base/status")
  echo "$state" | grep -q '"done"' || fail "the phone was told $state instead of done"
  sleep 6
  if curl -s -m 3 "$base/status" >/dev/null 2>&1; then fail "the photo page still answers after the photo was chosen"; fi
  adb forward --remove tcp:18080 >/dev/null 2>&1
  return 0
}

# video_from_phone: with the screensaver video dialog open, sends what a phone would: first a file
# that is no video, which the TV must turn down, then a real one, which must become the screensaver
video_from_phone() {
  local ip port token base code state
  read -r ip port token < <(photo_address)
  if [ -z "${token:-}" ]; then fail "the video dialog shows no address"; return 1; fi
  echo "video page at http://$ip:$port/$token"
  adb forward tcp:18081 tcp:"$port" >/dev/null
  base="http://127.0.0.1:18081/$token"
  if ! curl -fsS -m 10 "$base" -o "$OUT/video-page.html"; then fail "the video page did not open"; return 1; fi
  if grep -q "{{" "$OUT/video-page.html"; then fail "the video page kept its placeholders"; fi
  grep -q "Choose a video" "$OUT/video-page.html" || fail "the video page has no Choose a video button"
  code=$(curl -s -m 10 -H "Expect:" -o /dev/null -w "%{http_code}" --data-binary "hello" "http://127.0.0.1:18081/zzzzzz/video")
  [ "$code" = 404 ] || fail "a wrong address answered $code instead of 404"
  sleep 2
  screen_has "Phone connected" || fail "the TV did not notice the phone"
  shot video-qr
  head -c 300000 /dev/urandom > "$OUT/not-a-video.bin"
  code=$(curl -s -m 30 -H "Expect:" -H "Content-Type: application/octet-stream" -o /dev/null -w "%{http_code}" --data-binary "@$OUT/not-a-video.bin" "$base/video")
  [ "$code" = 200 ] || fail "sending a file answered $code"
  sleep 4
  state=$(curl -s -m 5 "$base/status")
  echo "$state" | grep -q '"error"' || fail "a file that is no video left the phone at $state instead of error"
  screen_has "play that video" || fail "the TV did not say it can't play a file that is no video"
  # A phone's video with sound, which the TV makes lighter before it keeps it. The page sends it in
  # pieces; one breaks off partway, as when the phone's screen goes dark, and one goes to the wrong
  # place, and the TV must keep what arrived and carry on from where it really got to
  local fixture=.github/scripts/video-fixture-sound.mp4 size half kept got
  size=$(stat -c %s "$fixture")
  half=$((size / 2))
  kept=$((half + 1000))
  piece() { # piece <offset> <length> [<claimed length>]
    local claim=()
    [ -n "${3:-}" ] && claim=(-H "Content-Length: $3")
    tail -c +"$(($1 + 1))" "$fixture" | head -c "$2" > "$OUT/piece.bin"
    curl -s -m "${4:-60}" -H "Expect:" -H "Content-Type: application/octet-stream" -H "X-Offset: $1" -H "X-Total: $size" \
      "${claim[@]}" --data-binary "@$OUT/piece.bin" "$base/piece"
  }
  got=$(piece 0 "$half")
  echo "first piece: $got"
  echo "$got" | grep -q "\"received\":$half" || fail "the TV did not take the first piece of the video ($got)"
  piece "$half" 1000 "$((size - half))" 3 >/dev/null 2>&1
  sleep 1
  got=$(curl -s -m 5 "$base/status")
  echo "after a piece broke off: $got"
  echo "$got" | grep -q "\"received\":$kept" || fail "the TV did not keep the part of a piece that arrived before it broke off ($got)"
  got=$(piece 1234 100)
  echo "$got" | grep -q "\"received\":$kept" || fail "a piece sent to the wrong place moved the video on ($got)"
  got=$(piece "$kept" "$((size - kept))")
  echo "last piece: $got"
  echo "$got" | grep -q "\"received\":$size" || fail "the TV did not take the rest of the video ($got)"
  if ! wait_for "Your video is on" 90; then fail "the TV never took the video"; return 1; fi
  # Its picture is light already, so what comes out without the sound must be smaller still
  shrunk=$(adb logcat -d -s DreamVideoShrink:I | grep -o "from [0-9]* kB to [0-9]* kB" | tail -n 1)
  echo "video shrink: ${shrunk:-none}"
  read -r _ before _ _ after _ <<< "${shrunk:-from 0 kB to 0 kB}"
  [ "${after:-0}" -gt 0 ] && [ "$after" -lt "$before" ] || fail "the TV did not make the video with sound lighter (${shrunk:-not shrunk})"
  state=$(curl -s -m 5 "$base/status")
  echo "$state" | grep -q '"done"' || fail "the phone was told $state instead of done"
  adb forward --remove tcp:18081 >/dev/null 2>&1
  return 0
}

# video_from_phone_page: a large phone video, sent by the real upload page in a real browser at an
# address that, like the TV's own, is not "secure" (so the page gets no screen wake lock), thrown
# away halfway and picked again. The TV must end up with it whole, made 1920x1080 for its screen
video_from_phone_page() {
  local ip port token video
  read -r ip port token < <(photo_address)
  if [ -z "${token:-}" ]; then fail "the video dialog shows no address the second time"; return 1; fi
  adb forward tcp:18082 tcp:"$port" >/dev/null
  if ! command -v ffmpeg >/dev/null; then fail "no ffmpeg on the test machine to make a large video"; return 1; fi
  video="$OUT/phone-video.mp4"
  # An upright phone film at 60 frames a second, about 250 MB: noise keeps it from squeezing
  ffmpeg -loglevel error -y -f lavfi -i "testsrc2=s=720x1280:r=60,noise=alls=50:allf=t+u" \
    -f lavfi -i "sine=frequency=440:sample_rate=48000" -t 5 -c:v libx264 -preset ultrafast -crf 6 \
    -pix_fmt yuv420p -c:a aac -shortest "$video"
  echo "large phone video: $(stat -c %s "$video") bytes"
  adb logcat -c
  NODE_PATH="${PHONE_NODE_PATH:-}" timeout 600 node "$(dirname "$0")/phone-page-test.js" \
    "http://tv.lan:18082/$token" "$video" "http://127.0.0.1:18082/$token/status" 2>&1 | tee "$OUT/phone-page.txt"
  grep -q "^FAIL" "$OUT/phone-page.txt" && sed -n 's/^FAIL: //p' "$OUT/phone-page.txt" | while read -r why; do fail "phone page: $why"; done
  grep -q "page ends on \"done\"" "$OUT/phone-page.txt" || fail "the phone page never finished sending the large video"
  if ! wait_for "Your video is on" 60; then fail "the TV never took the large video"; rm -f "$video"; return 1; fi
  made=$(adb logcat -d -s DreamVideoShrink:I | grep -o "shrunk .*" | tail -n 1)
  echo "large video made: ${made:-nothing}"
  echo "$made" | grep -q "as 1920x1080" || fail "the large upright video was not made 1920x1080 for the TV (${made:-not made})"
  rm -f "$video"
  adb forward --remove tcp:18082 >/dev/null 2>&1
  return 0
}

# hold_ok <seconds>: holds OK down like a thumb on the remote. `input keyevent` can only tap or
# long press, the shell may not write /dev/input, and the emulator console's keys never arrive
# without a hardware keyboard, so HoldKey.java injects the down, the repeats and the up itself
hold_ok() { hold_keys 23 "$1"; }

# hold_keys <codes> <seconds>: the same for any key, or for keys held together ("19,20")
hold_keys() {
  if ! adb shell ls /data/local/tmp/holdkey.dex >/dev/null 2>&1; then
    local sdk="${ANDROID_HOME:-$ANDROID_SDK_ROOT}" build tools
    build=$(mktemp -d)
    tools=$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)
    javac -nowarn --release 8 -cp "$sdk/platforms/android-30/android.jar" -d "$build" "$(dirname "$0")/HoldKey.java" &&
      "$tools/d8" --min-api 30 --lib "$sdk/platforms/android-30/android.jar" --output "$build" "$build/HoldKey.class" &&
      adb push "$build/classes.dex" /data/local/tmp/holdkey.dex >/dev/null || return 1
  fi
  local ms
  ms=$(awk -v s="$2" 'BEGIN { printf "%d", s * 1000 }')
  adb shell CLASSPATH=/data/local/tmp/holdkey.dex app_process /system/bin HoldKey "$1" "$ms" 2>&1 | tr -d '\r' | tail -1 | grep '^held key'
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

adb logcat -v time > "$OUT/logcat-full.txt" 2>&1 &
LOGCAT_PID=$!

# nt_ci turns off the update popup for this session. The emulator runs with animations off, so
# they are switched on for the intro, which is held still a little way in to photograph it and
# filmed with what follows.
adb shell settings put global animator_duration_scale 1
adb shell screenrecord --time-limit 24 --size 1280x720 /sdcard/intro.mp4 > "$OUT/intro-record.txt" 2>&1 &
REC_PID=$!
sleep 1
adb shell am start -n "$PKG/$ACTIVITY" --ez nt_ci true --ef nt_intro_hold 0.4
sleep 6
# The sun has risen to its place, where the skyline under it has dark buildings. Read before the
# photo, whose UI dump can take long enough for the intro to be over; the intro holds still for
# 14 s, which covers both.
read -r r g b < <(pixel 1250 560)
if [ "${r:-0}" -lt 200 ] || [ "${b:-255}" -gt 180 ]; then
  fail "the intro's sun is not on the screen (pixel $r $g $b)"
fi
shot intro
sleep 12
adb shell settings put global animator_duration_scale 0
read -r r g b < <(pixel 1250 560)
[ "${r:-255}" -lt 110 ] || fail "the intro is still on the screen (pixel $r $g $b)"
shot launch
wait "$REC_PID" 2>/dev/null
adb pull /sdcard/intro.mp4 "$OUT/intro.mp4" > /dev/null 2>&1 || true
grep -q "NeonIntro.*played" "$OUT/logcat-full.txt" || fail "the intro did not finish"
grep "NeonIntro" "$OUT/logcat-full.txt" > "$OUT/intro-log.txt" || true
grep -q "the intro stopped early" "$OUT/logcat-full.txt" && fail "the intro stopped early: $(grep -A3 'the intro stopped early' "$OUT/logcat-full.txt" | head -4)"

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
  # A photo of yourself instead of an avatar, sent from a phone through the QR code
  key DPAD_DOWN
  if [ "$(focused_label)" = "Use a Photo From Your Phone" ]; then
    key DPAD_CENTER
    sleep 4
    if ! photo_from_phone; then
      key BACK
      sleep 2
      key DPAD_UP DPAD_CENTER
    fi
  else
    fail "the phone photo button is not below the avatars"
    key DPAD_UP DPAD_CENTER
  fi
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
if ! wait_for "Streaming services" 15; then fail "Back did not return from the folder to home"; fi
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
# Adrian keeps this one for later; the watchlist is his alone
if focus_right_to "ADD TO WATCHLIST" 5; then
  key DPAD_CENTER
  sleep 2
  [ "$(focused_label)" = "WATCHLISTED" ] || fail "Add to watchlist did not take"
  shot details-watchlisted
else
  fail "no Add to watchlist button on the details page"
fi
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

# --- Live TV: a pretend Xtream provider on the runner, added from "the phone" -------------
IPTV_PORT=8765
# A six second test card in MPEG-TS, as a live channel sends it
iptv_stream="$(dirname "$0")/iptv-test.ts"
python3 "$(dirname "$0")/fake-iptv.py" "$IPTV_PORT" "$iptv_stream" &
IPTV_PID=$!
menu_go "Live TV"
sleep 4
shot livetv-empty
screen_has "Add your IPTV service" || fail "Live TV without a service does not invite adding one"
key DPAD_CENTER
sleep 3
shot livetv-setup
read -r ip port token < <(photo_address)
if [ -z "${token:-}" ]; then
  fail "the Live TV setup dialog shows no address"
else
  adb forward tcp:18082 tcp:"$port" >/dev/null
  base="http://127.0.0.1:18082/$token"
  curl -fsS -m 10 "$base" -o "$OUT/iptv-page.html" || fail "the Live TV phone page did not open"
  grep -q "Xtream login" "$OUT/iptv-page.html" || fail "the Live TV phone page has no Xtream form"
  wrong=$(curl -s -m 30 -d kind=xtream -d server="10.0.2.2:$IPTV_PORT" -d user=neon -d pass=nope "$base")
  echo "wrong login: $wrong"
  echo "$wrong" | grep -q '"ok":false' || fail "a wrong Xtream password was accepted: $wrong"
  echo "$wrong" | grep -qi "wrong username or password" || fail "a wrong Xtream password did not say so: $wrong"
  right=$(curl -s -m 30 -d kind=xtream -d server="10.0.2.2:$IPTV_PORT" -d user=neon -d pass=tokyo "$base")
  echo "right login: $right"
  echo "$right" | grep -q '"ok":true' || fail "the Xtream login was refused: $right"
  adb forward --remove tcp:18082 >/dev/null 2>&1
  if wait_for "Sverige" 30; then
    shot livetv-list
    screen_has "6 channels" || fail "Live TV does not count the six channels"
    screen_has "Popular in Sweden" || fail "Live TV has no Popular in Sweden group for SVT1 and TV4"
    # From Favourites (empty) past Popular in Sweden and the sport groups to All channels, then across into the channels
    key DPAD_DOWN
    sleep 2
    shot livetv-popular
    screen_has "SE: SVT1 FHD" || fail "Popular in Sweden does not show SVT1"
    # The sport groups: Neon Two has an SHL match next in the guide, Neon Sport shows Allsvenskan now
    key DPAD_DOWN
    sleep 2
    shot livetv-hockey
    screen_has "Swedish hockey" || fail "Live TV has no Swedish hockey group for the SHL match in the guide"
    screen_has "SHL: Fr" || fail "Swedish hockey does not show the channel with the SHL match"
    key DPAD_DOWN
    sleep 2
    shot livetv-football
    screen_has "Allsvenskan Live" || fail "the Football group does not show the channel with Allsvenskan on"
    key DPAD_DOWN
    sleep 2
    screen_has "Morning Neon" || fail "the guide shows no programme on Neon One"
    key DPAD_RIGHT
    sleep 2
    shot livetv-channel-focus
    timeout 20 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb shell cat /sdcard/ui.xml 2>/dev/null | grep -o '<node [^>]*>' | grep 'focused="true"' | grep -q 'content-desc="Neon One"' ||
      fail "the first channel did not take the focus, focus on: $(focused_label)"
    hold_ok 1.2 >/dev/null || fail "could not hold OK on a channel"
    sleep 2
    shot livetv-channel-menu
    screen_has "Add to Favourites" || fail "holding OK on a channel did not offer Add to Favourites"
    key DPAD_CENTER
    sleep 2
    screen_has 'text="  ★"' || fail "Add to Favourites did not make Neon One a favourite"
    shot livetv-favourite
    adb logcat -c
    key DPAD_CENTER
    sleep 3
    shot livetv-player
    # uiautomator cannot read the player's dialog over the video, so the player says what it tuned to
    adb logcat -d -s LiveTvPlayer:I | grep -q "tuned to Neon One" || fail "the player did not tune to Neon One"
    sleep 3
    shot livetv-playing
    if adb logcat -d 2>/dev/null | grep -q "ExoPlaybackException"; then fail "the test stream did not play (ExoPlayer error in the log)"; fi
    key DPAD_DOWN
    sleep 2
    shot livetv-zap
    adb logcat -d -s LiveTvPlayer:I | grep -q "tuned to Neon Two" || fail "Down in the player did not zap to the next channel"
    key BACK
    sleep 3
    shot livetv-back
    screen_has "Sverige" || fail "Back from the player did not return to the channel list"
    # Search: up to the Search entry, type, and the list narrows to what matches
    key DPAD_LEFT
    sleep 1
    key DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_UP
    sleep 1
    key DPAD_CENTER
    sleep 2
    adb shell input text sport
    sleep 2
    shot livetv-search-typing
    key BACK
    sleep 1
    screen_has "Search channels" && key BACK
    sleep 2
    shot livetv-search
    screen_has "Neon Sport" || fail "searching for sport did not find Neon Sport"
    screen_has "Morning Neon" && fail "searching for sport still lists Neon One" || true
    # A provider the size of a real one (40 000 channels, a 15 000 channel guide) froze the TV on
    # "Tuning in the channels": change to it from Service settings and check the TV keeps up
    key DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN
    sleep 2
    label=$(focused_label)
    if [ "$label" != "Service settings" ]; then
      fail "could not reach Service settings in Live TV, focus on: $label"
    else
      key DPAD_CENTER
      sleep 2
      key DPAD_DOWN
      sleep 1
      key DPAD_CENTER
      sleep 3
      shot livetv-big-setup
      read -r ip port token < <(photo_address)
      if [ -z "${token:-}" ]; then
        fail "Change service did not open the setup dialog"
      else
        adb forward tcp:18082 tcp:"$port" >/dev/null
        adb logcat -c
        big_started=$(date +%s)
        big=$(curl -s -m 30 -d kind=xtream -d server="10.0.2.2:$IPTV_PORT" -d user=big -d pass=tokyo "http://127.0.0.1:18082/$token")
        echo "big login: $big"
        adb forward --remove tcp:18082 >/dev/null 2>&1
        sleep 4
        shot livetv-big-loading
        if wait_for "40000 channels" 240; then
          echo "big provider: list shown after $(( $(date +%s) - big_started )) s" | tee "$OUT/livetv-big.txt"
          shot livetv-big-list
          # The guide comes in after the list; wait for it, then the list must still answer the remote
          t=0
          until adb logcat -d -s LiveTv:I | grep -q "Guide for"; do
            [ "$t" -ge 240 ] && break
            sleep 5
            t=$((t + 5))
          done
          adb logcat -d -s LiveTv:I LiveTv:W | tee -a "$OUT/livetv-big.txt"
          adb logcat -d -s LiveTv:I | grep -q "Guide for" || fail "the big provider's guide did not load (see livetv-big.txt)"
          before=$(focused_label)
          key DPAD_DOWN DPAD_DOWN DPAD_DOWN
          sleep 2
          after=$(focused_label)
          echo "focus moved from '$before' to '$after'" | tee -a "$OUT/livetv-big.txt"
          [ -n "$after" ] && [ "$after" != "$before" ] || fail "Live TV did not answer the remote with 40 000 channels"
          key DPAD_RIGHT
          sleep 2
          shot livetv-big-channels
          adb shell dumpsys meminfo "$PKG" 2>/dev/null | grep -E "TOTAL|Java Heap" | tee -a "$OUT/livetv-big.txt"
        else
          fail "40 000 channels from the big provider did not show within four minutes"
          shot livetv-big-failed
        fi
        adb logcat -d 2>/dev/null | grep -E "ANR in $PKG|FATAL EXCEPTION|OutOfMemoryError" | tee -a "$OUT/livetv-big.txt" | grep -q . &&
          fail "the big provider made the app stop answering or crash (see livetv-big.txt)" || true
        alive livetv-big
      fi
    fi
  else
    fail "the Live TV channels did not load from the pretend provider"
    shot livetv-failed
  fi
fi
menu_go Search
sleep 6
shot search-tab
type_grid "dune"
sleep 10
shot search-results
key DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT
sleep 2
shot search-results-focus
# --- The IPTV service's own films and series as sources ----------------------
# The pretend provider (still the big login from Live TV) has Dune (2021) and Dune: Prophecy
# among 35 000 titles; both must turn up first in the sources panel, marked [IPTV]
key DPAD_LEFT DPAD_LEFT DPAD_LEFT
sleep 2
vod_title=$(focused_poster)
echo "search focus for the film: $vod_title"
if [ "$vod_title" != "Dune" ]; then
  fail "could not reach Dune in the search results for the IPTV film, focus on: $vod_title"
else
  adb logcat -c
  key DPAD_CENTER
  sleep 10
  key DPAD_CENTER
  if wait_for '\[IPTV\]' 45; then
    shot vod-movie-sources
    adb logcat -d -s VodLibrary:I | tee "$OUT/vod.txt"
    # The service's copy comes first, so OK on the panel's first row plays it
    key DPAD_CENTER
    sleep 8
    shot vod-movie-playing
    if adb logcat -d 2>/dev/null | grep -qE "ExoPlaybackException|PlaybackException"; then fail "the IPTV film did not play (player error in the log)"; fi
    alive vod-movie
    key BACK
    sleep 3
    screen_has "Dune" || key BACK
  else
    shot vod-movie-none
    adb logcat -d -s VodLibrary:I | tee "$OUT/vod.txt"
    fail "the sources for Dune (2021) have no [IPTV] entry from the service (see vod.txt)"
  fi
  # Back to the search results, then the series row below
  for _ in 1 2 3; do
    screen_has 'text="Series"' && break
    key BACK
    sleep 3
  done
  key DPAD_DOWN
  sleep 2
  vod_series=$(focused_poster)
  echo "search focus for the series: $vod_series"
  if [ "$vod_series" != "Dune: Prophecy" ]; then
    fail "could not reach Dune: Prophecy in the search results, focus on: $vod_series"
  else
    key DPAD_CENTER
    sleep 10
    shot vod-series-details
    key DPAD_CENTER
    if wait_for '\[IPTV\]' 45; then
      shot vod-series-sources
      screen_has "S01E01" || fail "the IPTV source for Dune: Prophecy is not episode 1 of season 1"
    else
      shot vod-series-none
      adb logcat -d -s VodLibrary:I | tee -a "$OUT/vod.txt"
      fail "the sources for Dune: Prophecy S1E1 have no [IPTV] entry from the service (see vod.txt)"
    fi
    key BACK
    sleep 2
    key BACK
    sleep 3
  fi
fi
kill "$IPTV_PID" 2>/dev/null
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

  # --- Your own video as the screensaver, sent from a phone ------------------
  key DPAD_UP
  sleep 1
  if focus_right_to "Your video" 3; then
    key DPAD_CENTER
    if wait_for "Your own video" 12; then
      if video_from_phone; then
        sleep 2
        shot video-ready
        key DPAD_CENTER
        sleep 7
        shot dream-video
        lit=$(brightness)
        echo "video screensaver brightness: $lit"
        if [ "${lit:-0}" -lt 30 ]; then fail "the video screensaver stayed black (brightness $lit)"; fi
        key BACK
        sleep 3
        key BACK
        sleep 2
        screen_has "Change video" || fail "Settings offers no Change video once a video is on"
        shot settings-video
        # The same, once more, with a large video from the real phone page
        key DPAD_DOWN
        sleep 1
        # Down lands under the row's focused button: step left first, then along to Change video
        for _ in 1 2; do [ "$(focused_label)" = "Change video" ] && break; key DPAD_LEFT; sleep 0.8; done
        if focus_right_to "Change video" 3; then
          key DPAD_CENTER
          if wait_for "Your own video" 12; then
            if video_from_phone_page; then shot video-large-ready; fi
            key BACK
            sleep 3
          else
            fail "Change video did not open the video dialog"
          fi
        else
          fail "could not reach Change video"
        fi
      fi
    else
      fail "Your video did not open the video dialog"
    fi
  else
    fail "the screensaver row has no Your video option"
  fi
else
  fail "Screensaver row not found in Personalization"
fi

# --- Scene art on the service and genre cards ----------------------------------
# Every card in a grid while focus walks from one to the next, filmed so the moving scenes can
# be checked, then the tall and square cards at rest
adb shell settings put global animator_duration_scale 1
adb shell am start -n "$PKG/com.lumera.app.ui.home.SceneArtPreviewActivity" --ei dwell 3200 >/dev/null 2>&1
sleep 1
adb shell screenrecord --time-limit 76 --size 1280x720 --bit-rate 8000000 /sdcard/scene-art.mp4 > "$OUT/scene-art-record.txt" 2>&1 &
recorder=$!
sleep 6
shot scene-art
wait "$recorder" 2>/dev/null || true
adb pull /sdcard/scene-art.mp4 "$OUT/scene-art.mp4" > /dev/null 2>&1 || true
grep -E "SceneMotion|service_art" "$OUT/logcat-full.txt" | grep -iE "error|exception" | head -5 > "$OUT/scene-art-errors.txt" || true
[ -s "$OUT/scene-art-errors.txt" ] && fail "the scene art logged errors: $(head -2 "$OUT/scene-art-errors.txt")"
key BACK
sleep 2
for shape in tall square; do
  adb shell am start -n "$PKG/com.lumera.app.ui.home.SceneArtPreviewActivity" --es shape "$shape" --ei width 150 --ei dwell 60000 >/dev/null 2>&1
  sleep 6
  shot "scene-art-$shape"
  key BACK
  sleep 2
done
adb shell settings put global animator_duration_scale 0

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

# --- Neon Home: the home screen the TV setup script can turn on ---------------
# Nothing answers the Home button with it until the script turns its alias on
if adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | grep -q "$PKG"; then
  fail "Neon Home answers the Home button before the setup script turned it on"
fi
adb shell am start -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" > "$OUT/neon-home-start.txt" 2>&1
sleep 8
shot neon-home
screen_has "WATCH &amp; LISTEN" || fail "Neon Home has no Watch and listen row"
if screen_has "HDMI 6"; then fail "the consoles are still on the Home tab instead of on Källor"; fi
screen_has "Neon Tokyo" || fail "Neon Home has no Neon Tokyo tile where Stremio used to be"
label=$(focused_label)
[ "$label" = "YouTube" ] || fail "Neon Home does not start on YouTube ($label has the focus)"
# Holding OK for three seconds lifts a tile: the arrows move it, OK puts it down, and it stays
if ! hold_ok 3.6; then
  fail "could not hold OK down on the emulator"
else
  sleep 1
  shot neon-home-lifted
  screen_has "OK puts it down" || fail "holding OK on YouTube for three seconds did not lift it"
  key DPAD_RIGHT
  sleep 1
  shot neon-home-moved
  key DPAD_CENTER
  sleep 1
  if screen_has "OK puts it down"; then fail "OK did not put the lifted tile down"; fi
  key DPAD_LEFT
  sleep 1
  label=$(focused_label)
  [ "$label" = "YT Music" ] || fail "YouTube moved one step right, but $label is first instead of YT Music"
  # A new Neon Home reads the order back; -S would kill the app and leave it on the profile picker
  adb shell am start --activity-clear-task -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" >/dev/null 2>&1
  sleep 8
  label=$(focused_label)
  [ "$label" = "YT Music" ] || fail "the new order was gone when Neon Home opened again ($label is first)"
  # And back to where it was, for the checks below
  key DPAD_RIGHT
  sleep 1
  hold_ok 3.6
  sleep 1
  key DPAD_LEFT
  sleep 1
  key DPAD_CENTER
  sleep 1
  label=$(focused_label)
  [ "$label" = "YouTube" ] || fail "YouTube did not move back to the front ($label has the focus)"
fi
# Up from the tiles lands on the open tab, and pointing at All apps opens it
key DPAD_UP
sleep 1
label=$(focused_label)
[ "$label" = "HOME" ] || fail "Up from YouTube did not land on the Home tab ($label has the focus)"
key DPAD_RIGHT
sleep 2
shot neon-home-apps
key DPAD_DOWN
sleep 1
label=$(focused_label)
[ "$label" = "Neon Tokyo" ] || fail "All apps does not start with Neon Tokyo ($label has the focus)"
# Six small tiles to a row: 116dp, a little more while focused (2 px to the dp here)
width=$(focused_width)
[ "$width" -ge 200 ] && [ "$width" -le 270 ] || fail "All apps tiles are $width px wide instead of the small tile size, so six do not fit"
key DPAD_RIGHT
sleep 2
shot neon-home-apps-focus
# A short hold on an app in All apps puts it on Home in place of one of the four tiles: the
# next app along, which is not on Home yet
key DPAD_RIGHT
sleep 1
pinned=$(focused_label)
adb shell input keyevent --longpress DPAD_CENTER
sleep 2
shot neon-home-pin
# The same panel takes an app away: uninstall for one's own, hide for one that came with the TV
screen_has "App info" || fail "the panel for $pinned has no App info"
grep -q -e 'text="Uninstall"' -e 'text="Hide"' "$OUT"/*-neon-home-pin.xml 2>/dev/null || fail "the panel for $pinned offers neither Uninstall nor Hide"
if screen_has "Put $pinned on Home"; then
  key DPAD_CENTER
  sleep 2
  key DPAD_UP
  sleep 1
  key DPAD_LEFT
  sleep 2
  key DPAD_DOWN
  sleep 1
  label=$(focused_label)
  shot neon-home-pinned
  [ "$label" = "$pinned" ] || fail "$pinned did not take the first Home tile ($label is there)"
  # A short hold on a Home tile changes it, or puts the first four back
  adb shell input keyevent --longpress DPAD_CENTER
  sleep 2
  shot neon-home-change
  screen_has "Change $pinned" || fail "a short hold on a Home tile did not offer to change it"
  key DPAD_UP
  sleep 1
  key DPAD_CENTER
  sleep 2
  label=$(focused_label)
  [ "$label" = "YouTube" ] || fail "Back to the first four did not bring YouTube back to the front ($label is there)"
  key DPAD_UP
  sleep 1
  key DPAD_RIGHT
  sleep 2
  key DPAD_DOWN
  sleep 1
  key DPAD_RIGHT
  sleep 1
else
  key DPAD_LEFT
  sleep 1
  fail "a short hold on $pinned in All apps did not offer to put it on Home"
  key BACK
  sleep 1
fi
# Källor, next to All apps, has the consoles with their HDMI numbers
key DPAD_UP
sleep 1
label=$(focused_label)
[ "$label" = "ALL APPS" ] || fail "Up from the apps did not land on the All apps tab ($label has the focus)"
key DPAD_RIGHT
sleep 2
shot neon-home-sources
label=$(focused_label)
[ "$label" = "KÄLLOR" ] || fail "right of All apps is $label, not the Källor tab"
screen_has "HDMI 6" || fail "Källor does not show the consoles with their HDMI numbers"
key DPAD_DOWN
sleep 1
label=$(focused_label)
[ "$label" = "PC" ] || fail "Källor does not start with the PC on HDMI 1 ($label has the focus)"
# Three big tiles to a row: 190dp, a little more while focused
width=$(focused_width)
[ "$width" -ge 340 ] && [ "$width" -le 450 ] || fail "Källor tiles are $width px wide instead of the big tile size"
# A long press on a console asks which TV input it is on
adb shell input keyevent --longpress DPAD_CENTER
sleep 2
shot neon-home-link
screen_has "Which input is PC on" || fail "a long press on PC did not ask which input it is on"
key BACK
sleep 2
if screen_has "Which input is PC on"; then fail "Back did not close the input picker"; fi
# A console lifted on Källor goes down a whole row with Down
if hold_ok 3.6; then
  sleep 1
  screen_has "OK puts it down" || fail "holding OK on PC for three seconds did not lift it"
  key DPAD_DOWN
  sleep 1
  shot neon-home-sources-moved
  key DPAD_CENTER
  sleep 1
  key DPAD_UP
  sleep 1
  label=$(focused_label)
  [ "$label" = "Switch 2" ] || fail "PC moved down a row, but $label is above it instead of Switch 2"
  key DPAD_DOWN
  sleep 1
  hold_ok 3.6
  sleep 1
  key DPAD_UP
  sleep 1
  key DPAD_CENTER
  sleep 1
  key DPAD_RIGHT
  sleep 1
  label=$(focused_label)
  [ "$label" = "Switch 2" ] || fail "PC did not move back to the front of Källor ($label is next to it)"
fi
# Back goes to the Home tab first, then leaves the preview
key BACK
sleep 2
label=$(focused_label)
[ "$label" = "YouTube" ] || fail "Back on Källor did not return to YouTube ($label has the focus)"
key BACK
sleep 3
shot neon-home-left noalive
if adb shell dumpsys activity activities 2>/dev/null | grep -E "mResumedActivity|topResumedActivity" | grep -q NeonHomeActivity; then
  fail "Back on the Home tab did not leave the Neon Home preview"
fi

# Every home style, opened as a preview in it: a picture each of Home, and of All apps and
# Källor for the style being reviewed
# Six styles, few enough to review them all on every run
styles=(clean nature dusk deepblue icelake)
dreams=(dusk nature deepblue icelake)
echo "home styles under test ($MODE): ${styles[*]}"
for style in "${styles[@]}"; do
  adb logcat -c
  adb shell am start --activity-clear-task -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" --es style "$style" >/dev/null 2>&1
  sleep 7
  shot "style-$style"
  # The picture or film is shown by the video chip; the drawn painting only stands in for it
  plane=$(adb logcat -d -s PlaneSurface:I | tr -d '\r' | grep -o "showing .*\|can't play.*" | tail -n 1)
  echo "$style backdrop: ${plane:-nothing on the video chip}"
  if ! echo "$plane" | grep -q "^showing"; then fail "the $style style's picture never reached the video chip (${plane:-no word from it})"; fi
  label=$(focused_label)
  [ "$label" = "YouTube" ] || fail "Neon Home in the $style style does not start on YouTube ($label has the focus)"
  # Nature comes with the consoles on Home as well as on Källor
  if [ "$style" = nature ] && ! screen_has "HDMI 6"; then fail "Nature does not show the consoles on Home"; fi
  # The style under review is filmed too, for its motion
  if [ "$style" = nature ]; then
    adb shell settings put global animator_duration_scale 1
    adb shell screenrecord --time-limit 16 --size 1280x720 --bit-rate 6000000 "/sdcard/style-$style.mp4" > "$OUT/style-record.txt" 2>&1
    adb shell settings put global animator_duration_scale 0
    adb pull "/sdcard/style-$style.mp4" "$OUT/style-$style.mp4" > /dev/null 2>&1 || true
  fi
  if [ "$style" = clean ]; then
    key DPAD_UP; sleep 1; key DPAD_RIGHT; sleep 2
    shot "style-$style-apps"
    key DPAD_RIGHT; sleep 2
    shot "style-$style-sources"
    # Up from the tabs reaches the pills at the top; the profile pill is the first of them
    key DPAD_UP; sleep 1
    label=$(focused_label)
    case "$label" in N|Neon|Search|AirPlay|Inputs|Bluetooth|Update|nt.*|Motion|Settings) ;; *) fail "Up from the tabs in the $style style is $label, not a pill at the top" ;; esac
    for _ in 1 2 3 4 5 6 7 8; do
      # The pill reads as its face's letter or the profile's name
      case "$label" in N|Neon) break ;; esac
      key DPAD_LEFT; sleep 1
      label=$(focused_label)
    done
    case "$label" in N|Neon) ;; *) fail "Left along the pills in the $style style ends on $label, not the profile pill" ;; esac
    key DPAD_CENTER; sleep 2
    shot "style-$style-profiles"
    screen_has "Who's home?" || fail "the profile pill did not open the profile picker"
    # Under the profiles, Customize opens the look and layout of the profile that is home
    key DPAD_DOWN; sleep 1
    label=$(focused_label)
    if [ "$label" = "Customize" ]; then
      key DPAD_CENTER; sleep 2
      shot "style-$style-customize"
      screen_has "Liquid glass" || fail "Customize did not open the profile's glass and layout"
      screen_has "Consoles on Home" || fail "the profile's layout has no choice of consoles on Home"
      key BACK; sleep 2
      screen_has "Who's home?" || fail "Back from Customize did not go back to the profiles"
    else
      fail "Down from the profiles is $label, not Customize"
    fi
    key BACK; sleep 2
    screen_has "Who's home?" && fail "one press of Back did not close the profile picker"
    # The panel gives the focus back to the pill that opened it
    label=$(focused_label)
    case "$label" in N|Neon) ;; *) fail "the focus did not go back to the profile pill when the profile picker closed ($label has it)" ;; esac
    # Two along is the AirPlay button, for sharing from an iPhone through a receiver app
    key DPAD_RIGHT; sleep 1; key DPAD_RIGHT; sleep 1
    label=$(focused_label)
    [ "$label" = "AirPlay" ] || fail "two along from the profile pill is $label, not the AirPlay pill"
    shot "style-$style-airplay"
    # Bluetooth, two further along, lists the paired headphones and speakers of this profile
    key DPAD_RIGHT; sleep 1; key DPAD_RIGHT; sleep 1
    label=$(focused_label)
    [ "$label" = "Bluetooth" ] || fail "four along from the profile pill is $label, not Bluetooth"
    key DPAD_CENTER; sleep 3
    shot "style-$style-bluetooth"
    screen_has "devices" || screen_has "no Bluetooth" || fail "the Bluetooth pill did not open the Bluetooth panel"
    key BACK; sleep 2
    label=$(focused_label)
    [ "$label" = "Bluetooth" ] || fail "the focus did not go back to Bluetooth when its panel closed ($label has it)"
    # The pill with the version number, next along, says which version this is and looks for a newer one
    key DPAD_RIGHT; sleep 1
    label=$(focused_label)
    case "$label" in nt.*|Update) ;; *) fail "five along from the profile pill is $label, not the version pill" ;; esac
    key DPAD_CENTER; sleep 4
    shot "style-$style-updates"
    screen_has "This version" || fail "the version pill did not open the updates panel"
    key BACK; sleep 2
    label=$(focused_label)
    case "$label" in nt.*|Update) ;; *) fail "the focus did not go back to the version pill when the updates panel closed ($label has it)" ;; esac
    # Next along, the Motion panel: how much the picture, the tiles and the icons move
    key DPAD_RIGHT; sleep 1
    label=$(focused_label)
    [ "$label" = "Motion" ] || fail "next along from the version pill is $label, not Motion"
    key DPAD_CENTER; sleep 2
    shot "style-$style-motion"
    screen_has "BACKGROUND" || fail "the Motion pill did not open the Motion panel"
    key BACK; sleep 2
    label=$(focused_label)
    [ "$label" = "Motion" ] || fail "the focus did not go back to the Motion pill when its panel closed ($label has it)"
    key BACK; sleep 2
  fi
  key BACK
  sleep 2
done

# The glass and the layout a profile can choose, on styles that come without them: Neon in liquid
# glass with the consoles on Home, and Ice Lake with square icons at the foot of the screen
adb shell am start --activity-clear-task -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" --es style neon --es glass liquid --es layout consoles,names >/dev/null 2>&1
sleep 6
shot glass-neon-liquid
screen_has "HDMI 6" || fail "Neon in liquid glass, asked for the consoles on Home, shows none there"
label=$(focused_label)
[ "$label" = "YouTube" ] || fail "Neon in liquid glass does not start on YouTube ($label has the focus)"
key BACK; sleep 2
adb shell am start --activity-clear-task -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" --es style icelake --es layout down,square >/dev/null 2>&1
sleep 6
shot glass-icelake-square
label=$(focused_label)
[ "$label" = "YouTube" ] || fail "Ice Lake with square icons does not start on YouTube ($label has the focus)"
key BACK; sleep 2

# Each focus motion a profile can pick for liquid glass, on Deep Blue: the focus lands on YouTube,
# moves right (so the motion knows which way it came) and back, and the home screen keeps it
for motion in glow breathe tilt liquid rim drop layers; do
  adb shell am start --activity-clear-task -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" --es style deepblue --es focus "$motion" >/dev/null 2>&1
  sleep 5
  key DPAD_RIGHT; sleep 2
  label=$(focused_label)
  { [ -n "$label" ] && [ "$label" != "YouTube" ]; } || fail "Deep Blue with the $motion focus motion did not move the focus right ($label has it)"
  case "$motion" in tilt|layers) shot "focus-$motion" ;; esac
  key DPAD_LEFT; sleep 2
  label=$(focused_label)
  [ "$label" = "YouTube" ] || fail "Deep Blue with the $motion focus motion did not come back to YouTube ($label has the focus)"
  key BACK; sleep 2
done

# The app icon packs: the apps' own icons and banners, read from the apps on the TV, on Home and
# on the All apps tab. Apps the emulator lacks keep their neon face
for pack in original banner glass; do
  adb shell am start --activity-clear-task -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" --es style clean --es icons "$pack" >/dev/null 2>&1
  sleep 6
  shot "icons-$pack"
  # A banner is all picture, the app's name only in its description
  if [ "$pack" = banner ]; then
    screen_has 'content-desc="YouTube"' || fail "Neon Home with the banner icons shows no YouTube banner"
  else
    label=$(focused_label)
    [ "$label" = "YouTube" ] || fail "Neon Home with the $pack icons does not start on YouTube ($label has the focus)"
  fi
  key DPAD_UP; sleep 1; key DPAD_RIGHT; sleep 3
  shot "icons-$pack-apps"
  screen_has "Settings" || fail "the All apps tab with the $pack icons does not show Settings"
  key BACK; sleep 1
  key BACK; sleep 2
done

# Colour remixes of the apps' own icons, and the neon round Neon Tokyo's tile (fourth on Home)
for tint in synthwave night; do
  adb shell am start --activity-clear-task -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" --es style clean --es icons original --es tint "$tint" >/dev/null 2>&1
  sleep 6
  shot "tint-$tint"
  label=$(focused_label)
  [ "$label" = "YouTube" ] || fail "Neon Home with the $tint icons does not start on YouTube ($label has the focus)"
  if [ "$tint" = synthwave ]; then
    # Filmed with the animations on, to see each icon's motion as the focus lands on it
    adb shell settings put global animator_duration_scale 1
    adb shell screenrecord --time-limit 12 --size 1280x720 --bit-rate 6000000 /sdcard/icon-motion.mp4 > "$OUT/motion-record.txt" 2>&1 &
    recorder=$!
    sleep 1
    sleep 2; key DPAD_RIGHT; sleep 2; key DPAD_RIGHT; sleep 2; key DPAD_RIGHT; sleep 4
    wait "$recorder" 2>/dev/null || true
    adb pull /sdcard/icon-motion.mp4 "$OUT/icon-motion.mp4" > /dev/null 2>&1 || true
    adb shell settings put global animator_duration_scale 0
    shot neon-aura
    label=$(focused_label)
    [ "$label" = "Neon Tokyo" ] || fail "the fourth tile on Home is not Neon Tokyo ($label has the focus)"
  fi
  key BACK; sleep 2
done

# The scenes the streaming apps play inside their tile in focus. The emulator has none of these
# apps, so their tiles are dimmed, but the scene plays all the same
n=0
for tiles in "se.tv4.tv4play,se.svt.android.svtplay,allente.b2c.tvclient,com.viaplay.android" "com.netflix.ninja,com.disney.disneyplus,com.amazon.amazonvideo.livingroom,com.wbd.stream"; do
  adb shell am start --activity-clear-task -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" --es style clean --es icons original --es tiles "$tiles" >/dev/null 2>&1
  sleep 6
  for i in 1 2 3 4; do
    n=$((n + 1))
    sleep 2
    shot "scene-$n"
    if [ "$i" -lt 4 ]; then key DPAD_RIGHT; fi
  done
  key BACK; sleep 2
done

# A console switched on while the home screen shows: its card slides in at the top, the console
# boots in neon on it and it takes the focus, so OK switches to it; Back puts it away. The
# emulator has no HDMI inputs, so the preview shows the card as if Switch 2 had just come on
adb shell settings put global animator_duration_scale 1
adb shell am start --activity-clear-task -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" --es wake switch2 >/dev/null 2>&1
adb shell screenrecord --time-limit 7 --size 1280x720 --bit-rate 6000000 /sdcard/console-wake.mp4 > "$OUT/wake-record.txt" 2>&1 &
recorder=$!
sleep 2
shot console-wake
# The card goes by itself after 14 s and each UI dump waits out its breathing ring, so the
# checks read the one dump the shot made and Back comes before the time runs out
wake_xml="$OUT/$(printf "%02d" "$step")-console-wake.xml"
grep -q "Switch 2 is on" "$wake_xml" 2>/dev/null || fail "the card of a console that was just switched on did not show"
label=$(focused_text < "$wake_xml" 2>/dev/null)
case "$label" in *Play*) ;; *) fail "the card of a console that was just switched on does not have the focus on Play ($label has it)" ;; esac
key BACK
sleep 2
wait "$recorder" 2>/dev/null || true
adb pull /sdcard/console-wake.mp4 "$OUT/console-wake.mp4" > /dev/null 2>&1 || true
shot console-wake-away
if screen_has "Switch 2 is on"; then fail "Back did not put the console's card away"; fi
label=$(focused_label)
[ "$label" = "YouTube" ] || fail "the focus did not go back to YouTube when the console's card went ($label has it)"
# A paired headphone switched on near the TV: its card slides in at the bottom corner with the
# focus on Connect, and Back puts it away. The emulator has no Bluetooth devices, so the preview
# shows the card for a made-up pair of AirPods (one word: adb shell splits the extra at spaces)
adb shell am start -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" --es bluetooth AirPods >/dev/null 2>&1
sleep 2
shot bluetooth-card
bt_xml="$OUT/$(printf "%02d" "$step")-bluetooth-card.xml"
grep -q "AirPods turned on" "$bt_xml" 2>/dev/null || fail "the card of a Bluetooth device that was just switched on did not show"
label=$(focused_text < "$bt_xml" 2>/dev/null)
case "$label" in *Connect*) ;; *) fail "the Bluetooth card does not have the focus on Connect ($label has it)" ;; esac
key BACK
sleep 2
shot bluetooth-card-away
if screen_has "AirPods turned on"; then fail "Back did not put the Bluetooth card away"; fi
# Home pressed twice opens the Bluetooth corner: this profile's devices, each with its own mark
# and Connect, the first with the focus. The preview lists made-up devices
adb shell am start -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" --es bluetoothlist "AirPods,JBL" >/dev/null 2>&1
sleep 2
shot bluetooth-corner
corner_xml="$OUT/$(printf "%02d" "$step")-bluetooth-corner.xml"
grep -q 'text="JBL"' "$corner_xml" 2>/dev/null || fail "the Bluetooth corner did not list the devices"
label=$(focused_text < "$corner_xml" 2>/dev/null)
case "$label" in *AirPods*) ;; *) fail "the Bluetooth corner does not have the focus on its first device ($label has it)" ;; esac
key BACK
sleep 2
if screen_has "All devices"; then fail "Back did not put the Bluetooth corner away"; fi
adb shell settings put global animator_duration_scale 0
key BACK
sleep 2

# Widgets under the clock, a profile's own extra: weather, the week, what plays and the network
adb shell am start --activity-clear-task -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" --es style neon --es widgets Weather,Calendar,Music,Network >/dev/null 2>&1
sleep 5
shot neon-home-widgets
screen_has 'text="Week [0-9]*"' || fail "the week widget did not show under the clock"
screen_has 'text="Nothing playing"' || fail "the music widget did not show under the clock"
screen_has 'text="Wi‑Fi"\|text="Ethernet"\|text="Online"\|text="Offline"' || fail "the network widget did not show under the clock"
label=$(focused_label)
[ "$label" = "YouTube" ] || fail "with widgets on, Neon Home does not start on YouTube ($label has the focus)"
key BACK
sleep 2

# A home style's own screensaver: its picture, its little lights and a quiet clock, or for a
# style that is a film (Deep Blue, Ice Lake) the film alone. The preview opens over the app the
# way its Preview button does; clearing the task would close the app too
for style in "${dreams[@]}"; do
  adb shell am start -n "$PKG/com.lumera.app.dream.NeonDreamPreviewActivity" --es style "$style" >/dev/null 2>&1
  sleep 6
  shot "dream-style-$style"
  case "$style" in
    deepblue|icelake) screen_has 'text="[0-9]*:[0-9][0-9]' && fail "the $style screensaver should be its film alone, without a clock" ;;
    *) screen_has 'text="[0-9]*:[0-9][0-9]' || fail "the $style screensaver shows no clock" ;;
  esac
  key BACK
  sleep 2
done

# Neon Home on the Home button, switched on and off the way the TV setup script does it. The
# stock launcher answers Home ahead of any default for as long as it is on, so like the script
# the test turns it off meanwhile, and on again afterwards
home_resumed() { adb shell dumpsys activity activities 2>/dev/null | grep -E "mResumedActivity|topResumedActivity" | grep -q NeonHome; }
switch_home() { adb shell am broadcast --include-stopped-packages -n "$PKG/com.lumera.app.launcher.NeonHomeSwitch" --es home "$1" 2>&1 | tr -d '\r'; }
resolve_home() { adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tr -d '\r' | tail -n 1; }
log_homes() {
  {
    echo "--- $1"
    echo "resolves to: $(resolve_home)"
    adb shell cmd role get-role-holders --user 0 android.app.role.HOME
    adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
  } >> "$OUT/neon-home-switch.txt" 2>&1
}
stock_home=$(resolve_home)
stock_pkg=${stock_home%%/*}
case "$stock_pkg" in
  ""|android|"$PKG") fail "no stock home screen to stand in for ($stock_home)"; stock_pkg="" ;;
esac
answer=$(switch_home neon)
echo "$answer" > "$OUT/neon-home-switch.txt"
echo "$answer" | grep -q 'data="neon"' || fail "the app did not turn its Home button entry on ($answer)"
[ -n "$stock_pkg" ] && adb shell pm disable-user --user 0 "$stock_pkg" >> "$OUT/neon-home-switch.txt" 2>&1
adb shell cmd package set-home-activity "$PKG/$PKG.NeonHome" >> "$OUT/neon-home-switch.txt" 2>&1
log_homes "Neon Home on"
case "$(resolve_home)" in
  "$PKG/"*) ;;
  *) fail "the Home button does not resolve to Neon Home once it was made the home screen ($(resolve_home))" ;;
esac
adb shell input keyevent KEYCODE_HOME
sleep 6
shot neon-home-as-home
home_resumed || fail "the Home button did not open Neon Home once it was made the home screen"
label=$(focused_label)
[ "$label" = "YouTube" ] || fail "Neon Home as the home screen does not start on YouTube ($label has the focus)"
# The home screen stays on Back, and Home brings its first tile back
key DPAD_DOWN BACK
sleep 2
home_resumed || fail "Back left Neon Home while it is the home screen"
key DPAD_RIGHT
adb shell input keyevent KEYCODE_HOME
sleep 2
label=$(focused_label)
[ "$label" = "YouTube" ] || fail "Home on Neon Home did not go back to its first tile ($label has the focus)"
# The TV switched off and on again: Neon Home comes on to the neon intro
adb shell settings put global animator_duration_scale 1
adb logcat -c 2>/dev/null || true
adb shell input keyevent KEYCODE_SLEEP
sleep 4
# (screenrecord stops with the screen, so the log says whether it played rather than a film)
adb shell input keyevent KEYCODE_WAKEUP
sleep 0.6
shot wake-intro
adb shell settings put global animator_duration_scale 0
adb logcat -d -s NeonIntro > "$OUT/wake-intro-log.txt" 2>&1 || true
grep -q "played" "$OUT/wake-intro-log.txt" || fail "the intro did not play when the TV came on to Neon Home"
home_resumed || fail "Neon Home was not on the screen after the TV came on"
# Back to the stock launcher, the way undo does it: the original home first, then the alias off
[ -n "$stock_pkg" ] && adb shell pm enable "$stock_pkg" >> "$OUT/neon-home-switch.txt" 2>&1
adb shell cmd package set-home-activity "$stock_home" >> "$OUT/neon-home-switch.txt" 2>&1
answer=$(switch_home off)
echo "$answer" >> "$OUT/neon-home-switch.txt"
echo "$answer" | grep -q 'data="off"' || fail "the app did not turn its Home button entry off ($answer)"
log_homes "Neon Home off"
if adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | grep -q "$PKG"; then
  fail "Neon Home still answers the Home button after it was turned off"
fi
[ "$(resolve_home)" = "$stock_home" ] || fail "the stock home screen did not get the Home button back ($(resolve_home) instead of $stock_home)"
adb shell am start -n "$PKG/$ACTIVITY" > /dev/null 2>&1
sleep 3

# --- Overlays: picture diagnostics and the gaming HUD over whatever shows ---------
# The TV turns the overlay service on under Accessibility; the shell may do the same. Holding Up
# and Down together opens diagnostics and Back puts it away; Left and Right bring the HUD, which
# goes by itself. The windows draw over the app without taking its focus
overlay_on() { adb shell dumpsys window windows 2>/dev/null | grep -q "NeonOverlay"; }
overlay_me="$PKG/com.lumera.app.overlay.NeonOverlayService"
overlay_before=$(adb shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')
adb shell pm grant "$PKG" android.permission.DUMP > "$OUT/overlay.txt" 2>&1
adb shell settings put secure enabled_accessibility_services "$overlay_me"
adb shell settings put secure accessibility_enabled 1
sleep 4
adb shell dumpsys accessibility >> "$OUT/overlay.txt" 2>&1
grep -q "NeonOverlayService" "$OUT/overlay.txt" || fail "the overlay service did not start once Accessibility turned it on"
# The remote's presses go to a thread of their own, so a busy home screen never holds them up
adb logcat -d -s NeonOverlay:I | grep "Connected" | tail -1 >> "$OUT/overlay.txt"
adb logcat -d -s NeonOverlay:I | grep "Connected" | tail -1 | grep -q "their own thread" ||
  fail "the overlay service took the remote's presses on the main thread"
adb shell settings put global animator_duration_scale 1
adb shell screenrecord --time-limit 6 --size 1280x720 --bit-rate 6000000 /sdcard/overlay.mp4 >> "$OUT/overlay.txt" 2>&1 &
recorder=$!
sleep 0.5
# Keys injected from the shell never pass the accessibility key filter the chords listen through
# (a real remote's do; the chords themselves are unit tested), so the overlays are opened the way
# a PC can: a broadcast only the shell and the system may send. Pictures are plain screencaps,
# since uiautomator unbinds every accessibility service while it reads the screen
overlay() { adb shell am broadcast -a com.neontokyo.tv.OVERLAY --es show "$1" >/dev/null 2>&1; }
overlay_shot() { step=$((step + 1)); adb exec-out screencap -p > "$OUT/$(printf "%02d-%s" "$step" "$1").png"; }
# The window goes 220 ms after the panel is told to, on the main thread; the software-drawn emulator
# can hold that thread behind a slow frame of glass, so this waits up to 5 s and logs how long it took
overlay_gone() {
  local n=0
  while overlay_on; do
    [ "$n" -ge 10 ] && return 1
    sleep 0.5
    n=$((n + 1))
  done
  echo "$1 gone after $((n * 5 / 10)).$((n * 5 % 10)) s more" >> "$OUT/overlay.txt"
}
overlay diagnostics
sleep 2.5
overlay_on || fail "picture diagnostics did not open"
sleep 1.5
overlay_shot overlay-diagnostics
sleep 1.5
overlay off
sleep 1
wait "$recorder" 2>/dev/null || true
adb pull /sdcard/overlay.mp4 "$OUT/overlay-diagnostics.mp4" > /dev/null 2>&1 || true
if overlay_on; then fail "picture diagnostics did not go away"; fi
adb shell dumpsys activity activities 2>/dev/null | grep -E "mResumedActivity|topResumedActivity" | grep -q "$PKG/$ACTIVITY" ||
  fail "picture diagnostics took the app out of front"
# The app's own folder is closed to adb on Android 11, so the save is read from the log
adb logcat -d -s Dumps | grep -q "Saved the report" || fail "picture diagnostics saved no report"
adb shell am broadcast -a com.neontokyo.tv.OVERLAY --es show report > "$OUT/overlay-readings.txt" 2>&1
grep -q "\[Panel\]" "$OUT/overlay-readings.txt" || fail "the overlay service sent no readings back to am broadcast"
overlay hud
sleep 1.5
overlay_on || fail "the gaming HUD did not open"
overlay_shot overlay-hud
sleep 6
if overlay_on; then fail "the gaming HUD did not go by itself after 6 seconds"; fi

# Its setup card, which Neon Home shows once on a real TV: with the overlays on, only the Wi-Fi
# names are left to allow
adb shell am start -n "$PKG/com.lumera.app.control.ControlSetupActivity" >> "$OUT/overlay.txt" 2>&1
sleep 2.5
overlay_shot control-setup
adb shell input keyevent BACK
sleep 1

# The Control Center: a held Home opens it on a real remote; here the broadcast the Siri Remote
# bridge sends does. Each list page is opened straight away, with made-up headphones for the sound
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION >> "$OUT/overlay.txt" 2>&1
adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION >> "$OUT/overlay.txt" 2>&1
adb shell screenrecord --time-limit 5 --size 1280x720 --bit-rate 6000000 /sdcard/control.mp4 >> "$OUT/overlay.txt" 2>&1 &
recorder=$!
sleep 0.5
overlay control
sleep 2
overlay_on || fail "the Control Center did not open"
overlay_shot control-center
sleep 1
overlay off
wait "$recorder" 2>/dev/null || true
adb pull /sdcard/control.mp4 "$OUT/control-center.mp4" > /dev/null 2>&1 || true
sleep 1
overlay_gone control || fail "the Control Center did not go away"
adb shell dumpsys activity activities 2>/dev/null | grep -E "mResumedActivity|topResumedActivity" | grep -q "$PKG/$ACTIVITY" ||
  fail "the Control Center took the app out of front"
# adb shell joins its words into one line for the TV's shell, so the device list is quoted twice
adb shell am broadcast -a com.neontokyo.tv.OVERLAY --es show control:audio --es devices "'AirPods Pro,Erika AirPods,JBL Flip 6'" >/dev/null 2>&1
sleep 2
overlay_shot control-sound
for page in wifi profiles sleep; do
  overlay "control:$page"
  sleep 1.5
  overlay_shot "control-$page"
done
overlay off
sleep 1
overlay_gone control-list || fail "the Control Center did not go away from a list page"
adb shell settings put global animator_duration_scale 0
if [ -z "$overlay_before" ] || [ "$overlay_before" = null ]; then
  adb shell settings delete secure enabled_accessibility_services > /dev/null 2>&1
else
  adb shell settings put secure enabled_accessibility_services "$overlay_before"
fi
sleep 1

adb logcat -d -s NeonOverlay Dumps ControlCenter >> "$OUT/overlay.txt" 2>&1

if [ "$MODE" = full ]; then
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

# --- A second profile: the same addons, a history and watchlist of its own ----
menu_go Watchlist
sleep 5
shot watchlist-adrian
if screen_has "Nothing saved yet"; then fail "Adrian's watchlist lost the title he added"; fi
screen_has "WATCHLIST  ·  1 SERIES" || fail "Adrian's watchlist does not count his one series in its header"
menu_go "Profile Avatar"
if ! wait_for "watching" 20; then fail "Change Profile did not open the profile screen"; fi
sleep 2
shot profiles-again
# The photo profile offers to change its photo
key DPAD_DOWN
sleep 1
key DPAD_CENTER
sleep 2
screen_has "Change Photo" || fail "the options of a photo profile do not offer Change Photo"
shot profile-options
# One press of Back closes it, the way the remote is used
key BACK
sleep 2
if screen_has "Change Photo"; then
  fail "one press of Back did not close the profile options"
  key BACK
  sleep 2
fi
# Add Guest next to Adrian. The keyboard can open over the name step at once, and then the UI
# tree shows the keyboard instead, so an open keyboard counts as being on the name step too
key DPAD_RIGHT DPAD_CENTER
sleep 2
if screen_has "What should we call you" || adb shell dumpsys input_method | grep -q "mInputShown=true"; then
  adb shell input text "Guest"
  sleep 1
  key ENTER
  sleep 2
  if screen_has "Enter Name"; then key ENTER; sleep 2; fi
  key DPAD_CENTER
  sleep 2
  key DPAD_CENTER
  sleep 4
  shot profiles-two
  screen_has "GUEST" || fail "the Guest profile is missing on the profile screen"
  starter_before=$(grep -c "Starter catalog" "$OUT/logcat-full.txt")
  # Focus starts on Adrian; Guest is next to him
  key DPAD_RIGHT DPAD_CENTER
  if ! wait_for "Continue\|Popular\|Trending\|Top" 60; then fail "Guest's home shows no catalog rows"; fi
  sleep 6
  shot home-guest
  starter_after=$(grep -c "Starter catalog" "$OUT/logcat-full.txt")
  [ "$starter_after" = "$starter_before" ] || fail "Guest installed the starter catalog again instead of sharing it"
  menu_go Watchlist
  sleep 5
  shot watchlist-guest
  screen_has "Nothing saved yet" || fail "Guest sees a watchlist that is not his"
  # Back to Adrian: his watchlist is still there
  menu_go "Profile Avatar"
  if wait_for "watching" 20; then
    sleep 2
    key DPAD_CENTER
    sleep 10
    menu_go Watchlist
    sleep 5
    shot watchlist-adrian-again
    if screen_has "Nothing saved yet"; then fail "Adrian's watchlist was gone after Guest watched"; fi
  else
    fail "Change Profile did not open the profile screen from Guest"
  fi
else
  fail "Add Profile did not open the name step"
fi
fi

# --- Leaving: two quick presses of Back switch the app off like an old TV ------
menu_go Home
sleep 6
adb shell settings put global animator_duration_scale 1
adb shell screenrecord --time-limit 5 --size 1280x720 /sdcard/power-off.mp4 > "$OUT/power-off-record.txt" 2>&1 &
REC_PID=$!
sleep 1.5
app_in_front() {
  adb shell dumpsys activity activities 2>/dev/null | grep -E "mResumedActivity|topResumedActivity" | grep -q "$PKG/$ACTIVITY"
}
# Two input commands side by side: one command sending both waits for the app to finish with
# the first press before it sends the second, which on the software-drawn emulator can be
# later than the app's 700 ms for a double press, where a remote's two presses are not. Each
# command starts its own Java first, and on the emulator that alone can take them more than
# 700 ms apart, so a pair that came apart is sent again, up to three times
for attempt in 1 2 3; do
  adb shell 'input keyevent KEYCODE_BACK & input keyevent KEYCODE_BACK & wait'
  sleep 3
  app_in_front || break
  echo "double Back attempt $attempt: the presses came apart, sending them again"
  menu_go Home
  sleep 3
done
wait "$REC_PID" 2>/dev/null
adb shell settings put global animator_duration_scale 0
adb pull /sdcard/power-off.mp4 "$OUT/power-off.mp4" > /dev/null 2>&1 || true
shot after-power-off noalive
# Neon Home, still open from its own steps, may come back up behind: it is the same package
if app_in_front; then
  fail "two quick presses of Back did not close the app"
fi

# --- Siri Remote bridge: runs as the shell user, sees Bluetooth, injects keys --
# The emulator has no remote to pair, so this checks everything up to the remote itself
if [ -n "${SIRI_LIB:-}" ] && [ -f "$SIRI_LIB" ]; then
  adb shell mkdir -p /data/local/tmp/neon-siri
  adb push "$SIRI_LIB" /data/local/tmp/neon-siri/libneon_siri.so > /dev/null
  APK_PATH=$(adb shell pm path "$PKG" | head -1 | cut -d: -f2 | tr -d '\r')
  adb shell "CLASSPATH=$APK_PATH app_process /system/bin com.lumera.app.siri.SiriBridge $PKG check" > "$OUT/siri-check.txt" 2>&1
  cat "$OUT/siri-check.txt"
  grep -q "rust engine: loaded" "$OUT/siri-check.txt" || fail "Siri Remote bridge: the Rust engine did not load as shell"
  grep -q "package com.android.shell" "$OUT/siri-check.txt" || fail "Siri Remote bridge: does not speak for com.android.shell"
  grep -q "key injection: true" "$OUT/siri-check.txt" || fail "Siri Remote bridge: cannot inject keys"
  adb shell "CLASSPATH=$APK_PATH setsid app_process /system/bin com.lumera.app.siri.SiriBridge $PKG debug > /data/local/tmp/neon-siri.log 2>&1 < /dev/null &"
  sleep 4
  adb shell "echo status | nc -w 2 127.0.0.1 47391" > "$OUT/siri-status.txt" 2>&1 || true
  adb shell cat /data/local/tmp/neon-siri.log > "$OUT/siri-log.txt" 2>&1 || true
  cat "$OUT/siri-status.txt" "$OUT/siri-log.txt"
  grep -q '"version"' "$OUT/siri-status.txt" || fail "Siri Remote bridge: no status on 127.0.0.1:47391"
  adb shell "echo stop | nc -w 2 127.0.0.1 47391" > /dev/null 2>&1 || true
fi

# --- Results ----------------------------------------------------------------
adb shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo.txt" 2>&1
adb shell dumpsys meminfo "$PKG" > "$OUT/meminfo.txt" 2>&1
kill "$LOGCAT_PID" 2>/dev/null
sleep 1

# Only the app's own crashes count: the stock launcher can fall over while the test disables it
APP_PID_LINES=$( { grep -n -A1 "FATAL EXCEPTION" "$OUT/logcat-full.txt" | grep "Process: $PKG\b"; grep -n "ANR in $PKG" "$OUT/logcat-full.txt"; } || true)
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
