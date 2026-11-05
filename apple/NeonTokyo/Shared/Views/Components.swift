import SwiftUI

/// A picture from an address, through the shared loader (with the metahub fallback).
struct NeonImage: View {
    let url: String?
    var mode: ContentMode = .fill
    /// A soft panel shows while the picture loads; off for pictures that fade in over others.
    var placeholder = true
    @State private var image: UIImage?
    @Environment(\.neon) private var theme

    var body: some View {
        ZStack {
            if placeholder {
                Rectangle().fill(LinearGradient(colors: [theme.surface.opacity(0.6), theme.panel.opacity(0.75)],
                                                startPoint: .topLeading, endPoint: .bottomTrailing))
            }
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: mode)
                    .transition(.opacity)
            }
        }
        .task(id: url) {
            image = nil
            guard let url, !url.isEmpty else { return }
            let loaded = await ImageLoader.shared.image(for: url)
            withAnimation(.easeOut(duration: 0.2)) { image = loaded }
        }
    }
}

/// Where a link on any tab can lead.
enum Route: Hashable {
    case details(MetaItem)
    case sources(SourceRequest)
}

/// The film or episode whose sources to list.
struct SourceRequest: Hashable {
    let kind: String
    /// The id the addons know the film or episode by.
    let videoId: String
    let play: PlayItem
    let heading: String
}

extension View {
    /// The screens every tab can open.
    func neonRoutes() -> some View {
        navigationDestination(for: Route.self) { route in
            switch route {
            case .details(let item): DetailsView(item: item)
            case .sources(let request): SourcesView(request: request)
            }
        }
    }

    /// Groups a row for the Apple TV remote, so moving down lands on the row below.
    @ViewBuilder
    func tvFocusSection() -> some View {
        #if os(tvOS)
        focusSection()
        #else
        self
        #endif
    }
}

extension MetaItem: Identifiable {}

/// A small rounded label (quality, HDR, the episode number).
struct Badge: View {
    let text: String
    var strong = false
    @Environment(\.neon) private var theme

    var body: some View {
        Text(text)
            .font(.system(size: Metrics.bodySize * 0.72, weight: .bold))
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .foregroundStyle(strong ? theme.background : theme.text)
            .background(Capsule().fill(strong ? theme.primary : theme.surface))
    }
}
