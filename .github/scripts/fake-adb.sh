#!/usr/bin/env bash
# Stand-in for adb that behaves like a TCL Google TV, for testing tv-setup/neon-tv.ps1 without one.
# State lives in $FAKE_ADB_STATE: installed and disabled packages, settings, the Home activity.
# Set FAKE_ADB_MDNS to an ip:port to have the TV announce itself over mDNS.
ST="${FAKE_ADB_STATE:?set FAKE_ADB_STATE}"
mkdir -p "$ST"
touch "$ST/disabled" "$ST/settings"
if [ ! -s "$ST/installed" ]; then
  printf '%s\n' com.tcl.tv com.tcl.tvinput com.tcl.guard com.tcl.browser com.tcl.dashboard \
    com.google.android.apps.tv.launcherx com.google.android.videos com.google.android.tungsten.setupwraith \
    com.google.android.feedback com.google.android.gms com.neontokyo.tv > "$ST/installed"
  printf '%s\n' global/window_animation_scale=1.0 global/transition_animation_scale=1.0 \
    global/animator_duration_scale=1.0 secure/screensaver_components=com.google.android.apps.tv.dreamx/.service.Backdrop \
    secure/screensaver_enabled=1 > "$ST/settings"
fi
echo "ARGS: $*" >> "$ST/calls"

setting_get() { local v; v=$(grep -m1 "^$1/$2=" "$ST/settings" | cut -d= -f2-); echo "${v:-null}"; }
setting_put() { grep -v "^$1/$2=" "$ST/settings" > "$ST/s2"; echo "$1/$2=$3" >> "$ST/s2"; mv "$ST/s2" "$ST/settings"; }
setting_delete() { grep -v "^$1/$2=" "$ST/settings" > "$ST/s2"; mv "$ST/s2" "$ST/settings"; }
enabled() { grep -qx "$1" "$ST/installed" && ! grep -qx "$1" "$ST/disabled"; }

case "$1" in
  connect) echo "connected to $2"; exit 0 ;;
  pair) echo "Successfully paired to $2"; exit 0 ;;
  mdns)
    echo "List of discovered mdns services"
    [ -n "${FAKE_ADB_MDNS:-}" ] && printf 'adb-TCL75C849-a1b2c3\t_adb-tls-connect._tcp\t%s\n' "$FAKE_ADB_MDNS"
    exit 0 ;;
esac
[ "$1" = -s ] && shift 2
case "$1" in
  get-state) echo device ;;
  pull) touch "$3" ;;
  push|reboot) ;;
  install) echo Success ;;
  shell) shift; c="$*"
    case "$c" in
      "getprop ro.product.manufacturer"|"getprop ro.product.brand") echo TCL ;;
      "getprop ro.product.model") echo 75C849 ;;
      "getprop sys.boot_completed") echo 1 ;;
      "getprop | grep"*) echo "[ro.build.version.release]: [11]" ;;
      "pm path "*) p=${c#pm path }; grep -qx "$p" "$ST/installed" && echo "package:/system/app/$p.apk" ;;
      "pm list packages -d "*) p=${c#pm list packages -d }; grep -F "$p" "$ST/disabled" | sed 's/^/package:/' ;;
      "pm list packages -d") sed 's/^/package:/' "$ST/disabled" ;;
      "pm list packages"*) sed 's/^/package:/' "$ST/installed" ;;
      "pm disable-user --user 0 "*) p=${c#pm disable-user --user 0 }; echo "$p" >> "$ST/disabled"; echo "Package $p new state: disabled-user" ;;
      "pm enable "*) p=${c#pm enable }; grep -vx "$p" "$ST/disabled" > "$ST/d2"; mv "$ST/d2" "$ST/disabled"; echo "Package $p new state: enabled" ;;
      "pm trim-caches"*) ;;
      *"query-activities"*)
        enabled com.spocky.projengmenu && printf 'priority=0 preferredOrder=0\ncom.spocky.projengmenu/.ui.home.HomeActivity\n'
        enabled com.google.android.apps.tv.launcherx && printf 'priority=2\ncom.google.android.apps.tv.launcherx/.home.HomeActivity\n'
        enabled com.google.android.tungsten.setupwraith && printf 'priority=1\ncom.google.android.tungsten.setupwraith/.MainActivity\n'
        ;;
      *"query-services"*)
        enabled com.neontokyo.tv && printf 'com.neontokyo.tv/com.lumera.app.dream.NeonDreamService\n'
        printf 'com.google.android.apps.tv.dreamx/.service.Backdrop\n' ;;
      *"resolve-activity"*)
        if [ -f "$ST/home" ] && enabled "$(cut -d/ -f1 "$ST/home")"; then printf 'priority=0 preferredOrder=1\n%s\n' "$(cat "$ST/home")"
        elif enabled com.google.android.apps.tv.launcherx; then printf 'priority=2 preferredOrder=0\ncom.google.android.apps.tv.launcherx/.home.HomeActivity\n'
        else printf 'priority=0 preferredOrder=0\ncom.android.tv.settings/.MainSettings\n'; fi ;;
      "cmd package set-home-activity "*) echo "${c#cmd package set-home-activity }" > "$ST/home"; echo Success ;;
      "am start -a android.intent.action.VIEW"*) echo com.spocky.projengmenu >> "$ST/installed"; echo "Starting: Intent { market }" ;;
      "am start -n "*) echo "Starting: Intent { cmp=${c#am start -n } }" ;;
      "settings get "*) set -- $c; setting_get "$3" "$4" ;;
      "settings put "*) set -- $c; setting_put "$3" "$4" "$5" ;;
      "settings delete "*) set -- $c; setting_delete "$3" "$4" ;;
      *"MemTotal|MemAvailable"*) printf 'MemTotal: 2900000 kB\nMemAvailable: 900000 kB\n' ;;
      *"mCurrentFocus"*) echo "mCurrentFocus=Window{abc u0 $(cat "$ST/home" 2>/dev/null || echo launcher)}" ;;
      *) : ;;
    esac ;;
esac
