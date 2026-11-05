import SwiftUI

/// Search every addon that can search, one row per addon and type.
struct SearchView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme
    @State private var query = ""
    @State private var results: [SearchGroup] = []
    @State private var searching = false

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Metrics.rowSpacing) {
                if searching { ProgressView().padding(Metrics.edge) }
                ForEach(results) { group in
                    TileRow(title: "\(group.catalog.addonName) · \(Self.kindName(group.catalog.kind))", key: group.id,
                            count: group.items.count, tileWidth: Metrics.posterWidth) { focus in
                        ForEach(group.items) { item in
                            TileLink(route: .details(item), art: item.poster, focus: focus, key: group.id + "|" + item.id) {
                                PosterTile(title: item.name, poster: item.poster, progress: model.progress(item.id),
                                           watched: model.isWatched(item.id))
                            }
                            .modifier(EdgeSettle())
                        }
                    }
                }
                if !searching && results.isEmpty && query.count >= 2 {
                    Text("Nothing found for “\(query)”.").foregroundStyle(theme.muted).padding(Metrics.edge)
                }
            }
            .padding(.vertical, Metrics.rowSpacing)
            .neonFloorLight()
        }
        .scrollClipDisabled()
        .background(NeonBackground(dim: 0.45))
        .searchable(text: $query, prompt: "Films, series, anime")
        .task(id: query) {
            // wait for typing to pause
            try? await Task.sleep(for: .milliseconds(450))
            guard !Task.isCancelled else { return }
            searching = query.count >= 2
            results = await model.search(query)
            searching = false
        }
        .neonRoutes()
    }

    static func kindName(_ kind: String) -> String {
        switch kind {
        case "movie": return "Movies"
        case "series": return "Series"
        default: return kind.capitalized
        }
    }
}
