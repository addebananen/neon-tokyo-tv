import Foundation
import Observation
#if canImport(UIKit)
import UIKit
#endif

/// "Fortsätt på TV:n": hands a title and the spot reached to a Neon Tokyo TV, through the
/// profile's Stremio account (no pairing, works off the home network too). Two hidden items in
/// the account's library carry it, the same as the web app and the TV read and write; see
/// docs/handoff.md.
@MainActor
@Observable
final class Handoff {
    enum Phase: Equatable {
        case idle
        case looking
        case choosing([TV])
        case sending(String)
        case playing(String)
        case failed(String)
    }

    struct TV: Equatable, Identifiable {
        let id: String
        let name: String
        let seenAt: Int64
    }

    struct Item {
        let id: String
        let kind: String
        let video: String
        let name: String
        let title: String
        let poster: String?
        let background: String?
        let logo: String?
        let positionMs: UInt64
        let durationMs: UInt64
    }

    static let mailboxId = "neontokyo:handoff"
    static let presenceId = "neontokyo:tvs"
    private static let onWithinMs: Int64 = 10 * 60 * 1000

    private(set) var phase: Phase = .idle
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var pending: Item?

    private unowned let model: AppModel
    init(model: AppModel) { self.model = model }

    var available: Bool { model.accounts.stremio != nil }

    // MARK: - The press

    func start(_ item: Item) {
        task?.cancel()
        pending = item
        phase = .looking
        task = Task { [weak self] in
            guard let self else { return }
            do {
                let tvs = try await self.tvsOn()
                if tvs.count > 1 {
                    let last = LocalStore.string("handoff-tv")
                    self.phase = .choosing(tvs.sorted { ($0.id == last ? 1 : 0) > ($1.id == last ? 1 : 0) })
                    return
                }
                await self.send(item, to: tvs.first)
            } catch {
                self.phase = .failed(Self.why(error))
            }
        }
    }

    func choose(_ tv: TV) {
        guard let item = pending else { return }
        LocalStore.set(tv.id, "handoff-tv")
        task?.cancel()
        task = Task { [weak self] in await self?.send(item, to: tv) }
    }

    func cancel() {
        task?.cancel()
        task = nil
        pending = nil
        phase = .idle
    }

    private func send(_ item: Item, to tv: TV?) async {
        guard let key = model.accounts.stremio?.authKey else { phase = .failed("Logga in profilen med Stremio först."); return }
        phase = .sending(tv.map { "Skickar till \($0.name)…" } ?? "Skickar… Slå på TV:n så tar den emot det inom fem minuter.")
        let now = Date.nowMs
        let nonce = String(UUID().uuidString.prefix(12)).lowercased()
        let message: [String: Any] = [
            "v": 1, "nonce": nonce, "to": tv?.id ?? "*", "from": Self.deviceName,
            "profile": model.activeProfile?.name ?? "", "sentAt": now,
            "id": item.id, "type": item.kind == "series" ? "series" : "movie", "video": item.video,
            "name": item.name, "title": item.title,
            "poster": item.poster ?? "", "background": item.background ?? "", "logo": item.logo ?? "",
            "positionMs": item.positionMs, "durationMs": item.durationMs,
        ]
        do {
            let text = String(decoding: try JSONSerialization.data(withJSONObject: message), as: UTF8.self)
            try await put(key, [Self.hiddenItem(Self.mailboxId, name: text, nowMs: now)])
            #if canImport(UIKit)
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            #endif
            let until = Date.nowMs + (tv == nil ? 5 * 60 * 1000 : 25 * 1000)
            while Date.nowMs < until {
                try await Task.sleep(nanoseconds: 1_500_000_000)
                guard let box = try? await get(key, [Self.mailboxId])[Self.mailboxId],
                      let m = Self.parse(box["name"] as? String) else { continue }
                if (m["nonce"] as? String) != nonce { phase = .idle; return }
                if let taken = m["takenBy"] as? String, !taken.isEmpty {
                    phase = .playing((m["takenName"] as? String) ?? "TV:n")
                    #if canImport(UIKit)
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    #endif
                    try? await Task.sleep(nanoseconds: 2_200_000_000)
                    if case .playing = phase { phase = .idle }
                    return
                }
            }
            phase = .failed("TV:n svarade inte. Är den på, med Neon Tokyo inloggat på samma Stremio-konto?")
        } catch is CancellationError {
            return
        } catch {
            phase = .failed(Self.why(error))
        }
    }

    // MARK: - The account's library

    private func tvsOn() async throws -> [TV] {
        guard let key = model.accounts.stremio?.authKey else { return [] }
        let presence = try await get(key, [Self.presenceId])[Self.presenceId]
        let tvs = (Self.parse(presence?["name"] as? String)?["tvs"] as? [String: Any]) ?? [:]
        let now = Date.nowMs
        return tvs.compactMap { id, v in
            guard let t = v as? [String: Any], (t["on"] as? Bool) == true else { return nil }
            let seen = (t["seenAt"] as? NSNumber)?.int64Value ?? 0
            guard now - seen < Self.onWithinMs else { return nil }
            return TV(id: id, name: (t["name"] as? String) ?? "TV", seenAt: seen)
        }
        .sorted { $0.seenAt > $1.seenAt }
    }

    private func get(_ key: String, _ ids: [String]) async throws -> [String: [String: Any]] {
        let body: [String: Any] = ["authKey": key, "collection": "libraryItem", "ids": ids, "all": false]
        let a = try await Net.call(stremioUrl(method: "datastoreGet"), body: Self.json(body))
        guard let root = Self.parse(a.body) else { throw URLError(.cannotParseResponse) }
        if let err = root["error"] { throw Self.stremioError(err) }
        var out: [String: [String: Any]] = [:]
        for case let it as [String: Any] in (root["result"] as? [Any]) ?? [] {
            if let id = it["_id"] as? String { out[id] = it }
        }
        return out
    }

    private func put(_ key: String, _ items: [[String: Any]]) async throws {
        let body: [String: Any] = ["authKey": key, "collection": "libraryItem", "changes": items]
        let a = try await Net.call(stremioUrl(method: "datastorePut"), body: Self.json(body))
        if let root = Self.parse(a.body), let err = root["error"] { throw Self.stremioError(err) }
        if !a.ok { throw URLError(.badServerResponse) }
    }

    /// A library item Stremio's apps read without trouble and never show.
    static func hiddenItem(_ id: String, name: String, nowMs: Int64) -> [String: Any] {
        let iso = isoTime(nowMs)
        return [
            "_id": id, "name": name, "type": "other", "poster": "", "posterShape": "poster",
            "removed": true, "temp": true, "_ctime": iso, "_mtime": iso,
            "state": ["lastWatched": iso, "timeWatched": 0, "timeOffset": 0, "overallTimeWatched": 0,
                      "timesWatched": 0, "flaggedWatched": 0, "duration": 0, "video_id": id,
                      "watched": "", "noNotif": false] as [String: Any],
            "behaviorHints": ["defaultVideoId": NSNull(), "featuredVideoId": NSNull(), "hasScheduledVideos": false] as [String: Any],
        ]
    }

    private static func isoTime(_ ms: Int64) -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
    }

    private static func json(_ o: Any) throws -> String {
        String(decoding: try JSONSerialization.data(withJSONObject: o), as: UTF8.self)
    }

    private static func parse(_ text: String?) -> [String: Any]? {
        guard let text, let data = text.data(using: .utf8) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    }

    private static func stremioError(_ err: Any) -> Error {
        let message = ((err as? [String: Any])?["message"] as? String) ?? "\(err)"
        return NSError(domain: "Stremio", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
    }

    private static func why(_ error: Error) -> String {
        if (error as? URLError) != nil { return "Stremio gick inte att nå. Kolla nätet och försök igen." }
        let text = (error as NSError).localizedDescription
        if text.localizedCaseInsensitiveContains("session") || text.localizedCaseInsensitiveContains("auth") {
            return "Stremio har loggat ut profilen. Logga in igen i Inställningar."
        }
        return text
    }

    private static var deviceName: String {
        #if os(iOS)
        return UIDevice.current.userInterfaceIdiom == .pad ? "iPad" : "iPhone"
        #else
        return "Apple TV"
        #endif
    }
}
