#if os(iOS)
import SwiftUI

/// "Fortsätt på TV:n" while it happens: the poster beams over to the TV, which TV when more than
/// one is on, and a check once the TV plays it.
struct HandoffSheet: View {
    let poster: String?
    let what: String
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme
    @State private var beam = false

    private var phase: Handoff.Phase { model.handoff.phase }

    var body: some View {
        VStack(spacing: 16) {
            art
                .frame(height: 120)
                .padding(.top, 22)
            Text("Fortsätt på TV:n")
                .font(NeonFont.heading(24))
                .foregroundStyle(theme.text)
            if !what.isEmpty {
                Text(what)
                    .font(.system(size: 15))
                    .foregroundStyle(theme.muted)
                    .lineLimit(1)
            }
            status
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity)
        .onAppear { withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: false)) { beam = true } }
    }

    private var playing: Bool { if case .playing = phase { return true } else { return false } }

    private var art: some View {
        HStack(spacing: 0) {
            NeonImage(url: poster)
                .frame(width: 60, height: 90)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .shadow(color: .black.opacity(0.5), radius: 10, y: 6)
                .offset(x: playing ? 96 : (beam ? 36 : 0))
                .scaleEffect(playing ? 0.6 : (beam ? 0.8 : 1))
                .opacity(playing ? 1 : (beam ? 0.2 : 1))
            Capsule()
                .fill(LinearGradient(colors: [.clear, theme.primary, theme.secondary, .clear], startPoint: .leading, endPoint: .trailing))
                .frame(width: 70, height: 2)
                .shadow(color: theme.primary, radius: 6)
                .opacity(playing ? 0 : 1)
            Image(systemName: "tv")
                .font(.system(size: 64, weight: .light))
                .foregroundStyle(playing ? theme.primary : theme.secondary)
                .shadow(color: playing ? theme.primary : theme.secondary, radius: 10)
        }
        .animation(.spring(response: 0.45, dampingFraction: 0.62), value: playing)
    }

    @ViewBuilder
    private var status: some View {
        switch phase {
        case .idle, .looking:
            HStack(spacing: 10) { ProgressView().tint(theme.primary); Text("Letar efter TV:n…") }
                .foregroundStyle(theme.text)
        case .sending(let line):
            HStack(spacing: 10) { ProgressView().tint(theme.primary); Text(line).multilineTextAlignment(.center) }
                .foregroundStyle(theme.text)
        case .playing(let tv):
            Label("Spelas på \(tv)", systemImage: "checkmark.circle.fill")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(theme.secondary)
        case .choosing(let tvs):
            VStack(spacing: 10) {
                ForEach(Array(tvs.enumerated()), id: \.element.id) { i, tv in
                    Button { model.handoff.choose(tv) } label: {
                        Label(tv.name, systemImage: "tv").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(NeonPillStyle(prominent: i == 0))
                }
            }
        case .failed(let why):
            VStack(spacing: 12) {
                Text(why)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(theme.muted)
                Button("Stäng") { model.handoff.cancel() }
                    .buttonStyle(NeonPillStyle())
            }
        }
    }
}
#endif
