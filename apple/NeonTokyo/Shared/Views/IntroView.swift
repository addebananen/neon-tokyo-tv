import AVFoundation
import SwiftUI

/// The neon sign switching on, as on the TV: the tower's tube buzzes and flickers alight, the
/// striped sun rises behind it, the name follows with its katakana, one scan line sweeps down
/// like an old set warming up, and the sign opens onto the night behind.
struct IntroView: View {
    let done: () -> Void
    @Environment(\.neon) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var tower = 0.0
    @State private var sun = 0.0
    @State private var name = 0.0
    @State private var scan: CGFloat = 0
    @State private var open = 0.0

    var body: some View {
        let t = Metrics.titleSize
        GeometryReader { g in
            ZStack {
                theme.background.opacity(1 - open)
                VStack(spacing: t * 0.8) {
                    ZStack(alignment: .bottom) {
                        StripedSun(warm: theme.warm, hot: theme.primary, night: theme.background)
                            .frame(width: t * 6, height: t * 6)
                            .opacity(sun)
                            .offset(y: (1 - sun) * 40)
                        TowerShape()
                            .stroke(theme.primary.mixed(with: .white, tower > 0.9 ? 0.25 : 0),
                                    style: StrokeStyle(lineWidth: t * 0.13, lineCap: .round, lineJoin: .round))
                            .frame(width: t * 2.4, height: t * 5.2)
                            .shadow(color: theme.primary, radius: 18 * tower)
                            .shadow(color: theme.primary.opacity(0.6), radius: 5)
                            .opacity(tower)
                            .padding(.bottom, t * 0.4)
                    }
                    VStack(spacing: t * 0.25) {
                        NeonTitle(text: "NEON TOKYO", size: t * 2.2)
                            .tracking(t * 0.16)
                        Text("ネ オ ン 東 京")
                            .font(.system(size: t * 0.6, weight: .medium))
                            .tracking(t * 0.2)
                            .foregroundStyle(theme.secondary.opacity(0.85))
                            .shadow(color: theme.secondary.opacity(0.7), radius: 8)
                    }
                    .opacity(name)
                    .offset(y: (1 - name) * 18)
                }
                .scaleEffect(1 + open * 0.08)
                .opacity(1 - open)

                // one bright scan line passing down the screen
                LinearGradient(colors: [.clear, theme.secondary.opacity(0.10), .white.opacity(0.16), .clear],
                               startPoint: .top, endPoint: .bottom)
                    .frame(height: 66)
                    // starts above the screen and ends below it, so it is only seen passing
                    .position(x: g.size.width / 2, y: -40 + (g.size.height + 80) * scan)
                    .allowsHitTesting(false)
            }
        }
        .ignoresSafeArea()
        .task { await play() }
    }

    private func play() async {
        IntroSound.play()
        if reduceMotion {
            withAnimation(.easeOut(duration: 0.4)) { tower = 1; sun = 1; name = 1 }
            try? await Task.sleep(for: .milliseconds(900))
            done()
            return
        }
        try? await Task.sleep(for: .milliseconds(180))
        // the flicker of a tube catching
        for step in [0.7, 0.05, 0.9, 0.2, 0.6, 1.0] {
            withAnimation(.linear(duration: 0.06)) { tower = step }
            try? await Task.sleep(for: .milliseconds(95))
        }
        withAnimation(NeonMotion.fastOutSlowIn(0.7)) { sun = 1 }
        try? await Task.sleep(for: .milliseconds(260))
        withAnimation(NeonMotion.fastOutSlowIn(0.52)) { name = 1 }
        withAnimation(.linear(duration: 0.9)) { scan = 1 }
        try? await Task.sleep(for: .milliseconds(1150))
        withAnimation(NeonMotion.fastOutSlowIn(0.52)) { open = 1 }
        try? await Task.sleep(for: .milliseconds(480))
        done()
    }
}

/// The sun from the banner: warm at the top, hot pink below, cut by the night in thinning
/// stripes across its lower half, with a soft glow around it.
private struct StripedSun: View {
    let warm: Color
    let hot: Color
    let night: Color

    var body: some View {
        GeometryReader { g in
            let r = min(g.size.width, g.size.height) / 2
            ZStack {
                Circle()
                    .fill(RadialGradient(colors: [hot.opacity(0.35), .clear], center: .center, startRadius: r * 0.6, endRadius: r * 1.5))
                    .frame(width: r * 3, height: r * 3)
                Circle()
                    .fill(LinearGradient(colors: [warm, hot], startPoint: .top, endPoint: .bottom))
                    .frame(width: r * 2, height: r * 2)
                    .overlay {
                        // the stripes cut through its lower half
                        ZStack {
                            ForEach(Array([0.58, 0.68, 0.77, 0.85, 0.92].enumerated()), id: \.offset) { i, f in
                                Rectangle()
                                    .fill(night)
                                    .frame(width: r * 2, height: r * (0.035 + 0.016 * CGFloat(i)))
                                    .position(x: r, y: r * 2 * CGFloat(f))
                            }
                        }
                        .frame(width: r * 2, height: r * 2)
                    }
                    .clipShape(Circle())
            }
            .frame(width: g.size.width, height: g.size.height)
        }
    }
}

/// The intro's hum as the sign switches on, mixed with whatever else is playing.
enum IntroSound {
    private static var player: AVAudioPlayer?

    static func play() {
        guard let url = Bundle.main.url(forResource: "nt_ui_wake", withExtension: "wav") else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        player = try? AVAudioPlayer(contentsOf: url)
        player?.volume = 0.7
        player?.play()
    }
}

/// The Tokyo tower from the app icon, as lines.
struct TowerShape: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        let top = CGPoint(x: r.midX, y: r.minY)
        p.move(to: top)
        p.addLine(to: CGPoint(x: r.minX, y: r.maxY))
        p.move(to: top)
        p.addLine(to: CGPoint(x: r.maxX, y: r.maxY))
        p.move(to: CGPoint(x: r.midX, y: r.minY - r.height * 0.06))
        p.addLine(to: top)
        // two platforms
        p.move(to: CGPoint(x: r.minX + r.width * 0.33, y: r.minY + r.height * 0.36))
        p.addLine(to: CGPoint(x: r.maxX - r.width * 0.33, y: r.minY + r.height * 0.36))
        p.move(to: CGPoint(x: r.minX + r.width * 0.18, y: r.minY + r.height * 0.64))
        p.addLine(to: CGPoint(x: r.maxX - r.width * 0.18, y: r.minY + r.height * 0.64))
        // the arch at the foot
        p.move(to: CGPoint(x: r.minX + r.width * 0.25, y: r.maxY - r.height * 0.02))
        p.addQuadCurve(to: CGPoint(x: r.maxX - r.width * 0.25, y: r.maxY - r.height * 0.02),
                       control: CGPoint(x: r.midX, y: r.maxY - r.height * 0.2))
        return p
    }
}
