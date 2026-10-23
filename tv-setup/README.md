# Neon Tokyo on the TV

`neon-tv.ps1` tunes a TCL Google TV from a Windows PC on the same network, over ADB.

What it does:

- Turns off bloat that nothing depends on: TCL Safety Guard, TCL browser, TCL Channel, TCL Home, Google feedback and Play Games. Apps are only disabled, never uninstalled.
- Makes Projectivy Launcher the home screen and turns off the Google TV home screen, which is where the ads come from.
- Speeds up menus: window and screen transitions run at 0.5x. Animations inside apps keep their normal speed.
- Copies the Neon Tokyo wallpaper, banners and icons to `Pictures/NeonTokyo` on the TV.
- Installs the latest Neon Tokyo release (0.2 or newer).
- Restarts the TV and checks that it boots straight into Projectivy.

It never touches inputs/HDMI, the remote, Wi-Fi, system updates, Play Store/Play Services, voice search or Chromecast.

## Before you start (on the TV)

1. Settings → System → About → press OK 7 times on **Android TV OS build**.
2. Settings → System → Developer options → turn on **USB debugging**.
3. When the PC connects, the TV asks *Allow USB debugging?* Tick **Always allow** and press **OK**.

## Run

```powershell
mkdir $env:USERPROFILE\NeonTV -Force
iwr https://raw.githubusercontent.com/addebananen/neon-tokyo-tv/dev/tv-setup/neon-tv.ps1 -OutFile $env:USERPROFILE\NeonTV\neon-tv.ps1
powershell -ExecutionPolicy Bypass -File $env:USERPROFILE\NeonTV\neon-tv.ps1
```

You can also run one step at a time with `-Step connect|inventory|debloat|launcher|tune|art|app|restart|report`.
If the TV isn't found automatically, add `-Ip 192.168.x.x`. Its IP is under Settings → Network & Internet.

## Undo

```powershell
powershell -ExecutionPolicy Bypass -File $env:USERPROFILE\NeonTV\neon-tv.ps1 -Step undo
```

This turns everything back on and puts the animation speeds back. The TV's original state, including its package list, a screenshot and its memory use, is saved in `%USERPROFILE%\NeonTV\before`.
