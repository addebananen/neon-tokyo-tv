//! A profile signed in with a Stremio account: the account's addons come in, and its library
//! (Stremio's own watchlist and how far each film or episode got) stays in step both ways.
//!
//! The apps send the request bodies built here to Stremio's API and hand the answers back. The
//! merge is the shared sync rule: per film or series, the newer change wins. The web app's
//! web/js/sync.js does the same in JavaScript.

use crate::addon::normalize_addon_url;
use crate::error::CoreError;
use crate::sync::WatchlistItem;
use crate::update::{iso_to_ms, ms_iso};
use crate::watch::{canonical_series_id, is_episode_playback_id, record_progress, WatchEntry};
use serde_json::{json, Map, Value};
use std::collections::HashMap;

pub const STREMIO_API: &str = "https://api.strem.io/api/";

/// A signed-in Stremio account. The password is never kept, only this key.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct StremioAuth {
    pub auth_key: String,
    pub email: String,
}

/// What a library sync changes.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct StremioMerge {
    /// The whole history after taking in Stremio's newer progress.
    pub history: Vec<WatchEntry>,
    /// The whole watchlist after taking in Stremio's library.
    pub watchlist: Vec<WatchlistItem>,
    /// The body to send to `datastorePut`, or None when nothing here is newer.
    pub upload_body: Option<String>,
    /// How many titles came in from Stremio (for "12 titles synced").
    pub received: u32,
    pub sent: u32,
}

/// The address of one Stremio API method ("login", "addonCollectionGet", ...).
#[uniffi::export]
pub fn stremio_url(method: String) -> String {
    format!("{STREMIO_API}{method}")
}

#[uniffi::export]
pub fn stremio_login_body(email: String, password: String) -> String {
    json!({"type": "Login", "email": email.trim(), "password": password, "facebook": false}).to_string()
}

#[uniffi::export]
pub fn stremio_logout_body(auth_key: String) -> String {
    json!({"type": "Logout", "authKey": auth_key}).to_string()
}

#[uniffi::export]
pub fn stremio_addons_body(auth_key: String) -> String {
    json!({"type": "AddonCollectionGet", "authKey": auth_key, "update": true}).to_string()
}

#[uniffi::export]
pub fn stremio_library_body(auth_key: String) -> String {
    json!({"authKey": auth_key, "collection": "libraryItem", "ids": [], "all": true}).to_string()
}

fn result(json: &str) -> Result<Value, CoreError> {
    let v: Value = serde_json::from_str(json)?;
    if let Some(e) = v.get("error").filter(|e| !e.is_null()) {
        let msg = e.get("message").and_then(Value::as_str).map(str::to_string).unwrap_or_else(|| e.to_string());
        let lower = msg.to_ascii_lowercase();
        if lower.contains("session") || lower.contains("auth") {
            return Err(CoreError::SignedOut { reason: msg });
        }
        return Err(CoreError::BadResponse { reason: msg });
    }
    Ok(v.get("result").cloned().unwrap_or(Value::Null))
}

/// The key from a login answer; a wrong password is an error with Stremio's own words.
#[uniffi::export]
pub fn parse_stremio_login(json: String, email: String) -> Result<StremioAuth, CoreError> {
    let r = result(&json).map_err(|e| match e {
        CoreError::SignedOut { reason } => CoreError::BadResponse { reason },
        other => other,
    })?;
    let key = r.get("authKey").and_then(Value::as_str).unwrap_or_default();
    if key.is_empty() {
        return Err(CoreError::BadResponse { reason: "Wrong email or password".into() });
    }
    let email = r.pointer("/user/email").and_then(Value::as_str).map(str::to_string).unwrap_or(email);
    Ok(StremioAuth { auth_key: key.to_string(), email })
}

/// The addon addresses on the account, in Stremio's order, without Stremio's built-in local one.
#[uniffi::export]
pub fn parse_stremio_addons(json: String) -> Result<Vec<String>, CoreError> {
    let r = result(&json)?;
    let mut out: Vec<String> = Vec::new();
    for a in r.get("addons").and_then(Value::as_array).into_iter().flatten() {
        let url = a.get("transportUrl").and_then(Value::as_str).unwrap_or_default();
        let id = a.pointer("/manifest/id").and_then(Value::as_str).unwrap_or_default();
        if url.is_empty() || id == "org.stremio.local" {
            continue;
        }
        if let Ok(base) = normalize_addon_url(url.to_string()) {
            if !out.contains(&base) {
                out.push(base);
            }
        }
    }
    Ok(out)
}

/// Takes in the account's library and works out what to send back.
///
/// `since_ms` is when this profile last synced (0 = never): local changes after it are sent
/// when they are newer than Stremio's copy. Watchlist = Stremio's library (not removed, not
/// temporary); progress = each item's `state`.
#[uniffi::export]
pub fn stremio_library_merge(
    library_json: String,
    auth_key: String,
    history: Vec<WatchEntry>,
    watchlist: Vec<WatchlistItem>,
    profile_id: String,
    since_ms: i64,
    now_ms: i64,
) -> Result<StremioMerge, CoreError> {
    let r = result(&library_json)?;
    let items: Vec<Value> = r.as_array().cloned().unwrap_or_default();
    let mut by_id: HashMap<String, Value> = HashMap::new();
    for it in &items {
        if let Some(id) = it.get("_id").and_then(Value::as_str) {
            by_id.insert(id.to_string(), it.clone());
        }
    }
    let pid = profile_id.as_str();

    // ---- what to send: this profile's changes since the last sync, newest last ----
    let mut out: Vec<(String, Value)> = Vec::new();
    let put = |out: &mut Vec<(String, Value)>, id: String, v: Value| {
        if let Some(slot) = out.iter_mut().find(|(k, _)| *k == id) {
            slot.1 = v;
        } else {
            out.push((id, v));
        }
    };
    let take = |out: &Vec<(String, Value)>, id: &str, kind: &str, name: &str, poster: Option<&str>| -> Value {
        if let Some((_, v)) = out.iter().find(|(k, _)| k == id) {
            return v.clone();
        }
        if let Some(v) = by_id.get(id) {
            return v.clone();
        }
        let now = ms_iso(now_ms);
        json!({
            "_id": id, "name": name, "type": kind, "poster": poster.unwrap_or(""), "posterShape": "poster",
            "removed": true, "temp": true, "_ctime": now, "_mtime": now,
            "state": {"lastWatched": now, "timeWatched": 0, "timeOffset": 0, "overallTimeWatched": 0, "timesWatched": 0,
                      "flaggedWatched": 0, "duration": 0, "video_id": id, "watched": "", "noNotif": false},
            "behaviorHints": {"defaultVideoId": null, "featuredVideoId": null, "hasScheduledVideos": false}
        })
    };

    let mut mine: Vec<&WatchEntry> = history
        .iter()
        .filter(|e| e.profile_id == pid && !e.deleted && e.updated_at_ms > since_ms && e.kind != "tv" && e.kind != "own")
        .collect();
    mine.sort_by_key(|e| e.updated_at_ms);
    for e in mine {
        let id = if e.kind == "series" { canonical_series_id(e.id.clone()) } else { e.id.clone() };
        let remote_at = by_id.get(&id).and_then(|v| v.pointer("/state/lastWatched")).and_then(Value::as_str).and_then(iso_to_ms).unwrap_or(0);
        if remote_at > e.updated_at_ms {
            continue;
        }
        let mut it = take(&out, &id, &e.kind, &e.title, e.poster.as_deref());
        let times = it.pointer("/state/timesWatched").and_then(Value::as_i64).unwrap_or(0) + i64::from(e.watched);
        let state = it.get_mut("state").and_then(Value::as_object_mut).cloned().unwrap_or_default();
        let mut state: Map<String, Value> = state;
        state.insert("lastWatched".into(), json!(ms_iso(e.updated_at_ms)));
        state.insert("video_id".into(), json!(e.id));
        state.insert("duration".into(), json!(e.duration_ms));
        state.insert("timeOffset".into(), json!(if e.watched { 0 } else { e.position_ms }));
        state.insert("flaggedWatched".into(), json!(i64::from(e.watched)));
        state.insert("timesWatched".into(), json!(times));
        if e.kind == "series" && is_episode_playback_id(e.id.clone()) {
            let parts: Vec<&str> = e.id.split(':').collect();
            let n = parts.len();
            state.insert("season".into(), json!(parts[n - 2].parse::<i64>().unwrap_or(0)));
            state.insert("episode".into(), json!(parts[n - 1].parse::<i64>().unwrap_or(0)));
        }
        it["state"] = Value::Object(state);
        it["_mtime"] = json!(ms_iso(e.updated_at_ms));
        put(&mut out, id, it);
    }
    for w in watchlist.iter().filter(|w| w.profile_id == pid && w.updated_at_ms > since_ms) {
        let remote_at = by_id.get(&w.id).and_then(|v| v.get("_mtime")).and_then(Value::as_str).and_then(iso_to_ms).unwrap_or(0);
        if remote_at > w.updated_at_ms || (w.deleted && !by_id.contains_key(&w.id)) {
            continue;
        }
        let mut it = take(&out, &w.id, &w.kind, &w.title, w.poster.as_deref());
        it["removed"] = json!(w.deleted);
        it["temp"] = json!(w.deleted);
        it["_mtime"] = json!(ms_iso(w.updated_at_ms));
        put(&mut out, w.id.clone(), it);
    }

    // ---- what comes in ----
    let mut history = history;
    let mut watchlist = watchlist;
    let mut received = 0u32;
    for it in &items {
        let Some(id) = it.get("_id").and_then(Value::as_str) else { continue };
        let kind_raw = it.get("type").and_then(Value::as_str).unwrap_or("movie");
        if kind_raw == "other" {
            continue;
        }
        let kind = if kind_raw.is_empty() { "movie" } else { kind_raw };
        let name = it.get("name").and_then(Value::as_str).unwrap_or_default();
        let poster = it.get("poster").and_then(Value::as_str).filter(|p| !p.is_empty()).map(str::to_string);
        let mtime = it.get("_mtime").and_then(Value::as_str).and_then(iso_to_ms).unwrap_or(0);
        let removed = it.get("removed").and_then(Value::as_bool).unwrap_or(false);
        let temp = it.get("temp").and_then(Value::as_bool).unwrap_or(false);
        let in_library = !removed && !temp;
        let mut touched = false;

        match watchlist.iter_mut().find(|w| w.profile_id == pid && w.id == id) {
            Some(w) if mtime > w.updated_at_ms && w.deleted == in_library => {
                w.deleted = !in_library;
                w.updated_at_ms = mtime;
                if in_library {
                    w.added_at_ms = mtime;
                }
                touched = true;
            }
            None if in_library => {
                watchlist.push(WatchlistItem {
                    profile_id: pid.to_string(),
                    id: id.to_string(),
                    kind: kind.to_string(),
                    title: name.to_string(),
                    poster: poster.clone(),
                    added_at_ms: mtime,
                    updated_at_ms: mtime,
                    deleted: false,
                });
                touched = true;
            }
            _ => {}
        }

        let st = it.get("state").cloned().unwrap_or(Value::Null);
        let last = st.get("lastWatched").and_then(Value::as_str).and_then(iso_to_ms).unwrap_or(mtime);
        let video = st.get("video_id").and_then(Value::as_str).filter(|v| !v.is_empty());
        let playback_id = match (kind, video) {
            ("series", Some(v)) if is_episode_playback_id(v.to_string()) => Some(v.to_string()),
            ("series", _) => None,
            (_, Some(v)) => Some(v.to_string()),
            (_, None) => Some(id.to_string()),
        };
        if let Some(playback_id) = playback_id {
            let num = |k: &str| st.get(k).and_then(Value::as_f64).unwrap_or(0.0).max(0.0) as u64;
            let offset = num("timeOffset");
            let duration = num("duration");
            let flagged = num("flaggedWatched") > 0 || (offset == 0 && num("timesWatched") > 0 && kind != "series");
            if offset > 0 || flagged {
                let i = history.iter().position(|e| e.profile_id == pid && e.id == playback_id);
                let newer = i.map_or(true, |i| history[i].updated_at_ms < last);
                if newer {
                    let cur = i.map(|i| history[i].clone());
                    let (pos, dur) = if flagged && offset == 0 { (duration.max(1), duration.max(1)) } else { (offset, duration) };
                    let mut e = record_progress(cur, pid.to_string(), playback_id.clone(), kind.to_string(), name.to_string(), poster.clone(), pos, dur, last);
                    if flagged && offset == 0 {
                        e.watched = true;
                    }
                    match i {
                        Some(i) => history[i] = e,
                        None => history.push(e),
                    }
                    touched = true;
                }
            }
        }
        if touched {
            received += 1;
        }
    }

    let sent = out.len() as u32;
    let upload_body = if out.is_empty() {
        None
    } else {
        let changes: Vec<Value> = out.into_iter().map(|(_, v)| v).collect();
        Some(json!({"authKey": auth_key, "collection": "libraryItem", "changes": changes}).to_string())
    };
    Ok(StremioMerge { history, watchlist, upload_body, received, sent })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn lib(items: &str) -> String {
        format!(r#"{{"result":{items}}}"#)
    }

    #[test]
    fn login_and_addons() {
        let a = parse_stremio_login(r#"{"result":{"authKey":"k1","user":{"email":"a@b.se"}}}"#.into(), "x".into()).unwrap();
        assert_eq!(a, StremioAuth { auth_key: "k1".into(), email: "a@b.se".into() });
        let bad = parse_stremio_login(r#"{"error":{"message":"Wrong passphrase"}}"#.into(), "x".into());
        assert!(matches!(bad, Err(CoreError::BadResponse { .. })));
        let addons = parse_stremio_addons(
            r#"{"result":{"addons":[
              {"transportUrl":"https://v3-cinemeta.strem.io/manifest.json","manifest":{"id":"com.linvo.cinemeta"}},
              {"transportUrl":"http://127.0.0.1:11470/local-addon/manifest.json","manifest":{"id":"org.stremio.local"}},
              {"transportUrl":"https://v3-cinemeta.strem.io/manifest.json","manifest":{"id":"dup"}}]}}"#
                .into(),
        )
        .unwrap();
        assert_eq!(addons, vec!["https://v3-cinemeta.strem.io".to_string()]);
        let out = parse_stremio_addons(r#"{"error":{"message":"Session does not exist"}}"#.into());
        assert!(matches!(out, Err(CoreError::SignedOut { .. })));
    }

    #[test]
    fn takes_in_progress_and_library() {
        let json = lib(
            r#"[
          {"_id":"tt1","name":"Dune","type":"movie","poster":"p.jpg","removed":false,"temp":false,"_mtime":"2026-11-01T10:00:00Z",
           "state":{"lastWatched":"2026-11-01T10:00:00Z","timeOffset":600000,"duration":6000000,"video_id":"tt1"}},
          {"_id":"tt2","name":"Show","type":"series","removed":true,"temp":true,"_mtime":"2026-11-02T10:00:00Z",
           "state":{"lastWatched":"2026-11-02T10:00:00Z","timeOffset":0,"duration":3000000,"flaggedWatched":1,"video_id":"tt2:1:3"}},
          {"_id":"x","type":"other","name":"Channel"}
        ]"#,
        );
        let m = stremio_library_merge(json, "k".into(), vec![], vec![], "p".into(), 0, 1).unwrap();
        assert_eq!(m.received, 2);
        assert!(m.upload_body.is_none());
        assert_eq!(m.watchlist.len(), 1);
        assert_eq!(m.watchlist[0].id, "tt1");
        let dune = m.history.iter().find(|e| e.id == "tt1").unwrap();
        assert_eq!(dune.position_ms, 600_000);
        assert!(!dune.watched);
        let ep = m.history.iter().find(|e| e.id == "tt2:1:3").unwrap();
        assert!(ep.watched);
        assert_eq!(ep.updated_at_ms, iso_to_ms("2026-11-02T10:00:00Z").unwrap());
    }

    #[test]
    fn sends_newer_local_changes_and_keeps_newer_remote() {
        let t0 = iso_to_ms("2026-11-01T10:00:00Z").unwrap();
        let json = lib(
            r#"[{"_id":"tt1","name":"Dune","type":"movie","removed":false,"temp":false,"_mtime":"2026-11-01T10:00:00Z",
               "state":{"lastWatched":"2026-11-01T10:00:00Z","timeOffset":600000,"duration":6000000,"video_id":"tt1","timesWatched":0}}]"#,
        );
        // watched further here, later than Stremio's copy
        let local = record_progress(None, "p".into(), "tt1".into(), "movie".into(), "Dune".into(), None, 5_900_000, 6_000_000, t0 + 60_000);
        let ep = record_progress(None, "p".into(), "tt9:2:5".into(), "series".into(), "Ep".into(), None, 100_000, 3_000_000, t0 + 120_000);
        let other_profile = record_progress(None, "q".into(), "tt8".into(), "movie".into(), "X".into(), None, 1, 2, t0 + 1);
        let removed = WatchlistItem { profile_id: "p".into(), id: "tt1".into(), kind: "movie".into(), title: "Dune".into(), poster: None, added_at_ms: t0 - 5, updated_at_ms: t0 + 5_000, deleted: true };
        let m = stremio_library_merge(json, "k".into(), vec![local, ep, other_profile], vec![removed], "p".into(), t0 - 1, t0 + 200_000).unwrap();
        let body: Value = serde_json::from_str(m.upload_body.as_deref().unwrap()).unwrap();
        let changes = body["changes"].as_array().unwrap();
        assert_eq!(m.sent, 2);
        let dune = changes.iter().find(|c| c["_id"] == "tt1").unwrap();
        assert_eq!(dune["state"]["timesWatched"], 1);
        assert_eq!(dune["state"]["flaggedWatched"], 1);
        assert_eq!(dune["removed"], true, "the watchlist removal rides on the same item");
        let show = changes.iter().find(|c| c["_id"] == "tt9").unwrap();
        assert_eq!(show["state"]["video_id"], "tt9:2:5");
        assert_eq!(show["state"]["season"], 2);
        assert_eq!(show["state"]["episode"], 5);
        assert_eq!(show["state"]["timeOffset"], 100_000);
        // Stremio's older copy of Dune does not overwrite the newer local one
        let dune_here = m.history.iter().find(|e| e.id == "tt1").unwrap();
        assert!(dune_here.watched);
        assert!(m.watchlist.iter().find(|w| w.id == "tt1").unwrap().deleted);
    }
}
