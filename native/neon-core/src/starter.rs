//! The addons a new profile starts with, the same three as the Android app's
//! NeonTokyoStarterPack: Cinemeta, Streaming Catalogs (set up for Sweden) and Anime Catalogs.
//! All three are metadata-only.

use base64::Engine;

use crate::addon::encode_component;

pub const CINEMETA_URL: &str = "https://v3-cinemeta.strem.io";
const STREAMING_HOST: &str = "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club";
const ANIME_URL: &str = concat!(
    "https://1fe84bc728af-stremio-anime-catalogs.baby-beamup.club/",
    "%7B%22cinemeta%22%3A%22on%22%2C%22myanimelist_top-all-time%22%3A%22on%22%2C",
    "%22myanimelist_top-airing%22%3A%22on%22%2C%22anilist_trending-now%22%3A%22on%22%2C",
    "%22anilist_top-anime%22%3A%22on%22%2C%22kitsu_most-popular%22%3A%22on%22%7D"
);

/// Streaming Catalogs provider codes sold in Sweden, in card order, with their names.
pub const SERVICES: [(&str, &str); 8] = [
    ("nfx", "Netflix"),
    ("hbm", "HBO Max"),
    ("dnp", "Disney+"),
    ("amp", "Prime Video"),
    ("atp", "Apple TV+"),
    ("sst", "SkyShowtime"),
    ("pmp", "Paramount+"),
    ("cru", "Crunchyroll"),
];

const COUNTRY: &str = "SE";

/// Streaming Catalogs reads its settings from a base64 path segment:
/// providers:rpdbKey:country:installedAt:top10Global:top10Country:top10CountryCode
fn streaming_url() -> String {
    let providers: Vec<&str> = SERVICES.iter().map(|s| s.0).collect();
    let config = [providers.join(",").as_str(), "", COUNTRY, "1792700000000", "0", "1", COUNTRY].join(":");
    let encoded = base64::engine::general_purpose::STANDARD.encode(config.as_bytes());
    // Java's URLEncoder and encodeURIComponent agree on base64's characters except '+', '/', '='
    format!("{STREAMING_HOST}/{}", encode_component(&encoded))
}

/// Base addresses of the starter addons, in the order the home screen lists their rows.
#[uniffi::export]
pub fn starter_addon_urls() -> Vec<String> {
    vec![CINEMETA_URL.to_string(), streaming_url(), ANIME_URL.to_string()]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn starter_pack_matches_android() {
        let urls = starter_addon_urls();
        assert_eq!(urls[0], "https://v3-cinemeta.strem.io");
        // base64 of "nfx,hbm,dnp,amp,atp,sst,pmp,cru::SE:1792700000000:0:1:SE", url-encoded
        assert_eq!(
            urls[1],
            "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGhibSxkbnAsYW1wLGF0cCxzc3QscG1wLGNydTo6U0U6MTc5MjcwMDAwMDAwMDowOjE6U0U%3D"
        );
        assert!(urls[2].ends_with("%7D"));
    }
}
