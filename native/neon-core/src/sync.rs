//! The sync rule: which change wins when two devices (or iCloud and Trakt) disagree.
//!
//! Every synced record carries `updated_at_ms` and a `deleted` marker. For each key the newer
//! change wins; on an exact tie a removal wins (it was meant), then "watched", then the further
//! position. The result says what to upload and what to store locally, so a sync never sends
//! back what it just received.

use crate::watch::{SeriesNextUp, WatchEntry};

/// Something on someone's watchlist.
#[derive(Debug, Clone, PartialEq, Default, serde::Serialize, serde::Deserialize, uniffi::Record)]
#[serde(default)]
pub struct WatchlistItem {
    pub profile_id: String,
    pub id: String,
    pub kind: String,
    pub title: String,
    pub poster: Option<String>,
    pub added_at_ms: i64,
    pub updated_at_ms: i64,
    pub deleted: bool,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct WatchMerge {
    /// The full merged set, to show.
    pub merged: Vec<WatchEntry>,
    /// Newer here than in the cloud: send these.
    pub to_upload: Vec<WatchEntry>,
    /// Newer in the cloud than here: save these locally.
    pub to_store: Vec<WatchEntry>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct WatchlistMerge {
    pub merged: Vec<WatchlistItem>,
    pub to_upload: Vec<WatchlistItem>,
    pub to_store: Vec<WatchlistItem>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NextUpMerge {
    pub merged: Vec<SeriesNextUp>,
    pub to_upload: Vec<SeriesNextUp>,
    pub to_store: Vec<SeriesNextUp>,
}

trait Synced: Clone + PartialEq {
    fn key(&self) -> (String, String);
    fn updated(&self) -> i64;
    /// Tie-break order: higher wins.
    fn weight(&self) -> (bool, bool, u64);
}

impl Synced for WatchEntry {
    fn key(&self) -> (String, String) {
        (self.profile_id.clone(), self.id.clone())
    }
    fn updated(&self) -> i64 {
        self.updated_at_ms
    }
    fn weight(&self) -> (bool, bool, u64) {
        (self.deleted, self.watched, self.position_ms)
    }
}

impl Synced for WatchlistItem {
    fn key(&self) -> (String, String) {
        (self.profile_id.clone(), self.id.clone())
    }
    fn updated(&self) -> i64 {
        self.updated_at_ms
    }
    fn weight(&self) -> (bool, bool, u64) {
        (self.deleted, false, 0)
    }
}

impl Synced for SeriesNextUp {
    fn key(&self) -> (String, String) {
        (self.profile_id.clone(), self.series_id.clone())
    }
    fn updated(&self) -> i64 {
        self.updated_at_ms
    }
    fn weight(&self) -> (bool, bool, u64) {
        (self.deleted, self.is_complete, ((self.next_season as u64) << 32) | self.next_episode as u64)
    }
}

fn merge<T: Synced>(local: Vec<T>, remote: Vec<T>) -> (Vec<T>, Vec<T>, Vec<T>) {
    use std::collections::BTreeMap;
    let mut l: BTreeMap<(String, String), T> = BTreeMap::new();
    for x in local {
        let k = x.key();
        match l.get(&k) {
            Some(old) if !newer(&x, old) => {}
            _ => {
                l.insert(k, x);
            }
        }
    }
    let mut r: BTreeMap<(String, String), T> = BTreeMap::new();
    for x in remote {
        let k = x.key();
        match r.get(&k) {
            Some(old) if !newer(&x, old) => {}
            _ => {
                r.insert(k, x);
            }
        }
    }
    let mut merged = Vec::new();
    let mut up = Vec::new();
    let mut store = Vec::new();
    let keys: std::collections::BTreeSet<_> = l.keys().chain(r.keys()).cloned().collect();
    for k in keys {
        match (l.get(&k), r.get(&k)) {
            (Some(a), None) => {
                up.push(a.clone());
                merged.push(a.clone());
            }
            (None, Some(b)) => {
                store.push(b.clone());
                merged.push(b.clone());
            }
            (Some(a), Some(b)) => {
                if a == b {
                    merged.push(a.clone());
                } else if newer(a, b) {
                    up.push(a.clone());
                    merged.push(a.clone());
                } else {
                    store.push(b.clone());
                    merged.push(b.clone());
                }
            }
            (None, None) => {}
        }
    }
    (merged, up, store)
}

/// Whether `a` should replace `b`.
fn newer<T: Synced>(a: &T, b: &T) -> bool {
    a.updated() > b.updated() || (a.updated() == b.updated() && a.weight() > b.weight())
}

#[uniffi::export]
pub fn merge_watch(local: Vec<WatchEntry>, remote: Vec<WatchEntry>) -> WatchMerge {
    let (merged, to_upload, to_store) = merge(local, remote);
    WatchMerge { merged, to_upload, to_store }
}

#[uniffi::export]
pub fn merge_watchlist(local: Vec<WatchlistItem>, remote: Vec<WatchlistItem>) -> WatchlistMerge {
    let (merged, to_upload, to_store) = merge(local, remote);
    WatchlistMerge { merged, to_upload, to_store }
}

#[uniffi::export]
pub fn merge_next_up(local: Vec<SeriesNextUp>, remote: Vec<SeriesNextUp>) -> NextUpMerge {
    let (merged, to_upload, to_store) = merge(local, remote);
    NextUpMerge { merged, to_upload, to_store }
}

/// A record name iCloud accepts (ASCII, at most 255, no leading underscore) that is the same on
/// every device for the same profile and title: `<kind>.<profile>.<id>` with anything outside
/// `A-Za-z0-9:-` written as `_xx` hex.
#[uniffi::export]
pub fn cloud_record_name(kind: String, profile_id: String, id: String) -> String {
    let enc = |s: &str| -> String {
        s.bytes()
            .map(|b| {
                if b.is_ascii_alphanumeric() || b == b':' || b == b'-' {
                    (b as char).to_string()
                } else {
                    format!("_{b:02x}")
                }
            })
            .collect()
    };
    let name = format!("{}.{}.{}", enc(&kind), enc(&profile_id), enc(&id));
    if name.len() <= 255 {
        name
    } else {
        // Very long ids: keep the start readable and end with a stable hash
        let mut h: u64 = 0xcbf29ce484222325;
        for b in name.bytes() {
            h ^= b as u64;
            h = h.wrapping_mul(0x100000001b3);
        }
        format!("{}~{h:016x}", &name[..200])
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn w(id: &str, pos: u64, watched: bool, at: i64, deleted: bool) -> WatchEntry {
        WatchEntry {
            profile_id: "p".into(),
            id: id.into(),
            kind: "movie".into(),
            title: id.into(),
            poster: None,
            background: None,
            logo: None,
            position_ms: pos,
            duration_ms: 100,
            watched,
            updated_at_ms: at,
            deleted,
        }
    }

    #[test]
    fn newer_change_wins_both_ways() {
        let local = vec![w("a", 10, false, 5, false), w("b", 20, false, 1, false), w("c", 1, false, 1, false)];
        let remote = vec![w("a", 40, false, 3, false), w("b", 60, false, 9, false), w("d", 7, false, 2, false)];
        let m = merge_watch(local, remote);
        let pos: Vec<_> = m.merged.iter().map(|x| (x.id.as_str(), x.position_ms)).collect();
        assert_eq!(pos, vec![("a", 10), ("b", 60), ("c", 1), ("d", 7)]);
        let up: Vec<_> = m.to_upload.iter().map(|x| x.id.as_str()).collect();
        let store: Vec<_> = m.to_store.iter().map(|x| x.id.as_str()).collect();
        assert_eq!(up, vec!["a", "c"]);
        assert_eq!(store, vec!["b", "d"]);
    }

    #[test]
    fn ties_prefer_removal_then_watched() {
        let m = merge_watch(vec![w("a", 10, false, 5, false)], vec![w("a", 10, false, 5, true)]);
        assert!(m.merged[0].deleted);
        assert_eq!(m.to_store.len(), 1);
        let m = merge_watch(vec![w("a", 99, true, 5, false)], vec![w("a", 10, false, 5, false)]);
        assert!(m.merged[0].watched);
        assert_eq!(m.to_upload.len(), 1);
    }

    #[test]
    fn same_on_both_sides_sends_nothing() {
        let m = merge_watch(vec![w("a", 1, false, 1, false)], vec![w("a", 1, false, 1, false)]);
        assert!(m.to_upload.is_empty() && m.to_store.is_empty());
        assert_eq!(m.merged.len(), 1);
    }

    #[test]
    fn a_stale_device_cannot_bring_back_a_removal() {
        // removed on the phone at t=10; the TV was offline with an older copy from t=4
        let m = merge_watch(vec![w("a", 50, false, 4, false)], vec![w("a", 50, false, 10, true)]);
        assert!(m.merged[0].deleted);
        assert!(m.to_upload.is_empty());
    }

    #[test]
    fn profiles_never_mix() {
        let mut other = w("a", 80, false, 99, false);
        other.profile_id = "q".into();
        let m = merge_watch(vec![w("a", 10, false, 1, false)], vec![other]);
        assert_eq!(m.merged.len(), 2);
    }

    #[test]
    fn record_names_are_safe_and_stable() {
        assert_eq!(cloud_record_name("w".into(), "p1".into(), "tt1:2:3".into()), "w.p1.tt1:2:3");
        assert_eq!(cloud_record_name("w".into(), "p 1".into(), "_x/é".into()), "w.p_201._5fx_2f_c3_a9");
        let long = cloud_record_name("w".into(), "p".into(), "x".repeat(400));
        assert!(long.len() <= 255);
        assert_eq!(long, cloud_record_name("w".into(), "p".into(), "x".repeat(400)));
    }
}
