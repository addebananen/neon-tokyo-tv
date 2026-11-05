//! "A new version is out": reads GitHub's list of releases and picks the newest build for this
//! platform, so every app can offer its own update with one tap.
//!
//! Each platform's installer is an asset on a release (`.apk` for Android, `.msi` for Windows,
//! `.ipa` for iPhone and Apple TV). Android, Windows and Apple builds are numbered by different
//! CI runs, so versions are not compared by number: a release counts as newer when it was
//! published after this build was made (with a few minutes' grace for the release step itself)
//! and is not the version already running.

/// The repository whose releases carry every platform's installers.
pub const RELEASES_REPO: &str = "addebananen/neon-tokyo-tv";

/// A newer build for this platform.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct UpdateInfo {
    /// "0.2.0-win.7", the tag without its leading "v".
    pub version: String,
    pub tag: String,
    /// The release's web page (for platforms that cannot install by themselves).
    pub page_url: String,
    /// The installer to download.
    pub asset_url: String,
    pub asset_name: String,
    pub asset_size: u64,
    /// The release notes, as written on GitHub (Markdown).
    pub notes: String,
    pub published_at_ms: i64,
    pub prerelease: bool,
}

#[derive(serde::Deserialize, Default)]
#[serde(default)]
struct Release {
    tag_name: String,
    html_url: String,
    body: Option<String>,
    draft: bool,
    prerelease: bool,
    published_at: Option<String>,
    assets: Vec<Asset>,
}

#[derive(serde::Deserialize, Default)]
#[serde(default)]
struct Asset {
    name: String,
    browser_download_url: String,
    size: u64,
}

/// Where to read the releases from (newest first, enough to reach past other platforms' builds).
#[uniffi::export]
pub fn releases_url() -> String {
    format!("https://api.github.com/repos/{RELEASES_REPO}/releases?per_page=30")
}

/// The newest release with an asset ending in one of `asset_suffixes` (".msi", ".ipa"), when it
/// is newer than this build. `built_at_ms` is when this build was made; 0 means unknown, and
/// then only a different version counts.
#[uniffi::export]
pub fn pick_update(json: String, asset_suffixes: Vec<String>, current_version: String, built_at_ms: i64) -> Option<UpdateInfo> {
    let releases: Vec<Release> = serde_json::from_str(&json).ok()?;
    let current = current_version.trim().trim_start_matches('v').to_string();
    let suffixes: Vec<String> = asset_suffixes.iter().map(|s| s.to_ascii_lowercase()).collect();
    let mut best: Option<UpdateInfo> = None;
    for r in releases.into_iter().filter(|r| !r.draft) {
        let Some(asset) = r
            .assets
            .iter()
            .find(|a| suffixes.iter().any(|s| a.name.to_ascii_lowercase().ends_with(s.as_str())))
        else {
            continue;
        };
        let published = r.published_at.as_deref().and_then(iso_to_ms).unwrap_or(0);
        let version = r.tag_name.trim_start_matches('v').to_string();
        if best.as_ref().is_some_and(|b| b.published_at_ms >= published) {
            continue;
        }
        best = Some(UpdateInfo {
            version,
            tag: r.tag_name.clone(),
            page_url: r.html_url.clone(),
            asset_url: asset.browser_download_url.clone(),
            asset_name: asset.name.clone(),
            asset_size: asset.size,
            notes: r.body.clone().unwrap_or_default(),
            published_at_ms: published,
            prerelease: r.prerelease,
        });
    }
    let best = best?;
    if best.version == current {
        return None;
    }
    // 10 minutes of grace: CI makes the build, then publishes the release a little later
    if built_at_ms > 0 && best.published_at_ms <= built_at_ms + 10 * 60 * 1000 {
        return None;
    }
    Some(best)
}

/// "2026-11-04T20:07:45Z" or "2026-11-04T20:07:45.123+01:00" as milliseconds since 1970.
#[uniffi::export]
pub fn iso_time_ms(iso: String) -> Option<i64> {
    iso_to_ms(&iso)
}

/// Milliseconds since 1970 as "2026-11-04T20:07:45.000Z".
#[uniffi::export]
pub fn ms_to_iso(ms: i64) -> String {
    ms_iso(ms)
}

pub(crate) fn iso_to_ms(s: &str) -> Option<i64> {
    let s = s.trim();
    if s.len() < 10 {
        return None;
    }
    let num = |a: usize, b: usize| -> Option<i64> { s.get(a..b)?.parse::<i64>().ok() };
    let (y, mo, d) = (num(0, 4)?, num(5, 7)?, num(8, 10)?);
    let (mut h, mut mi, mut se, mut ms) = (0, 0, 0, 0);
    let mut rest = "";
    if s.len() >= 19 {
        h = num(11, 13)?;
        mi = num(14, 16)?;
        se = num(17, 19)?;
        rest = &s[19..];
        if let Some(frac) = rest.strip_prefix('.') {
            let digits: String = frac.chars().take_while(|c| c.is_ascii_digit()).collect();
            let padded = format!("{:0<3}", &digits[..digits.len().min(3)]);
            ms = padded.parse::<i64>().unwrap_or(0);
            rest = &frac[digits.len()..];
        }
    }
    let mut offset_min = 0;
    if let Some(sign) = rest.chars().next().filter(|c| *c == '+' || *c == '-') {
        let tz = &rest[1..];
        let oh = tz.get(0..2)?.parse::<i64>().ok()?;
        let om = tz.get(tz.len().saturating_sub(2)..).and_then(|m| m.parse::<i64>().ok()).unwrap_or(0);
        offset_min = (oh * 60 + om) * if sign == '-' { -1 } else { 1 };
    }
    let days = days_from_civil(y, mo, d);
    Some(((days * 86_400 + h * 3600 + mi * 60 + se - offset_min * 60) * 1000) + ms)
}

pub(crate) fn ms_iso(ms: i64) -> String {
    let secs = ms.div_euclid(1000);
    let milli = ms.rem_euclid(1000);
    let days = secs.div_euclid(86_400);
    let tod = secs.rem_euclid(86_400);
    let (y, m, d) = civil_from_days(days);
    format!("{y:04}-{m:02}-{d:02}T{:02}:{:02}:{:02}.{milli:03}Z", tod / 3600, tod / 60 % 60, tod % 60)
}

// Howard Hinnant's date algorithms
fn days_from_civil(y: i64, m: i64, d: i64) -> i64 {
    let y = if m <= 2 { y - 1 } else { y };
    let era = if y >= 0 { y } else { y - 399 } / 400;
    let yoe = y - era * 400;
    let mp = (m + 9) % 12;
    let doy = (153 * mp + 2) / 5 + d - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    era * 146_097 + doe - 719_468
}

fn civil_from_days(z: i64) -> (i64, i64, i64) {
    let z = z + 719_468;
    let era = if z >= 0 { z } else { z - 146_096 } / 146_097;
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    (if m <= 2 { y + 1 } else { y }, m, d)
}

#[cfg(test)]
mod tests {
    use super::*;

    const RELEASES: &str = r#"[
      {"tag_name":"v0.2.0-nt.260","html_url":"https://x/nt260","draft":false,"prerelease":false,"published_at":"2026-11-05T10:00:00Z",
       "assets":[{"name":"neon-tokyo-0.2.0-nt.260.apk","browser_download_url":"https://x/a.apk","size":1}]},
      {"tag_name":"v0.2.0-win.5","html_url":"https://x/win5","draft":false,"prerelease":true,"published_at":"2026-11-05T09:00:00Z","body":"Faster",
       "assets":[{"name":"NeonTokyo-0.2.0-win.5.msi","browser_download_url":"https://x/w5.msi","size":90},{"name":"NeonTokyo-0.2.0-win.5-setup.exe","browser_download_url":"https://x/w5.exe","size":91}]},
      {"tag_name":"v0.2.0-win.3","html_url":"https://x/win3","draft":false,"prerelease":true,"published_at":"2026-11-04T20:05:00Z",
       "assets":[{"name":"NeonTokyo-0.2.0-win.3.msi","browser_download_url":"https://x/w3.msi","size":80}]},
      {"tag_name":"v0.2.0-win.9","html_url":"https://x/draft","draft":true,"prerelease":true,"published_at":null,
       "assets":[{"name":"NeonTokyo-0.2.0-win.9.msi","browser_download_url":"https://x/w9.msi","size":80}]}
    ]"#;

    #[test]
    fn picks_the_newest_build_for_this_platform() {
        let built = iso_to_ms("2026-11-04T20:00:00Z").unwrap();
        let u = pick_update(RELEASES.into(), vec![".msi".into()], "0.2.0-win.3".into(), built).unwrap();
        assert_eq!(u.version, "0.2.0-win.5");
        assert_eq!(u.asset_url, "https://x/w5.msi");
        assert_eq!(u.notes, "Faster");
        assert!(u.prerelease);
    }

    #[test]
    fn nothing_when_up_to_date_or_older() {
        let built = iso_to_ms("2026-11-05T08:58:00Z").unwrap();
        assert!(pick_update(RELEASES.into(), vec![".msi".into()], "0.2.0-win.5".into(), built).is_none());
        // built after the newest release: the running build is newer
        let later = iso_to_ms("2026-11-05T12:00:00Z").unwrap();
        assert!(pick_update(RELEASES.into(), vec![".msi".into()], "0.2.0-win.6".into(), later).is_none());
        assert!(pick_update(RELEASES.into(), vec![".ipa".into()], "x".into(), 0).is_none());
        assert!(pick_update("broken".into(), vec![".msi".into()], "x".into(), 0).is_none());
    }

    #[test]
    fn reads_and_writes_times() {
        assert_eq!(iso_to_ms("1970-01-01T00:00:00Z"), Some(0));
        assert_eq!(iso_to_ms("1970-01-02"), Some(86_400_000));
        assert_eq!(iso_to_ms("2026-11-04T21:07:45.5+01:00"), iso_to_ms("2026-11-04T20:07:45.500Z"));
        let t = iso_to_ms("2026-02-28T23:59:59.123Z").unwrap();
        assert_eq!(ms_iso(t), "2026-02-28T23:59:59.123Z");
        assert_eq!(ms_iso(iso_to_ms("2024-02-29T12:00:00Z").unwrap()), "2024-02-29T12:00:00.000Z");
        assert_eq!(iso_to_ms("nope"), None);
    }
}
