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
  grep -q "Photo for" "$OUT/photo-page.html" || fail "the photo page has no title"
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

# --- Neon Home: the home screen the TV setup script can turn on ---------------
# Nothing answers the Home button with it until the script turns its alias on
if adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | grep -q "$PKG"; then
  fail "Neon Home answers the Home button before the setup script turned it on"
fi
adb shell am start -n "$PKG/com.lumera.app.launcher.NeonHomeActivity" > "$OUT/neon-home-start.txt" 2>&1
sleep 8
shot neon-home
screen_has "WATCH &amp; LISTEN" || fail "Neon Home has no Watch and listen row"
screen_has "HDMI 6" || fail "Neon Home does not show the consoles with their HDMI numbers"
label=$(focused_label)
[ "$label" = "YouTube" ] || fail "Neon Home does not start on YouTube ($label has the focus)"
key DPAD_DOWN
sleep 1
label=$(focused_label)
[ "$label" = "PC" ] || fail "Down from YouTube did not reach the PC console ($label has the focus)"
shot neon-home-consoles
# A long press on a console asks which TV input it is on
adb shell input keyevent --longpress DPAD_CENTER
sleep 2
shot neon-home-link
screen_has "Which input is PC on" || fail "a long press on PC did not ask which input it is on"
key BACK
sleep 2
if screen_has "Which input is PC on"; then fail "Back did not close the input picker"; fi
# Up from the tiles lands on the open tab, and pointing at All apps opens it
key DPAD_UP DPAD_UP
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
# Back goes to the Home tab first, then leaves the preview
key BACK
sleep 2
label=$(focused_label)
[ "$label" = "YouTube" ] || fail "Back on All apps did not return to YouTube ($label has the focus)"
key BACK
sleep 3
shot neon-home-left noalive
if adb shell dumpsys activity activities 2>/dev/null | grep -E "mResumedActivity|topResumedActivity" | grep -q NeonHomeActivity; then
  fail "Back on the Home tab did not leave the Neon Home preview"
fi

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
if screen_has "Your watchlist is empty"; then fail "Adrian's watchlist lost the title he added"; fi
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
  screen_has "Your watchlist is empty" || fail "Guest sees a watchlist that is not his"
  # Back to Adrian: his watchlist is still there
  menu_go "Profile Avatar"
  if wait_for "watching" 20; then
    sleep 2
    key DPAD_CENTER
    sleep 10
    menu_go Watchlist
    sleep 5
    shot watchlist-adrian-again
    if screen_has "Your watchlist is empty"; then fail "Adrian's watchlist was gone after Guest watched"; fi
  else
    fail "Change Profile did not open the profile screen from Guest"
  fi
else
  fail "Add Profile did not open the name step"
fi

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
