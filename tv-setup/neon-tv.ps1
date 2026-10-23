<#
Neon Tokyo setup for a TCL Google TV, run from a Windows PC on the same network.

  powershell -ExecutionPolicy Bypass -File neon-tv.ps1              # everything
  powershell -ExecutionPolicy Bypass -File neon-tv.ps1 -Step undo   # put it all back
  powershell -ExecutionPolicy Bypass -File neon-tv.ps1 -Step launcher -HomeScreen neon   # Neon Home on the Home button

Steps: connect, inventory, debloat, launcher, tune, art, app, dream, restart, report, undo.
Nothing is uninstalled: apps are only disabled, and every change is written to
%USERPROFILE%\NeonTV\undo.txt so -Step undo can reverse it. A factory reset also
restores everything.

TVs with "Wireless debugging" instead of network ADB on port 5555: pair once with
  neon-tv.ps1 -Pair 192.168.1.23:37000 -Code 123456
using the address and code the TV shows under "Pair device with pairing code".
#>
param(
    [ValidateSet('all', 'connect', 'inventory', 'debloat', 'launcher', 'tune', 'art', 'app', 'dream', 'restart', 'report', 'undo')]
    [string]$Step = 'all',
    # What the Home button opens: Neon Home, the home screen in the Neon Tokyo app, or Projectivy Launcher
    [ValidateSet('neon', 'projectivy')]
    [string]$HomeScreen = 'projectivy',
    [string]$Ip = '',
    [string]$Pair = '',
    [string]$Code = '',
    [switch]$AnyDevice
)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Work = Join-Path $env:USERPROFILE 'NeonTV'
$Before = Join-Path $Work 'before'
$UndoFile = Join-Path $Work 'undo.txt'
$IpFile = Join-Path $Work 'tv-ip.txt'
$Raw = 'https://raw.githubusercontent.com/addebananen/neon-tokyo-tv'
$Projectivy = 'com.spocky.projengmenu'
$NeonApp = 'com.neontokyo.tv'
$NeonDream = "$NeonApp/com.lumera.app.dream.NeonDreamService"
$NeonHome = "$NeonApp/$NeonApp.NeonHome"
$NeonHomeSwitch = "$NeonApp/com.lumera.app.launcher.NeonHomeSwitch"
New-Item -ItemType Directory -Force -Path $Work | Out-Null

# Never disabled, whatever a list says: inputs, remote, Wi-Fi, updates, Play, voice, casting.
$Protected = @(
    'com.tcl.tv', 'com.tcl.tvinput', 'com.tcl.suspension', 'com.tcl.ttvs',
    'com.tcl.providers.config', 'com.tcl.initsetup', 'com.tcl.tcl_bt_rcu_service', 'com.tcl.autopair',
    'com.google.android.tv.remote.service', 'com.google.android.gms', 'com.google.android.gsf',
    'com.android.vending', 'com.android.location.fused', 'com.google.android.katniss',
    'com.google.android.apps.mediashell', $Projectivy, $NeonApp
)

# Bloat nothing depends on. Each one is disabled only if this TV has it.
$Bloat = @(
    'com.tcl.guard',                    # Safety Guard, keeps the CPU busy in the background
    'com.tcl.browser',                  # TCL browser
    'com.tcl.waterfall.overseas',       # TCL Channel, ad-funded
    'com.tcl.tv.tclhome_passive',       # TCL Home smart home
    'com.tcl.dashboard',                # TCL Home dashboard
    'com.google.android.feedback',      # feedback reports
    'com.google.android.play.games'     # Play Games
)

# The Google TV home screen and its shop: where the ads live. Only once Projectivy is in.
$StockHome = @('com.google.android.apps.tv.launcherx', 'com.google.android.videos')
$SetupHome = 'com.google.android.tungsten.setupwraith'

function Say([string]$text) {
    Write-Host '[NeonTV] ' -ForegroundColor Magenta -NoNewline
    Write-Host $text
}

function Banner {
    Write-Host ''
    Write-Host '  N E O N   T O K Y O' -ForegroundColor Magenta -NoNewline
    Write-Host '   //   Google TV setup' -ForegroundColor Cyan
    Write-Host '  ----------------------------------------------' -ForegroundColor DarkMagenta
    Write-Host ''
}

function Ensure-Adb {
    $onPath = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($onPath) { $script:AdbExe = $onPath.Source; return }
    $script:AdbExe = Join-Path $env:USERPROFILE 'platform-tools\adb.exe'
    if (Test-Path $script:AdbExe) { return }
    Say 'Downloading Android platform-tools from Google...'
    $zip = Join-Path $env:TEMP 'platform-tools.zip'
    Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip -UseBasicParsing
    Expand-Archive $zip -DestinationPath $env:USERPROFILE -Force
    Remove-Item $zip
}

function Adb { & $script:AdbExe -s $script:Serial @args 2>&1 | Out-String }
function Shell([string]$cmd) { (& $script:AdbExe -s $script:Serial shell $cmd 2>$null | Out-String).Trim() }
function Lines([string]$text) { @($text -split '\r?\n' | ForEach-Object { $_.Trim() } | Where-Object { $_ }) }

function Find-TvCandidates {
    $found = @()
    $nets = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' }
    foreach ($n in $nets) {
        $base = ($n.IPAddress -split '\.')[0..2] -join '.'
        $probes = @{}
        foreach ($i in 1..254) {
            $addr = "$base.$i"
            if ($addr -eq $n.IPAddress) { continue }
            $client = New-Object System.Net.Sockets.TcpClient
            $probes[$addr] = @($client, $client.ConnectAsync($addr, 5555))
        }
        Start-Sleep -Milliseconds 2000
        foreach ($addr in $probes.Keys) {
            $client, $task = $probes[$addr]
            if ($task.Status -eq 'RanToCompletion' -and $client.Connected) { $found += $addr }
            $client.Dispose()
        }
    }
    @($found | Sort-Object -Unique)
}

# "192.168.1.23" or "192.168.1.23:5555" -> "192.168.1.23:5555"
function With-Port([string]$address) { if ($address -match ':\d+$') { $address } else { "${address}:5555" } }

function Test-Port([string]$address) {
    $hostName, $port = (With-Port $address) -split ':'
    $client = New-Object System.Net.Sockets.TcpClient
    try { $client.ConnectAsync($hostName, [int]$port).Wait(1500) -and $client.Connected }
    catch { $false }
    finally { $client.Dispose() }
}

# TVs with Wireless debugging announce themselves; classic network ADB on 5555 usually doesn't
function Get-MdnsCandidates {
    $out = & $script:AdbExe mdns services 2>$null | Out-String
    @(Lines $out | Where-Object { $_ -match '_adb-tls-connect|_adb\._tcp' } |
        ForEach-Object { [regex]::Match($_, '\d{1,3}(\.\d{1,3}){3}:\d{2,5}').Value } |
        Where-Object { $_ } | Sort-Object -Unique)
}

function Connect-Tv {
    Ensure-Adb
    if ($Pair) {
        if (-not $Code) { throw 'Add the six-digit pairing code the TV shows: -Code 123456' }
        Say "Pairing with $Pair..."
        Say ((& $script:AdbExe pair $Pair $Code 2>&1 | Out-String).Trim())
        Start-Sleep 2
    }
    $candidates = @()
    if ($Ip) { $candidates = @($Ip) }
    elseif (Test-Path $IpFile) {
        $saved = (Get-Content $IpFile -Raw).Trim()
        if ($saved -and (Test-Port $saved)) { $candidates = @($saved) }
        elseif ($saved) { Say "The TV is no longer answering at $saved. Looking for it again." }
    }
    if ($candidates.Count -eq 0) { $candidates = Get-MdnsCandidates }
    if ($candidates.Count -eq 0) {
        Say 'Looking for the TV on your network (port 5555)...'
        $candidates = Find-TvCandidates
    }
    if ($candidates.Count -eq 0) {
        throw 'No TV found. On the TV, turn on Settings > System > Developer options > USB debugging, then run again (or pass -Ip with the TV''s address).'
    }
    foreach ($c in $candidates) {
        $script:Serial = With-Port $c
        & $script:AdbExe connect $script:Serial | Out-Null
        Say "Connecting to $($script:Serial). If the TV asks 'Allow USB debugging?', tick 'Always allow' and press OK."
        $state = ''
        for ($s = 0; $s -lt 120; $s += 3) {
            $state = (& $script:AdbExe -s $script:Serial get-state 2>$null | Out-String).Trim()
            if ($state -eq 'device') { break }
            if ($s % 15 -eq 0) { & $script:AdbExe connect $script:Serial | Out-Null }
            Start-Sleep 3
        }
        if ($state -ne 'device') { Say "No answer from $c."; continue }
        $who = (Shell 'getprop ro.product.manufacturer') + ' ' + (Shell 'getprop ro.product.brand') + ' ' + (Shell 'getprop ro.product.model')
        if (-not $AnyDevice -and $who -notmatch 'TCL') { Say "Skipping $c ($who): not a TCL TV."; continue }
        Set-Content $IpFile $script:Serial
        Say "Connected: $who at $($script:Serial)"
        return
    }
    throw 'Could not connect to the TV. Accept the prompt on the TV and run again.'
}

function Save-Inventory {
    if (Test-Path (Join-Path $Before 'settings.txt')) { Say 'The original state is already saved.'; return }
    New-Item -ItemType Directory -Force -Path $Before | Out-Null
    Shell 'pm list packages -s' | Set-Content (Join-Path $Before 'packages-system.txt')
    Shell 'pm list packages -3' | Set-Content (Join-Path $Before 'packages-installed.txt')
    Shell 'pm list packages -d' | Set-Content (Join-Path $Before 'packages-disabled.txt')
    Shell "getprop | grep -E 'ro.product|ro.build.version|ro.build.display'" | Set-Content (Join-Path $Before 'device.txt')
    Shell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | Set-Content (Join-Path $Before 'home.txt')
    Shell "grep -E 'MemTotal|MemAvailable' /proc/meminfo" | Set-Content (Join-Path $Before 'memory.txt')
    $settings = foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') {
        "$k=$(Shell "settings get global $k")"
    }
    $settings | Set-Content (Join-Path $Before 'settings.txt')
    Shell 'screencap -p /sdcard/neontv-before.png' | Out-Null
    Adb pull /sdcard/neontv-before.png (Join-Path $Before 'screen.png') | Out-Null
    Shell 'rm /sdcard/neontv-before.png' | Out-Null
    Say "Saved the TV's original state in $Before"
}

function Disable-Package([string]$pkg) {
    if ($Protected -contains $pkg) { Say "Leaving $pkg alone (protected)."; return }
    if (-not (Shell "pm path $pkg")) { return }
    if ((Lines (Shell "pm list packages -d $pkg")) -contains "package:$pkg") { Say "${pkg}: already off"; return }
    $out = Shell "pm disable-user --user 0 $pkg"
    Say "${pkg}: $out"
    if ($out -match 'disabled') { Add-Content $UndoFile "enable $pkg" }
}

function Remove-Bloat {
    Say 'Turning off bloat...'
    foreach ($p in $Bloat) { Disable-Package $p }
}

function Get-Homes { Shell 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' }

function Get-ProjectivyHome {
    $m = [regex]::Match((Get-Homes), [regex]::Escape($Projectivy) + '/\S+')
    if ($m.Success) { $m.Value } else { '' }
}

# Android lets only the Neon Tokyo app turn its own Home button entry on and off, so the script
# asks it to. It answers "neon" or "off"; a Neon Tokyo without Neon Home answers nothing.
function Switch-NeonHome([string]$state) {
    if (-not (Shell "pm path $NeonApp")) { return '' }
    $out = Shell "am broadcast --include-stopped-packages -n $NeonHomeSwitch --es home $state"
    $m = [regex]::Match($out, 'data="(\w+)"')
    if ($m.Success) { $m.Groups[1].Value } else { '' }
}

function Set-HomeActivity([string]$activity, [string]$pkg) {
    Say (Shell "cmd package set-home-activity $activity")
    $resolved = Shell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
    if ($resolved -notmatch [regex]::Escape($pkg)) {
        Say 'Something else still claims the Home button. Turning off the Google TV setup screen as well.'
        Disable-Package $SetupHome
        Shell "cmd package set-home-activity $activity" | Out-Null
    }
}

function Set-NeonHome {
    if ((Switch-NeonHome 'neon') -ne 'neon') {
        Say 'The Neon Tokyo on the TV has no Neon Home yet (it comes with 0.2), so Projectivy is the home screen for now.'
        return $false
    }
    foreach ($p in $StockHome) { Disable-Package $p }
    Say "Making Neon Home the home screen ($NeonHome)..."
    Set-HomeActivity $NeonHome $NeonApp
    if (Shell "pm path $Projectivy") { Say 'Projectivy stays installed as the spare: -HomeScreen projectivy switches back.' }
    Shell 'input keyevent KEYCODE_HOME' | Out-Null
    return $true
}

function Set-Launcher {
    if ($HomeScreen -eq 'neon' -and (Set-NeonHome)) { return }
    if (-not (Shell "pm path $Projectivy")) {
        Say 'Opening Projectivy Launcher in the Play Store on the TV: press Install with the remote.'
        Shell "am start -a android.intent.action.VIEW -d 'market://details?id=$Projectivy'" | Out-Null
        for ($s = 0; $s -lt 600; $s += 5) {
            if (Shell "pm path $Projectivy") { break }
            Start-Sleep 5
        }
        if (-not (Shell "pm path $Projectivy")) { throw 'Projectivy is not installed yet. Install it from the Play Store and run again.' }
    }
    $homeActivity = Get-ProjectivyHome
    if (-not $homeActivity) { throw 'Projectivy has no home screen activity on this TV. Stopping before the stock home screen is touched.' }
    Say 'Projectivy is installed. Opening it once so it can set itself up...'
    Say (Shell "am start -n $homeActivity")
    Start-Sleep 8
    foreach ($p in $StockHome) { Disable-Package $p }
    Say "Making Projectivy the home screen ($homeActivity)..."
    Set-HomeActivity $homeActivity $Projectivy
    # Back from Neon Home: its Home button entry goes off again, the app keeps the screen itself
    if ((Get-Homes) -match [regex]::Escape($NeonHome)) {
        Switch-NeonHome 'off' | Out-Null
        Say 'Neon Home is no longer the home screen. Settings > Personalization in Neon Tokyo still opens it.'
    }
    Shell 'input keyevent KEYCODE_HOME' | Out-Null
}

function Set-Tuning {
    Say 'Faster menus: window and screen transitions at 0.5x.'
    Shell 'settings put global window_animation_scale 0.5' | Out-Null
    Shell 'settings put global transition_animation_scale 0.5' | Out-Null
    # animator_duration_scale stays as it is: it would also halve the animations inside apps.
    Say 'Clearing app caches...'
    Shell 'pm trim-caches 999G' | Out-Null
}

function Push-Art {
    $files = @('wallpaper.png')
    # Favourites and consoles (their banners say which HDMI input they are on), then the rest of the apps
    foreach ($n in 'youtube', 'yt-music', 'spotify', 'stremio', 'pc', 'switch-2', 'xbox-360', 'wii-u', 'wii', 'xbox',
                   'neon-tokyo', 'netflix', 'prime-video', 'disney', 'hbo-max', 'play-store', 'media-player') {
        $files += "banners/$n.png"; $files += "icons/$n.png"
    }
    $sep = [IO.Path]::DirectorySeparatorChar
    # The art next to the script when it runs from a copy of the repository, otherwise fresh
    # from GitHub every time (the script itself usually sits in the NeonTV folder)
    $fromRepo = $PSScriptRoot -and ($PSScriptRoot -ne $Work) -and (Test-Path (Join-Path $PSScriptRoot 'art/wallpaper.png'))
    if ($fromRepo) {
        $art = Join-Path $PSScriptRoot 'art'
    } else {
        $art = Join-Path $Work 'art'
        Say 'Downloading the Neon Tokyo wallpaper, banners and icons...'
        foreach ($f in $files) {
            $local = Join-Path $art ($f -replace '/', $sep)
            New-Item -ItemType Directory -Force -Path (Split-Path $local) | Out-Null
            # The released art first, the art still being worked on if it isn't released yet
            foreach ($branch in 'main', 'dev') {
                try {
                    Invoke-WebRequest "$Raw/$branch/tv-setup/art/$f" -OutFile $local -UseBasicParsing -ErrorAction Stop
                    break
                } catch {
                    Remove-Item $local -ErrorAction SilentlyContinue
                }
            }
        }
    }
    Shell 'mkdir -p /sdcard/Pictures/NeonTokyo/banners /sdcard/Pictures/NeonTokyo/icons' | Out-Null
    $missing = @()
    foreach ($f in $files) {
        $local = Join-Path $art ($f -replace '/', $sep)
        if (-not (Test-Path $local)) { $missing += $f; continue }
        Adb push $local "/sdcard/Pictures/NeonTokyo/$f" | Out-Null
        Shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/NeonTokyo/$f" | Out-Null
    }
    if ($missing.Count -gt 0) { Say "Could not download $($missing -join ', '). Run -Step art again later for those." }
    Say "$($files.Count - $missing.Count) pictures are on the TV in Pictures/NeonTokyo."
}

function Install-NeonApp {
    try {
        $rel = Invoke-RestMethod 'https://api.github.com/repos/addebananen/neon-tokyo-tv/releases/latest'
    } catch { Say 'Could not reach GitHub releases, skipping the app.'; return }
    $v = [regex]::Match($rel.tag_name, 'v(\d+)\.(\d+)')
    if (-not $v.Success -or ([int]$v.Groups[1].Value -eq 0 -and [int]$v.Groups[2].Value -lt 2)) {
        Say "The newest release is $($rel.tag_name). Neon Tokyo 0.2 is not released yet (merge the pull request first), skipping the app."
        return
    }
    $asset = $rel.assets | Where-Object { $_.name -like '*.apk' } | Select-Object -First 1
    if (-not $asset) { Say "Release $($rel.tag_name) has no APK yet, skipping the app."; return }
    $apk = Join-Path $Work $asset.name
    Say "Downloading Neon Tokyo $($rel.tag_name)..."
    Invoke-WebRequest $asset.browser_download_url -OutFile $apk -UseBasicParsing
    $out = Adb install -r $apk
    if ($out -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        Say 'The Neon Tokyo already on the TV is signed with another key. Uninstall it on the TV (its profiles start over) and run -Step app again.'
        return
    }
    Say $out.Trim()
}

function Restart-Tv {
    Say 'Restarting the TV to check that it starts on its new home screen...'
    Adb reboot | Out-Null
    Start-Sleep 25
    $booted = $false
    for ($s = 0; $s -lt 240; $s += 5) {
        & $script:AdbExe connect $script:Serial | Out-Null
        if ((Shell 'getprop sys.boot_completed') -eq '1') { $booted = $true; break }
        Start-Sleep 5
    }
    if (-not $booted) { Say 'The TV did not come back over the network within 4 minutes. Check it, then run -Step report.'; return }
    Start-Sleep 20
    $focus = Shell 'dumpsys window | grep mCurrentFocus'
    Say "On screen after restart: $focus"
    if ($focus -match [regex]::Escape($SetupHome)) {
        Say 'The Google TV setup screen took over. Turning it off.'
        Disable-Package $SetupHome
        Shell 'input keyevent KEYCODE_HOME' | Out-Null
    }
}

function Set-Screensaver {
    $dreams = Shell 'cmd package query-services --brief -a android.service.dreams.DreamService'
    if ($dreams -notmatch [regex]::Escape($NeonDream)) {
        Say 'This Neon Tokyo version has no screensaver yet (it comes with 0.2). Skipping it.'
        return
    }
    $saved = Join-Path $Before 'screensaver.txt'
    if (-not (Test-Path $saved)) {
        New-Item -ItemType Directory -Force -Path $Before | Out-Null
        $settings = foreach ($k in 'screensaver_components', 'screensaver_enabled') { "$k=$(Shell "settings get secure $k")" }
        $settings | Set-Content $saved
    }
    # Google TV only lists its own Ambient mode, so the screensaver is picked here
    Shell "settings put secure screensaver_components $NeonDream" | Out-Null
    Shell 'settings put secure screensaver_enabled 1' | Out-Null
    Say 'Screensaver: Neon Tokyo night (skyline, neon clock, shooting stars). It starts after the TV''s usual idle time.'
}

function Show-Report {
    Say '--- Report ---'
    Say ('Home button opens: ' + ((Lines (Shell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME')) | Select-Object -Last 1))
    Say ('Screensaver: ' + (Shell 'settings get secure screensaver_components'))
    Say ('On screen: ' + (Shell 'dumpsys window | grep mCurrentFocus'))
    Say ('Memory now: ' + ((Lines (Shell "grep -E 'MemTotal|MemAvailable' /proc/meminfo")) -join ', '))
    $was = Join-Path $Before 'memory.txt'
    if (Test-Path $was) { Say ('Memory before: ' + ((Get-Content $was) -join ', ')) }
    Say ('Turned off by this script: ' + ((@(Get-Content $UndoFile -ErrorAction SilentlyContinue) | ForEach-Object { $_ -replace '^enable ', '' } | Sort-Object -Unique) -join ', '))
    Shell 'screencap -p /sdcard/neontv-after.png' | Out-Null
    Adb pull /sdcard/neontv-after.png (Join-Path $Work 'screen-after.png') | Out-Null
    Shell 'rm /sdcard/neontv-after.png' | Out-Null
    Say "Screenshot of the TV: $(Join-Path $Work 'screen-after.png')"
    if ((Shell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME') -match [regex]::Escape($NeonApp)) {
        Say 'Tip: in Neon Home, hold OK on a console to pick which HDMI input it is on.'
    } elseif (Shell "pm path $Projectivy") {
        Say 'Tip: in Projectivy, Settings > Appearance > Wallpaper > Neon Tokyo makes the skyline follow the app you point at.'
    }
}

function Undo-All {
    if (Test-Path $UndoFile) {
        $lines = @(Get-Content $UndoFile | Select-Object -Unique)
        [array]::Reverse($lines)
        foreach ($l in $lines) {
            if ($l -match '^enable (\S+)$') { Say "$($Matches[1]): $(Shell "pm enable $($Matches[1])")" }
        }
        Remove-Item $UndoFile
    }
    Restore-Settings 'global' (Join-Path $Before 'settings.txt')
    Restore-Settings 'secure' (Join-Path $Before 'screensaver.txt')
    # The Home button goes back to whatever it opened before, normally the Google TV home screen
    $was = Join-Path $Before 'home.txt'
    $original = if (Test-Path $was) { (Get-Content $was) | Where-Object { $_ -match '^\S+/\S+$' } | Select-Object -Last 1 } else { '' }
    if ($original -and $original -notmatch [regex]::Escape($Projectivy)) {
        Say "Home button: $original $(Shell "cmd package set-home-activity $original")"
    }
    # Neon Home's Home button entry goes off again; Neon Tokyo itself stays
    if ((Get-Homes) -match [regex]::Escape($NeonHome)) { Say "Neon Home: $(Switch-NeonHome 'off')" }
    Shell 'input keyevent KEYCODE_HOME' | Out-Null
    Say 'Everything is back the way it was. Projectivy and Neon Tokyo stay installed; uninstall them on the TV if you want them gone.'
}

function Restore-Settings([string]$namespace, [string]$file) {
    if (-not (Test-Path $file)) { return }
    foreach ($l in Get-Content $file) {
        $k, $v = $l -split '=', 2
        if ($v -and $v -ne 'null') { Shell "settings put $namespace $k $v" | Out-Null }
        else { Shell "settings delete $namespace $k" | Out-Null }
    }
}

Start-Transcript -Path (Join-Path $Work 'log.txt') -Append | Out-Null
try {
    Banner
    Connect-Tv
    switch ($Step) {
        'all'       { Save-Inventory; Remove-Bloat; Install-NeonApp; Set-Launcher; Set-Tuning; Push-Art; Set-Screensaver; Restart-Tv; Show-Report }
        'connect'   { }
        'inventory' { Save-Inventory }
        'debloat'   { Save-Inventory; Remove-Bloat }
        'launcher'  { Save-Inventory; Set-Launcher }
        'tune'      { Save-Inventory; Set-Tuning }
        'art'       { Push-Art }
        'app'       { Install-NeonApp }
        'dream'     { Set-Screensaver }
        'restart'   { Restart-Tv; Show-Report }
        'report'    { Show-Report }
        'undo'      { Undo-All }
    }
} catch {
    Say "Stopped: $($_.Exception.Message)"
} finally {
    Stop-Transcript | Out-Null
}
