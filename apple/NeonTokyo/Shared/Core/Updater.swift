import Foundation
import Observation

/// Looks for a newer Neon Tokyo for this device on GitHub when the app starts and every six
/// hours. iPhone and Apple TV cannot install an app from inside another, so the update is
/// shown with where to get it (a link on iPhone, a code to scan on Apple TV), and Sideloadly or
/// atvloadly put it on as before; profiles, history and addons stay.
@MainActor
@Observable
final class Updater {
    private(set) var available: UpdateInfo?
    private(set) var status = ""
    private(set) var checking = false
    @ObservationIgnored private var loop: Task<Void, Never>?

    /// The installer this device takes.
    static var assetSuffix: String {
        #if os(tvOS)
        return "-tvOS.ipa"
        #else
        return "-iOS.ipa"
        #endif
    }

    func start() {
        loop?.cancel()
        loop = Task { [weak self] in
            try? await Task.sleep(for: .seconds(5))
            while !Task.isCancelled {
                await self?.check()
                try? await Task.sleep(for: .seconds(6 * 3600))
            }
        }
    }

    func check() async {
        guard !checking else { return }
        checking = true
        defer { checking = false }
        do {
            let a = try await Net.call(releasesUrl(), headers: [HttpHeader(name: "Accept", value: "application/vnd.github+json")])
            guard a.ok else {
                status = "GitHub answered \(a.status)"
                return
            }
            available = pickUpdate(json: a.body, assetSuffixes: [Self.assetSuffix], currentVersion: Bundle.main.shortVersion,
                                   builtAtMs: Bundle.main.builtAtMs)
            status = available == nil ? "You have the newest version" : ""
        } catch {
            status = "Could not reach GitHub"
        }
    }
}
