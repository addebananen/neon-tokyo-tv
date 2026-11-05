import SwiftUI

// MARK: - The tile's focus: Neon Home, exactly

/// A tile that lands like a Neon Home tile on the TV: a bouncy pop to 107 % (damping 0.55,
/// stiffness 650), a neon tube that lights around its edge in 150 ms with a white-hot core, a
/// glow in the picture's own colour spilling past it, light sweeping across it once, and the
/// floor light gliding under it. Deep Blue swaps the tube for a frosted glass rim, grows 43 %
/// more on a softer spring, and casts a shadow instead of a glow.
///
/// On iPhone there is no focus, so a press sinks the tile a little and lets it spring back.
struct NeonTileStyle: ButtonStyle {
    var radius: CGFloat = NeonTokens.shapeCardRadius
    var glow: Color? = nil
    var pop: CGFloat = NeonMotion.pop

    func makeBody(configuration: Configuration) -> some View {
        NeonTileBody(configuration: configuration, radius: radius, glow: glow, pop: pop)
    }
}

private struct NeonTileBody: View {
    let configuration: ButtonStyle.Configuration
    let radius: CGFloat
    let glow: Color?
    let pop: CGFloat
    @Environment(\.isFocused) private var focused
    @Environment(\.neon) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sweep: CGFloat = 0

    var body: some View {
        let glass = theme.focus == .glass
        let tint = glow ?? theme.primary
        let pressed = configuration.isPressed
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        let scale = scaleNow(pressed: pressed, glass: glass)
        let at = sweep
        configuration.label
            .overlay {
                // light sweeping across, once each time the focus lands
                LinearGradient(stops: [
                    .init(color: .white.opacity(0), location: 0.36),
                    .init(color: .white.opacity(glass ? 0.22 : 0.16), location: 0.5),
                    .init(color: .white.opacity(0), location: 0.64),
                ], startPoint: .leading, endPoint: .trailing)
                .scaleEffect(x: 1, y: 1.8)
                .rotationEffect(.degrees(14))
                .visualEffect { content, proxy in
                    content.offset(x: proxy.size.width * (-1.25 + 2.5 * at))
                }
                .blendMode(.plusLighter)
                .allowsHitTesting(false)
            }
            .overlay {
                // glass catches the light along its top
                if glass && focused {
                    LinearGradient(colors: [.white.opacity(0.14), .white.opacity(0)], startPoint: .top, endPoint: .center)
                        .allowsHitTesting(false)
                        .transition(.opacity)
                }
            }
            .clipShape(shape)
            .overlay {
                ZStack {
                    if focused {
                        if glass {
                            shape.strokeBorder(LinearGradient(colors: [.white.opacity(0.75), .white.opacity(0.18)],
                                                              startPoint: .top, endPoint: .bottom), lineWidth: 1.5)
                        } else {
                            // the neon tube: its colour, then the white-hot core down its middle
                            shape.inset(by: 1).stroke(tint, lineWidth: 3)
                            shape.inset(by: 1).stroke(tint.mixed(with: .white, 0.62), lineWidth: 1.2)
                        }
                    }
                }
                .animation(focused ? NeonMotion.tubeOn : NeonMotion.tubeOff, value: focused)
                .allowsHitTesting(false)
            }
            .background {
                ZStack {
                    if focused {
                        if glass {
                            shape.fill(Color.black.opacity(0.55))
                                .padding(.horizontal, 8)
                                .offset(y: 18)
                                .blur(radius: 22)
                        } else {
                            shape.fill(tint.opacity(0.55)).blur(radius: 16)
                            shape.fill(tint.opacity(0.3)).blur(radius: 40)
                        }
                    }
                }
                .animation(focused ? NeonMotion.tubeOn : NeonMotion.tubeOff, value: focused)
                .allowsHitTesting(false)
            }
            .anchorPreference(key: SpotKey.self, value: .bounds) { focused ? SpotValue(bounds: $0, color: tint) : nil }
            .scaleEffect(scale)
            .animation(focused && !pressed ? (glass ? NeonMotion.glassSpring : NeonMotion.popSpring) : NeonMotion.settle, value: scale)
            .zIndex(focused ? 1 : 0)
            .onChange(of: focused) { _, now in
                if now && !reduceMotion {
                    withAnimation(NeonMotion.sheen) { sweep = 1 }
                } else {
                    var t = Transaction()
                    t.disablesAnimations = true
                    withTransaction(t) { sweep = 0 }
                }
            }
    }

    /// Apple TV: pops when focused, dips a touch when pressed. iPhone: sinks when pressed.
    private func scaleNow(pressed: Bool, glass: Bool) -> CGFloat {
        #if os(tvOS)
        if pressed { return glass ? 0.985 : 1.02 }
        return focused ? 1 + (pop - 1) * (glass ? NeonMotion.glassFactor : 1) : 1
        #else
        return pressed ? 0.96 : 1
        #endif
    }
}

// MARK: - A tile that opens something

/// A tile that opens a film, series or episode: its glow takes the picture's own colour, and
/// on Apple TV it reports its focus so the row can slide and the hero can follow.
struct TileLink<Label: View>: View {
    let route: Route
    /// The picture whose colour the glow takes.
    var art: String? = nil
    var radius: CGFloat = NeonTokens.shapeCardRadius
    let focus: FocusState<String?>.Binding
    let key: String
    @ViewBuilder let label: () -> Label
    @State private var tint: Color?
    @Environment(\.neon) private var theme

    var body: some View {
        NavigationLink(value: route) { label() }
            .buttonStyle(NeonTileStyle(radius: radius, glow: theme.focus == .glow ? tint : nil))
            .focused(focus, equals: key)
            .id(key)
            .task(id: art) {
                guard let art, !art.isEmpty else { return }
                tint = await ImageLoader.shared.tint(for: art)
            }
    }
}

// MARK: - Pictures for films that came without them

/// Cinemeta's background and logo pictures for an IMDb id, when an addon or the history did not
/// give one (the same pictures Stremio uses).
enum Art {
    static func background(_ id: String, _ given: String?) -> String? {
        if let given, !given.isEmpty { return given }
        guard id.hasPrefix("tt") else { return nil }
        return "https://images.metahub.space/background/medium/\(id)/img"
    }

    static func logo(_ id: String, _ given: String?) -> String? {
        if let given, !given.isEmpty { return given }
        guard id.hasPrefix("tt") else { return nil }
        return "https://images.metahub.space/logo/medium/\(id)/img"
    }
}

// MARK: - The tiles

/// Sizes of the wide tile: 2:1, like Neon Home's app tiles.
enum WideTileSize {
    #if os(tvOS)
    static let width: CGFloat = 420
    #else
    static let width: CGFloat = 268
    #endif
    static var height: CGFloat { width / 2 }
}

/// The wide tile: the film's own picture, its logo (or its name in neon) on it, how far you
/// got, and while focused a slow drift into the picture.
struct WideTile: View {
    let title: String
    let art: String?
    var logo: String? = nil
    var progress: Double = 0
    var badge: String? = nil
    var fresh = false
    var watched = false
    var width: CGFloat = WideTileSize.width
    @Environment(\.isFocused) private var focused
    @Environment(\.neon) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let h = width / 2
        ZStack(alignment: .bottomLeading) {
            NeonImage(url: art)
                .frame(width: width, height: h)
                .scaleEffect(focused && !reduceMotion ? 1.06 : 1)
                .animation(focused ? .easeOut(duration: 6) : .easeOut(duration: 0.45), value: focused)
                .clipped()
            LinearGradient(colors: [.black.opacity(0), .black.opacity(0.78)], startPoint: UnitPoint(x: 0.5, y: 0.3), endPoint: .bottom)
            LinearGradient(colors: [.black.opacity(0.45), .black.opacity(0)], startPoint: .leading, endPoint: UnitPoint(x: 0.6, y: 0.5))
            VStack(alignment: .leading, spacing: h * 0.05) {
                LogoOrTitle(title: title, logo: logo, maxWidth: width * 0.6, maxHeight: h * 0.34, size: h * 0.15)
                if progress > 0.01 {
                    ProgressLine(value: progress)
                        .frame(width: width * 0.42)
                }
            }
            .padding(h * 0.09)
            HStack(spacing: 6) {
                if let badge { Badge(text: badge) }
                if fresh { Badge(text: "NEW", strong: true) }
                Spacer(minLength: 0)
                if watched { WatchedMark(size: h * 0.13) }
            }
            .padding(h * 0.07)
            .frame(width: width, height: h, alignment: .top)
        }
        .frame(width: width, height: h)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
    }
}

/// The poster tile, 2:3, with the title written on it only when there is no picture.
struct PosterTile: View {
    let title: String
    let poster: String?
    var progress: Double = 0
    var watched = false
    var width: CGFloat = Metrics.posterWidth
    @Environment(\.neon) private var theme

    var body: some View {
        let h = width / NeonTokens.posterAspect
        ZStack(alignment: .bottomLeading) {
            NeonImage(url: poster)
                .frame(width: width, height: h)
                .clipped()
            if poster == nil || poster?.isEmpty == true {
                LinearGradient(colors: [theme.surface, theme.panel], startPoint: .top, endPoint: .bottom)
                Text(title)
                    .font(theme.focus == .glow ? NeonFont.heading(width * 0.11) : .system(size: width * 0.1, weight: .semibold))
                    .foregroundStyle(theme.text)
                    .lineLimit(4)
                    .padding(width * 0.07)
            }
            if progress > 0.01 {
                ProgressLine(value: progress).padding(width * 0.05)
            }
            if watched {
                WatchedMark(size: width * 0.11)
                    .padding(width * 0.05)
                    .frame(width: width, height: h, alignment: .topTrailing)
            }
        }
        .frame(width: width, height: h)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
    }
}

/// A film's logo, or its name lit in neon when it has none (or it does not load).
struct LogoOrTitle: View {
    let title: String
    let logo: String?
    let maxWidth: CGFloat
    let maxHeight: CGFloat
    let size: CGFloat
    @State private var image: UIImage?
    @State private var failed = false
    /// A dark logo (a deep purple on the night) is lit from behind like a sign, so it reads.
    @State private var dark = false
    @Environment(\.neon) private var theme

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: maxWidth, maxHeight: maxHeight, alignment: .bottomLeading)
                    .shadow(color: dark ? .white.opacity(0.55) : .black.opacity(0.5), radius: dark ? 10 : 8)
                    .shadow(color: dark ? theme.secondary.opacity(0.35) : .clear, radius: dark ? 26 : 0)
                    .transition(.opacity)
            } else if failed || logo == nil {
                NeonTitle(text: title, size: size)
                    .lineLimit(2)
                    .minimumScaleFactor(0.6)
                    .frame(maxWidth: maxWidth, alignment: .leading)
                    .transition(.opacity)
            }
        }
        .frame(maxWidth: maxWidth, maxHeight: maxHeight, alignment: .bottomLeading)
        .task(id: logo) {
            image = nil
            failed = false
            guard let logo, !logo.isEmpty else { return }
            let loaded = await ImageLoader.shared.image(for: logo)
            dark = loaded.map { lightness(of: $0) < 0.3 } ?? false
            withAnimation(.easeOut(duration: 0.25)) {
                image = loaded
                failed = loaded == nil
            }
        }
    }
}

/// How light a picture is where it is not see-through, 0 (black) ... 1 (white), read from a
/// 12 x 12 copy (cheap enough to do as a logo arrives).
func lightness(of image: UIImage) -> CGFloat {
    guard let cg = image.cgImage else { return 1 }
    let n = 12
    var px = [UInt8](repeating: 0, count: n * n * 4)
    let drawn: Bool = px.withUnsafeMutableBytes { buf in
        guard let ctx = CGContext(data: buf.baseAddress, width: n, height: n, bitsPerComponent: 8, bytesPerRow: n * 4,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        else { return false }
        ctx.interpolationQuality = .medium
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: n, height: n))
        return true
    }
    guard drawn else { return 1 }
    var sum: CGFloat = 0
    var weight: CGFloat = 0
    for i in stride(from: 0, to: px.count, by: 4) {
        let a = CGFloat(px[i + 3]) / 255
        guard a > 0.2 else { continue }
        // the colours are premultiplied by the alpha
        let r = CGFloat(px[i]) / 255 / a, g = CGFloat(px[i + 1]) / 255 / a, b = CGFloat(px[i + 2]) / 255 / a
        sum += (0.2126 * r + 0.7152 * g + 0.0722 * b) * a
        weight += a
    }
    return weight > 0 ? sum / weight : 1
}

/// How far someone got: a lit line, the theme's colour fading into its second colour.
struct ProgressLine: View {
    let value: Double
    @Environment(\.neon) private var theme

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(.black.opacity(0.55))
                Capsule()
                    .fill(LinearGradient(colors: [theme.primary, theme.secondary], startPoint: .leading, endPoint: .trailing))
                    .frame(width: max(g.size.width * min(max(value, 0), 1), 4))
                    .shadow(color: theme.focus == .glow ? theme.primary.opacity(0.9) : .clear, radius: 4)
            }
        }
        .frame(height: Metrics.bodySize * 0.18)
    }
}

/// The round tick on something already watched.
struct WatchedMark: View {
    let size: CGFloat
    @Environment(\.neon) private var theme

    var body: some View {
        Image(systemName: "checkmark.circle.fill")
            .font(.system(size: size, weight: .semibold))
            .foregroundStyle(theme.background, theme.primary)
            .shadow(color: theme.focus == .glow ? theme.primary.opacity(0.8) : .black.opacity(0.4), radius: 6)
    }
}

// MARK: - Rows that slide

/// A titled row of tiles. On Apple TV the focused tile stays at the pivot, just in from the
/// left, and the row glides under it like Neon Home's film rows; on iPhone the row snaps to
/// whole tiles and tiles at the edges settle back a little.
///
/// The row keeps its own focus, so moving along it only redraws the row.
struct TileRow<Content: View>: View {
    let title: String
    /// The start of the focus keys of this row's tiles (`key|id`).
    let key: String
    var count: Int? = nil
    let tileWidth: CGFloat
    /// Told the focus key of each tile that takes focus.
    var onFocus: (String) -> Void = { _ in }
    /// CI screenshots: a tile to focus a moment after the row appears.
    var autoFocus: String? = nil
    @ViewBuilder let content: (FocusState<String?>.Binding) -> Content
    @FocusState private var focused: String?
    @State private var width: CGFloat = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: Metrics.cardSpacing * 0.5) {
            RowTitle(title: title, count: count)
                .padding(.horizontal, Metrics.edge)
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: Metrics.cardSpacing) {
                        content($focused)
                    }
                    #if os(iOS)
                    .scrollTargetLayout()
                    #else
                    .padding(.horizontal, Metrics.edge)
                    #endif
                    .padding(.vertical, Metrics.cardSpacing * 0.9)
                }
                .scrollClipDisabled()
                #if os(iOS)
                // the margin is the scroll view's, so tiles snap to it rather than to the screen edge
                .contentMargins(.horizontal, Metrics.edge, for: .scrollContent)
                .scrollTargetBehavior(.viewAligned)
                #endif
                .background {
                    GeometryReader { g in
                        Color.clear
                            .onAppear { width = g.size.width }
                            .onChange(of: g.size.width) { _, w in width = w }
                    }
                }
                .onChange(of: focused) { _, f in
                    guard let f else { return }
                    onFocus(f)
                    guard width > tileWidth else { return }
                    // the anchor that puts the tile's left edge at the pivot
                    let anchor = UnitPoint(x: Metrics.edge / (width - tileWidth), y: 0.5)
                    withAnimation(reduceMotion ? .easeOut(duration: 0.2) : NeonMotion.slide) {
                        proxy.scrollTo(f, anchor: anchor)
                    }
                }
            }
        }
        .tvFocusSection()
        .task(id: autoFocus) {
            guard let autoFocus else { return }
            try? await Task.sleep(for: .seconds(2))
            focused = autoFocus
        }
    }
}

/// A row's name, in the neon heading font, with how many there are.
struct RowTitle: View {
    let title: String
    var count: Int? = nil
    @Environment(\.neon) private var theme

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(title)
                .font(theme.focus == .glow ? NeonFont.heading(Metrics.titleSize * 0.8) : .system(size: Metrics.titleSize * 0.78, weight: .semibold))
                .foregroundStyle(theme.text)
                .shadow(color: theme.focus == .glow ? theme.primary.opacity(0.45) : .clear, radius: 10)
            if let count, count > 0 {
                Text("\(count)")
                    .font(.system(size: Metrics.bodySize * 0.7, weight: .bold))
                    .foregroundStyle(theme.muted)
            }
        }
    }
}

/// On iPhone, tiles near the row's edges settle back and dim a touch as they scroll away.
struct EdgeSettle: ViewModifier {
    func body(content: Content) -> some View {
        #if os(iOS)
        content.scrollTransition(.interactive, axis: .horizontal) { c, phase in
            c.scaleEffect(phase.isIdentity ? 1 : 0.93)
                .opacity(phase.isIdentity ? 1 : 0.75)
        }
        #else
        content
        #endif
    }
}
