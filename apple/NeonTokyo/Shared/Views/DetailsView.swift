import SwiftUI

/// A film or series: the picture, the story, Play or Resume, the watchlist, the trailer, and for
/// a series its seasons and episodes.
struct DetailsView: View {
    let item: MetaItem
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme
    @Environment(\.openURL) private var openURL
    @State private var detail: MetaDetail?
    @State private var failed = false
    @State private var season: UInt32 = 1

    private var meta: MetaItem { detail?.item ?? item }
    private var isSeries: Bool { meta.kind == "series" || !(detail?.videos.isEmpty ?? true) && meta.kind != "movie" }

    var body: some View {
        ZStack(alignment: .topLeading) {
            NeonImage(url: meta.background ?? meta.poster)
                .ignoresSafeArea()
                .overlay(LinearGradient(colors: [theme.background.opacity(0.35), theme.background.opacity(0.92), theme.background],
                                        startPoint: .top, endPoint: .bottom).ignoresSafeArea())
                .overlay(LinearGradient(colors: [theme.background.opacity(0.9), .clear], startPoint: .leading, endPoint: .trailing).ignoresSafeArea())

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: Metrics.rowSpacing * 0.6) {
                    Spacer().frame(height: Metrics.heroHeight * 0.32)
                    if let logo = meta.logo {
                        NeonImage(url: logo, mode: .fit)
                            .frame(maxWidth: Metrics.posterWidth * 2.6, maxHeight: Metrics.posterWidth * 0.9, alignment: .leading)
                    } else {
                        NeonTitle(text: meta.name, size: Metrics.titleSize * 1.5)
                    }
                    FactsLine(item: meta)
                    if let d = meta.description {
                        Text(d)
                            .font(.system(size: Metrics.bodySize))
                            .foregroundStyle(theme.text.opacity(0.88))
                            .frame(maxWidth: Metrics.posterWidth * 5, alignment: .leading)
                            .lineLimit(6)
                    }
                    actions
                        .tvFocusSection()
                    if let detail, !detail.cast.isEmpty {
                        Text("Cast: " + detail.cast.prefix(6).joined(separator: ", "))
                            .font(.system(size: Metrics.bodySize * 0.8))
                            .foregroundStyle(theme.muted)
                            .lineLimit(2)
                    }
                    if isSeries, let detail { episodes(detail) }
                    if failed {
                        Text("Could not load the details. Check the connection and your addons.")
                            .foregroundStyle(theme.error)
                    }
                }
                .padding(.horizontal, Metrics.edge)
                .padding(.bottom, Metrics.rowSpacing * 2)
            }
        }
        .background(theme.background)
        .neonNavigationChrome()
        .task(id: "\(item.id)|\(model.manifests.count)") { await load() }
    }

    private func load() async {
        guard !model.manifests.isEmpty else { return }
        failed = false
        do {
            let d = try await model.meta(kind: item.kind, id: item.id)
            detail = d
            if let resume = model.continueItems.first(where: { $0.id == item.id }), let s = resume.season {
                season = s
            } else if let first = d.videos.first(where: { $0.season > 0 }) {
                season = first.season
            }
        } catch {
            failed = true
        }
    }

    // MARK: Actions

    private var actions: some View {
        HStack(spacing: Metrics.cardSpacing * 0.7) {
            if let target = playTarget {
                NavigationLink(value: Route.sources(target.request)) {
                    Label(target.label, systemImage: "play.fill")
                }
                .buttonStyle(NeonPillStyle(prominent: true))
            }
            Button {
                model.toggleWatchlist(meta)
            } label: {
                Label(model.onWatchlist(meta.id) ? "On watchlist" : "Watchlist",
                      systemImage: model.onWatchlist(meta.id) ? "checkmark" : "plus")
            }
            .buttonStyle(NeonPillStyle())
            if let yt = detail?.trailerYoutubeIds.first {
                Button {
                    // The YouTube app plays trailers best on both Apple TV and iPhone
                    if let app = URL(string: "youtube://watch/\(yt)") {
                        openURL(app) { ok in
                            if !ok, let web = URL(string: "https://www.youtube.com/watch?v=\(yt)") { openURL(web) }
                        }
                    }
                } label: {
                    Label("Trailer", systemImage: "film")
                }
                .buttonStyle(NeonPillStyle())
            }
        }
    }

    private struct PlayTarget {
        let label: String
        let request: SourceRequest
    }

    /// Play, Resume or the next episode, like the Android details screen.
    private var playTarget: PlayTarget? {
        if !isSeries {
            let resume = model.resumePosition(meta.id)
            let play = PlayItem(playbackId: meta.id, kind: "movie", title: meta.name, poster: meta.poster, background: meta.background)
            return PlayTarget(label: resume > 0 ? "Resume \(Self.time(resume))" : "Play",
                              request: SourceRequest(kind: meta.kind, videoId: meta.id, play: play, heading: meta.name))
        }
        guard let detail else { return nil }
        let videos = detail.videos.filter { $0.season > 0 }
        var target = videos.first
        if let c = model.continueItems.first(where: { $0.id == meta.id }), let s = c.season, let e = c.episode {
            target = videos.first { $0.season == s && $0.episode == e } ?? target
        }
        guard let v = target else { return nil }
        let pid = episodePlaybackId(seriesId: meta.id, video: v)
        let resume = model.resumePosition(pid)
        return PlayTarget(label: (resume > 0 ? "Resume " : "Play ") + "S\(v.season):E\(v.episode)",
                          request: request(for: v))
    }

    private func request(for v: Video) -> SourceRequest {
        let pid = episodePlaybackId(seriesId: meta.id, video: v)
        let play = PlayItem(playbackId: pid, kind: "series", title: episodeDisplayTitle(video: v), poster: meta.poster,
                            background: v.thumbnail ?? meta.background, seriesId: meta.id, seriesTitle: meta.name,
                            episodes: detail?.videos ?? [])
        return SourceRequest(kind: "series", videoId: episodeStreamId(seriesId: meta.id, video: v), play: play,
                             heading: "\(meta.name)  ·  S\(v.season):E\(v.episode)")
    }

    // MARK: Episodes

    @ViewBuilder
    private func episodes(_ detail: MetaDetail) -> some View {
        let seasons = Array(Set(detail.videos.map(\.season).filter { $0 > 0 })).sorted()
        if !seasons.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(seasons, id: \.self) { s in
                        Button("Season \(s)") { season = s }
                            .buttonStyle(NeonPillStyle(prominent: s == season))
                    }
                }
                .padding(.vertical, 12)
            }
            .scrollClipDisabled()
            .tvFocusSection()

            LazyVStack(alignment: .leading, spacing: Metrics.cardSpacing * 0.6) {
                ForEach(detail.videos.filter { $0.season == season }, id: \.id) { v in
                    NavigationLink(value: Route.sources(request(for: v))) {
                        EpisodeRow(video: v, seriesId: meta.id)
                    }
                    .buttonStyle(NeonCardStyle())
                }
            }
            .tvFocusSection()
        }
    }

    static func time(_ ms: UInt64) -> String {
        let s = Int(ms / 1000)
        return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String(format: "%d:%02d", s / 60, s % 60)
    }
}

struct EpisodeRow: View {
    let video: Video
    let seriesId: String
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme

    var body: some View {
        let pid = episodePlaybackId(seriesId: seriesId, video: video)
        HStack(alignment: .top, spacing: Metrics.cardSpacing * 0.6) {
            ZStack(alignment: .bottomLeading) {
                NeonImage(url: video.thumbnail)
                    .frame(width: Metrics.posterWidth * 1.3, height: Metrics.posterWidth * 1.3 * 9 / 16)
                    .clipped()
                let p = model.progress(pid)
                if p > 0.01 { ProgressBar(value: p).padding(6) }
            }
            .clipShape(RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("\(video.episode). \(video.title)")
                        .font(.system(size: Metrics.bodySize, weight: .semibold))
                        .foregroundStyle(theme.text)
                        .lineLimit(1)
                    if model.isWatched(pid) {
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(theme.primary)
                    }
                }
                if let o = video.overview {
                    Text(o)
                        .font(.system(size: Metrics.bodySize * 0.8))
                        .foregroundStyle(theme.muted)
                        .lineLimit(2)
                }
                if let r = video.released {
                    Text(String(r.prefix(10)))
                        .font(.system(size: Metrics.bodySize * 0.7))
                        .foregroundStyle(theme.muted.opacity(0.8))
                }
            }
            Spacer(minLength: 0)
        }
        .padding(10)
        .frame(maxWidth: Metrics.posterWidth * 6, alignment: .leading)
        .background(theme.panel.opacity(0.6))
    }
}

extension View {
    /// A clear navigation bar over pictures.
    func neonNavigationChrome() -> some View {
        #if os(tvOS)
        return self.toolbar(.hidden, for: .navigationBar)
        #else
        return self.toolbarBackground(.hidden, for: .navigationBar)
        #endif
    }
}
