# Neon Tokyo on the TV

`neon-tv.ps1` tunes a TCL Google TV from a Windows PC on the same network, over ADB.

What it does:

- Turns off bloat that nothing depends on: TCL Safety Guard, TCL browser, TCL Channel, TCL Home, Google feedback and Play Games. Apps are only disabled, never uninstalled.
- Makes Neon Home the home screen, or Projectivy Launcher if you'd rather, and turns off the Google TV home screen, which is where the ads come from.
- Speeds up menus: window and screen transitions run at 0.5x. Animations inside apps keep their normal speed.
- Copies the Neon Tokyo wallpaper, banners and icons to `Pictures/NeonTokyo` on the TV: a neon banner and icon for every app, with just its name, and HDMI 1–6 on the consoles (PC, Switch 2, Xbox 360, Wii U, Wii, Xbox).
- Installs the latest Neon Tokyo release (0.2 or newer).
- Makes *Neon Tokyo night* the screensaver: the skyline with its grid rolling towards you, a neon clock, twinkling stars and the odd shooting star. Google TV only lists its own Ambient mode, so this can only be picked over ADB.
- Restarts the TV and checks that it boots straight into the new home screen.

### Neon Home, or Projectivy

Neon Tokyo 0.2 has its own home screen, **Neon Home**, and the script makes it the one the Home button opens: the clock, big tiles for YouTube, YT Music, Spotify and Stremio that come alive when you point at them, the consoles labelled HDMI 1–6 and an All apps tab with a neon icon for every app. A console that is switched on gets a glowing outline and a small white star in its corner; hold OK on a console to pick which HDMI input it is on.

Projectivy Launcher instead, and back again:

```powershell
powershell -ExecutionPolicy Bypass -File $env:USERPROFILE\NeonTV\neon-tv.ps1 -Step launcher -HomeScreen projectivy
powershell -ExecutionPolicy Bypass -File $env:USERPROFILE\NeonTV\neon-tv.ps1 -Step launcher -HomeScreen neon
```

A Neon Tokyo older than 0.2 has no Neon Home, so the script puts Projectivy on the Home button instead.

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

You can also run one step at a time with `-Step connect|inventory|debloat|launcher|tune|art|app|dream|restart|report`,
and pick the home screen with `-HomeScreen neon|projectivy` (Neon Home unless you say otherwise).
If the TV isn't found automatically, add `-Ip 192.168.x.x`. Its IP is under Settings → Network & Internet.
If the TV gets a new address later, the script notices and looks for it again.

A TV that offers **Wireless debugging** instead: open *Pair device with pairing code* on the TV and run once with
`-Pair 192.168.x.x:port -Code 123456`, using the address and code it shows.

## Undo

```powershell
powershell -ExecutionPolicy Bypass -File $env:USERPROFILE\NeonTV\neon-tv.ps1 -Step undo
```

This turns everything back on, puts the animation speeds and the screensaver back, and gives the Home button back to the Google TV home screen. The TV's original state, including its package list, a screenshot and its memory use, is saved in `%USERPROFILE%\NeonTV\before`.
