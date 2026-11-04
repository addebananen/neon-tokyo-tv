import SwiftUI

/// A picture from an address, through the shared loader (with the metahub fallback).
struct NeonImage: View {
    let url: String?
    var mode: ContentMode = .fill
    @State private var image: UIImage?
    @Environment(\.neon) private var theme

    var body: some View {
        ZStack {
            Rectangle().fill(theme.surface.opacity(0.55))
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

/// A poster on a row, with the title written on it when there is no picture and a thin bar for
/// how far someone got.
struct PosterArt: View {
    let title: String
    let poster: String?
    var progress: Double = 0
    var watched = false
    @Environment(\.neon) private var theme

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            NeonImage(url: poster)
                .frame(width: Metrics.posterWidth, height: Metrics.posterHeight)
                .clipped()
            if poster == nil {
                Text(title)
                    .font(.system(size: Metrics.bodySize, weight: .semibold))
                    .foregroundStyle(theme.text)
                    .padding(10)
                    .frame(width: Metrics.posterWidth, height: Metrics.posterHeight, alignment: .bottomLeading)
            }
            if progress > 0.01 {
                ProgressBar(value: progress)
                    .padding(8)
            }
            if watched {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: Metrics.bodySize))
                    .foregroundStyle(theme.primary, theme.background)
                    .padding(8)
                    .frame(width: Metrics.posterWidth, height: Metrics.posterHeight, alignment: .topTrailing)
            }
        }
        .frame(width: Metrics.posterWidth, height: Metrics.posterHeight)
        .accessibilityLabel(title)
    }
}

struct ProgressBar: View {
    let value: Double
    @Environment(\.neon) private var theme

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(.black.opacity(0.55))
                Capsule().fill(theme.primary).frame(width: g.size.width * min(max(value, 0), 1))
                    .shadow(color: theme.focus == .glow ? theme.primary : .clear, radius: 4)
            }
        }
        .frame(height: 5)
    }
}

/// A titled row that scrolls sideways. Cards may grow and glow past the row's edge.
struct RowSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content
    @Environment(\.neon) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: Metrics.cardSpacing * 0.5) {
            Text(title)
                .font(theme.focus == .glow ? NeonFont.heading(Metrics.titleSize * 0.8) : .system(size: Metrics.titleSize * 0.8, weight: .semibold))
                .foregroundStyle(theme.text)
                .padding(.horizontal, Metrics.edge)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: Metrics.cardSpacing) {
                    content()
                }
                .padding(.horizontal, Metrics.edge)
                .padding(.vertical, Metrics.cardSpacing * 0.6)
            }
            .scrollClipDisabled()
        }
        .tvFocusSection()
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
