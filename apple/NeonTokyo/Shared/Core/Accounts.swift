import Foundation
import Observation

/// A profile's Stremio sign-in. The password is never kept.
struct StremioLink: Equatable {
    let authKey: String
    let email: String
    var lastSync: Int64
}

/// A profile's Trakt sign-in and how far sync got.
struct TraktLink: Equatable {
    var token: TraktToken
    var stamp: String
    var pushedAt: Int64
}

/// Stremio and Trakt, per profile, on iPhone and Apple TV: signing in, and keeping addons,
/// Continue watching, watched marks and the watchlist the same as on the TV, the PC and other
/// apps. The rules are the shared core's; this only moves the bytes and keeps the keys.
@MainActor
@Observable
final class Accounts {
    private(set) var stremio: StremioLink?
    private(set) var trakt: TraktLink?
    /// A short line for Settings: "Synced 3 in, 1 out · 21:04", "Signed out of Stremio…".
    private(set) var stremioStatus = ""
    private(set) var traktStatus = ""
    private(set) var syncing = false
    /// The code to type on trakt.tv/activate while signing in.
    private(set) var traktCode: TraktDeviceCode?

    private unowned let model: AppModel
    @ObservationIgnored private var loop: Task<Void, Never>?
    @ObservationIgnored private var pairing: Task<Void, Never>?
    @ObservationIgnored private var pushSoon: Task<Void, Never>?
    @ObservationIgnored private var busy = false

    init(model: AppModel) {
        self.model = model
    }

    var traktAvailable: Bool { !Bundle.main.traktClientId.isEmpty }
    private var clientId: String { Bundle.main.traktClientId }
    private var clientSecret: String? { Bundle.main.traktClientSecret }

    // MARK: - Keeping the keys

    func load(profileId: String?) {
        pairing?.cancel()
        traktCode = nil
        stremio = nil
        trakt = nil
        if let pid = profileId {
            if let l = LocalStore.strings("stremio-\(pid)"), l.count >= 2 {
                stremio = StremioLink(authKey: l[0], email: l[1], lastSync: l.count > 2 ? Int64(l[2]) ?? 0 : 0)
            }
            if let l = LocalStore.strings("trakt-\(pid)"), l.count >= 3 {
                let user = l.count > 3 && l[3] != "-" ? l[3] : nil
                trakt = TraktLink(token: TraktToken(accessToken: l[0], refreshToken: l[1], expiresAtMs: Int64(l[2]) ?? 0, username: user),
                                  stamp: l.count > 4 && l[4] != "-" ? l[4] : "",
                                  pushedAt: l.count > 5 ? Int64(l[5]) ?? 0 : 0)
            }
        }
        stremioStatus = stremio.map { "Signed in as \($0.email)" } ?? ""
        traktStatus = trakt.map { "Signed in" + ($0.token.username.map { " as \($0)" } ?? "") } ?? ""
        startLoop()
    }

    private func saveStremio(_ pid: String, _ link: StremioLink?) {
        stremio = link
        if let link {
            LocalStore.set([link.authKey, link.email, String(link.lastSync)], "stremio-\(pid)")
        } else {
            LocalStore.remove("stremio-\(pid)")
        }
    }

    private func saveTrakt(_ pid: String, _ link: TraktLink?) {
        trakt = link
        if let link {
            LocalStore.set([link.token.accessToken, link.token.refreshToken, String(link.token.expiresAtMs),
                            link.token.username ?? "-", link.stamp.isEmpty ? "-" : link.stamp, String(link.pushedAt)], "trakt-\(pid)")
        } else {
            LocalStore.remove("trakt-\(pid)")
        }
    }

    /// Syncs now, then every five minutes while the app is open.
    private func startLoop() {
        loop?.cancel()
        guard stremio != nil || trakt != nil else { return }
        loop = Task { [weak self] in
            while !Task.isCancelled {
                await self?.syncAll()
                try? await Task.sleep(for: .seconds(300))
            }
        }
    }

    /// Called after something changes here; sends it a few seconds later, in one go.
    func changed() {
        guard stremio != nil || trakt != nil else { return }
        pushSoon?.cancel()
        pushSoon = Task { [weak self] in
            try? await Task.sleep(for: .seconds(6))
            guard !Task.isCancelled else { return }
            await self?.syncAll()
        }
    }

    func syncAll(full: Bool = false) async {
        guard let pid = model.activeProfileId, !busy else { return }
        busy = true
        syncing = true
        defer {
            busy = false
            syncing = false
        }
        if stremio != nil {
            do { try await syncStremio(pid, full: full) } catch { stremioStatus = Self.message(error, "Stremio") }
        }
        if trakt != nil {
            do { try await syncTrakt(pid) } catch { traktStatus = Self.message(error, "Trakt") }
        }
    }

    static func message(_ error: Error, _ who: String) -> String {
        if let e = error as? CoreError {
            switch e {
            case .SignedOut: return "Signed out of \(who), sign in again"
            case .BadResponse(let reason): return reason
            case .BadAddress(let input): return "Bad address: \(input)"
            }
        }
        if error is URLError { return "\(who) could not be reached" }
        return "\(who) sync failed"
    }

    private static var clock: String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f.string(from: Date())
    }

    // MARK: - Stremio

    func stremioSignIn(email: String, password: String) async throws {
        guard let pid = model.activeProfileId else { return }
        let a = try await Net.call(stremioUrl(method: "login"), body: stremioLoginBody(email: email, password: password))
        let auth = try parseStremioLogin(json: a.body, email: email)
        saveStremio(pid, StremioLink(authKey: auth.authKey, email: auth.email, lastSync: 0))
        stremioStatus = "Signed in as \(auth.email)"
        await syncAll(full: true)
        startLoop()
    }

    func stremioSignOut() {
        guard let pid = model.activeProfileId, let link = stremio else { return }
        Task { _ = try? await Net.call(stremioUrl(method: "logout"), body: stremioLogoutBody(authKey: link.authKey)) }
        saveStremio(pid, nil)
        stremioStatus = ""
    }

    private func syncStremio(_ pid: String, full: Bool) async throws {
        guard let link = stremio else { return }
        let started = Date.nowMs
        if full || link.lastSync == 0 {
            let a = try await Net.call(stremioUrl(method: "addonCollectionGet"), body: stremioAddonsBody(authKey: link.authKey))
            await model.mergeAddons(try parseStremioAddons(json: a.body))
        }
        let lib = try await Net.call(stremioUrl(method: "datastoreGet"), body: stremioLibraryBody(authKey: link.authKey))
        do {
            let m = try stremioLibraryMerge(libraryJson: lib.body, authKey: link.authKey, history: model.history,
                                            watchlist: model.watchlist, profileId: pid, sinceMs: link.lastSync, nowMs: started)
            model.applySynced(history: m.history, watchlist: m.watchlist)
            if let body = m.uploadBody {
                let put = try await Net.call(stremioUrl(method: "datastorePut"), body: body)
                if !put.ok { throw CoreError.BadResponse(reason: "Stremio did not take the changes (\(put.status))") }
            }
            var updated = link
            updated.lastSync = started
            saveStremio(pid, updated)
            stremioStatus = "Synced " + (m.received + m.sent > 0 ? "\(m.received) in, \(m.sent) out" : "and up to date") + " · " + Self.clock
        } catch CoreError.SignedOut(let reason) {
            saveStremio(pid, nil)
            throw CoreError.SignedOut(reason: reason)
        }
    }

    // MARK: - Trakt

    private func headers(_ token: String?) -> [HttpHeader] {
        traktHeaders(clientId: clientId, accessToken: token)
    }

    /// Shows a code; when it is typed on trakt.tv/activate the profile is signed in.
    func traktSignIn() {
        guard let pid = model.activeProfileId else { return }
        guard traktAvailable else {
            traktStatus = "This build has no Trakt key"
            return
        }
        pairing?.cancel()
        pairing = Task { [weak self] in
            guard let self else { return }
            do {
                let a = try await Net.call(traktUrl(path: "oauth/device/code"), body: traktDeviceCodeBody(clientId: clientId), headers: headers(nil))
                let code = try parseTraktDeviceCode(json: a.body, nowMs: Date.nowMs)
                traktCode = code
                traktStatus = ""
                var wait = code.intervalMs
                while !Task.isCancelled && Date.nowMs < code.expiresAtMs {
                    try await Task.sleep(for: .milliseconds(Int(wait)))
                    let p = try await Net.call(traktUrl(path: "oauth/device/token"),
                                               body: traktTokenBody(clientId: clientId, clientSecret: clientSecret, deviceCode: code.deviceCode),
                                               headers: headers(nil))
                    switch parseTraktPoll(status: UInt16(clamping: p.status), json: p.body, nowMs: Date.nowMs) {
                    case .done(let token):
                        var t = token
                        if let s = try? await Net.call(traktUrl(path: "users/settings"), headers: headers(token.accessToken)) {
                            t.username = parseTraktUsername(json: s.body)
                        }
                        saveTrakt(pid, TraktLink(token: t, stamp: "", pushedAt: 0))
                        traktCode = nil
                        traktStatus = "Signed in" + (t.username.map { " as \($0)" } ?? "")
                        await syncAll()
                        startLoop()
                        return
                    case .waiting:
                        break
                    case .slowDown:
                        wait += 1000
                    case .failed(let reason):
                        traktCode = nil
                        traktStatus = reason
                        return
                    }
                }
                if !Task.isCancelled {
                    traktCode = nil
                    traktStatus = "The code ran out, try again"
                }
            } catch is CancellationError {
                traktCode = nil
            } catch {
                traktCode = nil
                traktStatus = Self.message(error, "Trakt")
            }
        }
    }

    func traktCancel() {
        pairing?.cancel()
        traktCode = nil
    }

    func traktSignOut() {
        guard let pid = model.activeProfileId, let link = trakt else { return }
        let id = clientId
        let h = headers(link.token.accessToken)
        Task { _ = try? await Net.call(traktUrl(path: "oauth/revoke"), body: traktRevokeBody(clientId: id, accessToken: link.token.accessToken), headers: h) }
        saveTrakt(pid, nil)
        traktStatus = ""
    }

    private func freshToken(_ pid: String) async throws -> TraktLink? {
        guard var link = trakt else { return nil }
        if traktNeedsRefresh(token: link.token, nowMs: Date.nowMs) {
            let r = try await Net.call(traktUrl(path: "oauth/token"),
                                       body: traktRefreshBody(clientId: clientId, clientSecret: clientSecret, refreshToken: link.token.refreshToken),
                                       headers: headers(nil))
            if r.status == 400 || r.status == 401 {
                saveTrakt(pid, nil)
                throw CoreError.SignedOut(reason: "Trakt sign-in ran out")
            }
            var t = try parseTraktToken(json: r.body, nowMs: Date.nowMs)
            t.username = link.token.username
            link.token = t
            saveTrakt(pid, link)
        }
        return link
    }

    private func syncTrakt(_ pid: String) async throws {
        guard var link = try await freshToken(pid) else { return }
        let h = headers(link.token.accessToken)
        let act = try await Net.call(traktUrl(path: "sync/last_activities"), headers: h)
        if act.status == 401 {
            saveTrakt(pid, nil)
            throw CoreError.SignedOut(reason: "Trakt")
        }
        let stamp = traktActivityStamp(json: act.body)
        var changed: UInt32 = 0
        if stamp != link.stamp {
            let playback = try await Net.call(traktUrl(path: "sync/playback?limit=60"), headers: h)
            if playback.ok {
                let m = try traktMergePlayback(json: playback.body, history: model.history, profileId: pid)
                model.applySynced(history: m.history, watchlist: nil)
                changed += m.changed
            }
            let movies = try await Net.call(traktUrl(path: "sync/watched/movies"), headers: h)
            let shows = try await Net.call(traktUrl(path: "sync/watched/shows"), headers: h)
            if movies.ok && shows.ok {
                let m = try traktMergeWatched(moviesJson: movies.body, showsJson: shows.body, history: model.history, profileId: pid, maxTitles: 120)
                model.applySynced(history: m.history, watchlist: nil)
                changed += m.changed
            }
        }
        // what was watched here and Trakt has not heard of
        let pending = traktPendingHistory(history: model.history, profileId: pid, sinceMs: link.pushedAt)
        let started = Date.nowMs
        var sent = 0
        if let body = traktHistoryBody(entries: pending) {
            let r = try await Net.call(traktUrl(path: "sync/history"), body: body, headers: h)
            if r.ok { sent = pending.count }
        }
        link.stamp = stamp
        if pending.isEmpty || sent > 0 { link.pushedAt = started }
        saveTrakt(pid, link)
        traktStatus = "Synced " + (Int(changed) + sent > 0 ? "\(changed) in, \(sent) out" : "and up to date") + " · " + Self.clock
    }

    /// When the player closes: tells Trakt where it stopped (past 80 % counts as watched).
    func scrobbleStop(playbackId: String, kind: String, positionMs: UInt64, durationMs: UInt64) {
        guard trakt != nil, durationMs > 0, let pid = model.activeProfileId else { return }
        let pct = Double(positionMs) / Double(durationMs) * 100
        guard pct >= 1, let body = traktScrobbleBody(playbackId: playbackId, kind: kind, progressPercent: pct) else { return }
        Task { [weak self] in
            guard let self, let link = try? await freshToken(pid) else { return }
            _ = try? await Net.call(traktUrl(path: "scrobble/stop"), body: body, headers: headers(link.token.accessToken))
        }
    }
}
