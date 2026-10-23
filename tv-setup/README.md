# Neon Tokyo on the TV

`neon-tv.ps1` tunes a TCL Google TV from a Windows PC on the same network, over ADB.

What it does:

- Turns off bloat that nothing depends on: TCL Safety Guard, TCL browser, TCL Channel, TCL Home, Google feedback and Play Games. Apps are only disabled, never uninstalled.
- Makes Projectivy Launcher the home screen and turns off the Google TV home screen, which is where the ads come from.
- Speeds up menus: window and screen transitions run at 0.5x. Animations inside apps keep their normal speed.
- Copies the Neon Tokyo wallpaper, banners and icons to `Pictures/NeonTokyo` on the TV: a neon banner and icon for every app, with just its name, and HDMI 1–6 on the consoles (PC, Switch 2, Xbox 360, Wii U, Wii, Xbox).
- Installs the latest Neon Tokyo release (0.2 or newer).
- Makes *Neon Tokyo night* the screensaver: the skyline with its grid rolling towards you, a neon clock, twinkling stars and the odd shooting star. Google TV only lists its own Ambient mode, so this can only be picked over ADB.
- Restarts the TV and checks that it boots straight into Projectivy.

In Projectivy, pick **Settings → Appearance → Wallpaper → Neon Tokyo** and the skyline wallpaper takes the colour of the app you point at: red for YouTube and Netflix, green for Spotify and Xbox, violet for Stremio, blue for HDMI inputs. When the TV sits idle the grid starts rolling.

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

You can also run one step at a time with `-Step connect|inventory|debloat|launcher|tune|art|app|dream|restart|report`.
If the TV isn't found automatically, add `-Ip 192.168.x.x`. Its IP is under Settings → Network & Internet.
If the TV gets a new address later, the script notices and looks for it again.

A TV that offers **Wireless debugging** instead: open *Pair device with pairing code* on the TV and run once with
`-Pair 192.168.x.x:port -Code 123456`, using the address and code it shows.

## Undo

```powershell
powershell -ExecutionPolicy Bypass -File $env:USERPROFILE\NeonTV\neon-tv.ps1 -Step undo
```

This turns everything back on, puts the animation speeds and the screensaver back, and gives the Home button back to the Google TV home screen. The TV's original state, including its package list, a screenshot and its memory use, is saved in `%USERPROFILE%\NeonTV\before`.
