import SwiftUI

/// The neon sign switching on: the tower flickers alight, the name follows, then it all fades
/// into the app. Same beats as the Android intro (intro/NeonIntroView.kt), in SwiftUI.
struct IntroView: View {
    let done: () -> Void
    @Environment(\.neon) private var theme
    @State private var tower = 0.0
    @State private var name = 0.0
    @State private var sun = 0.0

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()
            VStack(spacing: Metrics.titleSize) {
                ZStack {
                    Circle()
                        .fill(LinearGradient(colors: [theme.warm, theme.primary], startPoint: .top, endPoint: .bottom))
                        .frame(width: Metrics.titleSize * 5, height: Metrics.titleSize * 5)
                        .opacity(sun)
                        .blur(radius: (1 - sun) * 20)
                    TowerShape()
                        .stroke(theme.primary, style: StrokeStyle(lineWidth: Metrics.titleSize * 0.14, lineCap: .round, lineJoin: .round))
                        .frame(width: Metrics.titleSize * 2.6, height: Metrics.titleSize * 6)
                        .shadow(color: theme.primary, radius: 16 * tower)
                        .shadow(color: theme.primary.opacity(0.6), radius: 4)
                        .opacity(tower)
                }
                NeonTitle(text: "NEON TOKYO", size: Metrics.titleSize * 2.2)
                    .opacity(name)
                    .scaleEffect(0.94 + 0.06 * name)
            }
        }
        .task {
            // the flicker of a tube catching
            for step in [0.6, 0.1, 0.9, 0.3, 1.0] {
                withAnimation(.linear(duration: 0.07)) { tower = step }
                try? await Task.sleep(for: .milliseconds(90))
            }
            withAnimation(.easeOut(duration: 0.5)) { sun = 1 }
            try? await Task.sleep(for: .milliseconds(250))
            withAnimation(.easeOut(duration: 0.45)) { name = 1 }
            try? await Task.sleep(for: .milliseconds(Int(NeonTokens.motionIntroMs) - 900))
            done()
        }
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
