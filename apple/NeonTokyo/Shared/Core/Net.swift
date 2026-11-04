import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Fetching addon answers and pictures. The core builds every address and reads every answer;
/// this only moves the bytes.
enum Net {
    static let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 15
        c.timeoutIntervalForResource = 30
        c.urlCache = URLCache(memoryCapacity: 32 << 20, diskCapacity: 256 << 20)
        c.requestCachePolicy = .useProtocolCachePolicy
        c.httpAdditionalHeaders = ["User-Agent": "NeonTokyo-Apple/\(Bundle.main.shortVersion)"]
        return URLSession(configuration: c)
    }()

    static func text(_ address: String) async throws -> String {
        guard let url = URL(string: address) else { throw URLError(.badURL) }
        let (data, response) = try await session.data(from: url)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw URLError(.badServerResponse)
        }
        return String(decoding: data, as: UTF8.self)
    }
}

extension Bundle {
    /// The release name shared with the Android app (`0.2.0-nt.260`).
    var shortVersion: String {
        infoDictionary?["NeonVersion"] as? String ?? infoDictionary?["CFBundleShortVersionString"] as? String ?? "0"
    }
}

/// Posters and backgrounds, kept in memory while they are on screen.
///
/// Cinemeta's pictures come from images.metahub.space, which some networks (Adrian's among
/// them) cannot reach. Like the Android app's MetahubFallbackInterceptor, a failed metahub
/// picture is fetched again through wsrv.nl.
actor ImageLoader {
    static let shared = ImageLoader()
    private let cache = NSCache<NSString, UIImage>()
    private var metahubDown = false

    init() {
        cache.countLimit = 400
    }

    func image(for address: String) async -> UIImage? {
        if let hit = cache.object(forKey: address as NSString) { return hit }
        let direct = metahubDown && address.contains("metahub.space") ? nil : await fetch(address)
        var result = direct
        if result == nil, address.contains("metahub.space") {
            metahubDown = true
            let proxied = "https://wsrv.nl/?url=" + (address.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? address)
            result = await fetch(proxied)
        }
        if let result { cache.setObject(result, forKey: address as NSString) }
        return result
    }

    private func fetch(_ address: String) async -> UIImage? {
        guard let url = URL(string: address),
              let answer = try? await Net.session.data(from: url)
        else { return nil }
        let status = (answer.1 as? HTTPURLResponse)?.statusCode ?? 200
        guard status < 400 else { return nil }
        return UIImage(data: answer.0)
    }
}
