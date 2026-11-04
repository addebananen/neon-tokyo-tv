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

/// The page background: the theme's night colour with a faint glow from the bottom, like the
/// Android app's LumeraBackground.
struct NeonBackground: View {
    @Environment(\.neon) private var theme

    var body: some View {
        ZStack {
            theme.background
            LinearGradient(
                colors: [theme.background, theme.surface.opacity(0.55), theme.primary.opacity(theme.focus == .glow ? 0.18 : 0.08)],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        .ignoresSafeArea()
    }
}

/// Cards and buttons: Neon grows a little and glows; Deep Blue grows more and catches light like
/// glass. On iPhone a press shrinks the card a touch instead.
struct NeonCardStyle: ButtonStyle {
    var radius: CGFloat = NeonTokens.shapeCardRadius

    func makeBody(configuration: Configuration) -> some View {
        NeonCardBody(configuration: configuration, radius: radius)
    }
}

private struct NeonCardBody: View {
    let configuration: ButtonStyle.Configuration
    let radius: CGFloat
    @Environment(\.isFocused) private var focused
    @Environment(\.neon) private var theme

    var body: some View {
        let glow = theme.focus == .glow
        configuration.label
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay {
                if glass && focused {
                    RoundedRectangle(cornerRadius: radius, style: .continuous)
                        .fill(LinearGradient(colors: [.white.opacity(0.28), .clear, .white.opacity(0.06)], startPoint: .topLeading, endPoint: .bottomTrailing))
                        .allowsHitTesting(false)
                }
            }
            .overlay {
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .strokeBorder(glow ? theme.primary : .white.opacity(0.7), lineWidth: focused ? (glow ? 3 : 1.5) : 0)
            }
            .shadow(color: focused ? (glow ? theme.primary.opacity(NeonTokens.focusGlowOpacity) : .black.opacity(0.5)) : .clear,
                    radius: focused ? NeonTokens.focusGlowRadius : 0, y: glow ? 0 : 18)
            .scaleEffect(focused ? theme.focusScale : (configuration.isPressed ? 0.96 : 1))
            .animation(.easeOut(duration: NeonTokens.motionFocusMs / 1000), value: focused)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }

    private var glass: Bool { theme.focus == .glass }
}

/// A pill button for actions (Play, Sources, Add to list).
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
        let lit = focused || prominent
        configuration.label
            .font(.system(size: Metrics.bodySize, weight: .semibold))
            .padding(.horizontal, Metrics.bodySize * 1.1)
            .padding(.vertical, Metrics.bodySize * 0.55)
            .foregroundStyle(lit ? theme.background : theme.text)
            .background(
                Capsule().fill(lit ? (focused ? theme.text : theme.primary) : theme.surface.opacity(0.7))
            )
            .shadow(color: focused && theme.focus == .glow ? theme.primary.opacity(0.8) : .clear, radius: 18)
            .scaleEffect(focused ? 1.06 : (configuration.isPressed ? 0.95 : 1))
            .animation(.easeOut(duration: NeonTokens.motionFocusMs / 1000), value: focused)
    }
}
