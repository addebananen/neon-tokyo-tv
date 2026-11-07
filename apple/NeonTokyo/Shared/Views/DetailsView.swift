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
        GeometryReader { g in
            ZStack(alignment: .topLeading) {
                NeonWorld(dim: 0.55)
                // the title's own picture, drifting, fading into the night on the left and below
                DriftingPicture(url: Art.background(meta.id, meta.background) ?? meta.poster)
                    .id(meta.id)
                    .frame(width: g.size.width * (wide ? 0.8 : 1), height: g.size.height * (wide ? 0.86 : 0.62))
                    .clipped()
                    .mask(LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: .black, location: wide ? 0.42 : 0)],
                                         startPoint: .leading, endPoint: .trailing))
                    .mask(LinearGradient(stops: [.init(color: .black, location: 0.45), .init(color: .clear, location: 1)],
                                         startPoint: .top, endPoint: .bottom))
                    .frame(width: g.size.width, height: g.size.height, alignment: .topTrailing)
                    .transition(.opacity)
                if !wide {
                    // on a phone the story sits over the picture's foot: a soft night under it
                    LinearGradient(stops: [.init(color: .clear, location: 0.2), .init(color: theme.background.opacity(0.82), location: 0.58)],
                                   startPoint: .top, endPoint: .bottom)
                        .frame(width: g.size.width, height: g.size.height * 0.62)
                        .allowsHitTesting(false)
                }

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: Metrics.rowSpacing * 0.55) {
                        Spacer().frame(height: g.size.height * (wide ? 0.2 : 0.34))
                        VStack(alignment: .leading, spacing: Metrics.rowSpacing * 0.45) {
                            LogoOrTitle(title: meta.name, logo: Art.logo(meta.id, meta.logo), maxWidth: Metrics.posterWidth * 2.8,
                                        maxHeight: Metrics.posterWidth * 0.75, size: Metrics.titleSize * 1.6)
                                .modifier(Staggered(index: 0))
                            FactsLine(item: meta)
                                .modifier(Staggered(index: 1))
                            if let d = meta.description {
                                Text(d)
                                    .font(.system(size: Metrics.bodySize))
                                    .foregroundStyle(theme.text.opacity(0.88))
                                    .frame(maxWidth: Metrics.posterWidth * 4.6, alignment: .leading)
                                    .lineLimit(5)
                                    .shadow(color: .black.opacity(0.6), radius: 8)
                                    .modifier(Staggered(index: 2))
                            }
                            actions
                                .modifier(Staggered(index: 3))
                                .tvFocusSection()
                            #if os(iOS)
                            tvButton
                                .modifier(Staggered(index: 3))
                            #endif
                            if let detail, !detail.cast.isEmpty {
                                Text("Cast: " + detail.cast.prefix(6).joined(separator: ", "))
                                    .font(.system(size: Metrics.bodySize * 0.8))
                                    .foregroundStyle(theme.muted)
                                    .lineLimit(2)
                                    .modifier(Staggered(index: 4))
                            }
                            if failed {
                                Text("Could not load the details. Check the connection and your addons.")
                                    .foregroundStyle(theme.error)
                            }
                        }
                        .padding(.horizontal, Metrics.edge)
                        if isSeries, let detail { episodes(detail) }
                    }
                    .padding(.bottom, Metrics.rowSpacing * 2)
                    .neonFloorLight()
                }
                .scrollClipDisabled()
            }
        }
        .ignoresSafeArea()
        .background(theme.background)
        .neonNavigationChrome()
        .task(id: "\(item.id)|\(model.manifests.count)") { await load() }
        #if os(iOS)
        .sheet(isPresented: handoffShown) {
            HandoffSheet(poster: meta.poster, what: handoffWhat)
                .presentationDetents([.height(330)])
                .presentationBackground(theme.panel)
        }
        #endif
    }

    #if os(iOS)
    // MARK: Fortsätt på TV:n

    @State private var handoffWhat = ""

    private var handoffShown: Binding<Bool> {
        Binding(get: { model.handoff.phase != .idle }, set: { if !$0 { model.handoff.cancel() } })
    }

    /// The film, or the episode Play would play, and how far this profile got in it.
    private var handoffItem: Handoff.Item? {
        guard let target = playTarget else { return nil }
        let play = target.request.play
        let e = model.entry(play.playbackId)
        let started = e.map { !$0.watched && !$0.deleted && $0.positionMs > 0 } ?? false
        let title = isSeries ? "\(target.request.heading.replacingOccurrences(of: "\(meta.name)  ·  ", with: "")) - \(meta.name)" : meta.name
        return Handoff.Item(id: meta.id, kind: isSeries ? "series" : "movie", video: play.playbackId, name: meta.name,
                            title: title, poster: meta.poster, background: meta.background, logo: meta.logo,
                            positionMs: started ? e!.positionMs : 0, durationMs: started ? e!.durationMs : 0)
    }

    private var tvButton: some View {
        let started = handoffItem.map { $0.positionMs > 0 } ?? false
        return Button {
            guard let item = handoffItem else { return }
            handoffWhat = item.title
            if model.handoff.available {
                model.handoff.start(item)
            } else {
                model.handoff.cancel()
                handoffWhat = ""
                showSignInHint = true
            }
        } label: {
            Label(started ? "Fortsätt på TV:n" : "Spela på TV:n", systemImage: "tv")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(NeonPillStyle())
        .frame(maxWidth: Metrics.posterWidth * 4.6)
        .disabled(handoffItem == nil)
        .alert("Fortsätt på TV:n", isPresented: $showSignInHint) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Logga in profilen med Stremio i Inställningar, med samma konto som TV:n. Sedan skickar den här knappen det du tittar på till TV:n, på samma sekund.")
        }
    }

    @State private var showSignInHint = false
    #endif

    /// Apple TV and iPad: the picture sits to the right; on a phone it fills the top.
    #if os(iOS)
    @Environment(\.horizontalSizeClass) private var sizeClass
    private var wide: Bool { sizeClass == .regular }
    #else
    private var wide: Bool { true }
    #endif

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
                        Button("Season \(s)") {
                            withAnimation(NeonMotion.fastOutSlowIn(0.35)) { season = s }
                        }
                        .buttonStyle(NeonPillStyle(prominent: s == season))
                    }
                }
                .padding(.horizontal, Metrics.edge)
                .padding(.vertical, 14)
            }
            .scrollClipDisabled()
            .tvFocusSection()

            let list = detail.videos.filter { $0.season == season }
            TileRow(title: "Season \(season)", key: "ep", count: list.count, tileWidth: WideTileSize.width) { focus in
                ForEach(list, id: \.id) { v in
                    EpisodeTile(video: v, seriesId: meta.id, fallback: meta.background, route: .sources(request(for: v)), focus: focus)
                        .modifier(EdgeSettle())
                }
            }
            .id(season)
            .transition(.opacity)
        }
    }

    static func time(_ ms: UInt64) -> String {
        let s = Int(ms / 1000)
        return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String(format: "%d:%02d", s / 60, s % 60)
    }
}

/// An episode as a wide 16:9 tile, Neon Home style: its still, the number, how far you got;
/// its name and story under it.
struct EpisodeTile: View {
    let video: Video
    let seriesId: String
    let fallback: String?
    let route: Route
    let focus: FocusState<String?>.Binding
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme

    var body: some View {
        let pid = episodePlaybackId(seriesId: seriesId, video: video)
        let w = WideTileSize.width
        let h = w * 9 / 16
        let art = video.thumbnail ?? fallback
        VStack(alignment: .leading, spacing: Metrics.cardSpacing * 0.45) {
            TileLink(route: route, art: art, radius: NeonTokens.shapeCardRadius + 2, focus: focus, key: "ep|" + video.id) {
                ZStack(alignment: .bottomLeading) {
                    NeonImage(url: art)
                        .frame(width: w, height: h)
                        .clipped()
                    LinearGradient(colors: [.black.opacity(0), .black.opacity(0.75)], startPoint: .center, endPoint: .bottom)
                    HStack(spacing: 8) {
                        Badge(text: "E\(video.episode)")
                        if model.isWatched(pid) { Badge(text: "Watched", strong: true) }
                    }
                    .padding(h * 0.07)
                    let p = model.progress(pid)
                    if p > 0.01 {
                        ProgressLine(value: p)
                            .padding(.horizontal, h * 0.07)
                            .padding(.bottom, 6)
                            .frame(width: w, height: h, alignment: .bottom)
                    }
                }
                .frame(width: w, height: h)
            }
            Text(video.title)
                .font(.system(size: Metrics.bodySize * 0.9, weight: .semibold))
                .foregroundStyle(theme.text)
                .lineLimit(1)
            if let o = video.overview {
                Text(o)
                    .font(.system(size: Metrics.bodySize * 0.72))
                    .foregroundStyle(theme.muted)
                    .lineLimit(2)
            }
        }
        .frame(width: w, alignment: .leading)
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
