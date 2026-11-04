import SwiftUI

/// The home screen. Apple TV: the focused title's picture fills the top right and drifts slowly,
/// its logo and story sit above the rows, and the rows slide like Neon Home's (the focused tile
/// stays put, the row glides under it, the focused row stays at the same height). iPhone: a
/// hero that turns every few seconds, then rows that snap to whole tiles.
///
/// Continue watching and the first catalog are wide tiles, like Neon Home's app tiles; the
/// other rows are posters.
struct HomeView: View {
    var body: some View {
        HomeBody()
            .neonRoutes()
            .toolbar(.hidden, for: .navigationBar)
    }
}

// MARK: - What the home screen shows

/// One tile on the home screen.
struct HomeTile: Identifiable {
    /// The focus key: `row|id`.
    let key: String
    let item: MetaItem
    let art: String?
    let logo: String?
    let poster: String?
    var progress: Double = 0
    var badge: String? = nil
    var fresh = false
    var watched = false
    var id: String { key }
}

/// One row on the home screen.
struct HomeSection: Identifiable {
    let key: String
    let title: String
    let wide: Bool
    let tiles: [HomeTile]
    var id: String { key }
}

extension AppModel {
    /// Continue watching, the watchlist, then every catalog row; the first catalog is wide.
    var homeSections: [HomeSection] {
        var out: [HomeSection] = []
        let cw = continueItems
        if !cw.isEmpty {
            out.append(HomeSection(key: "cw", title: "Continue watching", wide: true, tiles: cw.map { e -> HomeTile in
                let badge: String? = {
                    guard let s = e.season, let ep = e.episode else { return nil }
                    return "S\(s):E\(ep)"
                }()
                return HomeTile(key: "cw|" + e.id, item: e.asMeta, art: Art.background(e.id, e.background), logo: Art.logo(e.id, e.logo),
                                poster: e.poster, progress: Double(e.progress), badge: badge, fresh: e.hasNewEpisode)
            }))
        }
        let list = watchlistItems
        if !list.isEmpty {
            out.append(HomeSection(key: "wl", title: "Watchlist", wide: false, tiles: list.map { w in
                HomeTile(key: "wl|" + w.id, item: w.asMeta, art: Art.background(w.id, nil), logo: Art.logo(w.id, nil), poster: w.poster,
                         watched: isWatched(w.id))
            }))
        }
        var first = true
        for row in rows {
            guard let items = rowItems[row.key], !items.isEmpty else { continue }
            let wide = first
            first = false
            out.append(HomeSection(key: row.key, title: row.title, wide: wide, tiles: items.map { m in
                HomeTile(key: row.key + "|" + m.id, item: m, art: Art.background(m.id, m.background), logo: Art.logo(m.id, m.logo),
                         poster: m.poster, progress: progress(m.id), watched: isWatched(m.id))
            }))
        }
        return out
    }
}

// MARK: - Shared pieces

/// A home row: wide tiles or posters, each opening the title.
private struct HomeShelf: View {
    let section: HomeSection
    var onFocus: (String) -> Void = { _ in }

    var body: some View {
        TileRow(title: section.title, key: section.key, count: section.tiles.count,
                tileWidth: section.wide ? WideTileSize.width : Metrics.posterWidth, onFocus: onFocus) { focus in
            ForEach(section.tiles) { t in
                TileLink(route: .details(t.item), art: section.wide ? t.art : t.poster,
                         radius: section.wide ? NeonTokens.shapeCardRadius + 4 : NeonTokens.shapeCardRadius,
                         focus: focus, key: t.key) {
                    if section.wide {
                        WideTile(title: t.item.name, art: t.art ?? t.poster, logo: t.logo, progress: t.progress, badge: t.badge,
                                 fresh: t.fresh, watched: t.watched)
                    } else {
                        PosterTile(title: t.item.name, poster: t.poster, progress: t.progress, watched: t.watched)
                    }
                }
                .modifier(EdgeSettle())
            }
        }
    }
}

/// Logo (or neon title), facts and the start of the story, each sliding in a beat after the
/// one before, like the TV's hero.
struct HeroText: View {
    let item: MetaItem
    var lines = 3
    @Environment(\.neon) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: Metrics.bodySize * 0.55) {
            LogoOrTitle(title: item.name, logo: Art.logo(item.id, item.logo), maxWidth: Metrics.posterWidth * 2.6,
                        maxHeight: Metrics.posterWidth * 0.62, size: Metrics.titleSize * 1.5)
                .modifier(Staggered(index: 0))
            FactsLine(item: item)
                .modifier(Staggered(index: 1))
            if let d = item.description, !d.isEmpty {
                Text(d)
                    .font(.system(size: Metrics.bodySize * 0.92))
                    .foregroundStyle(theme.text.opacity(0.86))
                    .lineLimit(lines)
                    .frame(maxWidth: Metrics.posterWidth * 3.8, alignment: .leading)
                    .shadow(color: .black.opacity(0.6), radius: 8)
                    .modifier(Staggered(index: 2))
            }
        }
    }
}

/// Slides in from the right and fades up, `index` beats after the block appears.
struct Staggered: ViewModifier {
    let index: Int
    @State private var shown = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(x: shown || reduceMotion ? 0 : 26)
            .onAppear {
                withAnimation(NeonMotion.fastOutSlowIn(0.55).delay(0.07 * Double(index))) { shown = true }
            }
    }
}

/// A big picture that drifts slowly (Ken Burns) for as long as it is on screen.
struct DriftingPicture: View {
    let url: String?
    @State private var drift = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        NeonImage(url: url, placeholder: false)
            .scaleEffect(drift ? 1.09 : 1.0, anchor: .topTrailing)
            .offset(x: drift ? -16 : 0)
            .onAppear {
                guard !reduceMotion else { return }
                withAnimation(.easeInOut(duration: 24).repeatForever(autoreverses: true)) { drift = true }
            }
    }
}

/// Year · rating · runtime · genres.
struct FactsLine: View {
    let item: MetaItem
    @Environment(\.neon) private var theme

    var body: some View {
        HStack(spacing: 12) {
            if let r = item.imdbRating, !r.isEmpty { Badge(text: "IMDb \(r)", strong: true) }
            Text([item.releaseInfo, item.runtime, item.genres.prefix(3).joined(separator: ", ")]
                .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: "  ·  "))
                .font(.system(size: Metrics.bodySize * 0.85, weight: .medium))
                .foregroundStyle(theme.text.opacity(0.75))
                .lineLimit(1)
                .shadow(color: .black.opacity(0.6), radius: 6)
        }
    }
}

/// Shown while there are no rows yet.
private struct HomeEmpty: View {
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme

    var body: some View {
        HStack(spacing: 16) {
            if model.loadingAddons { ProgressView() }
            Text(model.loadingAddons ? "Loading your addons…" : "No catalogs yet. Add an addon in Settings, or sign in to Stremio there.")
                .font(.system(size: Metrics.bodySize))
                .foregroundStyle(theme.muted)
        }
        .padding(Metrics.edge)
    }
}

/// Waits for a picture, but never longer than `ms`, so the hero changes as soon as it can
/// without flashing an empty frame.
func preload(_ address: String?, ms: Int) async {
    guard let address else { return }
    await withTaskGroup(of: Void.self) { g in
        g.addTask { _ = await ImageLoader.shared.image(for: address) }
        g.addTask { try? await Task.sleep(for: .milliseconds(ms)) }
        _ = await g.next()
        g.cancelAll()
    }
}

#if os(tvOS)

// MARK: - Apple TV

private typealias HomeBody = TVHome

private struct TVHome: View {
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hero: MetaItem?
    @State private var heroTask: Task<Void, Never>?
    @State private var focusRow: String?

    var body: some View {
        let sections = model.homeSections
        let shown = hero ?? sections.first?.tiles.first?.item
        GeometryReader { g in
            let w = g.size.width
            let h = g.size.height
            let pivot = h * 0.5
            ZStack(alignment: .topLeading) {
                // the focused title's picture, top right, fading into the night on the left and below
                ZStack {
                    if let shown {
                        DriftingPicture(url: Art.background(shown.id, shown.background) ?? shown.poster)
                            .id(shown.id)
                            .transition(.opacity)
                    }
                }
                .frame(width: w * 0.74, height: h * 0.74)
                .clipped()
                .mask(LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: .black, location: 0.38)],
                                     startPoint: .leading, endPoint: .trailing))
                .mask(LinearGradient(stops: [.init(color: .black, location: 0.5), .init(color: .clear, location: 1)],
                                     startPoint: .top, endPoint: .bottom))
                .frame(width: w, height: h, alignment: .topTrailing)

                // the story, above the rows
                ZStack(alignment: .bottomLeading) {
                    if let shown {
                        HeroText(item: shown)
                            .id(shown.id)
                            .transition(.asymmetric(insertion: .identity, removal: .opacity.animation(.easeOut(duration: 0.18))))
                    }
                }
                .frame(width: w * 0.55, height: pivot - 40, alignment: .bottomLeading)
                .padding(.leading, Metrics.edge)

                ScrollViewReader { proxy in
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(alignment: .leading, spacing: Metrics.rowSpacing) {
                            Color.clear.frame(height: pivot).id("top")
                            ForEach(sections) { s in
                                HomeShelf(section: s) { key in focus(key, in: s) }
                                    .id(s.key)
                            }
                            if sections.isEmpty { HomeEmpty() }
                        }
                        .padding(.bottom, h * 0.42)
                        .neonFloorLight()
                    }
                    .scrollClipDisabled()
                    // rows that slide up past the focused one fade out under the story
                    .mask(LinearGradient(stops: [
                        .init(color: .clear, location: 0),
                        .init(color: .clear, location: max(0, (pivot - 130) / h)),
                        .init(color: .black, location: max(0, (pivot - 36) / h)),
                        .init(color: .black, location: 1),
                    ], startPoint: .top, endPoint: .bottom))
                    .onChange(of: focusRow) { _, key in
                        guard let key, let s = sections.first(where: { $0.key == key }) else { return }
                        let rowH = Metrics.rowHeight(tile: s.wide ? WideTileSize.height : Metrics.posterHeight)
                        let anchor = UnitPoint(x: 0, y: min(1, pivot / max(h - rowH, 1)))
                        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : NeonMotion.slide) {
                            proxy.scrollTo(key, anchor: anchor)
                        }
                    }
                }
            }
            .frame(width: w, height: h)
            .backgroundPreferenceValue(SpotKey.self) { spot in
                GeometryReader { gg in
                    NeonWorld(dim: 0.42, lean: spot.map { gg[$0.bounds].midX / max(gg.size.width, 1) - 0.5 } ?? 0)
                }
            }
        }
        .ignoresSafeArea()
    }

    /// A tile took focus: its row becomes the focused row, and after a short pause (so moving
    /// fast along a row does not flicker the picture) the hero follows it.
    private func focus(_ key: String, in s: HomeSection) {
        if focusRow != s.key { focusRow = s.key }
        guard let t = s.tiles.first(where: { $0.key == key }) else { return }
        heroTask?.cancel()
        heroTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(140))
            guard !Task.isCancelled, hero?.id != t.item.id else { return }
            await preload(Art.background(t.item.id, t.item.background) ?? t.item.poster, ms: 650)
            guard !Task.isCancelled else { return }
            withAnimation(.easeInOut(duration: 0.55)) { hero = t.item }
        }
    }
}

#else

// MARK: - iPhone and iPad

private typealias HomeBody = PhoneHome

private struct PhoneHome: View {
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme

    var body: some View {
        let sections = model.homeSections
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: Metrics.rowSpacing) {
                PhoneHero(items: featured(sections))
                ForEach(sections) { s in
                    HomeShelf(section: s)
                }
                if sections.isEmpty { HomeEmpty() }
            }
            .padding(.bottom, Metrics.rowSpacing * 2)
        }
        .background(NeonBackground(dim: 0.35))
        .ignoresSafeArea(edges: .top)
        .refreshable { await model.refreshAddons() }
    }

    /// What the hero turns through: the first catalog's first few, or Continue watching.
    private func featured(_ sections: [HomeSection]) -> [MetaItem] {
        let pick = sections.first { $0.key != "cw" && $0.key != "wl" } ?? sections.first
        return Array((pick?.tiles ?? []).prefix(6).map(\.item))
    }
}

/// The iPhone hero: a big picture that drifts, the logo and story over it, turning to the next
/// title every eight seconds (or with a swipe).
private struct PhoneHero: View {
    let items: [MetaItem]
    @State private var index = 0
    @Environment(\.neon) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let height: CGFloat = 470
        ZStack(alignment: .bottomLeading) {
            if items.isEmpty {
                Color.clear
            } else {
                TabView(selection: $index) {
                    ForEach(Array(items.enumerated()), id: \.element.id) { i, item in
                        NavigationLink(value: Route.details(item)) {
                            ZStack(alignment: .bottomLeading) {
                                DriftingPicture(url: Art.background(item.id, item.background) ?? item.poster)
                                    .frame(height: height)
                                    .clipped()
                                    .mask(LinearGradient(stops: [.init(color: .black, location: 0.45), .init(color: .clear, location: 1)],
                                                         startPoint: .top, endPoint: .bottom))
                                LinearGradient(colors: [.black.opacity(0.35), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.25))
                                HeroText(item: item, lines: 2)
                                    .padding(.horizontal, Metrics.edge)
                                    .padding(.bottom, 34)
                            }
                            .frame(height: height)
                        }
                        .buttonStyle(.plain)
                        .tag(i)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .frame(height: height)
                // the page dots, as neon dashes
                HStack(spacing: 6) {
                    ForEach(0..<items.count, id: \.self) { i in
                        Capsule()
                            .fill(i == index ? theme.primary : theme.text.opacity(0.3))
                            .frame(width: i == index ? 18 : 6, height: 4)
                            .shadow(color: i == index && theme.focus == .glow ? theme.primary : .clear, radius: 4)
                    }
                }
                .animation(NeonMotion.popSpring, value: index)
                .padding(.leading, Metrics.edge)
                .padding(.bottom, 14)
            }
        }
        .frame(height: height)
        .task(id: items.map(\.id)) {
            guard !reduceMotion, items.count > 1 else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(8))
                guard !Task.isCancelled else { return }
                withAnimation(.easeInOut(duration: 0.6)) { index = (index + 1) % items.count }
            }
        }
    }
}

#endif

// MARK: - Conversions

extension ContinueEntry {
    var asMeta: MetaItem {
        MetaItem(id: id, kind: kind, name: name, poster: poster, background: background, logo: logo, description: nil,
                 releaseInfo: nil, imdbRating: nil, genres: [], runtime: nil)
    }
}

extension WatchlistItem {
    var asMeta: MetaItem {
        MetaItem(id: id, kind: kind, name: title, poster: poster, background: nil, logo: nil, description: nil,
                 releaseInfo: nil, imdbRating: nil, genres: [], runtime: nil)
    }
}
