import SwiftUI

/// The intro, then the profile picker until someone is chosen, then the app.
struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ZStack {
            // the screens draw their own world; this is only what shows between them
            model.theme.background.ignoresSafeArea()
            if model.activeProfile == nil {
                ProfilePickerView()
                    .transition(.opacity)
            } else {
                MainTabs()
                    .transition(.opacity)
            }
            if model.showIntro {
                IntroView { withAnimation(.easeOut(duration: 0.4)) { model.showIntro = false } }
                    .transition(.opacity)
                    .zIndex(10)
            }
        }
        .animation(.easeInOut(duration: NeonTokens.motionPageMs / 1000), value: model.activeProfileId)
        .task { await model.refreshAddons() }
    }
}

/// Home, Search, Watchlist and Settings. Apple TV shows these as its own top tab bar, iPhone as
/// the bottom bar, which is what people expect on each.
struct MainTabs: View {
    enum Tab: Hashable { case home, search, watchlist, settings }

    @Environment(AppModel.self) private var model
    @State private var tab: Tab = .home
    @State private var homePath = NavigationPath()

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack(path: $homePath) { HomeView() }
                .tabItem { Label("Home", systemImage: "house.fill") }
                .tag(Tab.home)
            NavigationStack { SearchView() }
                .tabItem { Label("Search", systemImage: "magnifyingglass") }
                .tag(Tab.search)
            NavigationStack { WatchlistView() }
                .tabItem { Label("Watchlist", systemImage: "bookmark.fill") }
                .tag(Tab.watchlist)
            NavigationStack { SettingsView() }
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
                .tag(Tab.settings)
        }
        .onAppear(perform: openLaunchScreen)
    }

    /// CI screenshots start the app on a given screen: `-NeonScreen details:movie:tt15239678`
    /// or `-NeonScreen settings`.
    private func openLaunchScreen() {
        guard let screen = model.launchScreen else { return }
        let parts = screen.split(separator: ":", maxSplits: 2).map(String.init)
        switch parts.first {
        case "details" where parts.count == 3:
            homePath.append(Route.details(MetaItem(id: parts[2], kind: parts[1], name: "", poster: nil, background: nil, logo: nil,
                                                   description: nil, releaseInfo: nil, imdbRating: nil, genres: [], runtime: nil)))
        case "settings":
            tab = .settings
        case "watchlist":
            tab = .watchlist
        default:
            break
        }
    }
}
