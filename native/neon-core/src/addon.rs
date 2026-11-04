//! The Stremio addon protocol: manifests, catalogs and metadata.
//!
//! Addons answer plain JSON over HTTP at `<base>/<resource>/<type>/<id>[/<extra>].json`. Real
//! addons are loose with types (a year as a number or a string, a rating as either, episodes
//! numbered under `episode` or `number`), so everything is read leniently from `serde_json::Value`
//! instead of strict structs: one odd field never costs a whole row.

use serde_json::Value;

use crate::error::CoreError;

/// One catalog an addon offers, as a home row or a search target.
#[derive(Debug, Clone, PartialEq, Default, serde::Serialize, serde::Deserialize, uniffi::Record)]
#[serde(default)]
pub struct CatalogDef {
    pub addon_base_url: String,
    pub addon_name: String,
    /// `movie`, `series`, `anime`, `tv`, … as the addon names it.
    pub kind: String,
    pub id: String,
    pub name: String,
    /// Extra arguments the catalog cannot be fetched without (`search`, `genre`, …).
    pub extra_required: Vec<String>,
    /// Every extra argument it understands, required or not.
    pub extra_supported: Vec<String>,
    /// The values its `genre` filter takes, if it has one.
    pub genres: Vec<String>,
}

/// An addon's manifest, with its address so later requests know where to go.
#[derive(Debug, Clone, PartialEq, Default, serde::Serialize, serde::Deserialize, uniffi::Record)]
#[serde(default)]
pub struct AddonManifest {
    pub base_url: String,
    pub id: String,
    pub name: String,
    pub version: String,
    pub description: Option<String>,
    pub logo: Option<String>,
    /// Resource names: `catalog`, `meta`, `stream`, `subtitles`.
    pub resources: Vec<String>,
    pub types: Vec<String>,
    /// Id prefixes the addon answers for (`tt`, `kitsu:`); empty = any.
    pub id_prefixes: Vec<String>,
    /// Per resource, the types and prefixes it is limited to, when the manifest says so.
    pub resource_rules: Vec<ResourceRule>,
    pub catalogs: Vec<CatalogDef>,
}

/// A resource the manifest limits to some types or id prefixes.
#[derive(Debug, Clone, PartialEq, Default, serde::Serialize, serde::Deserialize, uniffi::Record)]
#[serde(default)]
pub struct ResourceRule {
    pub name: String,
    pub types: Vec<String>,
    pub id_prefixes: Vec<String>,
}

/// A poster on a row: what a catalog lists.
#[derive(Debug, Clone, PartialEq, Default, serde::Serialize, serde::Deserialize, uniffi::Record)]
#[serde(default)]
pub struct MetaItem {
    pub id: String,
    pub kind: String,
    pub name: String,
    pub poster: Option<String>,
    pub background: Option<String>,
    pub logo: Option<String>,
    pub description: Option<String>,
    pub release_info: Option<String>,
    pub imdb_rating: Option<String>,
    pub genres: Vec<String>,
    pub runtime: Option<String>,
}

/// An episode, or a part of a film collection.
#[derive(Debug, Clone, PartialEq, Default, serde::Serialize, serde::Deserialize, uniffi::Record)]
#[serde(default)]
pub struct Video {
    /// The addon's own id for it, which stream requests use.
    pub id: String,
    pub title: String,
    pub season: u32,
    pub episode: u32,
    /// ISO date it aired or airs, when known.
    pub released: Option<String>,
    pub thumbnail: Option<String>,
    pub overview: Option<String>,
}

/// Everything the details screen shows.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MetaDetail {
    pub item: MetaItem,
    pub videos: Vec<Video>,
    pub cast: Vec<String>,
    pub directors: Vec<String>,
    /// YouTube ids of trailers.
    pub trailer_youtube_ids: Vec<String>,
}

/// A row on the home screen: one catalog that can be fetched as it is.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct HomeRow {
    /// Stable key for the row: `<addon base>/<type>/<catalog id>`, the same key Android uses for
    /// its catalog configs, so saved row order and visibility mean the same on every platform.
    pub key: String,
    pub title: String,
    pub catalog: CatalogDef,
}

// ---------------------------------------------------------------------------------------------
// Addresses

/// Turns whatever someone pasted (a `stremio://` link, a manifest address, an address with
/// trailing slashes) into the addon's base address.
#[uniffi::export]
pub fn normalize_addon_url(input: String) -> Result<String, CoreError> {
    let mut url = input.trim().to_string();
    if let Some(rest) = url.strip_prefix("stremio://") {
        url = format!("https://{rest}");
    }
    if !(url.starts_with("https://") || url.starts_with("http://")) {
        return Err(CoreError::BadAddress { input });
    }
    if let Some(stripped) = url.strip_suffix("/manifest.json") {
        url = stripped.to_string();
    }
    while url.ends_with('/') {
        url.pop();
    }
    if url.len() <= "https://".len() {
        return Err(CoreError::BadAddress { input });
    }
    Ok(url)
}

#[uniffi::export]
pub fn manifest_url(base_url: String) -> String {
    format!("{}/manifest.json", trim_base(&base_url))
}

/// Address of one page of a catalog. `skip` pages on (Stremio pages are 100 long at most,
/// most addons send 20 to 100); `genre` and `search` are the usual extras.
#[uniffi::export]
pub fn catalog_url(
    catalog: CatalogDef,
    skip: Option<u32>,
    genre: Option<String>,
    search: Option<String>,
) -> String {
    let mut extra: Vec<(String, String)> = Vec::new();
    if let Some(q) = search.filter(|s| !s.trim().is_empty()) {
        extra.push(("search".into(), q.trim().to_string()));
    }
    if let Some(g) = genre.filter(|s| !s.is_empty()) {
        extra.push(("genre".into(), g));
    }
    if let Some(s) = skip.filter(|s| *s > 0) {
        extra.push(("skip".into(), s.to_string()));
    }
    resource_url(&catalog.addon_base_url, "catalog", &catalog.kind, &catalog.id, &extra)
}

#[uniffi::export]
pub fn meta_url(base_url: String, kind: String, id: String) -> String {
    resource_url(&base_url, "meta", &kind, &id, &[])
}

/// Address for the streams of a film (its id) or an episode (the episode's own video id).
#[uniffi::export]
pub fn stream_url(base_url: String, kind: String, video_id: String) -> String {
    resource_url(&base_url, "stream", &kind, &video_id, &[])
}

#[uniffi::export]
pub fn subtitles_url(base_url: String, kind: String, video_id: String) -> String {
    resource_url(&base_url, "subtitles", &kind, &video_id, &[])
}

pub(crate) fn resource_url(base: &str, resource: &str, kind: &str, id: &str, extra: &[(String, String)]) -> String {
    let mut url = format!("{}/{}/{}/{}", trim_base(base), resource, encode_component(kind), encode_component(id));
    if !extra.is_empty() {
        let joined: Vec<String> = extra
            .iter()
            .map(|(k, v)| format!("{}={}", encode_component(k), encode_component(v)))
            .collect();
        url.push('/');
        url.push_str(&joined.join("&"));
    }
    url.push_str(".json");
    url
}

fn trim_base(base: &str) -> &str {
    let b = base.trim();
    let b = b.strip_suffix("/manifest.json").unwrap_or(b);
    b.trim_end_matches('/')
}

/// JavaScript's encodeURIComponent, which is what Stremio clients use for path parts.
pub(crate) fn encode_component(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for b in s.bytes() {
        let keep = b.is_ascii_alphanumeric() || matches!(b, b'-' | b'_' | b'.' | b'!' | b'~' | b'*' | b'\'' | b'(' | b')');
        if keep {
            out.push(b as char);
        } else {
            out.push_str(&format!("%{b:02X}"));
        }
    }
    out
}

// ---------------------------------------------------------------------------------------------
// Reading answers

#[uniffi::export]
pub fn parse_manifest(base_url: String, json: String) -> Result<AddonManifest, CoreError> {
    let v: Value = serde_json::from_str(&json)?;
    if !v.is_object() {
        return Err(CoreError::BadResponse { reason: "manifest is not an object".into() });
    }
    let base = trim_base(&base_url).to_string();
    let name = text(&v, "name").unwrap_or_else(|| "Addon".into());

    let mut resources = Vec::new();
    let mut resource_rules = Vec::new();
    if let Some(list) = v.get("resources").and_then(Value::as_array) {
        for r in list {
            match r {
                Value::String(s) => resources.push(s.clone()),
                Value::Object(_) => {
                    if let Some(n) = text(r, "name") {
                        resources.push(n.clone());
                        resource_rules.push(ResourceRule {
                            name: n,
                            types: strings(r, "types"),
                            id_prefixes: strings(r, "idPrefixes"),
                        });
                    }
                }
                _ => {}
            }
        }
    }

    let catalogs = v
        .get("catalogs")
        .and_then(Value::as_array)
        .map(|list| list.iter().filter_map(|c| parse_catalog_def(&base, &name, c)).collect())
        .unwrap_or_default();

    Ok(AddonManifest {
        base_url: base,
        id: text(&v, "id").unwrap_or_default(),
        name,
        version: text(&v, "version").unwrap_or_default(),
        description: text(&v, "description"),
        logo: text(&v, "logo"),
        resources,
        types: strings(&v, "types"),
        id_prefixes: strings(&v, "idPrefixes"),
        resource_rules,
        catalogs,
    })
}

fn parse_catalog_def(base: &str, addon_name: &str, c: &Value) -> Option<CatalogDef> {
    let kind = text(c, "type")?;
    let id = text(c, "id")?;
    let mut required = strings(c, "extraRequired");
    let mut supported = strings(c, "extraSupported");
    let mut genres = Vec::new();
    if let Some(extra) = c.get("extra").and_then(Value::as_array) {
        for e in extra {
            let Some(n) = text(e, "name") else { continue };
            if e.get("isRequired").and_then(Value::as_bool).unwrap_or(false) && !required.contains(&n) {
                required.push(n.clone());
            }
            if n == "genre" {
                genres = strings(e, "options");
            }
            if !supported.contains(&n) {
                supported.push(n);
            }
        }
    }
    for r in &required {
        if !supported.contains(r) {
            supported.push(r.clone());
        }
    }
    if genres.is_empty() {
        genres = strings(c, "genres");
    }
    Some(CatalogDef {
        addon_base_url: base.to_string(),
        addon_name: addon_name.to_string(),
        name: text(c, "name").unwrap_or_else(|| id.clone()),
        kind,
        id,
        extra_required: required,
        extra_supported: supported,
        genres,
    })
}

#[uniffi::export]
pub fn parse_catalog(json: String) -> Result<Vec<MetaItem>, CoreError> {
    let v: Value = serde_json::from_str(&json)?;
    let metas = v
        .get("metas")
        .and_then(Value::as_array)
        .ok_or_else(|| CoreError::BadResponse { reason: "no metas".into() })?;
    let mut seen = std::collections::HashSet::new();
    Ok(metas
        .iter()
        .filter_map(parse_meta_item)
        .filter(|m| seen.insert(m.id.clone()))
        .collect())
}

#[uniffi::export]
pub fn parse_meta(json: String) -> Result<MetaDetail, CoreError> {
    let v: Value = serde_json::from_str(&json)?;
    let meta = v.get("meta").ok_or_else(|| CoreError::BadResponse { reason: "no meta".into() })?;
    let item = parse_meta_item(meta).ok_or_else(|| CoreError::BadResponse { reason: "meta without id".into() })?;

    let mut videos: Vec<Video> = meta
        .get("videos")
        .and_then(Value::as_array)
        .map(|list| list.iter().filter_map(parse_video).collect())
        .unwrap_or_default();
    videos.sort_by_key(|x| (x.season == 0, x.season, x.episode));

    let mut trailers: Vec<String> = Vec::new();
    if let Some(list) = meta.get("trailerStreams").and_then(Value::as_array) {
        trailers.extend(list.iter().filter_map(|t| text(t, "ytId")));
    }
    if let Some(list) = meta.get("trailers").and_then(Value::as_array) {
        for t in list {
            if let Some(s) = text(t, "source") {
                if !trailers.contains(&s) {
                    trailers.push(s);
                }
            }
        }
    }

    Ok(MetaDetail {
        cast: strings(meta, "cast"),
        directors: {
            let d = strings(meta, "director");
            if d.is_empty() { strings(meta, "directors") } else { d }
        },
        trailer_youtube_ids: trailers,
        videos,
        item,
    })
}

fn parse_meta_item(m: &Value) -> Option<MetaItem> {
    let id = text(m, "id").or_else(|| text(m, "imdb_id"))?;
    let mut genres = strings(m, "genres");
    if genres.is_empty() {
        genres = strings(m, "genre");
    }
    Some(MetaItem {
        kind: text(m, "type").unwrap_or_else(|| "movie".into()),
        name: text(m, "name").or_else(|| text(m, "title")).unwrap_or_else(|| id.clone()),
        poster: text(m, "poster"),
        background: text(m, "background"),
        logo: text(m, "logo"),
        description: text(m, "description"),
        release_info: text(m, "releaseInfo").or_else(|| text(m, "year")),
        imdb_rating: text(m, "imdbRating").filter(|r| !r.is_empty() && r != "0"),
        runtime: text(m, "runtime"),
        genres,
        id,
    })
}

fn parse_video(x: &Value) -> Option<Video> {
    let season = number(x, "season").unwrap_or(0);
    let episode = number(x, "episode").or_else(|| number(x, "number")).unwrap_or(0);
    let id = text(x, "id").unwrap_or_default();
    if id.is_empty() && episode == 0 {
        return None;
    }
    Some(Video {
        title: text(x, "title")
            .or_else(|| text(x, "name"))
            .unwrap_or_else(|| format!("Episode {episode}")),
        released: text(x, "released").or_else(|| text(x, "firstAired")),
        thumbnail: text(x, "thumbnail"),
        overview: text(x, "overview").or_else(|| text(x, "description")),
        id,
        season,
        episode,
    })
}

// ---------------------------------------------------------------------------------------------
// Rows, search and which addon answers what

/// The rows a home screen starts with: every catalog that needs no extra argument, in addon
/// order, then catalog order, without duplicates.
#[uniffi::export]
pub fn home_rows(manifests: Vec<AddonManifest>) -> Vec<HomeRow> {
    let mut seen = std::collections::HashSet::new();
    let mut rows = Vec::new();
    for m in &manifests {
        for c in &m.catalogs {
            if !c.extra_required.is_empty() {
                continue;
            }
            let key = key_of(c);
            if !seen.insert(key.clone()) {
                continue;
            }
            rows.push(HomeRow { title: row_title(c), key, catalog: c.clone() });
        }
    }
    rows
}

/// Same key format as the Android app's catalog configs: `<addon base>/<type>/<catalog id>`.
#[uniffi::export]
pub fn row_key(catalog: CatalogDef) -> String {
    key_of(&catalog)
}

fn key_of(catalog: &CatalogDef) -> String {
    format!("{}/{}/{}", catalog.addon_base_url, catalog.kind, catalog.id)
}

fn row_title(c: &CatalogDef) -> String {
    let kind = match c.kind.as_str() {
        "movie" => "Movies",
        "series" => "Series",
        "anime" => "Anime",
        "tv" | "channel" => "TV",
        _ => "",
    };
    if kind.is_empty() || c.name.to_lowercase().contains(&kind.to_lowercase()) {
        c.name.clone()
    } else {
        format!("{} {}", c.name, kind)
    }
}

/// Catalogs a search should ask, one per addon and type.
#[uniffi::export]
pub fn search_catalogs(manifests: Vec<AddonManifest>) -> Vec<CatalogDef> {
    let mut seen = std::collections::HashSet::new();
    manifests
        .iter()
        .flat_map(|m| m.catalogs.iter())
        .filter(|c| c.extra_supported.iter().any(|e| e == "search"))
        .filter(|c| c.extra_required.iter().all(|e| e == "search"))
        .filter(|c| seen.insert((c.addon_base_url.clone(), c.kind.clone())))
        .cloned()
        .collect()
}

/// Whether an addon says it answers `resource` for this type and id.
#[uniffi::export]
pub fn addon_supports(manifest: AddonManifest, resource: String, kind: String, id: String) -> bool {
    supports(&manifest, &resource, &kind, &id)
}

fn supports(manifest: &AddonManifest, resource: &str, kind: &str, id: &str) -> bool {
    if !manifest.resources.iter().any(|r| *r == resource) {
        return false;
    }
    let (types, prefixes) = match manifest.resource_rules.iter().find(|r| r.name == resource) {
        Some(rule) => (
            if rule.types.is_empty() { &manifest.types } else { &rule.types },
            if rule.id_prefixes.is_empty() { &manifest.id_prefixes } else { &rule.id_prefixes },
        ),
        None => (&manifest.types, &manifest.id_prefixes),
    };
    let type_ok = types.is_empty() || types.iter().any(|t| *t == kind);
    let prefix_ok = prefixes.is_empty() || prefixes.iter().any(|p| id.starts_with(p.as_str()));
    type_ok && prefix_ok
}

/// The addons to ask for a title's details: those that say they answer for it, Cinemeta-style
/// metadata addons first in the order they were added.
#[uniffi::export]
pub fn meta_addons(manifests: Vec<AddonManifest>, kind: String, id: String) -> Vec<String> {
    manifests
        .iter()
        .filter(|m| supports(m, "meta", &kind, &id))
        .map(|m| m.base_url.clone())
        .collect()
}

#[uniffi::export]
pub fn stream_addons(manifests: Vec<AddonManifest>, kind: String, id: String) -> Vec<String> {
    manifests
        .iter()
        .filter(|m| supports(m, "stream", &kind, &id))
        .map(|m| m.base_url.clone())
        .collect()
}

// ---------------------------------------------------------------------------------------------
// Lenient field readers

pub(crate) fn text(v: &Value, key: &str) -> Option<String> {
    match v.get(key)? {
        Value::String(s) if !s.trim().is_empty() => Some(s.trim().to_string()),
        Value::Number(n) => Some(n.to_string()),
        _ => None,
    }
}

pub(crate) fn number(v: &Value, key: &str) -> Option<u32> {
    match v.get(key)? {
        Value::Number(n) => n.as_u64().map(|x| x as u32).or_else(|| n.as_f64().map(|f| f.max(0.0) as u32)),
        Value::String(s) => s.trim().parse().ok(),
        _ => None,
    }
}

pub(crate) fn strings(v: &Value, key: &str) -> Vec<String> {
    match v.get(key) {
        Some(Value::Array(list)) => list
            .iter()
            .filter_map(|x| match x {
                Value::String(s) if !s.trim().is_empty() => Some(s.trim().to_string()),
                Value::Object(_) => text(x, "name"),
                _ => None,
            })
            .collect(),
        Some(Value::String(s)) if !s.trim().is_empty() => vec![s.trim().to_string()],
        _ => Vec::new(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const CINEMETA: &str = r#"{
        "id":"com.linvo.cinemeta","version":"3.0.13","name":"Cinemeta",
        "resources":["catalog","meta","addon_catalog"],"types":["movie","series"],"idPrefixes":["tt"],
        "catalogs":[
          {"type":"movie","id":"top","name":"Popular","extra":[{"name":"genre","options":["Action","Drama"]},{"name":"search"},{"name":"skip"}]},
          {"type":"series","id":"top","name":"Popular","extra":[{"name":"genre"},{"name":"search"},{"name":"skip"}]},
          {"type":"movie","id":"year","name":"New","extra":[{"name":"genre","options":["2026","2025"],"isRequired":true}]},
          {"type":"movie","id":"imdbRating","name":"Featured","extraSupported":["genre","skip"]}
        ]}"#;

    fn cinemeta() -> AddonManifest {
        parse_manifest("https://v3-cinemeta.strem.io/manifest.json".into(), CINEMETA.into()).unwrap()
    }

    #[test]
    fn normalizes_addresses() {
        assert_eq!(
            normalize_addon_url("stremio://torrentio.example/abc/manifest.json".into()).unwrap(),
            "https://torrentio.example/abc"
        );
        assert_eq!(normalize_addon_url(" https://a.b/// ".into()).unwrap(), "https://a.b");
        assert!(normalize_addon_url("not a url".into()).is_err());
        assert!(normalize_addon_url("https://".into()).is_err());
    }

    #[test]
    fn reads_manifest_and_catalogs() {
        let m = cinemeta();
        assert_eq!(m.base_url, "https://v3-cinemeta.strem.io");
        assert_eq!(m.name, "Cinemeta");
        assert_eq!(m.catalogs.len(), 4);
        assert_eq!(m.catalogs[0].genres, vec!["Action", "Drama"]);
        assert_eq!(m.catalogs[2].extra_required, vec!["genre"]);
        assert_eq!(m.catalogs[3].extra_supported, vec!["genre", "skip"]);
    }

    #[test]
    fn home_rows_skip_required_extras_and_duplicates() {
        let rows = home_rows(vec![cinemeta(), cinemeta()]);
        let titles: Vec<_> = rows.iter().map(|r| r.title.as_str()).collect();
        assert_eq!(titles, vec!["Popular Movies", "Popular Series", "Featured Movies"]);
        assert_eq!(rows[0].key, "https://v3-cinemeta.strem.io/movie/top");
    }

    #[test]
    fn builds_urls_like_stremio() {
        let m = cinemeta();
        let c = m.catalogs[0].clone();
        assert_eq!(catalog_url(c.clone(), None, None, None), "https://v3-cinemeta.strem.io/catalog/movie/top.json");
        assert_eq!(
            catalog_url(c.clone(), Some(100), Some("Sci-Fi".into()), None),
            "https://v3-cinemeta.strem.io/catalog/movie/top/genre=Sci-Fi&skip=100.json"
        );
        assert_eq!(
            catalog_url(c, None, None, Some("blade runner 2049".into())),
            "https://v3-cinemeta.strem.io/catalog/movie/top/search=blade%20runner%202049.json"
        );
        assert_eq!(
            stream_url("https://x.y/".into(), "series".into(), "tt0903747:1:2".into()),
            "https://x.y/stream/series/tt0903747%3A1%3A2.json"
        );
    }

    #[test]
    fn reads_catalog_leniently() {
        let json = r#"{"metas":[
            {"id":"tt1","type":"movie","name":"A","poster":"p","imdbRating":7.9,"releaseInfo":2024},
            {"id":"tt1","type":"movie","name":"A again"},
            {"type":"movie","name":"no id"},
            {"id":"tt2","type":"movie","title":"B","genre":["Drama"],"imdbRating":"0"}
        ]}"#;
        let items = parse_catalog(json.into()).unwrap();
        assert_eq!(items.len(), 2);
        assert_eq!(items[0].imdb_rating.as_deref(), Some("7.9"));
        assert_eq!(items[0].release_info.as_deref(), Some("2024"));
        assert_eq!(items[1].name, "B");
        assert_eq!(items[1].genres, vec!["Drama"]);
        assert_eq!(items[1].imdb_rating, None);
        assert!(parse_catalog("{}".into()).is_err());
        assert!(parse_catalog("<html>".into()).is_err());
    }

    #[test]
    fn reads_meta_with_episodes_in_order() {
        let json = r#"{"meta":{"id":"tt0903747","type":"series","name":"Breaking Bad",
            "videos":[
              {"id":"tt0903747:2:1","title":"Seven Thirty-Seven","season":2,"episode":1},
              {"id":"tt0903747:0:1","title":"Special","season":0,"episode":1},
              {"id":"tt0903747:1:2","name":"Cat's in the Bag...","season":"1","number":2,"released":"2008-01-27T00:00:00.000Z"},
              {"id":"tt0903747:1:1","title":"Pilot","season":1,"episode":1}
            ],
            "trailerStreams":[{"title":"Trailer","ytId":"HhesaQXLuRY"}],
            "trailers":[{"source":"HhesaQXLuRY","type":"Trailer"},{"source":"abc","type":"Clip"}],
            "cast":["Bryan Cranston"],"director":"Vince Gilligan"}}"#;
        let d = parse_meta(json.into()).unwrap();
        let order: Vec<_> = d.videos.iter().map(|v| (v.season, v.episode)).collect();
        assert_eq!(order, vec![(1, 1), (1, 2), (2, 1), (0, 1)]);
        assert_eq!(d.videos[1].title, "Cat's in the Bag...");
        assert_eq!(d.trailer_youtube_ids, vec!["HhesaQXLuRY", "abc"]);
        assert_eq!(d.directors, vec!["Vince Gilligan"]);
    }

    #[test]
    fn knows_which_addon_answers() {
        let streams = parse_manifest(
            "https://s.example".into(),
            r#"{"id":"s","name":"S","types":["movie","series"],"catalogs":[],
               "resources":[{"name":"stream","types":["movie"],"idPrefixes":["tt"]}]}"#.into(),
        )
        .unwrap();
        assert!(addon_supports(streams.clone(), "stream".into(), "movie".into(), "tt1".into()));
        assert!(!addon_supports(streams.clone(), "stream".into(), "series".into(), "tt1".into()));
        assert!(!addon_supports(streams.clone(), "stream".into(), "movie".into(), "kitsu:1".into()));
        assert!(!addon_supports(streams.clone(), "meta".into(), "movie".into(), "tt1".into()));
        assert_eq!(stream_addons(vec![cinemeta(), streams.clone()], "movie".into(), "tt9".into()), vec!["https://s.example"]);
        assert_eq!(meta_addons(vec![cinemeta(), streams], "series".into(), "tt9".into()), vec!["https://v3-cinemeta.strem.io"]);
    }

    #[test]
    fn search_asks_each_addon_type_once() {
        let s = search_catalogs(vec![cinemeta()]);
        assert_eq!(s.len(), 2);
        assert_eq!(s[0].kind, "movie");
        assert_eq!(s[1].kind, "series");
    }
}
