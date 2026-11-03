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

## Cloud validation

169 debug unit tests passed with no failures or skips. The default Lint runner crashes
in AndroidX's NonNullableMutableLiveDataDetector due to a Kotlin analysis API mismatch.
A temporary `-Pandroid.experimental.lint.version=8.9.2` completes analysis without
disabling checks. Its first report contains 170 error, 301 warning and 21 information
entries across the existing application; this is not a clean Lint pass.

Follow-up review fixes: defer screensaver drift reads to placement, load motion
preferences through an effect rather than a Unit-returning remember, and use the
legacy task ID on API 26–28 when closing the app's own window from the switcher.
