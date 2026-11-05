import SwiftUI

/// How a focused card shows it is focused: Neon Tokyo glows, Deep Blue lifts like glass.
enum FocusStyle {
    case glow
    case glass
}

/// One theme's colours, filled in from design/neon-tokens.json (see NeonTokens.swift).
struct NeonThemeTokens: Identifiable {
    let id: String
    let name: String
    let focus: FocusStyle
    let primary: Color
    let secondary: Color
    let warm: Color
    let background: Color
    let surface: Color
    let panel: Color
    let text: Color
    let muted: Color
    let error: Color

    var focusScale: CGFloat { focus == .glow ? NeonTokens.focusGlowScale : NeonTokens.focusGlassScale }
}

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }

    /// `#RRGGBB`, as profile colours are stored.
    init(hexString: String) {
        let clean = hexString.trimmingCharacters(in: CharacterSet(charactersIn: "# "))
        self.init(hex: UInt32(clean, radix: 16) ?? 0xFF3D9A)
    }
}

private struct NeonThemeKey: EnvironmentKey {
    static let defaultValue: NeonThemeTokens = NeonTokens.neonTokyo
}

extension EnvironmentValues {
    var neon: NeonThemeTokens {
        get { self[NeonThemeKey.self] }
        set { self[NeonThemeKey.self] = newValue }
    }
}

/// Sizes that differ between a TV across the room and a phone in the hand.
enum Metrics {
    #if os(tvOS)
    static let posterWidth: CGFloat = NeonTokens.posterTvWidth
    static let edge: CGFloat = 80
    static let rowSpacing: CGFloat = 48
    static let cardSpacing: CGFloat = 40
    static let titleSize: CGFloat = 38
    static let bodySize: CGFloat = 26
    static let heroHeight: CGFloat = 620
    #else
    static let posterWidth: CGFloat = NeonTokens.posterPhoneWidth
    static let edge: CGFloat = 16
    static let rowSpacing: CGFloat = 24
    static let cardSpacing: CGFloat = 12
    static let titleSize: CGFloat = 20
    static let bodySize: CGFloat = 15
    static let heroHeight: CGFloat = 420
    #endif
    static var posterHeight: CGFloat { posterWidth / NeonTokens.posterAspect }
    /// How tall a row of tiles is, title included, to keep the focused row in one place.
    static func rowHeight(tile: CGFloat) -> CGFloat {
        titleSize * 0.8 * 1.3 + cardSpacing * 0.5 + tile + cardSpacing * 1.8
    }
}

/// Kanit, the Android app's neon font, from the same font files.
enum NeonFont {
    static func display(_ size: CGFloat) -> Font { .custom(NeonTokens.fontDisplay, size: size) }
    static func heading(_ size: CGFloat) -> Font { .custom(NeonTokens.fontHeading, size: size) }
    static func label(_ size: CGFloat) -> Font { .custom(NeonTokens.fontLabel, size: size) }
}

/// A title lit like a neon sign: the text plus two soft glows in the theme's colour.
struct NeonTitle: View {
    let text: String
    var size: CGFloat = Metrics.titleSize
    var glow: Color? = nil
    @Environment(\.neon) private var theme

    var body: some View {
        let g = glow ?? theme.primary
        Text(text)
            .font(theme.focus == .glow ? NeonFont.display(size) : .system(size: size, weight: .semibold))
            .foregroundStyle(theme.text)
            .shadow(color: theme.focus == .glow ? g.opacity(NeonTokens.focusGlowOpacity) : .clear, radius: size * 0.35)
            .shadow(color: theme.focus == .glow ? g.opacity(0.5) : .clear, radius: size * 0.12)
    }
}

/// The page background: Neon Tokyo's world (skyline, stars, the rolling grid), dimmed a little
/// so what is on top reads clearly.
struct NeonBackground: View {
    var dim: Double = 0.25

    var body: some View {
        NeonWorld(dim: dim)
    }
}

/// Lists and big buttons (sources, episodes, profiles): Neon Home's focus with a smaller pop,
/// since they are wide.
struct NeonCardStyle: ButtonStyle {
    var radius: CGFloat = NeonTokens.shapeCardRadius
    var pop: CGFloat = 1.025

    func makeBody(configuration: Configuration) -> some View {
        NeonTileStyle(radius: radius, pop: pop).makeBody(configuration: configuration)
    }
}

/// A pill button for actions (Play, Watchlist, Trailer). Focused, it pops on Neon Home's spring
/// and lights white with a neon halo; the prominent one (Play) glows in the theme's colours.
struct NeonPillStyle: ButtonStyle {
    var prominent = false

    func makeBody(configuration: Configuration) -> some View {
        NeonPillBody(configuration: configuration, prominent: prominent)
    }
}

private struct NeonPillBody: View {
    let configuration: ButtonStyle.Configuration
    let prominent: Bool
    @Environment(\.isFocused) private var focused
    @Environment(\.neon) private var theme

    var body: some View {
        let glow = theme.focus == .glow
        let pressed = configuration.isPressed
        let scale: CGFloat = pressed ? 0.95 : (focused ? 1.08 : 1)
        configuration.label
            .font(.system(size: Metrics.bodySize, weight: .semibold))
            .padding(.horizontal, Metrics.bodySize * 1.1)
            .padding(.vertical, Metrics.bodySize * 0.55)
            .foregroundStyle(focused ? theme.background : theme.text)
            .background {
                ZStack {
                    if focused {
                        Capsule().fill(theme.text)
                    } else if prominent {
                        Capsule().fill(LinearGradient(colors: [theme.primary, theme.primary.mixed(with: theme.secondary, 0.35)],
                                                      startPoint: .leading, endPoint: .trailing))
                    } else {
                        Capsule().fill(theme.surface.opacity(0.72))
                        Capsule().strokeBorder(.white.opacity(0.12), lineWidth: 1)
                    }
                }
            }
            .shadow(color: focused ? (glow ? theme.primary.opacity(0.85) : .black.opacity(0.45)) : (prominent && glow ? theme.primary.opacity(0.45) : .clear),
                    radius: focused ? 20 : 12, y: glow ? 0 : 10)
            .scaleEffect(scale)
            .animation(focused && !pressed ? NeonMotion.popSpring : NeonMotion.settle, value: scale)
            .animation(focused ? NeonMotion.tubeOn : NeonMotion.tubeOff, value: focused)
    }
}
