import Foundation
import ImageIO
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// Fetching addon answers, account calls and pictures. The core builds every address and every
/// body and reads every answer; this only moves the bytes.
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

    /// What an account server answered: its status and its text, whatever the status was
    /// (Trakt answers "not yet" while a code waits to be typed with a 400).
    struct Answer {
        let status: Int
        let body: String
        var ok: Bool { (200..<300).contains(status) }
    }

    /// A GET, or a POST of JSON when there is a body, with the given headers. Never cached.
    static func call(_ address: String, body: String? = nil, headers: [HttpHeader] = []) async throws -> Answer {
        guard let url = URL(string: address) else { throw URLError(.badURL) }
        var r = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 20)
        if let body {
            r.httpMethod = "POST"
            r.httpBody = Data(body.utf8)
            r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        for h in headers { r.setValue(h.value, forHTTPHeaderField: h.name) }
        let (data, response) = try await session.data(for: r)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        return Answer(status: status, body: String(decoding: data, as: UTF8.self))
    }
}

extension Bundle {
    /// The release name shared with the Android app (`0.2.0-nt.260`).
    var shortVersion: String {
        infoDictionary?["NeonVersion"] as? String ?? infoDictionary?["CFBundleShortVersionString"] as? String ?? "0"
    }

    /// When CI built this app, in ms since 1970 (0 when built by hand).
    var builtAtMs: Int64 {
        Int64((infoDictionary?["NeonBuiltAt"] as? String) ?? "") ?? 0
    }

    /// The Trakt app id CI built in (empty when Trakt is not set up for this build).
    var traktClientId: String {
        ((infoDictionary?["TraktClientId"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
    }

    /// The Trakt app's secret, for Trakt apps that still have one (the Android app sends it too).
    var traktClientSecret: String? {
        let s = ((infoDictionary?["TraktClientSecret"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
        return s.isEmpty ? nil : s
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
    private var tints: [String: Color] = [:]
    private var metahubDown = false

    init() {
        cache.countLimit = 500
        // decoded pictures are big: keep about 160 MB of them
        cache.totalCostLimit = 160 << 20
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
        if let result {
            let px = (result.cgImage?.width ?? 0) * (result.cgImage?.height ?? 0)
            cache.setObject(result, forKey: address as NSString, cost: max(px * 4, 1))
        }
        return result
    }

    /// The picture's own neon: its strongest saturated colour, brightened to glow, or nil for a
    /// grey picture (the theme's colour is used then). Neon Home lights each tile this way.
    func tint(for address: String) async -> Color? {
        if let known = tints[address] { return known }
        guard let img = await image(for: address), let c = Self.dominant(img) else { return nil }
        tints[address] = c
        return c
    }

    private func fetch(_ address: String) async -> UIImage? {
        guard let url = URL(string: address),
              let answer = try? await Net.session.data(from: url)
        else { return nil }
        let status = (answer.1 as? HTTPURLResponse)?.statusCode ?? 200
        guard status < 400 else { return nil }
        return Self.decode(answer.0)
    }

    /// Decodes here, off the main thread, and no bigger than a 4K screen needs, so a row of
    /// posters never stalls scrolling while pictures unpack at first draw.
    private static func decode(_ data: Data) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return UIImage(data: data) }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: 1920,
        ]
        guard let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return UIImage(data: data) }
        return UIImage(cgImage: cg)
    }

    /// Looks at the picture shrunk to 16 × 16: sorts the colourful pixels into twelve hues and
    /// takes the hue with the most colour in it.
    private static func dominant(_ image: UIImage) -> Color? {
        guard let cg = image.cgImage else { return nil }
        let side = 16
        var buckets = [(weight: CGFloat, r: CGFloat, g: CGFloat, b: CGFloat)](repeating: (weight: 0, r: 0, g: 0, b: 0), count: 12)
        var pixels = [UInt8](repeating: 0, count: side * side * 4)
        let drawn: Bool = pixels.withUnsafeMutableBytes { buf in
            guard let ctx = CGContext(data: buf.baseAddress, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4,
                                      space: CGColorSpaceCreateDeviceRGB(),
                                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
            else { return false }
            ctx.interpolationQuality = .medium
            ctx.draw(cg, in: CGRect(x: 0, y: 0, width: side, height: side))
            return true
        }
        guard drawn else { return nil }
        for i in stride(from: 0, to: pixels.count, by: 4) {
            let r = CGFloat(pixels[i]) / 255, g = CGFloat(pixels[i + 1]) / 255, b = CGFloat(pixels[i + 2]) / 255
            let mx = max(r, g, b), mn = min(r, g, b)
            let sat = mx > 0 ? (mx - mn) / mx : 0
            guard sat > 0.25, mx > 0.2 else { continue }
            var hue: CGFloat = 0, s: CGFloat = 0, v: CGFloat = 0, a: CGFloat = 0
            UIColor(red: r, green: g, blue: b, alpha: 1).getHue(&hue, saturation: &s, brightness: &v, alpha: &a)
            let k = min(Int(hue * 12), 11)
            let w = sat * mx
            buckets[k].weight += w
            buckets[k].r += r * w
            buckets[k].g += g * w
            buckets[k].b += b * w
        }
        guard let best = buckets.max(by: { $0.weight < $1.weight }), best.weight > 4 else { return nil }
        var hue: CGFloat = 0, s: CGFloat = 0, v: CGFloat = 0, a: CGFloat = 0
        UIColor(red: best.r / best.weight, green: best.g / best.weight, blue: best.b / best.weight, alpha: 1)
            .getHue(&hue, saturation: &s, brightness: &v, alpha: &a)
        // neon: saturated and bright, whatever the poster's shade was
        return Color(hue: Double(hue), saturation: Double(max(s, 0.6)), brightness: Double(max(v, 0.92)))
    }
}
