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
  adb shell rm -f /sdcard/window.xml
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
  adb shell dumpsys gfxinfo "$pkg" > "tv-review/$scenario-gfxinfo.txt"
done
# Exercise the real UI with D-pad events; never write profile preferences directly.
python3 - <<'PY_UI'
import json
import pathlib
import subprocess
import time
import xml.etree.ElementTree as ET

OUT = pathlib.Path("tv-review")
PKG = "com.neontokyo.tv.test"
ACTIVITY = PKG + "/com.lumera.app.launcher.NeonHomeActivity"
UP, DOWN, LEFT, RIGHT, OK, BACK = 19, 20, 21, 22, 23, 4
step = "starting"

def adb(*args):
    return subprocess.check_output(["adb", *map(str, args)], timeout=45)

def key(*codes):
    adb("shell", "input", "keyevent", *codes)
    time.sleep(0.3)

def tree():
    adb("shell", "rm", "-f", "/sdcard/review-window.xml")
    adb("shell", "uiautomator", "dump", "/sdcard/review-window.xml")
    raw = adb("exec-out", "cat", "/sdcard/review-window.xml")
    (OUT / "latest.xml").write_bytes(raw)
    return ET.fromstring(raw)

def labels(node):
    return {n.get(k) for n in node.iter("node") for k in ("text", "content-desc") if n.get(k)}

def focused(root=None):
    root = root if root is not None else tree()
    return [n for n in root.iter("node") if n.get("focused") == "true"]

def expect_focus(label):
    actual = [labels(n) for n in focused()]
    assert any(label in values for values in actual), (step, "focus", label, actual)

def expect_text(label):
    assert label in labels(tree()), (step, "missing text", label)

def seek(label, direction, limit=14):
    for _ in range(limit):
        if any(label in labels(n) for n in focused()):
            return
        key(direction)
    raise AssertionError((step, "D-pad target unreachable", label))

def capture(name):
    global step
    step = name
    root = tree()
    (OUT / (name + ".xml")).write_bytes(ET.tostring(root))
    (OUT / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
    resumed = adb("shell", "dumpsys", "activity", "activities").decode()
    assert any("mResumedActivity" in line and "NeonHomeActivity" in line for line in resumed.splitlines()), name
    print("PASS", name, "focus:", [sorted(labels(n)) for n in focused(root)], flush=True)

def launch(*extras):
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY, *extras)
    time.sleep(3)
    expect_focus("YouTube")

def profile():
    # From the first home tile, Up visits Home and then the profile toolbar pill.
    key(UP, UP)
    expect_focus("Neon")
    key(OK)
    expect_text("Who's home?")

def preferences():
    raw = adb("exec-out", "run-as", PKG, "cat", "shared_prefs/neon_home_profiles.xml")
    prefs = ET.fromstring(raw)
    profiles = json.loads(prefs.find("string[@name='profiles']").text)
    return next(p for p in profiles if p["id"] == "neon")

def saved(**expected):
    actual = preferences()
    assert all(actual.get(k) == v for k, v in expected.items()), (step, expected, actual)
    return actual

try:
    for glass in ("frost", "liquid"):
        step = "semantic-" + glass
        launch("--es", "style", "bluehour", "--es", "glass", glass)
        key(UP, RIGHT)
        expect_focus("All apps")
        key(OK)
        nodes = focused()
        assert nodes and all(not labels(n) & {"Home", "All apps", "Källor"} for n in nodes), "No app-grid focus"
        capture(step + "-apps")
        key(BACK)
        expect_focus("YouTube")
        key(UP, RIGHT, RIGHT)
        expect_focus("Källor")
        key(OK)
        nodes = focused()
        assert nodes and all("Källor" not in labels(n) for n in nodes), "No source-tile focus"
        capture("semantic-" + glass + "-sources")
        key(BACK)
        expect_focus("YouTube")
        profile()
        capture("semantic-" + glass + "-profiles")
        key(BACK)
        expect_focus("Neon")
        seek("Inputs", RIGHT)
        key(OK)
        expect_text("Inputs")
        capture("semantic-" + glass + "-inputs")
        key(BACK)
        expect_focus("Inputs")
        seek("Motion", RIGHT)
        key(OK)
        expect_text("TILES")
        capture("semantic-" + glass + "-motion")
        key(BACK)
        expect_focus("Motion")
        capture("semantic-" + glass + "-restored")

    step = "profile-persistence"
    launch("--es", "style", "bluehour")
    profile()
    seek("Customize", DOWN, 4)
    key(OK)
    expect_focus("Neon")
    seek("Blue Hour", RIGHT)
    key(OK)
    saved(style="bluehour")
    # Start at the left edge of the style row before entering the glass row.
    seek("Neon", LEFT)
    key(DOWN)
    seek("Off", LEFT, 5)
    seek("Liquid glass", RIGHT, 5)
    key(OK)
    saved(style="bluehour", glass="liquid")
    key(UP)
    seek("Neon", LEFT)
    seek("Deep Blue", RIGHT)
    key(OK)
    p = saved(style="deepblue")
    assert p["looks"]["bluehour"]["glass"] == "liquid", "Lost per-style glass"
    seek("Blue Hour", RIGHT)
    key(OK)
    saved(style="bluehour", glass="liquid")
    capture("profile-bluehour-liquid-saved")
    key(BACK)
    expect_text("Who's home?")
    key(BACK)
    expect_focus("Neon")
    # No style or glass extras: the fresh process must load the saved profile.
    launch()
    saved(style="bluehour", glass="liquid")
    capture("profile-bluehour-liquid-restarted")
    profile()
    expect_text("Blue Hour")
    capture("profile-persisted-dialog")
    key(BACK)
    expect_focus("Neon")
    (OUT / "semantic-result.txt").write_text("PASS: D-pad tab entry, Back-to-Home, profile/input/motion focus restoration in Frost and Liquid; UI-driven style switch, per-style glass restoration and process-restart persistence.\n")
except Exception:
    (OUT / "semantic-failure-step.txt").write_text(step)
    (OUT / "semantic-failure.png").write_bytes(adb("exec-out", "screencap", "-p"))
    raise
PY_UI
adb logcat -b crash -d > tv-review/crashes.txt
if grep -q "$pkg" tv-review/crashes.txt; then
  cat tv-review/crashes.txt
  exit 1
fi
printf '%s\n' 'Navigation/liveness checks passed. Inspect PNG/XML evidence before visual sign-off.' > tv-review/result.txt
