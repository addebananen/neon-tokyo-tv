# Aurora TV review

Aurora is selectable in the launcher profile's Style row. Existing styles and saved
per-style choices remain available. Its static blue gradient needs no video decoder
or ambient frame loop. Home and All apps share 88 dp square cards with 50 dp artwork
and 44 dp fallback glyphs. Focus grows by at most 6%, uses a critically damped spring,
and lifts the foreground icon. Buttons, tabs and opening panels use restrained motion.
No Apple assets are included.

## Fixes and performance work

- An interrupted liquid landing now settles instead of freezing its squash/bob.
- Default focus motion comes from the selected style; explicit profile overrides win.
- Skip unused landing and sheen animations for focus modes that do not render them.
- Do not create a video surface/player for procedural backgrounds.
- Pause focused scene clocks outside RESUMED and reset timestamps on return.
- Propagate coroutine cancellation in catalog and metadata loading instead of caching
  it as a failed request or changing the state of a newer load.
- Drain buffered catalog pages with a deque instead of copying all remaining items.
- Include locale in the clock formatting cache key.
- Aurora's screensaver follows its background instead of falling back to City Drive.

## Device acceptance before merge

Use the debug application (`com.neontokyo.tv.test`) alongside the installed launcher.
Check Aurora and Deep Blue at 1080p and 4K: Home, All apps, profile editor, settings,
Bluetooth and input dialogs. Rapidly reverse D-pad direction during landing; no tile
should remain deformed. Check long-press reordering, hidden apps, back/focus restoration,
style changes with custom backgrounds, and return from another app.

Capture frame times and memory on a representative low-end TV, with animations enabled.
Compare the same installed apps and background against the base commit. There is no
measured FPS or memory improvement claim until that comparison has been performed.
