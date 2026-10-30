#!/usr/bin/env bash
# The Siri Remote end to end on an Android TV 14 emulator (TV 1 runs Android TV 14), with a
# pretend remote: the bridge runs as the shell user exactly as on the TV, plays scripted reports
# (siri-sim.py) and injects the keys; the voice part goes through Neon Tokyo's voice screen, the
# TV's Opus decoder and a stand-in speech recognizer that checks the sound it gets.
#
#   siri-test.sh <test apk> <x86 libneon_siri.so> <out dir>
set -u
APK=$1
LIB=$2
OUT=$3
PKG=com.neontokyo.tv
SIM=/data/local/tmp/siri-sim
mkdir -p "$OUT"
: > "$OUT/FAILURES.txt"
fail() { echo "FAIL  $*"; echo "$*" >> "$OUT/FAILURES.txt"; }
ok() { echo "ok    $*"; }
send() { adb shell "echo '$1' | nc -w 2 127.0.0.1 47391" 2>&1 | tr -d '\r'; }
bridge_pid() { adb shell "pgrep -f '^app_process /system/bin com.lumera.app.siri.SiriBridge'" 2>/dev/null | tr -d '\r' | head -1; }
# the length of a script, in seconds, plus some slack
length() { awk 'END { print int($1 / 1000) + 3 }' "$1"; }

adb wait-for-device
adb shell getprop ro.build.version.release | tr -d '\r' | sed 's/^/Android /'
adb logcat -c

# --- what the test needs on the emulator ------------------------------------
adb install -r -g "$APK" > /dev/null || { fail "Neon Tokyo did not install"; exit 1; }
adb install -r -g siritest.apk > /dev/null || { fail "the test recognizer did not install"; exit 1; }
adb shell settings put secure voice_recognition_service com.neontokyo.siritest/.FakeRecognizer
# the TV's recognizer is a system app that may use the microphone from the background; a plain
# app only while it is in use, so the stand-in gets the same standing (Neon Tokyo's own stays as is)
adb shell appops set com.neontokyo.siritest RECORD_AUDIO allow
adb shell mkdir -p /data/local/tmp/neon-siri $SIM
adb push "$LIB" /data/local/tmp/neon-siri/libneon_siri.so > /dev/null
python3 .github/scripts/siri-sim.py make sim > /dev/null || { fail "could not make the pretend remote's scripts"; exit 1; }
adb push sim/. $SIM/ > /dev/null
APK_PATH=$(adb shell pm path $PKG | head -1 | cut -d: -f2 | tr -d '\r')
# the voice screen asks for this on the TV the first time; the test grants it beforehand
adb shell pm grant $PKG android.permission.RECORD_AUDIO
adb shell am start -n $PKG/com.lumera.app.MainActivity > /dev/null
sleep 6

# --- what Android 14 lets the shell user do ---------------------------------
adb shell "CLASSPATH=$APK_PATH app_process /system/bin com.lumera.app.siri.SiriBridge $PKG check" > "$OUT/check.txt" 2>&1
cat "$OUT/check.txt"
for want in "BLUETOOTH_PRIVILEGED: yes" "BLUETOOTH_CONNECT: yes" "BLUETOOTH_SCAN: yes" "key injection: true" "rust engine: loaded" "package com.android.shell" "wake lock: yes"; do
  grep -q "$want" "$OUT/check.txt" && ok "check: $want" || fail "check: wanted '$want'"
done

# --- the bridge, started as the TV setup script starts it -------------------
LAUNCH="cd /data/local/tmp; setsid sh -c 'while :; do CLASSPATH=$APK_PATH app_process /system/bin com.lumera.app.siri.SiriBridge $PKG sim; c=\$?; [ \$c -eq 3 ] || [ \$c -eq 4 ] && break; echo bridge exited \$c, restarting; sleep 2; done' > /data/local/tmp/neon-siri.log 2>&1 < /dev/null &"
timeout 30 adb shell "$LAUNCH" || fail "starting the bridge did not return (the start command holds adb open)"
sleep 4
grep -q running <(adb shell cat /data/local/tmp/neon-siri.log) && ok "bridge runs" || fail "bridge did not start: $(adb shell cat /data/local/tmp/neon-siri.log)"
send status > "$OUT/status.txt"
grep -q '"version"' "$OUT/status.txt" && ok "status answers" || fail "no status: $(cat "$OUT/status.txt")"

# a second start must leave the one bridge running, not a pile of them
timeout 30 adb shell "$LAUNCH" || fail "starting the bridge did not return (the start command holds adb open)"
sleep 4
n=$(adb shell "pgrep -f '^app_process /system/bin com.lumera.app.siri.SiriBridge'" | wc -l)
[ "$n" -eq 1 ] && ok "starting twice leaves one bridge" || fail "$n bridges after starting twice"

# --- the pretend remote -----------------------------------------------------
reply=$(send "play $SIM/remote.sim")
[ "$reply" = ok ] || fail "play: $reply"
secs=$(length sim/remote.sim)
echo "playing the pretend remote for $secs s"
sleep "$secs"
sleep 6 # the voice screen hears, decides and opens the app
adb shell cat /data/local/tmp/neon-siri.log > "$OUT/bridge.log"
python3 .github/scripts/siri-sim.py check "$OUT/bridge.log" remote | tee "$OUT/remote-check.txt"
[ "${PIPESTATUS[0]}" -eq 0 ] || fail "the pretend remote's buttons and touch (see remote-check.txt)"

# --- voice: remote microphone -> Opus decoder -> 16 kHz -> recognizer -> app opens -----------
adb logcat -d -s NeonSiri:V NeonSiriTest:V > "$OUT/voice.log"
cat "$OUT/voice.log"
frames=$(sed -n 's/.*voice: \([0-9]*\) frames.*/\1/p' "$OUT/voice.log" | tail -1)
rms=$(sed -n 's/.*voice: .* rms \([0-9]*\),.*/\1/p' "$OUT/voice.log" | tail -1)
[ "${frames:-0}" -ge 75 ] && ok "voice: $frames frames reached the voice screen" || fail "voice: only ${frames:-0} of 80 frames reached the voice screen"
[ "${rms:-0}" -ge 1500 ] && ok "voice: decoded sound is loud enough (rms $rms)" || fail "voice: decoded sound too quiet (rms ${rms:-0}), the decoder did not work"
grep -q "fake recognizer: listening, source true, 16000 Hz, 1 ch" "$OUT/voice.log" && ok "voice: the recognizer got the sound at 16 kHz mono" || fail "voice: the recognizer did not get the sound as it expects"
grep -q "voice heard: öppna Hikari" "$OUT/voice.log" && ok "voice: the tone came through clean (440 Hz)" || fail "voice: the sound arrived changed: $(grep 'fake recognizer: [0-9]' "$OUT/voice.log")"
grep -q "voice action: OpenApp(packageName=com.neontokyo.siritest" "$OUT/voice.log" && ok "voice: 'öppna Hikari' opens the app" || fail "voice: the command was not understood"
top=$(adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | tr -d '\r')
echo "$top" | grep -q "com.neontokyo.siritest/.Hikari" && ok "voice: Hikari is on screen" || fail "voice: Hikari did not open ($top)"
adb exec-out screencap -p > "$OUT/after-voice.png"
# whose microphone permission Android checked, should the recognizer have said no
adb logcat -d | grep -iE "RecognitionService|without permission|AppOps.*(RECORD_AUDIO|siritest|neontokyo)|PermissionChecker" > "$OUT/voice-permissions.txt"
grep -q "voice recognizer error" "$OUT/voice.log" && cat "$OUT/voice-permissions.txt"

# --- Power: off, and on again from the remote --------------------------------
wakefulness() { adb shell dumpsys power | grep -m1 -o "mWakefulness=[A-Za-z]*" | cut -d= -f2; }
send "play $SIM/power-off.sim" > /dev/null
sleep "$(length sim/power-off.sim)"
sleep 2
w=$(wakefulness)
[ "$w" != Awake ] && ok "Power turns the TV off ($w)" || fail "Power did not turn the TV off"
pid=$(bridge_pid)
[ -n "$pid" ] && ok "the bridge lives on with the screen off" || fail "the bridge died when the screen went off"
send "play $SIM/power-on.sim" > /dev/null
sleep "$(length sim/power-on.sim)"
sleep 2
w=$(wakefulness)
[ "$w" = Awake ] && ok "Power turns the TV on again" || fail "Power did not wake the TV ($w)"
adb shell cat /data/local/tmp/neon-siri.log > "$OUT/bridge.log"
python3 .github/scripts/siri-sim.py check "$OUT/bridge.log" power-off || fail "power-off log"
python3 .github/scripts/siri-sim.py check "$OUT/bridge.log" power-on || fail "power-on log"

# --- a crash brings it back, a stop does not ---------------------------------
pid=$(bridge_pid)
adb shell kill -9 "$pid"
sleep 5
new=$(bridge_pid)
[ -n "$new" ] && [ "$new" != "$pid" ] && ok "a crashed bridge comes back by itself" || fail "the bridge stayed dead after a crash"
send status | grep -q '"version"' && ok "and answers again" || fail "the restarted bridge does not answer"
send stop > /dev/null
sleep 4
[ -z "$(bridge_pid)" ] && ok "stop ends it for good" || fail "the bridge came back after stop"
adb shell cat /data/local/tmp/neon-siri.log > "$OUT/bridge.log"

adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  grep -A30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80
  grep -A2 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG" && fail "Neon Tokyo crashed"
fi

if [ -s "$OUT/FAILURES.txt" ]; then
  echo; echo "Siri Remote test failed:"; cat "$OUT/FAILURES.txt"
  exit 1
fi
echo "Siri Remote test passed"
