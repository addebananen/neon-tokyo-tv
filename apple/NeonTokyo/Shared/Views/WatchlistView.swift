import SwiftUI

struct WatchlistView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme
    @FocusState private var focused: String?

    var body: some View {
        ScrollView {
            let items = model.watchlistItems
            if items.isEmpty {
                Text("Nothing here yet. Press Watchlist on a film or series to keep it here.")
                    .foregroundStyle(theme.muted)
                    .padding(Metrics.edge)
            }
            LazyVGrid(columns: [GridItem(.adaptive(minimum: Metrics.posterWidth), spacing: Metrics.cardSpacing)],
                      alignment: .leading, spacing: Metrics.cardSpacing) {
                ForEach(items, id: \.id) { w in
                    TileLink(route: .details(w.asMeta), art: w.poster, focus: $focused, key: "wl|" + w.id) {
                        PosterTile(title: w.title, poster: w.poster, progress: model.progress(w.id), watched: model.isWatched(w.id))
                    }
                }
            }
            .padding(Metrics.edge)
            .neonFloorLight()
        }
        .scrollClipDisabled()
        .background(NeonBackground(dim: 0.45))
        .navigationTitle("Watchlist")
        .neonRoutes()
    }
}
