//! Saving and loading, in one JSON format that every platform reads and writes.
//!
//! The apps keep their lists as the text these functions make, so a backup from one device can
//! be read on another, and a format change happens once, here. Decoding never fails: a damaged
//! or older file gives what could be read (unknown fields are skipped, missing ones default).

use serde::{de::DeserializeOwned, Serialize};

use crate::addon::{AddonManifest, MetaItem};
use crate::sync::WatchlistItem;
use crate::watch::{SeriesNextUp, WatchEntry};

/// Someone who watches: their own history, watchlist and next episodes.
#[derive(Debug, Clone, PartialEq, Default, serde::Serialize, serde::Deserialize, uniffi::Record)]
#[serde(default)]
pub struct Profile {
    pub id: String,
    pub name: String,
    /// Accent of the profile's badge, `#RRGGBB`.
    pub color: String,
    /// Photo as an address or a `data:` URI, when one was set.
    pub photo: Option<String>,
    pub created_at_ms: i64,
    pub updated_at_ms: i64,
    pub deleted: bool,
}

/// Badge colours new profiles take in turn, from the Neon palette.
pub const PROFILE_COLORS: [&str; 6] = ["#FF3D9A", "#3EF0FF", "#FFC46B", "#B98CFF", "#7CFFB2", "#FF8A5B"];

#[uniffi::export]
pub fn new_profile(name: String, existing: Vec<Profile>, now_ms: i64) -> Profile {
    let live = existing.iter().filter(|p| !p.deleted).count();
    // ids only need to be unique per Apple ID / install; time + count + name hash is enough
    let mut h: u32 = 2166136261;
    for b in name.bytes() {
        h ^= b as u32;
        h = h.wrapping_mul(16777619);
    }
    Profile {
        id: format!("p{now_ms:x}{:x}{h:08x}", existing.len()),
        name: name.trim().to_string(),
        color: PROFILE_COLORS[live % PROFILE_COLORS.len()].to_string(),
        photo: None,
        created_at_ms: now_ms,
        updated_at_ms: now_ms,
        deleted: false,
    }
}

fn encode<T: Serialize>(v: &T) -> String {
    serde_json::to_string(v).unwrap_or_else(|_| "[]".into())
}

fn decode<T: DeserializeOwned>(json: &str) -> Vec<T> {
    // Read item by item so one bad entry does not cost the others
    match serde_json::from_str::<Vec<serde_json::Value>>(json) {
        Ok(list) => list.into_iter().filter_map(|v| serde_json::from_value(v).ok()).collect(),
        Err(_) => Vec::new(),
    }
}

#[uniffi::export]
pub fn encode_watch_entries(list: Vec<WatchEntry>) -> String {
    encode(&list)
}

#[uniffi::export]
pub fn decode_watch_entries(json: String) -> Vec<WatchEntry> {
    decode(&json)
}

#[uniffi::export]
pub fn encode_next_up(list: Vec<SeriesNextUp>) -> String {
    encode(&list)
}

#[uniffi::export]
pub fn decode_next_up(json: String) -> Vec<SeriesNextUp> {
    decode(&json)
}

#[uniffi::export]
pub fn encode_watchlist(list: Vec<WatchlistItem>) -> String {
    encode(&list)
}

#[uniffi::export]
pub fn decode_watchlist(json: String) -> Vec<WatchlistItem> {
    decode(&json)
}

#[uniffi::export]
pub fn encode_manifests(list: Vec<AddonManifest>) -> String {
    encode(&list)
}

#[uniffi::export]
pub fn decode_manifests(json: String) -> Vec<AddonManifest> {
    decode(&json)
}

#[uniffi::export]
pub fn encode_meta_items(list: Vec<MetaItem>) -> String {
    encode(&list)
}

#[uniffi::export]
pub fn decode_meta_items(json: String) -> Vec<MetaItem> {
    decode(&json)
}

#[uniffi::export]
pub fn encode_profiles(list: Vec<Profile>) -> String {
    encode(&list)
}

#[uniffi::export]
pub fn decode_profiles(json: String) -> Vec<Profile> {
    decode(&json)
}

/// The newest `max` entries, removal markers older than 90 days dropped. Apple TV keeps only a
/// little local data (iCloud holds the rest), so the apps trim before saving.
#[uniffi::export]
pub fn trim_history(list: Vec<WatchEntry>, max: u32, now_ms: i64) -> Vec<WatchEntry> {
    let cutoff = now_ms - 90 * 24 * 3600 * 1000;
    let mut list: Vec<WatchEntry> = list.into_iter().filter(|e| !(e.deleted && e.updated_at_ms < cutoff)).collect();
    list.sort_by(|a, b| b.updated_at_ms.cmp(&a.updated_at_ms));
    list.truncate(max as usize);
    list
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trips_and_survives_damage() {
        let e = crate::watch::record_progress(None, "p".into(), "tt1".into(), "movie".into(), "Dune".into(), None, 60_000, 6_000_000, 42);
        let json = encode_watch_entries(vec![e.clone()]);
        assert_eq!(decode_watch_entries(json), vec![e.clone()]);
        // an older file without newer fields, an unknown field, and a broken entry
        let old = r#"[{"profile_id":"p","id":"tt2","title":"Old","position_ms":5,"duration_ms":10,"future":1}, 7]"#;
        let read = decode_watch_entries(old.into());
        assert_eq!(read.len(), 1);
        assert_eq!(read[0].id, "tt2");
        assert!(!read[0].watched);
        assert!(decode_watch_entries("not json".into()).is_empty());
    }

    #[test]
    fn profiles_get_distinct_ids_and_colors() {
        let a = new_profile(" Adrian ".into(), vec![], 1000);
        let b = new_profile("Adrian".into(), vec![a.clone()], 1000);
        assert_eq!(a.name, "Adrian");
        assert_ne!(a.id, b.id);
        assert_ne!(a.color, b.color);
        assert_eq!(decode_profiles(encode_profiles(vec![a.clone()])), vec![a]);
    }

    #[test]
    fn trims_newest_first() {
        let mk = |id: &str, at: i64, deleted: bool| {
            let mut e = crate::watch::record_progress(None, "p".into(), id.into(), "movie".into(), id.into(), None, 1, 2, at);
            e.deleted = deleted;
            e
        };
        let now = 200 * 24 * 3600 * 1000;
        let list = vec![mk("a", 1, true), mk("b", now - 5, false), mk("c", now - 1, false), mk("d", now - 9, false)];
        let ids: Vec<_> = trim_history(list, 2, now).into_iter().map(|e| e.id).collect();
        assert_eq!(ids, vec!["c", "b"]);
    }
}
