import Foundation
import Observation

/// Everything the screens show, and every change they make. All rules (which rows exist, what
/// counts as watched, what comes next, which source is best) come from the shared Rust core so
/// the Apple apps behave exactly like the Android app.
@MainActor
@Observable
final class AppModel {
    // Profiles
    var profiles: [Profile] = []
    var activeProfileId: String?

    // Addons and the home screen
    var addonUrls: [String] = []
    var manifests: [AddonManifest] = []
    var rows: [HomeRow] = []
    var rowItems: [String: [MetaItem]] = [:]
    var loadingAddons = false

    // What the active profile has watched
    var history: [WatchEntry] = []
    var nextUp: [SeriesNextUp] = []
    var watchlist: [WatchlistItem] = []

    var themeId: String = NeonTokens.neonTokyo.id
    var showIntro = true

    /// Set by the CI screenshot run (launch argument `-NeonScreen`), opens a screen directly.
    let launchScreen: String? = UserDefaults.standard.string(forKey: "NeonScreen")
    let demo: Bool = UserDefaults.standard.bool(forKey: "NeonDemo")

    private static let historyLimit: UInt32 = 400

    /// Updates for this app, looked for on GitHub.
    let updates = Updater()
    @ObservationIgnored private var accountsStore: Accounts?

    /// Stremio and Trakt for the active profile.
    var accounts: Accounts {
        if let a = accountsStore { return a }
        let a = Accounts(model: self)
        accountsStore = a
        return a
    }

    init() {
        load()
        if demo {
            showIntro = launchScreen == nil
            if liveProfiles.isEmpty { createProfile(name: "Neon") }
            if activeProfileId == nil { activeProfileId = liveProfiles.first?.id }
        } else {
            accounts.load(profileId: activeProfileId)
            updates.start()
        }
    }

    var theme: NeonThemeTokens { NeonTokens.themes.first { $0.id == themeId } ?? NeonTokens.neonTokyo }
    var liveProfiles: [Profile] { profiles.filter { !$0.deleted } }
    var activeProfile: Profile? { liveProfiles.first { $0.id == activeProfileId } }

    // MARK: - Saving and loading

    private func load() {
        profiles = decodeProfiles(json: LocalStore.string("profiles") ?? "[]")
        activeProfileId = LocalStore.string("activeProfile")
        addonUrls = LocalStore.strings("addons") ?? starterAddonUrls()
        manifests = decodeManifests(json: LocalStore.cache("manifests") ?? "[]")
        rows = homeRows(manifests: manifests)
        for row in rows {
            let cached = decodeMetaItems(json: LocalStore.cache(rowCacheName(row)) ?? "[]")
            if !cached.isEmpty { rowItems[row.key] = cached }
        }
        history = decodeWatchEntries(json: LocalStore.string("history") ?? "[]")
        nextUp = decodeNextUp(json: LocalStore.string("nextUp") ?? "[]")
        watchlist = decodeWatchlist(json: LocalStore.string("watchlist") ?? "[]")
        themeId = LocalStore.string("theme") ?? NeonTokens.neonTokyo.id
    }

    private func saveProfiles() {
        LocalStore.set(encodeProfiles(list: profiles), "profiles")
        LocalStore.set(activeProfileId, "activeProfile")
    }

    private func saveWatch() {
        history = trimHistory(list: history, max: Self.historyLimit, nowMs: Date.nowMs)
        LocalStore.set(encodeWatchEntries(list: history), "history")
        LocalStore.set(encodeNextUp(list: nextUp), "nextUp")
        LocalStore.set(encodeWatchlist(list: watchlist), "watchlist")
    }

    func setTheme(_ id: String) {
        themeId = id
        LocalStore.set(id, "theme")
    }

    // MARK: - Profiles

    @discardableResult
    func createProfile(name: String) -> Profile {
        let p = newProfile(name: name, existing: profiles, nowMs: Date.nowMs)
        profiles.append(p)
        activeProfileId = p.id
        saveProfiles()
        return p
    }

    func select(_ profile: Profile?) {
        activeProfileId = profile?.id
        saveProfiles()
        accounts.load(profileId: activeProfileId)
    }

    func remove(_ profile: Profile) {
        guard let i = profiles.firstIndex(where: { $0.id == profile.id }) else { return }
        profiles[i].deleted = true
        profiles[i].updatedAtMs = Date.nowMs
        if activeProfileId == profile.id { activeProfileId = nil }
        saveProfiles()
    }

    // MARK: - Addons and rows

    func refreshAddons() async {
        loadingAddons = true
        defer { loadingAddons = false }
        let urls = addonUrls
        let cached = manifests
        let fetched: [Int: AddonManifest] = await withTaskGroup(of: (Int, AddonManifest?).self) { group in
            for (i, base) in urls.enumerated() {
                group.addTask {
                    guard let json = try? await Net.text(manifestUrl(baseUrl: base)) else { return (i, nil) }
                    return (i, try? parseManifest(baseUrl: base, json: json))
                }
            }
            var out: [Int: AddonManifest] = [:]
            for await (i, m) in group { if let m { out[i] = m } }
            return out
        }
        // keep the order addons were added in; an addon that did not answer keeps its cached manifest
        manifests = urls.enumerated().compactMap { i, base in
            fetched[i] ?? cached.first { $0.baseUrl == base }
        }
        LocalStore.setCache(encodeManifests(list: manifests), "manifests")
        rows = homeRows(manifests: manifests)
        await withTaskGroup(of: Void.self) { group in
            for row in rows {
                group.addTask { await self.loadRow(row) }
            }
        }
    }

    func loadRow(_ row: HomeRow) async {
        let address = catalogUrl(catalog: row.catalog, skip: nil, genre: nil, search: nil)
        guard let json = try? await Net.text(address), let items = try? parseCatalog(json: json), !items.isEmpty else { return }
        rowItems[row.key] = items
        LocalStore.setCache(encodeMetaItems(list: items), rowCacheName(row))
    }

    /// A file name that stays the same between launches (Swift's hashValue does not).
    private func rowCacheName(_ row: HomeRow) -> String {
        "row-" + cloudRecordName(kind: "r", profileId: "", id: row.key)
    }

    func addAddon(_ input: String) async throws {
        let base = try normalizeAddonUrl(input: input)
        let json = try await Net.text(manifestUrl(baseUrl: base))
        _ = try parseManifest(baseUrl: base, json: json)
        if !addonUrls.contains(base) {
            addonUrls.append(base)
            LocalStore.set(addonUrls, "addons")
        }
        await refreshAddons()
    }

    /// Addons from a Stremio account that are not here yet, added after the ones already here.
    func mergeAddons(_ bases: [String]) async {
        let fresh = bases.filter { !addonUrls.contains($0) }
        guard !fresh.isEmpty else { return }
        addonUrls.append(contentsOf: fresh)
        LocalStore.set(addonUrls, "addons")
        await refreshAddons()
    }

    func removeAddon(_ base: String) {
        addonUrls.removeAll { $0 == base }
        LocalStore.set(addonUrls, "addons")
        manifests.removeAll { $0.baseUrl == base }
        rows = homeRows(manifests: manifests)
    }

    // MARK: - Details, sources, search

    func meta(kind: String, id: String) async throws -> MetaDetail {
        var lastError: Error = URLError(.resourceUnavailable)
        for base in metaAddons(manifests: manifests, kind: kind, id: id) {
            do {
                return try parseMeta(json: try await Net.text(metaUrl(baseUrl: base, kind: kind, id: id)))
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    func streams(kind: String, videoId: String) async -> [StreamSource] {
        let addons = streamAddons(manifests: manifests, kind: kind, id: videoId)
        let names = Dictionary(manifests.map { ($0.baseUrl, $0.name) }, uniquingKeysWith: { a, _ in a })
        let all: [StreamSource] = await withTaskGroup(of: [StreamSource].self) { group in
            for base in addons {
                group.addTask {
                    guard let json = try? await Net.text(streamUrl(baseUrl: base, kind: kind, videoId: videoId)) else { return [] }
                    return (try? parseStreams(addonName: names[base] ?? "Addon", json: json)) ?? []
                }
            }
            var out: [StreamSource] = []
            for await list in group { out.append(contentsOf: list) }
            return out
        }
        // torrents are left to the Android app; Apple plays direct links
        return rankStreams(streams: all, apple: true, hideTorrents: true)
    }

    var hasStreamAddon: Bool { manifests.contains { $0.resources.contains("stream") } }

    func search(_ query: String) async -> [SearchGroup] {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2 else { return [] }
        let targets = searchCatalogs(manifests: manifests)
        let found: [(Int, [MetaItem])] = await withTaskGroup(of: (Int, [MetaItem]).self) { group in
            for (i, c) in targets.enumerated() {
                group.addTask {
                    let address = catalogUrl(catalog: c, skip: nil, genre: nil, search: q)
                    guard let json = try? await Net.text(address) else { return (i, []) }
                    return (i, (try? parseCatalog(json: json)) ?? [])
                }
            }
            var out: [(Int, [MetaItem])] = []
            for await r in group { out.append(r) }
            return out
        }
        return found.sorted { $0.0 < $1.0 }.compactMap { i, items in
            items.isEmpty ? nil : SearchGroup(catalog: targets[i], items: items)
        }
    }

    // MARK: - Watching

    var continueItems: [ContinueEntry] {
        guard let pid = activeProfileId else { return [] }
        return continueWatching(history: history, nextUp: nextUp, profileId: pid, today: Date.todayIso)
    }

    func entry(_ playbackId: String) -> WatchEntry? {
        history.first { $0.profileId == activeProfileId && $0.id == playbackId && !$0.deleted }
    }

    func resumePosition(_ playbackId: String) -> UInt64 {
        resumePositionMs(entry: entry(playbackId))
    }

    /// Saves where playback is. For an episode that just became watched, the series' next
    /// episode is worked out so "Continue watching" offers it.
    func saveProgress(_ item: PlayItem, positionMs: UInt64, durationMs: UInt64) {
        guard let pid = activeProfileId, durationMs > 0 else { return }
        let old = entry(item.playbackId)
        let e = recordProgress(existing: old, profileId: pid, id: item.playbackId, kind: item.kind, title: item.title,
                               poster: item.poster, positionMs: positionMs, durationMs: durationMs, nowMs: Date.nowMs)
        history.removeAll { $0.profileId == pid && $0.id == e.id }
        history.insert(e, at: 0)
        if e.watched, old?.watched != true, item.kind == "series", let seriesId = item.seriesId {
            let n = nextUpAfter(profileId: pid, seriesId: seriesId, title: item.seriesTitle ?? item.title, poster: item.poster,
                                watchedPlaybackId: item.playbackId, episodes: item.episodes, nowMs: Date.nowMs)
            nextUp.removeAll { $0.profileId == pid && $0.seriesId == seriesId }
            nextUp.append(n)
        }
        saveWatch()
    }

    /// The player closed: saves where it stopped, tells Trakt, and lets the accounts sync.
    func playerClosed(_ item: PlayItem, positionMs: UInt64, durationMs: UInt64) {
        saveProgress(item, positionMs: positionMs, durationMs: durationMs)
        accounts.scrobbleStop(playbackId: item.playbackId, kind: item.kind, positionMs: positionMs, durationMs: durationMs)
        accounts.changed()
    }

    /// What sync merged: the whole history (and the watchlist, when given).
    func applySynced(history newHistory: [WatchEntry], watchlist newWatchlist: [WatchlistItem]?) {
        history = newHistory
        if let newWatchlist { watchlist = newWatchlist }
        saveWatch()
    }

    /// Where to start as a share of the film, when only the share is known (from Trakt).
    func resumeShare(_ playbackId: String) -> Double {
        resumeFraction(entry: entry(playbackId))
    }

    func isWatched(_ playbackId: String) -> Bool { entry(playbackId)?.watched == true }

    func progress(_ playbackId: String) -> Double {
        guard let e = entry(playbackId), e.durationMs > 0, !e.watched else { return 0 }
        return min(Double(e.positionMs) / Double(e.durationMs), 1)
    }

    // MARK: - Watchlist

    var watchlistItems: [WatchlistItem] {
        watchlist.filter { $0.profileId == activeProfileId && !$0.deleted }.sorted { $0.addedAtMs > $1.addedAtMs }
    }

    func onWatchlist(_ id: String) -> Bool { watchlistItems.contains { $0.id == id } }

    func toggleWatchlist(_ item: MetaItem) {
        guard let pid = activeProfileId else { return }
        let now = Date.nowMs
        if let i = watchlist.firstIndex(where: { $0.profileId == pid && $0.id == item.id }) {
            watchlist[i].deleted.toggle()
            watchlist[i].updatedAtMs = now
            if !watchlist[i].deleted { watchlist[i].addedAtMs = now }
        } else {
            watchlist.append(WatchlistItem(profileId: pid, id: item.id, kind: item.kind, title: item.name, poster: item.poster,
                                           addedAtMs: now, updatedAtMs: now, deleted: false))
        }
        saveWatch()
        accounts.changed()
    }
}

/// One addon's answer to a search.
struct SearchGroup: Identifiable {
    let catalog: CatalogDef
    let items: [MetaItem]
    var id: String { catalog.addonBaseUrl + "/" + catalog.kind + "/" + catalog.id }
}

/// What the player needs to know about the film or episode it plays.
struct PlayItem: Hashable {
    let playbackId: String
    let kind: String
    let title: String
    let poster: String?
    let background: String?
    var seriesId: String? = nil
    var seriesTitle: String? = nil
    var episodes: [Video] = []
}
