import SwiftUI

/// The sources the addons offer for a film or episode, best first. Sources Apple's player cannot
/// open yet (MKV) are shown, marked, and wait for the mpv player in a later update.
struct SourcesView: View {
    let request: SourceRequest
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme
    @State private var streams: [StreamSource] = []
    @State private var loading = true
    @State private var playing: PlayRequest?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Metrics.cardSpacing * 0.6) {
                NeonTitle(text: request.heading, size: Metrics.titleSize)
                    .padding(.bottom, 8)
                if loading {
                    HStack(spacing: 14) {
                        ProgressView()
                        Text("Asking your addons…").foregroundStyle(theme.muted)
                    }
                } else if streams.isEmpty {
                    Text(model.hasStreamAddon
                         ? "No sources found for this one."
                         : "Your addons only list titles. Add an addon that provides streams in Settings › Addons.")
                        .foregroundStyle(theme.muted)
                        .frame(maxWidth: Metrics.posterWidth * 5, alignment: .leading)
                }
                ForEach(Array(streams.enumerated()), id: \.offset) { _, s in
                    Button {
                        start(s)
                    } label: {
                        SourceRow(source: s)
                    }
                    .buttonStyle(NeonCardStyle())
                    .disabled(!s.appleNative)
                }
            }
            .padding(Metrics.edge)
        }
        .background(NeonBackground(dim: 0.55))
        .task(id: request.videoId) {
            loading = true
            streams = await model.streams(kind: request.kind, videoId: request.videoId)
            loading = false
        }
        .fullScreenCover(item: $playing) { p in
            PlayerScreen(request: p, model: model)
                .ignoresSafeArea()
        }
    }

    private func start(_ s: StreamSource) {
        guard let address = s.url, let url = URL(string: address) else { return }
        playing = PlayRequest(url: url, headers: s.requestHeaders, item: request.play,
                              startMs: model.resumePosition(request.play.playbackId),
                              startShare: model.resumeShare(request.play.playbackId))
    }
}

struct SourceRow: View {
    let source: StreamSource
    @Environment(\.neon) private var theme

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    if !source.qualityLabel.isEmpty { Badge(text: source.qualityLabel, strong: true) }
                    ForEach(source.tags, id: \.self) { Badge(text: $0) }
                    if let size = source.sizeLabel { Badge(text: size) }
                    if !source.appleNative { Badge(text: "MKV · coming with the mpv player") }
                }
                Text(source.name.replacingOccurrences(of: "\n", with: " "))
                    .font(.system(size: Metrics.bodySize * 0.9, weight: .semibold))
                    .foregroundStyle(theme.text)
                    .lineLimit(1)
                if !source.title.isEmpty {
                    Text(source.title)
                        .font(.system(size: Metrics.bodySize * 0.75))
                        .foregroundStyle(theme.muted)
                        .lineLimit(3)
                }
            }
            Spacer(minLength: 0)
            Text(source.addonName)
                .font(.system(size: Metrics.bodySize * 0.7))
                .foregroundStyle(theme.muted)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.panel.opacity(0.75))
        .opacity(source.appleNative ? 1 : 0.55)
    }
}

/// What the player is asked to play.
struct PlayRequest: Identifiable {
    let id = UUID()
    let url: URL
    let headers: [StreamHeader]
    let item: PlayItem
    let startMs: UInt64
    var startShare: Double = 0
}
