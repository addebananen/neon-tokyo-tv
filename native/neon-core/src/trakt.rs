//! Trakt for every platform: sign in with a code on trakt.tv/activate, send what is watched
//! (scrobble and history), and take in what was watched elsewhere (other apps such as Omni,
//! the TV, the phone).
//!
//! The apps send the bodies built here with the headers from [`trakt_headers`] and hand the
//! answers back. Ids follow the Android app's TraktContentIds: addons name a title by IMDb
//! ("tt123") or with a prefix ("tmdb:123", "tvdb:123"); a playback id adds ":season:episode".

use crate::error::CoreError;
use crate::update::{iso_to_ms, ms_iso};
use crate::watch::{canonical_series_id, is_episode_playback_id, WatchEntry, PERCENT_DURATION_MS};
use serde_json::{json, Value};

pub const TRAKT_API: &str = "https://api.trakt.tv";

/// The code to show while signing in.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TraktDeviceCode {
    pub device_code: String,
    /// What the person types on trakt.tv/activate.
    pub user_code: String,
    pub verification_url: String,
    pub expires_at_ms: i64,
    /// How often to ask whether the code was entered.
    pub interval_ms: u64,
}

/// A signed-in Trakt account (per profile).
#[derive(Debug, Clone, PartialEq, Default, serde::Serialize, serde::Deserialize, uniffi::Record)]
#[serde(default)]
pub struct TraktToken {
    pub access_token: String,
    pub refresh_token: String,
    pub expires_at_ms: i64,
    /// The account's name, once read (for "Signed in as …").
    pub username: Option<String>,
}

/// The answer to "was the code entered yet?".
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum TraktPoll {
    Done { token: TraktToken },
    /// Not yet: ask again after the interval.
    Waiting,
    /// Asked too often: wait a little longer.
    SlowDown,
    /// The code ran out or was refused: start again.
    Failed { reason: String },
}

/// A header the Trakt API wants on every call.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct HttpHeader {
    pub name: String,
    pub value: String,
}

/// What Trakt said was watched or paused elsewhere, merged into this profile's history.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TraktMerge {
    /// The whole history after the merge.
    pub history: Vec<WatchEntry>,
    pub changed: u32,
}

#[uniffi::export]
pub fn trakt_url(path: String) -> String {
    format!("{TRAKT_API}/{}", path.trim_start_matches('/'))
}

/// Content-Type, the API version, the app's key and, when signed in, the account's token.
#[uniffi::export]
pub fn trakt_headers(client_id: String, access_token: Option<String>) -> Vec<HttpHeader> {
    let mut h = vec![
        HttpHeader { name: "Content-Type".into(), value: "application/json".into() },
        HttpHeader { name: "trakt-api-version".into(), value: "2".into() },
        HttpHeader { name: "trakt-api-key".into(), value: client_id },
    ];
    if let Some(t) = access_token.filter(|t| !t.is_empty()) {
        h.push(HttpHeader { name: "Authorization".into(), value: format!("Bearer {t}") });
    }
    h
}

#[uniffi::export]
pub fn trakt_device_code_body(client_id: String) -> String {
    json!({ "client_id": client_id }).to_string()
}

#[uniffi::export]
pub fn parse_trakt_device_code(json: String, now_ms: i64) -> Result<TraktDeviceCode, CoreError> {
    let v: Value = serde_json::from_str(&json)?;
    let s = |k: &str| v.get(k).and_then(Value::as_str).unwrap_or_default().to_string();
    let device_code = s("device_code");
    if device_code.is_empty() {
        return Err(CoreError::BadResponse { reason: "Trakt gave no code".into() });
    }
    let expires = v.get("expires_in").and_then(Value::as_i64).unwrap_or(600);
    let interval = v.get("interval").and_then(Value::as_u64).unwrap_or(5);
    let url = s("verification_url");
    Ok(TraktDeviceCode {
        device_code,
        user_code: s("user_code"),
        verification_url: if url.is_empty() { "https://trakt.tv/activate".into() } else { url },
        expires_at_ms: now_ms + expires * 1000,
        interval_ms: interval.max(1) * 1000,
    })
}

/// The body for "was the code entered yet?". Trakt no longer gives new apps a client secret,
/// so it is left out when empty.
#[uniffi::export]
pub fn trakt_token_body(client_id: String, client_secret: Option<String>, device_code: String) -> String {
    let mut b = json!({ "code": device_code, "client_id": client_id });
    if let Some(s) = client_secret.filter(|s| !s.is_empty()) {
        b["client_secret"] = json!(s);
    }
    b.to_string()
}

/// Trakt's answer to a poll: its HTTP status and body.
#[uniffi::export]
pub fn parse_trakt_poll(status: u16, json: String, now_ms: i64) -> TraktPoll {
    match status {
        200 | 201 => match parse_trakt_token(json, now_ms) {
            Ok(token) => TraktPoll::Done { token },
            Err(e) => TraktPoll::Failed { reason: e.to_string() },
        },
        400 => TraktPoll::Waiting,
        429 => TraktPoll::SlowDown,
        404 => TraktPoll::Failed { reason: "That code is not valid".into() },
        409 => TraktPoll::Failed { reason: "That code was already used".into() },
        410 => TraktPoll::Failed { reason: "The code ran out, get a new one".into() },
        418 => TraktPoll::Failed { reason: "Sign-in was declined on Trakt".into() },
        other => TraktPoll::Failed { reason: format!("Trakt answered {other}") },
    }
}

/// The body to renew a token that is about to run out (Trakt tokens live 7 days; each refresh
/// token works once).
#[uniffi::export]
pub fn trakt_refresh_body(client_id: String, client_secret: Option<String>, refresh_token: String) -> String {
    let mut b = json!({ "refresh_token": refresh_token, "client_id": client_id, "grant_type": "refresh_token" });
    if let Some(s) = client_secret.filter(|s| !s.is_empty()) {
        b["client_secret"] = json!(s);
        b["redirect_uri"] = json!("urn:ietf:wg:oauth:2.0:oob");
    }
    b.to_string()
}

#[uniffi::export]
pub fn trakt_revoke_body(client_id: String, access_token: String) -> String {
    json!({ "token": access_token, "client_id": client_id }).to_string()
}

#[uniffi::export]
pub fn parse_trakt_token(json: String, now_ms: i64) -> Result<TraktToken, CoreError> {
    let v: Value = serde_json::from_str(&json)?;
    let access = v.get("access_token").and_then(Value::as_str).unwrap_or_default();
    if access.is_empty() {
        return Err(CoreError::SignedOut { reason: "Trakt gave no token".into() });
    }
    let created = v.get("created_at").and_then(Value::as_i64).map(|s| s * 1000).unwrap_or(now_ms);
    let expires = v.get("expires_in").and_then(Value::as_i64).unwrap_or(7 * 24 * 3600);
    Ok(TraktToken {
        access_token: access.into(),
        refresh_token: v.get("refresh_token").and_then(Value::as_str).unwrap_or_default().into(),
        expires_at_ms: created + expires * 1000,
        username: None,
    })
}

/// Whether to renew now: less than a day left.
#[uniffi::export]
pub fn trakt_needs_refresh(token: TraktToken, now_ms: i64) -> bool {
    !token.refresh_token.is_empty() && token.expires_at_ms - now_ms < 24 * 3600 * 1000
}

/// The account name from `users/settings`.
#[uniffi::export]
pub fn parse_trakt_username(json: String) -> Option<String> {
    let v: Value = serde_json::from_str(&json).ok()?;
    v.pointer("/user/username").or_else(|| v.pointer("/user/name")).and_then(Value::as_str).map(str::to_string)
}

// ---------- ids ----------

const PREFIXED: [&str; 6] = ["tmdb", "tvdb", "kitsu", "mal", "anilist", "anidb"];

/// "tmdb:1399:2:5" is ("tmdb:1399", ["2","5"]).
fn split(playback_id: &str) -> (String, Vec<String>) {
    let parts: Vec<&str> = playback_id.split(':').collect();
    let n = if parts.len() > 1 && PREFIXED.contains(&parts[0]) { 2 } else { 1 };
    (parts[..n.min(parts.len())].join(":"), parts[n.min(parts.len())..].iter().map(|s| s.to_string()).collect())
}

/// Trakt's ids object for a content id, or None when Trakt cannot place it.
fn ids_for(content_id: &str) -> Option<Value> {
    if content_id.starts_with("tt") {
        return Some(json!({ "imdb": content_id }));
    }
    let (prefix, num) = content_id.split_once(':')?;
    let n: i64 = num.parse().ok()?;
    match prefix {
        "tmdb" => Some(json!({ "tmdb": n })),
        "tvdb" => Some(json!({ "tvdb": n })),
        _ => None,
    }
}

/// Every content id one Trakt title can go by here, IMDb first.
fn keys_of(ids: &Value) -> Vec<String> {
    let mut out = Vec::new();
    if let Some(i) = ids.get("imdb").and_then(Value::as_str).filter(|s| !s.is_empty()) {
        out.push(i.to_string());
    }
    if let Some(t) = ids.get("tmdb").and_then(Value::as_i64) {
        out.push(format!("tmdb:{t}"));
    }
    if let Some(t) = ids.get("tvdb").and_then(Value::as_i64) {
        out.push(format!("tvdb:{t}"));
    }
    out
}

/// The film's ids, or the show's ids with the episode's season and number.
fn target(playback_id: &str, kind: &str) -> Option<Value> {
    let (content, rest) = split(playback_id);
    let ids = ids_for(&content)?;
    if kind == "series" || rest.len() >= 2 {
        let season: i64 = rest.first()?.parse().ok()?;
        let number: i64 = rest.get(1)?.parse().ok()?;
        Some(json!({ "show": { "ids": ids }, "episode": { "season": season, "number": number } }))
    } else {
        Some(json!({ "movie": { "ids": ids } }))
    }
}

/// The body for `scrobble/start`, `scrobble/pause` or `scrobble/stop`: Trakt counts a stop
/// past 80 % as watched. None for ids Trakt cannot place.
#[uniffi::export]
pub fn trakt_scrobble_body(playback_id: String, kind: String, progress_percent: f64) -> Option<String> {
    let mut b = target(&playback_id, &kind)?;
    b["progress"] = json!((progress_percent.clamp(0.0, 100.0) * 100.0).round() / 100.0);
    Some(b.to_string())
}

/// `sync/history` for entries watched here (films and episodes), with when they were watched.
#[uniffi::export]
pub fn trakt_history_body(entries: Vec<WatchEntry>) -> Option<String> {
    let mut movies: Vec<Value> = Vec::new();
    let mut shows: Vec<(String, Value, Vec<(i64, i64, String)>)> = Vec::new();
    for e in entries.iter().filter(|e| e.watched && !e.deleted) {
        let at = ms_iso(e.updated_at_ms);
        let (content, rest) = split(&e.id);
        let Some(ids) = ids_for(&content) else { continue };
        if rest.len() >= 2 {
            let (Ok(s), Ok(n)) = (rest[0].parse::<i64>(), rest[1].parse::<i64>()) else { continue };
            match shows.iter_mut().find(|(c, _, _)| *c == content) {
                Some((_, _, eps)) => eps.push((s, n, at)),
                None => shows.push((content, ids, vec![(s, n, at)])),
            }
        } else if e.kind != "series" {
            movies.push(json!({ "ids": ids, "watched_at": at }));
        }
    }
    if movies.is_empty() && shows.is_empty() {
        return None;
    }
    let shows: Vec<Value> = shows
        .into_iter()
        .map(|(_, ids, eps)| {
            let mut seasons: Vec<(i64, Vec<Value>)> = Vec::new();
            for (s, n, at) in eps {
                let ep = json!({ "number": n, "watched_at": at });
                match seasons.iter_mut().find(|(x, _)| *x == s) {
                    Some((_, list)) => list.push(ep),
                    None => seasons.push((s, vec![ep])),
                }
            }
            json!({ "ids": ids, "seasons": seasons.into_iter().map(|(s, e)| json!({ "number": s, "episodes": e })).collect::<Vec<_>>() })
        })
        .collect();
    Some(json!({ "movies": movies, "shows": shows }).to_string())
}

/// One stamp for "has anything changed on Trakt": the times films and episodes were last
/// watched or paused, from `sync/last_activities`.
#[uniffi::export]
pub fn trakt_activity_stamp(json: String) -> String {
    let v: Value = serde_json::from_str(&json).unwrap_or(Value::Null);
    ["/movies/watched_at", "/movies/paused_at", "/episodes/watched_at", "/episodes/paused_at"]
        .iter()
        .map(|p| v.pointer(p).and_then(Value::as_str).unwrap_or(""))
        .collect::<Vec<_>>()
        .join("|")
}

/// The id this device already uses for a title Trakt names by `ids`, so a film added here as
/// "tmdb:123" is not shown twice when Trakt calls it "tt456".
fn local_id(ids: &Value, history: &[WatchEntry], profile_id: &str) -> Option<String> {
    let keys = keys_of(ids);
    let first = keys.first()?.clone();
    for k in &keys {
        if history.iter().any(|e| e.profile_id == profile_id && (e.id == *k || canonical_series_id(e.id.clone()) == *k)) {
            return Some(k.clone());
        }
    }
    Some(first)
}

fn upsert(history: &mut Vec<WatchEntry>, e: WatchEntry) -> bool {
    match history.iter().position(|x| x.profile_id == e.profile_id && x.id == e.id) {
        Some(i) if history[i].updated_at_ms >= e.updated_at_ms => false,
        Some(i) => {
            let old = &history[i];
            let merged = WatchEntry {
                poster: e.poster.clone().or_else(|| old.poster.clone()),
                background: old.background.clone(),
                logo: old.logo.clone(),
                title: if e.title.is_empty() { old.title.clone() } else { e.title.clone() },
                ..e
            };
            history[i] = merged;
            true
        }
        None => {
            history.push(e);
            true
        }
    }
}

/// Films and episodes paused part way on any app (`sync/playback`), into Continue watching.
/// Where this device knows the length, the position is worked out; otherwise only the share
/// is kept and the player seeks there once it knows the length.
#[uniffi::export]
pub fn trakt_merge_playback(json: String, history: Vec<WatchEntry>, profile_id: String) -> Result<TraktMerge, CoreError> {
    let list: Vec<Value> = serde_json::from_str(&json)?;
    let mut history = history;
    let mut changed = 0;
    for p in &list {
        let pct = p.get("progress").and_then(Value::as_f64).unwrap_or(0.0).clamp(0.0, 100.0);
        let at = p.get("paused_at").and_then(Value::as_str).and_then(iso_to_ms).unwrap_or(0);
        if pct < 1.0 || at == 0 {
            continue;
        }
        let (id, kind, title) = match p.get("type").and_then(Value::as_str) {
            Some("movie") => {
                let m = p.get("movie").cloned().unwrap_or(Value::Null);
                let Some(id) = m.get("ids").and_then(|ids| local_id(ids, &history, &profile_id)) else { continue };
                (id, "movie", m.get("title").and_then(Value::as_str).unwrap_or_default().to_string())
            }
            Some("episode") => {
                let show = p.get("show").cloned().unwrap_or(Value::Null);
                let ep = p.get("episode").cloned().unwrap_or(Value::Null);
                let Some(sid) = show.get("ids").and_then(|ids| local_id(ids, &history, &profile_id)) else { continue };
                let (Some(s), Some(n)) = (ep.get("season").and_then(Value::as_i64), ep.get("number").and_then(Value::as_i64)) else { continue };
                (format!("{sid}:{s}:{n}"), "series", show.get("title").and_then(Value::as_str).unwrap_or_default().to_string())
            }
            _ => continue,
        };
        let known = history.iter().find(|e| e.profile_id == profile_id && e.id == id && e.duration_ms > 0 && e.duration_ms != PERCENT_DURATION_MS);
        let (pos, dur) = match known {
            Some(k) => ((k.duration_ms as f64 * pct / 100.0) as u64, k.duration_ms),
            None => ((PERCENT_DURATION_MS as f64 * pct / 100.0) as u64, PERCENT_DURATION_MS),
        };
        let e = WatchEntry {
            profile_id: profile_id.clone(),
            id,
            kind: kind.into(),
            title,
            position_ms: pos,
            duration_ms: dur,
            watched: false,
            updated_at_ms: at,
            ..Default::default()
        };
        if upsert(&mut history, e) {
            changed += 1;
        }
    }
    Ok(TraktMerge { history, changed })
}

/// Films and episodes watched on any app (`sync/watched/movies` and `sync/watched/shows`), so
/// they show as watched here. Only the newest `max_titles` films and shows are taken, to keep
/// a long Trakt history from filling the device.
#[uniffi::export]
pub fn trakt_merge_watched(
    movies_json: String,
    shows_json: String,
    history: Vec<WatchEntry>,
    profile_id: String,
    max_titles: u32,
) -> Result<TraktMerge, CoreError> {
    let mut movies: Vec<Value> = serde_json::from_str(&movies_json).unwrap_or_default();
    let mut shows: Vec<Value> = serde_json::from_str(&shows_json).unwrap_or_default();
    let last = |v: &Value| v.get("last_watched_at").and_then(Value::as_str).and_then(iso_to_ms).unwrap_or(0);
    movies.sort_by_key(|v| std::cmp::Reverse(last(v)));
    shows.sort_by_key(|v| std::cmp::Reverse(last(v)));
    movies.truncate(max_titles as usize);
    shows.truncate(max_titles as usize);
    let mut history = history;
    let mut changed = 0;
    let mut mark = |history: &mut Vec<WatchEntry>, id: String, kind: &str, title: String, at: i64| {
        let i = history.iter().position(|e| e.profile_id == profile_id && e.id == id);
        if let Some(i) = i {
            if history[i].watched || history[i].updated_at_ms >= at {
                return;
            }
        }
        let e = WatchEntry {
            profile_id: profile_id.clone(),
            id,
            kind: kind.into(),
            title,
            position_ms: 1,
            duration_ms: 1,
            watched: true,
            updated_at_ms: at,
            ..Default::default()
        };
        if upsert(history, e) {
            changed += 1;
        }
    };
    for m in &movies {
        let movie = m.get("movie").cloned().unwrap_or(Value::Null);
        let Some(id) = movie.get("ids").and_then(|ids| local_id(ids, &history, &profile_id)) else { continue };
        let title = movie.get("title").and_then(Value::as_str).unwrap_or_default().to_string();
        mark(&mut history, id, "movie", title, last(m));
    }
    for s in &shows {
        let show = s.get("show").cloned().unwrap_or(Value::Null);
        let Some(sid) = show.get("ids").and_then(|ids| local_id(ids, &history, &profile_id)) else { continue };
        let title = show.get("title").and_then(Value::as_str).unwrap_or_default().to_string();
        for season in s.get("seasons").and_then(Value::as_array).into_iter().flatten() {
            let Some(sn) = season.get("number").and_then(Value::as_i64) else { continue };
            if sn == 0 {
                continue;
            }
            for ep in season.get("episodes").and_then(Value::as_array).into_iter().flatten() {
                let Some(en) = ep.get("number").and_then(Value::as_i64) else { continue };
                let at = last(ep).max(1);
                mark(&mut history, format!("{sid}:{sn}:{en}"), "series", title.clone(), at);
            }
        }
    }
    Ok(TraktMerge { history, changed })
}

/// Entries watched here that Trakt has not heard of yet: watched after `since_ms`, with ids
/// Trakt can place (pass these to [`trakt_history_body`]).
#[uniffi::export]
pub fn trakt_pending_history(history: Vec<WatchEntry>, profile_id: String, since_ms: i64) -> Vec<WatchEntry> {
    history
        .into_iter()
        .filter(|e| e.profile_id == profile_id && e.watched && !e.deleted && e.updated_at_ms > since_ms)
        .filter(|e| ids_for(&split(&e.id).0).is_some())
        .filter(|e| e.kind != "series" || is_episode_playback_id(e.id.clone()))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::watch::record_progress;

    #[test]
    fn sign_in_flow() {
        let c = parse_trakt_device_code(
            r#"{"device_code":"d1","user_code":"AB12CD34","verification_url":"https://trakt.tv/activate","expires_in":600,"interval":5}"#.into(),
            1000,
        )
        .unwrap();
        assert_eq!(c.user_code, "AB12CD34");
        assert_eq!(c.expires_at_ms, 601_000);
        assert_eq!(c.interval_ms, 5000);
        assert_eq!(parse_trakt_poll(400, "".into(), 0), TraktPoll::Waiting);
        assert_eq!(parse_trakt_poll(429, "".into(), 0), TraktPoll::SlowDown);
        assert!(matches!(parse_trakt_poll(410, "".into(), 0), TraktPoll::Failed { .. }));
        let done = parse_trakt_poll(200, r#"{"access_token":"a","refresh_token":"r","expires_in":604800,"created_at":1000}"#.into(), 0);
        let TraktPoll::Done { token } = done else { panic!() };
        assert_eq!(token.expires_at_ms, 1_000_000 + 604_800_000);
        assert!(!trakt_needs_refresh(token.clone(), 1_000_000));
        assert!(trakt_needs_refresh(token, 1_000_000 + 600_000_000));
        let body: Value = serde_json::from_str(&trakt_token_body("cid".into(), Some("".into()), "d1".into())).unwrap();
        assert!(body.get("client_secret").is_none());
        let h = trakt_headers("cid".into(), Some("tok".into()));
        assert!(h.iter().any(|h| h.name == "Authorization" && h.value == "Bearer tok"));
    }

    #[test]
    fn scrobble_bodies() {
        let m: Value = serde_json::from_str(&trakt_scrobble_body("tt1".into(), "movie".into(), 42.123).unwrap()).unwrap();
        assert_eq!(m, json!({"movie": {"ids": {"imdb": "tt1"}}, "progress": 42.12}));
        let e: Value = serde_json::from_str(&trakt_scrobble_body("tmdb:1399:2:5".into(), "series".into(), 90.0).unwrap()).unwrap();
        assert_eq!(e["show"]["ids"]["tmdb"], 1399);
        assert_eq!(e["episode"], json!({"season": 2, "number": 5}));
        assert!(trakt_scrobble_body("kitsu:1:1:1".into(), "series".into(), 5.0).is_none());
    }

    #[test]
    fn history_groups_by_show_and_season() {
        let mut a = record_progress(None, "p".into(), "tt9:1:1".into(), "series".into(), "S".into(), None, 1, 1, 0);
        a.watched = true;
        let mut b = a.clone();
        b.id = "tt9:1:2".into();
        let mut m = record_progress(None, "p".into(), "tt1".into(), "movie".into(), "M".into(), None, 1, 1, 0);
        m.watched = true;
        let not = record_progress(None, "p".into(), "tt2".into(), "movie".into(), "N".into(), None, 1, 6_000_000, 0);
        let body: Value = serde_json::from_str(&trakt_history_body(vec![a, b, m, not]).unwrap()).unwrap();
        assert_eq!(body["movies"].as_array().unwrap().len(), 1);
        assert_eq!(body["shows"][0]["seasons"][0]["episodes"].as_array().unwrap().len(), 2);
        assert!(trakt_history_body(vec![]).is_none());
    }

    #[test]
    fn takes_in_playback_with_local_ids() {
        // this device knows the show by its tmdb id and the episode's length
        let mine = record_progress(None, "p".into(), "tmdb:1399:1:1".into(), "series".into(), "GoT".into(), None, 10, 3_600_000, 5);
        let json = r#"[
          {"progress":50.0,"paused_at":"2026-11-03T20:00:00.000Z","type":"episode",
           "episode":{"season":1,"number":1},"show":{"title":"GoT","ids":{"imdb":"tt0944947","tmdb":1399}}},
          {"progress":25.0,"paused_at":"2026-11-03T21:00:00.000Z","type":"movie","movie":{"title":"Dune","ids":{"imdb":"tt1160419","tmdb":438631}}},
          {"progress":0.2,"paused_at":"2026-11-03T21:00:00.000Z","type":"movie","movie":{"title":"Skip","ids":{"imdb":"tt0"}}}
        ]"#;
        let m = trakt_merge_playback(json.into(), vec![mine], "p".into()).unwrap();
        assert_eq!(m.changed, 2);
        let ep = m.history.iter().find(|e| e.id == "tmdb:1399:1:1").unwrap();
        assert_eq!(ep.position_ms, 1_800_000);
        let dune = m.history.iter().find(|e| e.id == "tt1160419").unwrap();
        assert_eq!(dune.duration_ms, PERCENT_DURATION_MS);
        assert_eq!(crate::watch::resume_position_ms(Some(dune.clone())), 0);
        assert!((crate::watch::resume_fraction(Some(dune.clone())) - 0.25).abs() < 1e-6);
        // the same answer again changes nothing
        let again = trakt_merge_playback(json.into(), m.history, "p".into()).unwrap();
        assert_eq!(again.changed, 0);
    }

    #[test]
    fn takes_in_watched() {
        let movies = r#"[{"last_watched_at":"2026-11-01T10:00:00Z","movie":{"title":"Dune","ids":{"imdb":"tt1"}}}]"#;
        let shows = r#"[{"last_watched_at":"2026-11-02T10:00:00Z","show":{"title":"S","ids":{"imdb":"tt9"}},
          "seasons":[{"number":0,"episodes":[{"number":1}]},{"number":1,"episodes":[{"number":1,"last_watched_at":"2026-11-02T10:00:00Z"},{"number":2}]}]}]"#;
        let m = trakt_merge_watched(movies.into(), shows.into(), vec![], "p".into(), 100).unwrap();
        assert_eq!(m.changed, 3);
        assert!(m.history.iter().all(|e| e.watched));
        assert!(m.history.iter().any(|e| e.id == "tt9:1:2"));
        let pending = trakt_pending_history(m.history.clone(), "p".into(), 0);
        assert_eq!(pending.len(), 3);
    }
}
