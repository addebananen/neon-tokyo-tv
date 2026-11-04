import SwiftUI

/// "Who's watching?" Each profile has its own history, watchlist and next episodes; addons are
/// shared, as in the Android app.
struct ProfilePickerView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme
    @State private var adding = false
    @State private var name = ""
    private let badge = Metrics.posterWidth * 0.8

    var body: some View {
        VStack(spacing: Metrics.rowSpacing) {
            NeonTitle(text: model.liveProfiles.isEmpty ? "Welcome to Neon Tokyo" : "Who's watching?", size: Metrics.titleSize * 1.4)
            HStack(alignment: .top, spacing: Metrics.cardSpacing * 1.4) {
                ForEach(model.liveProfiles, id: \.id) { p in
                    VStack(spacing: 14) {
                        Button {
                            model.select(p)
                        } label: {
                            ProfileBadgeView(profile: p, size: badge, showName: false)
                        }
                        .buttonStyle(NeonTileStyle(radius: badge, glow: Color(hexString: p.color), pop: 1.1))
                        .contextMenu {
                            Button("Remove profile", role: .destructive) { model.remove(p) }
                        }
                        Text(p.name)
                            .font(.system(size: Metrics.bodySize * 0.9, weight: .medium))
                            .foregroundStyle(theme.text)
                    }
                }
                VStack(spacing: 14) {
                    Button {
                        name = ""
                        adding = true
                    } label: {
                        Image(systemName: "plus")
                            .font(.system(size: Metrics.titleSize, weight: .light))
                            .foregroundStyle(theme.muted)
                            .frame(width: badge, height: badge)
                            .background(Circle().strokeBorder(theme.muted, style: StrokeStyle(lineWidth: 2, dash: [6, 6])))
                            .padding(8)
                    }
                    .buttonStyle(NeonTileStyle(radius: badge, pop: 1.1))
                    Text("Add profile")
                        .font(.system(size: Metrics.bodySize * 0.9))
                        .foregroundStyle(theme.muted)
                }
            }
            .neonFloorLight()
            .tvFocusSection()
        }
        .padding(Metrics.edge)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(NeonWorld(dim: 0.05))
        .alert("New profile", isPresented: $adding) {
            TextField("Name", text: $name)
            Button("Create") {
                let n = name.trimmingCharacters(in: .whitespaces)
                if !n.isEmpty { model.createProfile(name: n) }
            }
            Button("Cancel", role: .cancel) {}
        }
    }
}

/// A round badge with the profile's photo, or its first letter in its colour.
struct ProfileBadgeView: View {
    let profile: Profile
    var size: CGFloat
    var showName = true
    @Environment(\.neon) private var theme

    var body: some View {
        VStack(spacing: 10) {
            ZStack {
                Circle().fill(Color(hexString: profile.color).gradient)
                if let photo = profile.photo {
                    NeonImage(url: photo).clipShape(Circle())
                } else {
                    Text(String(profile.name.prefix(1)).uppercased())
                        .font(NeonFont.display(size * 0.45))
                        .foregroundStyle(.white)
                }
            }
            .frame(width: size, height: size)
            if showName {
                Text(profile.name)
                    .font(.system(size: Metrics.bodySize * 0.85, weight: .medium))
                    .foregroundStyle(theme.text)
            }
        }
        .padding(8)
    }
}
