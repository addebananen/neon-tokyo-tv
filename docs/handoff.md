# "Fortsätt på TV:n": continue watching on another device

A profile picks up where another device left off, on the TV, the iPhone/iPad app and the web
app (and Stremio's own apps), in two ways:

1. **Progress sync.** Each device keeps Continue watching in step with the profile's accounts:
   - **Stremio** (library `state.timeOffset` / `duration` / `video_id`, newest change wins).
     Web, iPhone and PC sync per profile. The TV shares one Stremio account between profiles
     for addons, so its Continue watching follows the account for one profile only: the one
     picked in Settings › Integrations › Stremio ("Synka Fortsätt titta hit"), else the first
     profile. Code: `app/.../handoff/StremioWatchSync.kt`, `web/js/sync.js`,
     `apple/.../Core/Accounts.swift`.
   - **Trakt** (scrobble pause / `sync/playback`). On the TV a pause on Trakt more than 90 s
     newer than its own save moves the TV's spot (`TraktSyncManager.syncPlaybackProgress`).
     Trakt only knows the share watched, so such entries are saved against
     `PERCENT_DURATION_MS` (1 000 000 000) and the player seeks to that share of the real length.

2. **The button.** "Fortsätt på TV:n" (details page on web and iPhone, and in the web player)
   sends the title and the exact second to a TV through the profile's Stremio account. No
   pairing, works away from home too. The TV must be signed in to the same Stremio account.

## The two hidden library items

Both are ordinary `libraryItem`s with `type: "other"`, `removed: true`, `temp: true` and no
progress, so Stremio's apps read them without trouble and never show them. The message is JSON
in `name`.

`neontokyo:tvs`: the TVs that are on. Each TV writes itself every 4 minutes while its screen
is on (and `on: false` when it goes off); phones list TVs seen within 10 minutes.

```json
{"v":1,"tvs":{"3f2a9c1b7d4e":{"name":"Vardagsrum","seenAt":1762490000000,"on":true}}}
```

`neontokyo:handoff`: the last message sent.

```json
{"v":1,"nonce":"k3j2h1","to":"3f2a9c1b7d4e","from":"iPhone","profile":"Adrian","sentAt":1762490000000,
 "id":"tt0903747","type":"series","video":"tt0903747:2:3","name":"Breaking Bad","title":"S2:E3 - Breaking Bad",
 "poster":"…","background":"…","logo":"…","positionMs":1234567,"durationMs":2820000}
```

- `to` is a TV id, or `*` for whichever TV is on (or the first one turned on within 5 minutes).
- A TV whose screen is on looks every 4 s. It acts on a message that is for it, under 5 minutes
  old and not yet taken: it writes `takenBy` / `takenName` / `takenAt` back (for `*` it reads
  again after 0.9 s and only plays if its id stayed), then `HandoffOpen.start` picks the TV
  profile with the same name (else the one watching), saves the spot in that profile's history,
  opens the details page and plays the best source from that second.
- The phone polls the mailbox every 1.5 s and shows "Spelas på Vardagsrum" once `takenBy` is set.

Tests: `app/src/test/.../handoff/HandoffProtocolTest.kt`, `web/test/handoff.test.mjs`.
