//! What someone has watched, and what to watch next.
//!
//! Ported rule for rule from the Android app so every platform shows the same "Continue
//! watching" row: domain/ContinueWatching.kt, domain/EpisodeUtils.kt and the watched threshold in
//! ui/player/PlayerViewModel.kt.

use crate::addon::Video;

/// Past 90 % (above Trakt's 80 % minimum), or the last 30 seconds, counts as watched.
pub const WATCHED_RATIO: f64 = 0.90;
pub const WATCHED_REMAINING_MS: u64 = 30_000;

/// A film or an episode someone has started. `id` is the playback id: the film's id, or
/// `<series id>:<season>:<episode>` for an episode, whatever id format the addon uses.
#[derive(Debug, Clone, PartialEq, Default, serde::Serialize, serde::Deserialize, uniffi::Record)]
#[serde(default)]
pub struct WatchEntry {
    pub profile_id: String,
    pub id: String,
    pub kind: String,
    pub title: String,
    pub poster: Option<String>,
    pub background: Option<String>,
    pub logo: Option<String>,
    pub position_ms: u64,
    pub duration_ms: u64,
    pub watched: bool,
    /// When this entry last changed, in milliseconds since 1970; the sync rule compares these.
    pub updated_at_ms: i64,
    /// A removal kept as a marker, so a device that was offline does not bring it back.
    pub deleted: bool,
}

/// The next episode of a series whose started episodes are all watched.
#[derive(Debug, Clone, PartialEq, Default, serde::Serialize, serde::Deserialize, uniffi::Record)]
#[serde(default)]
pub struct SeriesNextUp {
    pub profile_id: String,
    pub series_id: String,
    pub title: String,
    pub poster: Option<String>,
    pub next_season: u32,
    pub next_episode: u32,
    pub next_episode_title: Option<String>,
    /// Air date (ISO), None = already aired.
    pub next_released: Option<String>,
    /// Every episode so far is watched.
    pub is_complete: bool,
    /// A new episode aired after the viewer had caught up.
    pub is_new_episode: bool,
    pub updated_at_ms: i64,
    pub deleted: bool,
}

/// One card in "Continue watching".
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ContinueEntry {
    /// The film's or series' own id, as the details screen opens it.
    pub id: String,
    pub kind: String,
    pub name: String,
    pub poster: Option<String>,
    pub background: Option<String>,
    pub logo: Option<String>,
    pub progress: f32,
    pub has_new_episode: bool,
    pub season: Option<u32>,
    pub episode: Option<u32>,
    /// The playback id to resume, when there is one.
    pub resume_id: Option<String>,
    pub resume_position_ms: u64,
    pub last_activity_ms: i64,
}

/// Tracking id for an episode: always `<series>:<season>:<episode>`, as on Android.
#[uniffi::export]
pub fn episode_playback_id(series_id: String, video: Video) -> String {
    format!("{series_id}:{}:{}", video.season, video.episode)
}

/// The id a stream request uses: the addon's own episode id, or the playback id if it has none.
#[uniffi::export]
pub fn episode_stream_id(series_id: String, video: Video) -> String {
    if video.id.trim().is_empty() {
        format!("{series_id}:{}:{}", video.season, video.episode)
    } else {
        video.id
    }
}

/// "S1:E2 - Title", season and episode 0 shown as 1, as on Android.
#[uniffi::export]
pub fn episode_display_title(video: Video) -> String {
    format!("S{}:E{} - {}", video.season.max(1), video.episode.max(1), video.title)
}

#[uniffi::export]
pub fn is_episode_playback_id(playback_id: String) -> bool {
    is_episode_id(&playback_id)
}

fn is_episode_id(id: &str) -> bool {
    let parts: Vec<&str> = id.split(':').collect();
    parts.len() >= 3
        && parts[parts.len() - 2].parse::<i64>().is_ok()
        && parts[parts.len() - 1].parse::<i64>().is_ok()
}

/// The series' own id from an episode's playback id ("tt123:2:5" is "tt123").
#[uniffi::export]
pub fn canonical_series_id(playback_id: String) -> String {
    canonical(&playback_id)
}

fn canonical(id: &str) -> String {
    if !is_episode_id(id) {
        return id.to_string();
    }
    let parts: Vec<&str> = id.split(':').collect();
    parts[..parts.len() - 2].join(":")
}

/// The episode after the one just played, among regular episodes (season and episode above 0).
#[uniffi::export]
pub fn find_next_episode(series_id: String, current_playback_id: String, episodes: Vec<Video>) -> Option<Video> {
    let mut regular: Vec<Video> = episodes.into_iter().filter(|e| e.season > 0 && e.episode > 0).collect();
    if regular.is_empty() {
        return None;
    }
    regular.sort_by_key(|e| (e.season, e.episode));
    let mut index = regular
        .iter()
        .position(|e| format!("{series_id}:{}:{}", e.season, e.episode) == current_playback_id);
    if index.is_none() {
        // Old-format playback ids: match by the season and episode numbers at the end
        let parts: Vec<&str> = current_playback_id.split(':').collect();
        if parts.len() >= 3 {
            if let (Ok(s), Ok(e)) = (parts[parts.len() - 2].parse::<u32>(), parts[parts.len() - 1].parse::<u32>()) {
                index = regular.iter().position(|v| v.season == s && v.episode == e);
            }
        }
    }
    let i = index?;
    regular.get(i + 1).cloned()
}

/// The entry to save after playing: position, duration and whether it now counts as watched.
#[uniffi::export]
pub fn record_progress(
    existing: Option<WatchEntry>,
    profile_id: String,
    id: String,
    kind: String,
    title: String,
    poster: Option<String>,
    position_ms: u64,
    duration_ms: u64,
    now_ms: i64,
) -> WatchEntry {
    let duration = duration_ms;
    let position = if duration > 0 { position_ms.min(duration) } else { position_ms };
    let ratio = if duration > 0 { position as f64 / duration as f64 } else { 0.0 };
    let remaining = duration.saturating_sub(position);
    let completed = duration > 0 && (ratio >= WATCHED_RATIO || remaining <= WATCHED_REMAINING_MS);
    WatchEntry {
        poster: poster.or_else(|| existing.as_ref().and_then(|e| e.poster.clone())),
        background: existing.as_ref().and_then(|e| e.background.clone()),
        logo: existing.as_ref().and_then(|e| e.logo.clone()),
        kind: if kind.trim().is_empty() { "movie".into() } else { kind },
        profile_id,
        id,
        title,
        position_ms: position,
        duration_ms: duration,
        watched: completed,
        updated_at_ms: now_ms,
        deleted: false,
    }
}

/// Where a title should start: from the saved position unless it was watched or barely begun.
#[uniffi::export]
pub fn resume_position_ms(entry: Option<WatchEntry>) -> u64 {
    match entry {
        Some(e) if !e.watched && !e.deleted && e.position_ms >= 10_000 => e.position_ms,
        _ => 0,
    }
}

fn progress(e: &WatchEntry) -> f32 {
    if e.duration_ms == 0 {
        0.0
    } else {
        (e.position_ms as f64 / e.duration_ms as f64) as f32
    }
}

/// "Continue watching", most recent first: what was left part way, then the next episode of
/// series whose started episodes are all watched, once that episode has aired. `today` is an
/// ISO date (`2026-11-04`).
#[uniffi::export]
pub fn continue_watching(
    history: Vec<WatchEntry>,
    next_up: Vec<SeriesNextUp>,
    profile_id: String,
    today: String,
) -> Vec<ContinueEntry> {
    let mut history: Vec<WatchEntry> = history
        .into_iter()
        .filter(|e| e.profile_id == profile_id && !e.deleted)
        .collect();
    // Android's query hands these over newest first; do the same before choosing per series
    history.sort_by(|a, b| b.updated_at_ms.cmp(&a.updated_at_ms));

    let mut result: Vec<ContinueEntry> = Vec::new();
    let mut series_included = std::collections::HashSet::new();
    let mut seen_movies = std::collections::HashSet::new();

    for e in history.iter().filter(|e| !e.watched) {
        if e.kind == "series" {
            let canonical_id = canonical(&e.id);
            // the newest episode-format entry of this series wins
            let chosen = history
                .iter()
                .filter(|x| !x.watched && x.kind == "series" && canonical(&x.id) == canonical_id)
                .find(|x| is_episode_id(&x.id))
                .unwrap_or(e);
            if chosen.id != e.id || !series_included.insert(canonical_id.clone()) {
                continue;
            }
            let parts: Vec<&str> = chosen.id.split(':').collect();
            let ep = is_episode_id(&chosen.id);
            result.push(ContinueEntry {
                id: canonical_id,
                kind: e.kind.clone(),
                name: e.title.clone(),
                poster: e.poster.clone(),
                background: chosen.background.clone(),
                logo: chosen.logo.clone(),
                progress: progress(chosen),
                has_new_episode: false,
                season: if ep { parts[parts.len() - 2].parse().ok() } else { None },
                episode: if ep { parts[parts.len() - 1].parse().ok() } else { None },
                resume_id: Some(chosen.id.clone()),
                resume_position_ms: chosen.position_ms,
                last_activity_ms: chosen.updated_at_ms,
            });
        } else {
            if !seen_movies.insert(e.id.clone()) {
                continue;
            }
            result.push(ContinueEntry {
                id: e.id.clone(),
                kind: e.kind.clone(),
                name: e.title.clone(),
                poster: e.poster.clone(),
                background: e.background.clone(),
                logo: e.logo.clone(),
                progress: progress(e),
                has_new_episode: false,
                season: None,
                episode: None,
                resume_id: Some(e.id.clone()),
                resume_position_ms: e.position_ms,
                last_activity_ms: e.updated_at_ms,
            });
        }
    }

    let today = today.get(..10).unwrap_or(&today).to_string();
    for n in next_up.iter().filter(|n| n.profile_id == profile_id && !n.deleted) {
        if series_included.contains(&n.series_id) {
            continue;
        }
        let released = n.next_released.as_deref().map(|r| r.get(..10).unwrap_or(r));
        if let Some(r) = released {
            if r > today.as_str() {
                continue;
            }
        }
        if n.is_complete && released.is_none() {
            continue;
        }
        result.push(ContinueEntry {
            id: n.series_id.clone(),
            kind: "series".into(),
            name: n.title.clone(),
            poster: n.poster.clone(),
            background: None,
            logo: None,
            progress: 0.0,
            has_new_episode: n.is_complete || n.is_new_episode,
            season: Some(n.next_season),
            episode: Some(n.next_episode),
            resume_id: Some(format!("{}:{}:{}", n.series_id, n.next_season, n.next_episode)),
            resume_position_ms: 0,
            last_activity_ms: n.updated_at_ms,
        });
    }

    result.sort_by(|a, b| b.last_activity_ms.cmp(&a.last_activity_ms));
    result
}

/// After an episode is watched: the next-up record for its series, or a completed marker when
/// it was the last aired one.
#[uniffi::export]
pub fn next_up_after(
    profile_id: String,
    series_id: String,
    title: String,
    poster: Option<String>,
    watched_playback_id: String,
    episodes: Vec<Video>,
    now_ms: i64,
) -> SeriesNextUp {
    let next = find_next_episode(series_id.clone(), watched_playback_id.clone(), episodes);
    match next {
        Some(v) => SeriesNextUp {
            profile_id,
            series_id,
            title,
            poster,
            next_season: v.season,
            next_episode: v.episode,
            next_episode_title: Some(v.title),
            next_released: v.released,
            is_complete: false,
            is_new_episode: false,
            updated_at_ms: now_ms,
            deleted: false,
        },
        None => {
            let parts: Vec<&str> = watched_playback_id.split(':').collect();
            let (s, e) = if parts.len() >= 3 {
                (parts[parts.len() - 2].parse().unwrap_or(0), parts[parts.len() - 1].parse().unwrap_or(0))
            } else {
                (0, 0)
            };
            SeriesNextUp {
                profile_id,
                series_id,
                title,
                poster,
                next_season: s,
                next_episode: e,
                next_episode_title: None,
                next_released: None,
                is_complete: true,
                is_new_episode: false,
                updated_at_ms: now_ms,
                deleted: false,
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ep(s: u32, e: u32) -> Video {
        Video { id: format!("tt9:{s}:{e}"), title: format!("E{e}"), season: s, episode: e, released: None, thumbnail: None, overview: None }
    }

    fn entry(id: &str, kind: &str, pos: u64, dur: u64, watched: bool, at: i64) -> WatchEntry {
        WatchEntry {
            profile_id: "p1".into(),
            id: id.into(),
            kind: kind.into(),
            title: id.into(),
            poster: None,
            background: None,
            logo: None,
            position_ms: pos,
            duration_ms: dur,
            watched,
            updated_at_ms: at,
            deleted: false,
        }
    }

    #[test]
    fn watched_threshold_matches_android() {
        let r = |p, d| record_progress(None, "p".into(), "tt1".into(), "".into(), "T".into(), None, p, d, 1).watched;
        assert!(!r(4_800_000, 6_000_000)); // 80 min of 100
        assert!(r(5_400_000, 6_000_000)); // 90 %
        assert!(r(5_000_000, 5_029_000)); // last 30 s
        assert!(!r(0, 0));
        assert_eq!(record_progress(None, "p".into(), "x".into(), "".into(), "T".into(), None, 1, 2, 1).kind, "movie");
    }

    #[test]
    fn ids_like_android() {
        assert!(is_episode_playback_id("tt1:2:5".into()));
        assert!(!is_episode_playback_id("tt1".into()));
        assert!(!is_episode_playback_id("kitsu:123".into()));
        assert_eq!(canonical_series_id("kitsu:1:2:5".into()), "kitsu:1");
        assert_eq!(episode_display_title(ep(0, 0)), "S1:E1 - E0");
        let mut v = ep(1, 3);
        v.id = String::new();
        assert_eq!(episode_stream_id("tt9".into(), v), "tt9:1:3");
    }

    #[test]
    fn next_episode_skips_specials_and_crosses_seasons() {
        let eps = vec![ep(2, 1), ep(0, 1), ep(1, 2), ep(1, 1)];
        assert_eq!(find_next_episode("tt9".into(), "tt9:1:2".into(), eps.clone()).map(|v| (v.season, v.episode)), Some((2, 1)));
        assert_eq!(find_next_episode("tt9".into(), "old:1:1".into(), eps.clone()).map(|v| v.episode), Some(2));
        assert_eq!(find_next_episode("tt9".into(), "tt9:2:1".into(), eps), None);
    }

    #[test]
    fn continue_watching_picks_newest_episode_per_series() {
        let history = vec![
            entry("tt9:1:1", "series", 10, 100, false, 100),
            entry("tt9:1:2", "series", 50, 100, false, 300),
            entry("tt5", "movie", 30, 100, false, 200),
            entry("tt6", "movie", 95, 100, true, 400),
        ];
        let next = vec![SeriesNextUp {
            profile_id: "p1".into(),
            series_id: "tt7".into(),
            title: "Next".into(),
            poster: None,
            next_season: 3,
            next_episode: 1,
            next_episode_title: None,
            next_released: Some("2026-11-01T00:00:00.000Z".into()),
            is_complete: true,
            is_new_episode: false,
            updated_at_ms: 250,
            deleted: false,
        }];
        let mut future = next[0].clone();
        future.series_id = "tt8".into();
        future.next_released = Some("2027-01-01".into());
        let mut all_next = next.clone();
        all_next.push(future);

        let cw = continue_watching(history.clone(), all_next, "p1".into(), "2026-11-04".into());
        let ids: Vec<_> = cw.iter().map(|c| c.id.as_str()).collect();
        assert_eq!(ids, vec!["tt9", "tt7", "tt5"]);
        assert_eq!((cw[0].season, cw[0].episode), (Some(1), Some(2)));
        assert_eq!(cw[0].resume_id.as_deref(), Some("tt9:1:2"));
        assert!(cw[1].has_new_episode);
        assert_eq!(cw[1].resume_id.as_deref(), Some("tt7:3:1"));

        // another profile sees nothing of p1's
        assert!(continue_watching(history, next, "p2".into(), "2026-11-04".into()).is_empty());
    }

    #[test]
    fn next_up_after_last_episode_is_complete() {
        let eps = vec![ep(1, 1), ep(1, 2)];
        let n = next_up_after("p".into(), "tt9".into(), "S".into(), None, "tt9:1:1".into(), eps.clone(), 5);
        assert_eq!((n.next_season, n.next_episode, n.is_complete), (1, 2, false));
        let done = next_up_after("p".into(), "tt9".into(), "S".into(), None, "tt9:1:2".into(), eps, 6);
        assert!(done.is_complete);
    }

    #[test]
    fn resume_rules() {
        assert_eq!(resume_position_ms(Some(entry("a", "movie", 5_000, 100_000, false, 1))), 0);
        assert_eq!(resume_position_ms(Some(entry("a", "movie", 50_000, 100_000, false, 1))), 50_000);
        assert_eq!(resume_position_ms(Some(entry("a", "movie", 95_000, 100_000, true, 1))), 0);
        assert_eq!(resume_position_ms(None), 0);
    }
}
