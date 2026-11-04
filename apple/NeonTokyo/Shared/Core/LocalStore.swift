import Foundation

/// Small things kept on the device: profiles, addons, history, watchlist, theme.
///
/// Apple TV only promises to keep a little local data (UserDefaults, about 500 KB), so history
/// is trimmed by the core before saving and bigger caches go to the Caches folder, which the
/// system may empty; they are fetched again when that happens.
enum LocalStore {
    private static let defaults = UserDefaults.standard

    static func string(_ key: String) -> String? { defaults.string(forKey: key) }
    static func set(_ value: String?, _ key: String) { defaults.set(value, forKey: key) }
    static func strings(_ key: String) -> [String]? { defaults.stringArray(forKey: key) }
    static func set(_ value: [String], _ key: String) { defaults.set(value, forKey: key) }

    static func cache(_ name: String) -> String? {
        guard let url = cacheURL(name) else { return nil }
        return try? String(contentsOf: url, encoding: .utf8)
    }

    static func setCache(_ text: String, _ name: String) {
        guard let url = cacheURL(name) else { return }
        try? text.write(to: url, atomically: true, encoding: .utf8)
    }

    private static func cacheURL(_ name: String) -> URL? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?.appendingPathComponent("neon-\(name).json")
    }
}

extension Date {
    static var nowMs: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    static var todayIso: String {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }
}
