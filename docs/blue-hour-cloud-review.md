# Blue Hour: cloud implementation review

## Baseline and scope

- Repository baseline: `f9f0f2b` on `main`.
- Isolated branch: `feat/cloud-tv-theme-polish`; no main updates or release publication.
- No AGENTS.md found in the checkout or workspace parent paths.
- Build configuration: Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, Java target 17,
  app compile SDK 36. The sibling assrender module compiles against SDK 35.
- Cloud SDK provisioned with platforms 35/36, build tools 34/35, NDK
  27.0.12077973 and CMake 3.22.1. Initial Java runtime: OpenJDK 17. The final successful build used a complete Temurin JDK 17.0.21 (the initial runtime lacked jlink).
- No Windows machine, local backgrounds or previous screenshots were accessed.

## Design

Blue Hour is a selectable home style, without changing an existing profile's theme.
It uses the existing HomeLook pipeline for the home, All apps, sources, toolbar,
profile editor and launcher dialogs. A static cached gradient supplies blue depth
without a video decoder, image asset, blur pass or continuous backdrop animation.
Home icons are 104dp square, with existing 22dp gutters, persistent labels,
sentence-case type and frosted surfaces. Focus uses a critically damped spring,
growing to 103% for the Motion panel's Still choice and at most 106% for Soft or
Bouncy; press feedback shrinks to 98.5%. New targets redirect the existing Compose
animation instead of queuing focus animations. Toolbar, tabs and glass dialog
buttons use the same calm damping. Existing icon motion settings, backgrounds and
layout overrides remain usable. All apps keeps its existing compact grid dimensions.

As before, the Focus animation picker applies to Liquid glass only; Frost uses its
own highlight and scale. If a Blue Hour profile selects Liquid, its initial focus
animation is Layers, and an explicit choice such as Drop or Tilt takes precedence.
Glass and focus choices are retained separately for each style. No console row is
added to Blue Hour by default.

A matching Blue Hour palette is also selectable in the media app's existing theme
picker. Launcher profiles and media profiles are separate systems; this change
intentionally does not silently overwrite either profile's preferences. The media
app retains its existing layout and typography, including explicitly styled neon
headings; it is not a full tvOS restyle of every media screen.

## Evidence and changes

| Finding in baseline | Change | Verification limit |
| --- | --- | --- |
| Tile, toolbar and tab scale, tile light/fade, dialog button light, and backdrop/floor-light animation state are read during composition | Defer these State.value reads to graphics/draw callbacks; their changing values no longer require those composables to recompose | Needs runtime recomposition/frame tracing; other state can still cause recomposition |
| rememberFocusClock loops while focused even when its activity is paused | Run the loop only in RESUMED lifecycle; initialize time on each resume to exclude time away | Needs device background/return test |
| Key-up may reach a different tile or window after focus moves; the old tile's timed hold was not cancelled on focus loss or pause | Cancel its job, clear the down timestamp and pressed state, reset hold light on tile focus loss, window focus loss and ON_PAUSE | Needs D-pad focus-loss and background/dialog reproduction on TV |
| Losing focus cancels the liquid landing animation at its current squash value | Settle the cancelled landing to its neutral value over 120 ms; refocusing interrupts that settling too | Needs rapid direction-reversal testing with Liquid glass |
| Startup warmup allocates and recycles three blank 280x420 ARGB bitmaps; no image decoder reads them | Remove that work; retain the actual Coil request that initializes the image pipeline | 1,411,200 bytes of cumulative dummy pixel allocations removed by code arithmetic; not measured peak memory |

The baseline already loads app inventory and icon bitmaps off the main thread,
uses stable package keys and a lazy All apps grid, bounds app artwork at 24MiB,
limits full-screen painting caches, shares Coil/OkHttp, pauses backdrop video when
opening another app and releases its decoder after time away. Those mechanisms
were preserved instead of replacing them without a measured problem. The existing
seek on returning to an ended still video is also preserved: it can be necessary
to render a frame onto a recreated SurfaceView, and changing it needs device evidence.

## Required runtime acceptance

The cloud host has no `/dev/kvm`, Android emulator or connected TV at inspection.
No screenshots, FPS, frame times or visual quality score are claimed.

Before merging, run the debug package `com.neontokyo.tv.test` on a TV/emulator:

1. Preview with `adb shell am start -n com.neontokyo.tv.test/com.lumera.app.launcher.NeonHomeActivity --es style bluehour`.
2. Rapid left/right/up/down, reverse before focus settles; check labels, clipping,
   focus position and navigation through Home, All apps and sources.
3. Open/close profile, motion, background, Bluetooth and input dialogs with Back.
4. Select Blue Hour in a profile, restart the app and verify persistence. Switch
   back to Deep Blue and verify its saved glass/layout/focus settings return.
5. Hold OK, move focus before releasing, and verify the old tile is not lifted.
   Repeat while opening another app or a separate dialog window; returning must
   not activate an old hold. Change Still/Soft/Bouncy and verify the scale changes;
   try Liquid with Drop, Tilt and Liquid focus modes, reversing directions mid-landing.
6. Open another app while an icon is focused; trace that no focus clock runs while
   paused. Return repeatedly and check focus restoration.
7. Play Deep Blue, own video and own still; background/return both before and after
   the 30s decoder timeout; verify no stuck playback, black frame or leaked decoder.
8. Compare `dumpsys gfxinfo ... framestats`, CPU and memory using the same device,
   app inventory, background and navigation script before/after. Do not compare
   FPS across different styles or emulator/physical hardware as a speedup claim.

## Static review and test coverage

- `git diff --check`: passed after the final review edits.
- Unit coverage now checks Blue Hour's default absence of a console row or decoder
  media, preservation of per-style glass/focus/name choices, and that calm scaling
  respects reduced motion and never exceeds the user's chosen scale.
- New and existing tests require the cloud Gradle run; their execution results will
  be recorded below when that run completes. Static inspection is not a test pass.


## Cloud execution results (2026-11-02)

- `:app:assembleDebug :app:testDebugUnitTest`: **BUILD SUCCESSFUL**.
- JUnit XML: **171 tests, 32 suites, 0 failures, 0 errors, 0 skipped**.
  This includes HomeStyleTest and HomeProfileLooksTest and the existing launcher,
  parser, Siri audio/voice, profile, artwork and update tests.
- Runtime: complete Temurin JDK 17.0.21, Gradle 8.9, SDK/NDK/CMake above.
  Java used the host's trusted `/etc/ssl/certs/java/cacerts` through the environment's
  required proxy; certificate verification remained enabled. No machine-specific
  proxy settings or credentials were committed.
- The first attempt with the host's incomplete Java runtime passed Kotlin but
  stopped at the missing `jlink` tool. Installing a complete JDK resolved that
  environment issue. The fresh JDK also needed the existing host CA trust store.
- Incomplete temporary native-library copies from the cloud checkout were excluded
  from the commit and removed locally. Native merge/strip outputs were regenerated
  before the final APK packaging check.
- No emulator/device execution, screenshots, frame-time traces or before/after CPU,
  peak-memory or FPS measurements were produced. The runtime acceptance list above
  remains required before this draft is considered ready to merge. The media app's
  matching palette is not a redesign of every media screen.

- Final `:app:assembleDebug --offline` after regenerating native merge/strip outputs: **BUILD SUCCESSFUL**. APK ZIP CRC check passed; both expected TorrServer ABI libraries are present and no hidden temporary native entries remain.
