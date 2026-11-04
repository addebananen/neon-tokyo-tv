import SwiftUI

/// The home screen: a large picture of the focused title on top, then Continue watching, the
/// watchlist and every catalog row from the addons.
struct HomeView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme
    @FocusState private var focused: String?
    @State private var hero: MetaItem?

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            LazyVStack(alignment: .leading, spacing: Metrics.rowSpacing) {
                HeroView(item: hero ?? firstItem)
                    .frame(height: Metrics.heroHeight)

                let cw = model.continueItems
                if !cw.isEmpty {
                    RowSection(title: "Continue watching") {
                        ForEach(cw, id: \.id) { entry in
                            NavigationLink(value: Route.details(entry.asMeta)) {
                                ContinueCard(entry: entry)
                            }
                            .buttonStyle(NeonCardStyle())
                            .focused($focused, equals: "cw|" + entry.id)
                        }
                    }
                }

                let list = model.watchlistItems
                if !list.isEmpty {
                    RowSection(title: "Watchlist") {
                        ForEach(list, id: \.id) { w in
                            NavigationLink(value: Route.details(w.asMeta)) {
                                PosterArt(title: w.title, poster: w.poster)
                            }
                            .buttonStyle(NeonCardStyle())
                            .focused($focused, equals: "wl|" + w.id)
                        }
                    }
                }

                ForEach(model.rows, id: \.key) { row in
                    if let items = model.rowItems[row.key], !items.isEmpty {
                        RowSection(title: row.title) {
                            ForEach(items) { item in
                                NavigationLink(value: Route.details(item)) {
                                    PosterArt(title: item.name, poster: item.poster, progress: model.progress(item.id),
                                              watched: model.isWatched(item.id))
                                }
                                .buttonStyle(NeonCardStyle())
                                .focused($focused, equals: row.key + "|" + item.id)
                            }
                        }
                    }
                }

                if model.rows.isEmpty {
                    HStack(spacing: 16) {
                        if model.loadingAddons { ProgressView() }
                        Text(model.loadingAddons ? "Loading your addons…" : "No catalogs yet. Add an addon in Settings.")
                            .foregroundStyle(theme.muted)
                    }
                    .padding(Metrics.edge)
                }
            }
            .padding(.bottom, Metrics.rowSpacing * 2)
        }
        .background(NeonBackground())
        .ignoresSafeArea(edges: .top)
        .onChange(of: focused) { _, key in
            guard let key, let item = item(forFocusKey: key) else { return }
            withAnimation(.easeInOut(duration: NeonTokens.motionPageMs / 1000)) { hero = item }
        }
        .refreshable { await model.refreshAddons() }
        .neonRoutes()
        .toolbar(.hidden, for: .navigationBar)
    }

    private var firstItem: MetaItem? {
        model.rows.lazy.compactMap { model.rowItems[$0.key]?.first }.first
    }

    private func item(forFocusKey key: String) -> MetaItem? {
        guard let bar = key.lastIndex(of: "|") else { return nil }
        let prefix = String(key[..<bar])
        let id = String(key[key.index(after: bar)...])
        switch prefix {
        case "cw": return model.continueItems.first { $0.id == id }?.asMeta
        case "wl": return model.watchlistItems.first { $0.id == id }?.asMeta
        default: return model.rowItems[prefix]?.first { $0.id == id }
        }
    }
}

/// The big picture: background, title or logo, a line of facts and the start of the story.
struct HeroView: View {
    let item: MetaItem?
    @Environment(\.neon) private var theme

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            NeonImage(url: item?.background ?? item?.poster)
                .clipped()
                .overlay(
                    LinearGradient(colors: [.clear, theme.background.opacity(0.6), theme.background],
                                   startPoint: .top, endPoint: .bottom)
                )
                .overlay(
                    LinearGradient(colors: [theme.background.opacity(0.85), .clear], startPoint: .leading, endPoint: .center)
                )
            if let item {
                VStack(alignment: .leading, spacing: Metrics.bodySize * 0.5) {
                    if let logo = item.logo {
                        NeonImage(url: logo, mode: .fit)
                            .frame(maxWidth: Metrics.posterWidth * 2.4, maxHeight: Metrics.posterWidth * 0.8, alignment: .leading)
                            .background(Color.clear)
                    } else {
                        NeonTitle(text: item.name, size: Metrics.titleSize * 1.4)
                    }
                    FactsLine(item: item)
                    if let d = item.description {
                        Text(d)
                            .font(.system(size: Metrics.bodySize))
                            .foregroundStyle(theme.text.opacity(0.85))
                            .lineLimit(3)
                            .frame(maxWidth: Metrics.posterWidth * 4.5, alignment: .leading)
                    }
                }
                .padding(.horizontal, Metrics.edge)
                .padding(.bottom, Metrics.rowSpacing * 0.5)
                .id(item.id)
                .transition(.opacity)
            }
        }
        .clipped()
    }
}

/// Year · rating · runtime · genres.
struct FactsLine: View {
    let item: MetaItem
    @Environment(\.neon) private var theme

    var body: some View {
        HStack(spacing: 12) {
            if let r = item.imdbRating { Badge(text: "IMDb \(r)", strong: true) }
            Text([item.releaseInfo, item.runtime, item.genres.prefix(3).joined(separator: ", ")]
                .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: "  ·  "))
                .font(.system(size: Metrics.bodySize * 0.85, weight: .medium))
                .foregroundStyle(theme.muted)
                .lineLimit(1)
        }
    }
}

/// A wide card for Continue watching: the picture, the episode and how far along.
struct ContinueCard: View {
    let entry: ContinueEntry
    @Environment(\.neon) private var theme

    var body: some View {
        let w = Metrics.posterWidth * 1.6
        ZStack(alignment: .bottomLeading) {
            NeonImage(url: entry.background ?? entry.poster)
                .frame(width: w, height: w * 9 / 16)
                .clipped()
            LinearGradient(colors: [.clear, .black.opacity(0.8)], startPoint: .center, endPoint: .bottom)
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    if let s = entry.season, let e = entry.episode { Badge(text: "S\(s):E\(e)") }
                    if entry.hasNewEpisode { Badge(text: "NEW", strong: true) }
                }
                Text(entry.name)
                    .font(.system(size: Metrics.bodySize * 0.9, weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                if entry.progress > 0.01 { ProgressBar(value: Double(entry.progress)) }
            }
            .padding(10)
        }
        .frame(width: w, height: w * 9 / 16)
    }
}

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
