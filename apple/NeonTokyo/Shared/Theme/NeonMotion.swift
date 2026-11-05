import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// Neon Home's motion, number for number (the Google TV home screen): the pop when a tile
/// takes focus, the settle when it lets go, the floor light gliding to the next tile, the row
/// sliding so the focused tile stays at the same spot. Compose gives springs as damping and
/// stiffness; SwiftUI's response is 2π/√stiffness for the same unit mass.
enum NeonMotion {
    /// How much a focused tile grows on Neon Tokyo. Deep Blue's glass grows 43 % more.
    static let pop: CGFloat = 1.07
    static let glassFactor: CGFloat = 1.43
    /// Damping 0.55, stiffness 650: the bouncy landing.
    static let popSpring = Animation.spring(response: 0.246, dampingFraction: 0.55)
    /// Stiffness 900, no bounce: letting go.
    static let settle = Animation.spring(response: 0.209, dampingFraction: 1)
    /// Damping 0.72, stiffness 300: glass lifting toward you.
    static let glassSpring = Animation.spring(response: 0.363, dampingFraction: 0.72)
    /// Damping 0.86, stiffness 240: the floor light gliding under the next tile.
    static let glide = Animation.spring(response: 0.405, dampingFraction: 0.86)
    /// Damping 0.9, stiffness 260: a row sliding under the focus pivot.
    static let slide = Animation.spring(response: 0.39, dampingFraction: 0.9)
    /// The neon tube: 150 ms on, 240 ms off.
    static let tubeOn = Animation.easeOut(duration: 0.15)
    static let tubeOff = Animation.easeOut(duration: 0.24)
    /// The light sweeping across a tile once, 900 ms after a 50 ms wait.
    static let sheen = Animation.timingCurve(0.25, 0.1, 0.25, 1, duration: 0.9).delay(0.05)

    /// Material's FastOutSlowIn, which the TV uses for leans and fades.
    static func fastOutSlowIn(_ seconds: Double) -> Animation {
        .timingCurve(0.4, 0, 0.2, 1, duration: seconds)
    }
}

extension Color {
    /// This colour moved toward another by `amount` (0...1), like Compose's lerp: the neon
    /// tube's white-hot core is the tube's colour 62 % of the way to white.
    func mixed(with other: Color, _ amount: CGFloat) -> Color {
        var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
        var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
        UIColor(self).getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        UIColor(other).getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        let t = min(max(amount, 0), 1)
        return Color(.sRGB,
                     red: Double(r1 + (r2 - r1) * t),
                     green: Double(g1 + (g2 - g1) * t),
                     blue: Double(b1 + (b2 - b1) * t),
                     opacity: Double(a1 + (a2 - a1) * t))
    }
}

// MARK: - The spot: where the focused tile is

/// The focused tile's place and colour, handed up the view tree so the floor light can find it.
struct SpotValue {
    let bounds: Anchor<CGRect>
    let color: Color
}

struct SpotKey: PreferenceKey {
    static let defaultValue: SpotValue? = nil

    static func reduce(value: inout SpotValue?, nextValue: () -> SpotValue?) {
        if value == nil { value = nextValue() }
    }
}

extension View {
    /// Draws Neon Home's floor light under whichever tile inside this view has focus.
    func neonFloorLight() -> some View {
        backgroundPreferenceValue(SpotKey.self) { spot in
            GeometryReader { g in
                FloorPool(rect: spot.map { g[$0.bounds] }, color: spot?.color ?? .clear)
            }
            .allowsHitTesting(false)
        }
    }
}

/// A pool of the focused tile's colour on the floor under it, and its faint reflection, that
/// glides to the next tile instead of jumping, and fades when the focus leaves the rows.
private struct FloorPool: View {
    let rect: CGRect?
    let color: Color
    @State private var last: CGRect = .zero
    @State private var lastColor: Color = .clear
    @Environment(\.neon) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let r = rect ?? last
        let glass = theme.focus == .glass
        let c = glass ? Color.white : (rect == nil ? lastColor : color)
        ZStack {
            // the pool on the floor
            Circle()
                .fill(RadialGradient(colors: [c.opacity(glass ? 0.20 : 0.42), c.opacity(glass ? 0.06 : 0.14), c.opacity(0)],
                                     center: .center, startRadius: 0, endRadius: max(r.width, 1) * 0.78))
                .frame(width: r.width * 1.56, height: r.width * 1.56)
                .scaleEffect(x: 1, y: 0.2)
                .position(x: r.midX, y: r.maxY + r.height * 0.05)
            // the tile's reflection on the wet floor, fading downward
            if !glass {
                LinearGradient(colors: [c.opacity(0.18), c.opacity(0)], startPoint: .top, endPoint: .bottom)
                    .frame(width: max(r.width - 16, 0), height: r.height * 0.2)
                    .position(x: r.midX, y: r.maxY + 4 + r.height * 0.1)
            }
        }
        .blendMode(.plusLighter)
        .opacity(rect == nil || r.width < 1 ? 0 : 1)
        .animation(.easeOut(duration: 0.3), value: rect == nil)
        .animation(reduceMotion ? .easeOut(duration: 0.15) : NeonMotion.glide, value: r)
        .animation(.easeInOut(duration: 0.36), value: c)
        .onChange(of: rect) { _, new in
            if let new {
                last = new
                lastColor = color
            }
        }
    }
}
