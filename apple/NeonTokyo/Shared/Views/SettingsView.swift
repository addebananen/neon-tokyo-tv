import SwiftUI

/// Profile, theme, addons and versions.
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme
    @State private var addonInput = ""
    @State private var addError: String?
    @State private var adding = false
    @State private var showAdd = false

    var body: some View {
        List {
            Section("Profile") {
                if let p = model.activeProfile {
                    HStack(spacing: 16) {
                        ProfileBadgeView(profile: p, size: Metrics.bodySize * 2, showName: false)
                        Text(p.name).font(.system(size: Metrics.bodySize, weight: .semibold))
                    }
                }
                Button("Switch profile") { model.select(nil) }
            }

            Section("Theme") {
                ForEach(NeonTokens.themes) { t in
                    Button {
                        model.setTheme(t.id)
                    } label: {
                        HStack {
                            Circle().fill(t.primary).frame(width: 18, height: 18)
                            Text(t.name)
                            Spacer()
                            if t.id == model.themeId { Image(systemName: "checkmark") }
                        }
                    }
                }
            }

            Section {
                ForEach(model.addonUrls, id: \.self) { base in
                    let m = model.manifests.first { $0.baseUrl == base }
                    VStack(alignment: .leading, spacing: 4) {
                        Text(m?.name ?? "Addon").font(.system(size: Metrics.bodySize, weight: .semibold))
                        Text(m.map { "\($0.catalogs.count) catalogs · " + $0.resources.joined(separator: ", ") } ?? base)
                            .font(.system(size: Metrics.bodySize * 0.7))
                            .foregroundStyle(theme.muted)
                            .lineLimit(1)
                    }
                    .contextMenu {
                        Button("Remove addon", role: .destructive) { model.removeAddon(base) }
                    }
                    #if os(iOS)
                    .swipeActions { Button("Remove", role: .destructive) { model.removeAddon(base) } }
                    #endif
                }
                Button {
                    addonInput = ""
                    addError = nil
                    showAdd = true
                } label: {
                    Label(adding ? "Adding…" : "Add addon", systemImage: "plus")
                }
                .disabled(adding)
                if let addError {
                    Text(addError).foregroundStyle(theme.error)
                }
            } header: {
                Text("Addons")
            } footer: {
                Text("Paste or type the addon's address (ending in manifest.json). Addons are shared by every profile.")
            }

            Section("About") {
                LabeledContent("Neon Tokyo", value: Bundle.main.shortVersion)
                LabeledContent("Core", value: coreVersion())
            }
        }
        .navigationTitle("Settings")
        .scrollContentBackground(.hidden)
        .background(NeonBackground())
        .alert("Add addon", isPresented: $showAdd) {
            TextField("https://…/manifest.json", text: $addonInput)
                #if os(iOS)
                .keyboardType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                #endif
            Button("Add") { add() }
            Button("Cancel", role: .cancel) {}
        }
    }

    private func add() {
        let input = addonInput
        adding = true
        Task {
            do {
                try await model.addAddon(input)
                addError = nil
            } catch {
                addError = "Could not add that addon. Check the address."
            }
            adding = false
        }
    }
}
