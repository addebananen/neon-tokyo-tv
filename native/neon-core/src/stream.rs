//! Stream sources from addons: reading them, labelling them and putting the best first.
//!
//! Torrent sources are only flagged here (`is_torrent`): the Apple and Windows apps do not play
//! them, and the Android app keeps its own torrent code untouched.

use serde_json::Value;

use crate::addon::text;
use crate::error::CoreError;

/// How the file is packed, which decides which player can open it.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum StreamContainer {
    Mp4,
    Hls,
    Mkv,
    Webm,
    Avi,
    Unknown,
}

/// One playable source of a film or episode.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct StreamSource {
    pub addon_name: String,
    /// The addon's first line, often the provider and quality (`Torrentio\n4k`).
    pub name: String,
    /// The addon's longer text: file name, size, seeders, …
    pub title: String,
    pub url: Option<String>,
    pub info_hash: Option<String>,
    pub file_index: Option<u32>,
    pub youtube_id: Option<String>,
    pub external_url: Option<String>,
    /// HTTP headers the addon says the request needs (debrid proxies, some IPTV).
    pub request_headers: Vec<StreamHeader>,
    pub quality: StreamQuality,
    /// `4K`, `1080p`, … for the badge; empty when unknown.
    pub quality_label: String,
    /// `HDR`, `DV`, `HEVC`, `Atmos` … found in the text, in that order.
    pub tags: Vec<String>,
    pub size_label: Option<String>,
    pub container: StreamContainer,
    pub is_torrent: bool,
    /// True when Apple's own player (AVPlayer) can open it: MP4 and HLS, or an unknown
    /// container from a direct link, which is worth a try.
    pub apple_native: bool,
    pub binge_group: Option<String>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct StreamHeader {
    pub name: String,
    pub value: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, uniffi::Enum)]
pub enum StreamQuality {
    Unknown,
    Sd,
    Hd720,
    FullHd1080,
    Uhd4k,
}

#[uniffi::export]
pub fn parse_streams(addon_name: String, json: String) -> Result<Vec<StreamSource>, CoreError> {
    let v: Value = serde_json::from_str(&json)?;
    let list = v
        .get("streams")
        .and_then(Value::as_array)
        .ok_or_else(|| CoreError::BadResponse { reason: "no streams".into() })?;
    Ok(list.iter().filter_map(|s| parse_stream(&addon_name, s)).collect())
}

fn parse_stream(addon_name: &str, s: &Value) -> Option<StreamSource> {
    let url = text(s, "url");
    let info_hash = text(s, "infoHash");
    let youtube_id = text(s, "ytId");
    let external_url = text(s, "externalUrl");
    if url.is_none() && info_hash.is_none() && youtube_id.is_none() && external_url.is_none() {
        return None;
    }
    let name = text(s, "name").unwrap_or_else(|| addon_name.to_string());
    let title = text(s, "title").or_else(|| text(s, "description")).unwrap_or_default();
    let hints = s.get("behaviorHints");
    let filename = hints.and_then(|h| text(h, "filename"));
    let binge_group = hints.and_then(|h| text(h, "bingeGroup"));
    let request_headers = hints
        .and_then(|h| h.get("proxyHeaders"))
        .and_then(|p| p.get("request"))
        .and_then(Value::as_object)
        .map(|o| {
            o.iter()
                .filter_map(|(k, v)| v.as_str().map(|v| StreamHeader { name: k.clone(), value: v.to_string() }))
                .collect()
        })
        .unwrap_or_default();

    let haystack = format!("{name}\n{title}\n{}", filename.clone().unwrap_or_default());
    let quality = detect_quality(&haystack);
    let is_torrent = info_hash.is_some() && url.is_none();
    let container = detect_container(url.as_deref(), filename.as_deref(), &haystack);
    let apple_native = !is_torrent
        && url.is_some()
        && matches!(container, StreamContainer::Mp4 | StreamContainer::Hls | StreamContainer::Unknown);

    Some(StreamSource {
        addon_name: addon_name.to_string(),
        quality_label: quality_label(quality).to_string(),
        tags: detect_tags(&haystack),
        size_label: detect_size(&haystack),
        file_index: crate::addon::number(s, "fileIdx"),
        name,
        title,
        url,
        info_hash,
        youtube_id,
        external_url,
        request_headers,
        quality,
        container,
        is_torrent,
        apple_native,
        binge_group,
    })
}

fn quality_label(q: StreamQuality) -> &'static str {
    match q {
        StreamQuality::Uhd4k => "4K",
        StreamQuality::FullHd1080 => "1080p",
        StreamQuality::Hd720 => "720p",
        StreamQuality::Sd => "SD",
        StreamQuality::Unknown => "",
    }
}

fn detect_quality(s: &str) -> StreamQuality {
    let l = s.to_lowercase();
    if l.contains("2160p") || l.contains("4k") || l.contains("uhd") {
        StreamQuality::Uhd4k
    } else if l.contains("1080p") || l.contains("1080i") || l.contains("fhd") {
        StreamQuality::FullHd1080
    } else if l.contains("720p") {
        StreamQuality::Hd720
    } else if l.contains("480p") || l.contains("576p") || l.contains("360p") || l.contains("sd ") {
        StreamQuality::Sd
    } else {
        StreamQuality::Unknown
    }
}

fn detect_tags(s: &str) -> Vec<String> {
    let l = s.to_lowercase();
    let words: Vec<&str> = l.split(|c: char| !c.is_ascii_alphanumeric() && c != '+').collect();
    let has = |w: &str| words.iter().any(|x| *x == w);
    let mut tags = Vec::new();
    if has("dv") || l.contains("dolby vision") || l.contains("dovi") {
        tags.push("DV".to_string());
    }
    if has("hdr") || has("hdr10") || has("hdr10+") {
        tags.push("HDR".to_string());
    }
    if has("hevc") || has("x265") || has("h265") {
        tags.push("HEVC".to_string());
    }
    if has("atmos") {
        tags.push("Atmos".to_string());
    }
    if has("remux") {
        tags.push("Remux".to_string());
    }
    tags
}

fn detect_size(s: &str) -> Option<String> {
    // Addons write sizes like "💾 12.4 GB", "Size: 900 MB" or "1.2GiB"
    let tokens: Vec<&str> = s.split_whitespace().collect();
    for (i, t) in tokens.iter().enumerate() {
        let digits: String = t.chars().take_while(|c| c.is_ascii_digit() || *c == '.' || *c == ',').collect();
        let rest = &t[digits.len()..];
        let unit = if rest.is_empty() { tokens.get(i + 1).copied().unwrap_or("") } else { rest };
        let unit = unit.trim_end_matches(|c: char| !c.is_ascii_alphabetic());
        let unit = match unit {
            "GB" | "GiB" => "GB",
            "MB" | "MiB" => "MB",
            _ => continue,
        };
        if digits.chars().any(|c| c.is_ascii_digit()) {
            return Some(format!("{} {unit}", digits.replace(',', ".")));
        }
    }
    None
}

fn detect_container(url: Option<&str>, filename: Option<&str>, text: &str) -> StreamContainer {
    let pick = |s: &str| -> Option<StreamContainer> {
        let l = s.to_lowercase();
        let path = l.split(['?', '#']).next().unwrap_or("");
        if path.ends_with(".m3u8") || l.contains("m3u8") {
            Some(StreamContainer::Hls)
        } else if path.ends_with(".mkv") {
            Some(StreamContainer::Mkv)
        } else if path.ends_with(".mp4") || path.ends_with(".m4v") || path.ends_with(".mov") {
            Some(StreamContainer::Mp4)
        } else if path.ends_with(".webm") {
            Some(StreamContainer::Webm)
        } else if path.ends_with(".avi") {
            Some(StreamContainer::Avi)
        } else {
            None
        }
    };
    if let Some(c) = filename.and_then(pick) {
        return c;
    }
    if let Some(c) = url.and_then(pick) {
        return c;
    }
    // A file name inside the addon's text ("Movie.2024.2160p.mkv")
    for line in text.lines() {
        for word in line.split_whitespace() {
            if let Some(c) = pick(word) {
                return c;
            }
        }
    }
    StreamContainer::Unknown
}

/// Sources in the order the source list shows them: playable on this device first, then by
/// quality, then the addon's own order. `apple` puts AVPlayer-friendly sources first.
#[uniffi::export]
pub fn rank_streams(streams: Vec<StreamSource>, apple: bool, hide_torrents: bool) -> Vec<StreamSource> {
    let mut indexed: Vec<(usize, StreamSource)> = streams
        .into_iter()
        .filter(|s| !(hide_torrents && s.is_torrent))
        .enumerate()
        .collect();
    indexed.sort_by(|(ia, a), (ib, b)| {
        let pa = if apple { a.apple_native } else { a.url.is_some() };
        let pb = if apple { b.apple_native } else { b.url.is_some() };
        pb.cmp(&pa).then(b.quality.cmp(&a.quality)).then(ia.cmp(ib))
    });
    indexed.into_iter().map(|(_, s)| s).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    const STREAMS: &str = r#"{"streams":[
        {"name":"Torrentio\n4k","title":"Dune.Part.Two.2024.2160p.UHD.BluRay.REMUX.DV.HDR.HEVC.Atmos.mkv\n👤 120 💾 61.2 GB","infoHash":"abc","fileIdx":0},
        {"name":"RD+ 1080p","title":"Dune Part Two 1080p WEB-DL\n💾 6,4 GB","url":"https://debrid.example/dl/dune.mp4?token=1"},
        {"name":"RD+ 4K","title":"Dune.Part.Two.2160p.mkv","url":"https://debrid.example/dl/123","behaviorHints":{"filename":"Dune.Part.Two.2160p.DV.mkv","bingeGroup":"rd-4k"}},
        {"name":"Live","title":"HLS","url":"https://iptv.example/live/1.m3u8","behaviorHints":{"proxyHeaders":{"request":{"User-Agent":"VLC"}}}},
        {"title":"no address at all"}
    ]}"#;

    #[test]
    fn reads_and_labels_streams() {
        let s = parse_streams("Torrentio".into(), STREAMS.into()).unwrap();
        assert_eq!(s.len(), 4);
        assert!(s[0].is_torrent);
        assert!(!s[0].apple_native);
        assert_eq!(s[0].quality, StreamQuality::Uhd4k);
        assert_eq!(s[0].tags, vec!["DV", "HDR", "HEVC", "Atmos", "Remux"]);
        assert_eq!(s[0].size_label.as_deref(), Some("61.2 GB"));
        assert_eq!(s[0].container, StreamContainer::Mkv);

        assert_eq!(s[1].container, StreamContainer::Mp4);
        assert!(s[1].apple_native);
        assert_eq!(s[1].size_label.as_deref(), Some("6.4 GB"));
        assert_eq!(s[1].quality_label, "1080p");

        assert_eq!(s[2].container, StreamContainer::Mkv);
        assert!(!s[2].apple_native);
        assert_eq!(s[2].binge_group.as_deref(), Some("rd-4k"));

        assert_eq!(s[3].container, StreamContainer::Hls);
        assert_eq!(s[3].request_headers, vec![StreamHeader { name: "User-Agent".into(), value: "VLC".into() }]);
    }

    #[test]
    fn ranks_playable_then_quality() {
        let s = parse_streams("X".into(), STREAMS.into()).unwrap();
        let apple: Vec<_> = rank_streams(s.clone(), true, true).into_iter().map(|x| x.name).collect();
        assert_eq!(apple, vec!["RD+ 1080p", "Live", "RD+ 4K"]);
        let windows: Vec<_> = rank_streams(s, false, false).into_iter().map(|x| x.name).collect();
        assert_eq!(windows, vec!["RD+ 4K", "RD+ 1080p", "Live", "Torrentio\n4k"]);
    }

    #[test]
    fn bad_answers_are_errors_not_crashes() {
        assert!(parse_streams("X".into(), "".into()).is_err());
        assert!(parse_streams("X".into(), r#"{"streams":"nope"}"#.into()).is_err());
        assert_eq!(parse_streams("X".into(), r#"{"streams":[]}"#.into()).unwrap().len(), 0);
    }
}
