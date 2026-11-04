import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// Neon Tokyo's world behind the screens: the banner's skyline at dusk (the striped sun, the
/// tower, the karaoke signs), stars that breathe, a shooting star now and then, and the neon
/// grid floor rolling toward you. Like Neon Home's backdrop it leans a little toward the
/// focused tile.
///
/// Every moving part is a layer transform or a fade of a picture drawn once, so the GPU does
/// the work and scrolling stays smooth. Deep Blue gets the same world, cooled and quieter.
struct NeonWorld: View {
    /// How much to darken it under busy screens, 0...1.
    var dim: Double = 0
    /// Where the focus is across the screen, -0.5 (left) ... 0.5 (right).
    var lean: CGFloat = 0
    @Environment(\.neon) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var twinkle = false

    var body: some View {
        GeometryReader { g in
            let w = max(g.size.width, 1)
            let h = max(g.size.height, 1)
            let glass = theme.focus == .glass
            let compact = w < h
            let horizon = h * (compact ? 0.46 : 0.6)
            let shift = reduceMotion ? 0 : -lean * 2 * (compact ? 10 : 22)
            ZStack(alignment: .topLeading) {
                Color(hex: glass ? 0x061423 : 0x0D0721)

                // the sky warming toward the horizon
                LinearGradient(colors: [.clear, (glass ? Color(hex: 0x123A5C) : Color(hex: 0x3A1468)).opacity(0.7)],
                               startPoint: .top, endPoint: .bottom)
                    .frame(width: w, height: horizon)

                StarField(seed: 7, count: compact ? 40 : 70)
                    .equatable()
                    .frame(width: w + 60, height: horizon * 0.7)
                    .offset(x: shift * 0.3 - 30)
                    .opacity(twinkle ? 0.95 : 0.35)
                StarField(seed: 19, count: compact ? 30 : 50)
                    .equatable()
                    .frame(width: w + 60, height: horizon * 0.7)
                    .offset(x: shift * 0.3 - 30)
                    .opacity(twinkle ? 0.3 : 0.9)

                if !glass && !reduceMotion {
                    ShootingStars(width: w, sky: horizon * 0.35)
                }

                // the city from the banner, its foot on the horizon
                if let city = WorldArt.city {
                    let imgH = compact ? horizon * 0.82 : max(horizon, w / WorldArt.aspect)
                    let imgW = imgH * WorldArt.aspect
                    Image(uiImage: city)
                        .resizable()
                        .frame(width: imgW * 1.04, height: imgH * 1.04)
                        .saturation(glass ? 0.2 : 1)
                        .colorMultiply(glass ? Color(hex: 0x9FD4FF) : .white)
                        .opacity(glass ? 0.5 : 1)
                        .position(x: w / 2 + (compact ? imgW * 0.08 : 0) + shift, y: horizon - imgH * 0.52 + 2)
                }

                GridFloor(width: w, height: h - horizon, glass: glass)
                    .equatable()
                    .offset(x: shift * 0.8, y: horizon)
            }
            .frame(width: w, height: h)
            .overlay(Color.black.opacity(dim))
            .animation(NeonMotion.fastOutSlowIn(1.1), value: lean)
            .clipped()
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 2.6).repeatForever(autoreverses: true)) { twinkle = true }
        }
    }
}

/// The skyline picture, loaded once.
enum WorldArt {
    static let aspect: CGFloat = 1920.0 / 694.0
    static let city: UIImage? = {
        guard let path = Bundle.main.path(forResource: "world-city", ofType: "jpg") else { return nil }
        return UIImage(contentsOfFile: path)
    }()
}

/// Stars scattered over the sky, denser near the top, a few brighter with a halo. Drawn once.
private struct StarField: View, Equatable {
    let seed: UInt64
    let count: Int

    var body: some View {
        Canvas { ctx, size in
            var rng = SeededRandom(seed: seed)
            for i in 0..<count {
                let x = rng.next() * size.width
                let y = rng.next() * rng.next() * size.height
                let big = i % 9 == 0
                let r: CGFloat = big ? 1.8 : 1.1
                if big {
                    ctx.fill(Path(ellipseIn: CGRect(x: x - 5, y: y - 5, width: 10, height: 10)),
                             with: .color(Color(hex: 0xB9A6FF, opacity: 0.18)))
                }
                ctx.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
                         with: .color(.white.opacity(0.6)))
            }
        }
    }
}

/// A small repeatable random source, so the stars stay where they are between launches.
struct SeededRandom {
    private var state: UInt64

    init(seed: UInt64) { state = seed &* 0x9E3779B97F4A7C15 | 1 }

    /// 0 ..< 1
    mutating func next() -> CGFloat {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return CGFloat(state % 1_000_000) / 1_000_000
    }
}

/// Every eleven seconds, a streak across the upper sky.
private struct ShootingStars: View {
    let width: CGFloat
    let sky: CGFloat
    @State private var phase: CGFloat = 0
    @State private var start: CGPoint = .zero

    var body: some View {
        Streak(phase: phase, start: start, length: width * 0.16)
            .task {
                var rng = SeededRandom(seed: UInt64(Date().timeIntervalSince1970))
                while !Task.isCancelled {
                    try? await Task.sleep(for: .seconds(11))
                    if Task.isCancelled { break }
                    var t = Transaction()
                    t.disablesAnimations = true
                    withTransaction(t) {
                        phase = 0
                        start = CGPoint(x: width * (0.15 + rng.next() * 0.55), y: sky * (0.1 + rng.next() * 0.6))
                    }
                    try? await Task.sleep(for: .milliseconds(30))
                    withAnimation(.easeIn(duration: 1.1)) { phase = 1 }
                }
            }
    }
}

private struct Streak: View, Animatable {
    var phase: CGFloat
    let start: CGPoint
    let length: CGFloat

    var animatableData: CGFloat {
        get { phase }
        set { phase = newValue }
    }

    var body: some View {
        let tail = length * 0.45
        let head = CGPoint(x: start.x + length * phase, y: start.y + length * 0.32 * phase)
        Capsule()
            .fill(LinearGradient(colors: [.white.opacity(0), .white.opacity(0.9)], startPoint: .leading, endPoint: .trailing))
            .frame(width: tail, height: 2)
            .rotationEffect(.radians(atan2(0.32, 1.0)), anchor: .trailing)
            .position(x: head.x - tail / 2, y: head.y)
            .opacity(phase <= 0 || phase >= 1 ? 0 : Double(sin(phase * .pi)))
    }
}

/// The neon grid floor. Lines run to a vanishing point on the horizon; the cross lines roll
/// toward you. The cross lines sit at perspective spacing that repeats under scaling (each one
/// is the last one's distance times q), so growing the layer by 1/q from the horizon puts
/// every line where the next one was: an endless roll that is one GPU transform.
private struct GridFloor: View, Equatable {
    let width: CGFloat
    let height: CGFloat
    let glass: Bool

    static func == (a: GridFloor, b: GridFloor) -> Bool {
        a.width == b.width && a.height == b.height && a.glass == b.glass
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var roll = false

    private static let q: CGFloat = 0.8

    var body: some View {
        let cyan = glass ? Color(hex: 0x8FDCFF) : Color(hex: 0x3EF0FF)
        let magenta = glass ? Color(hex: 0xBFE8FF) : Color(hex: 0xFF3D9A)
        ZStack(alignment: .top) {
            LinearGradient(colors: [glass ? Color(hex: 0x0B1E31) : Color(hex: 0x1A0B3A), glass ? Color(hex: 0x061423) : Color(hex: 0x0D0721)],
                           startPoint: .top, endPoint: .bottom)

            // lanes to the vanishing point
            Canvas { ctx, size in
                let vx = size.width / 2
                let lanes = 26
                for i in -lanes...lanes {
                    var p = Path()
                    p.move(to: CGPoint(x: vx + CGFloat(i) * 2, y: 0))
                    p.addLine(to: CGPoint(x: vx + CGFloat(i) * size.width / 14, y: size.height))
                    let a = 0.55 * (1 - Double(abs(i)) / Double(lanes) * 0.6) * (glass ? 0.35 : 0.7)
                    ctx.stroke(p, with: .color(cyan.opacity(a)), lineWidth: 1.2)
                }
            }

            // cross lines, thicker as they come close
            Canvas { ctx, size in
                var d = size.height
                while d > 1.5 {
                    let k = d / size.height
                    var p = Path()
                    p.move(to: CGPoint(x: 0, y: d))
                    p.addLine(to: CGPoint(x: size.width, y: d))
                    ctx.stroke(p, with: .color(magenta.opacity((glass ? 0.22 : 0.6) * Double(min(1, k * 1.6)))), lineWidth: 0.6 + 2 * k)
                    d *= Self.q
                }
            }
            .scaleEffect(x: 1, y: roll ? 1 / Self.q : 1, anchor: .top)
            .animation(reduceMotion ? nil : .linear(duration: 2.2).repeatForever(autoreverses: false), value: roll)

            // the horizon: a lit line with a halo
            LinearGradient(colors: [magenta.opacity(0), magenta.opacity(glass ? 0.18 : 0.4), magenta.opacity(0)],
                           startPoint: .top, endPoint: .bottom)
                .frame(height: 52)
                .offset(y: -26)
            Rectangle()
                .fill(magenta.mixed(with: .white, 0.35).opacity(glass ? 0.4 : 0.95))
                .frame(height: 1.6)

            // the floor fades into the night at the bottom so tiles read clearly over it
            LinearGradient(colors: [.clear, (glass ? Color(hex: 0x061423) : Color(hex: 0x0D0721)).opacity(0.85)],
                           startPoint: UnitPoint(x: 0.5, y: 0.35), endPoint: .bottom)
        }
        .frame(width: width + 80, height: max(height, 1), alignment: .top)
        .offset(x: -40)
        .clipped()
        .onAppear { roll = !reduceMotion }
    }
}
