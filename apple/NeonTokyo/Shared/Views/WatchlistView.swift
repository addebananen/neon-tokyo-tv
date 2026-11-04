import SwiftUI

struct WatchlistView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme

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
                    NavigationLink(value: Route.details(w.asMeta)) {
                        PosterArt(title: w.title, poster: w.poster)
                    }
                    .buttonStyle(NeonCardStyle())
                }
            }
            .padding(Metrics.edge)
        }
        .background(NeonBackground())
        .navigationTitle("Watchlist")
        .neonRoutes()
    }
}
