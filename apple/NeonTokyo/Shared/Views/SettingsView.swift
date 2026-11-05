import CoreImage.CIFilterBuiltins
import SwiftUI

/// Profile, accounts (Stremio and Trakt), updates, theme, addons and versions.
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme
    @Environment(\.openURL) private var openURL
    @State private var addonInput = ""
    @State private var addError: String?
    @State private var adding = false
    @State private var showAdd = false
    @State private var email = ""
    @State private var password = ""
    @State private var signingIn = false
    @State private var signInError: String?

    var body: some View {
        #if os(tvOS)
        // Apple TV's own Settings layout: who and what on the left, the list on a glass pane
        // to the right, so nothing reads over the busy skyline.
        HStack(alignment: .top, spacing: 64) {
            SettingsAside()
                .frame(width: 500)
                .padding(.top, 90)
            list
                .padding(.horizontal, 28)
                .padding(.vertical, 18)
                .background(SettingsPane())
                .padding(.vertical, 40)
        }
        .padding(.horizontal, Metrics.edge)
        .background(NeonBackground(dim: 0.62))
        .alert("Add addon", isPresented: $showAdd) { addAlert }
        #else
        list
            .navigationTitle("Settings")
            .scrollContentBackground(.hidden)
            .background(NeonBackground(dim: 0.55))
            .alert("Add addon", isPresented: $showAdd) { addAlert }
        #endif
    }

    @ViewBuilder
    private var addAlert: some View {
        TextField("https://…/manifest.json", text: $addonInput)
            #if os(iOS)
            .keyboardType(.URL)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            #endif
        Button("Add") { add() }
        Button("Cancel", role: .cancel) {}
    }

    private var list: some View {
        let accounts = model.accounts
        return List {
            Section("Profile") {
                #if os(iOS)
                // Apple TV shows the profile big beside the list
                if let p = model.activeProfile {
                    HStack(spacing: 16) {
                        ProfileBadgeView(profile: p, size: Metrics.bodySize * 2, showName: false)
                        Text(p.name).font(.system(size: Metrics.bodySize, weight: .semibold))
                    }
                }
                #endif
                Button("Switch profile") { model.select(nil) }
            }

            stremioSection(accounts)
            traktSection(accounts)
            updatesSection

            Section("Theme") {
                ForEach(NeonTokens.themes) { t in
                    Button {
                        withAnimation(NeonMotion.fastOutSlowIn(0.4)) { model.setTheme(t.id) }
                    } label: {
                        HStack {
                            Circle().fill(t.primary).frame(width: 18, height: 18)
                                .shadow(color: t.focus == .glow ? t.primary : .clear, radius: 6)
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
                Text("Paste or type the addon's address (ending in manifest.json). Addons are shared by every profile; signing in to Stremio adds yours.")
            }

            Section("About") {
                LabeledContent("Neon Tokyo", value: Bundle.main.shortVersion)
                LabeledContent("Core", value: coreVersion())
            }
        }
        .tint(theme.primary)
    }

    // MARK: - Stremio

    @ViewBuilder
    private func stremioSection(_ accounts: Accounts) -> some View {
        Section {
            if let link = accounts.stremio {
                StatusLine(title: "Stremio", detail: accounts.stremioStatus.isEmpty ? "Signed in as \(link.email)" : accounts.stremioStatus,
                           busy: accounts.syncing)
                Button("Sync now") { Task { await accounts.syncAll(full: true) } }
                    .disabled(accounts.syncing)
                Button("Sign out of Stremio", role: .destructive) { accounts.stremioSignOut() }
            } else {
                TextField("Email", text: $email)
                    .textContentType(.username)
                    #if os(iOS)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    #endif
                SecureField("Password", text: $password)
                    .textContentType(.password)
                Button {
                    signIn(accounts)
                } label: {
                    HStack {
                        Text(signingIn ? "Signing in…" : "Sign in to Stremio")
                        if signingIn { Spacer(); ProgressView() }
                    }
                }
                .disabled(signingIn || email.isEmpty || password.isEmpty)
                if let signInError {
                    Text(signInError).foregroundStyle(theme.error)
                } else if !accounts.stremioStatus.isEmpty {
                    Text(accounts.stremioStatus).foregroundStyle(theme.muted)
                }
            }
        } header: {
            Text("Stremio")
        } footer: {
            Text("Brings your Stremio addons here and keeps Continue watching, watched marks and the library the same on every device. The password is not kept.")
        }
    }

    private func signIn(_ accounts: Accounts) {
        signingIn = true
        signInError = nil
        let e = email.trimmingCharacters(in: .whitespaces)
        let p = password
        Task {
            do {
                try await accounts.stremioSignIn(email: e, password: p)
                password = ""
            } catch CoreError.BadResponse(let reason) {
                signInError = reason
            } catch {
                signInError = Accounts.message(error, "Stremio")
            }
            signingIn = false
        }
    }

    // MARK: - Trakt

    @ViewBuilder
    private func traktSection(_ accounts: Accounts) -> some View {
        Section {
            if let code = accounts.traktCode {
                TraktCodeView(code: code)
                Button("Cancel") { accounts.traktCancel() }
            } else if accounts.trakt != nil {
                StatusLine(title: "Trakt", detail: accounts.traktStatus, busy: accounts.syncing)
                Button("Sync now") { Task { await accounts.syncAll() } }
                    .disabled(accounts.syncing)
                Button("Disconnect Trakt", role: .destructive) { accounts.traktSignOut() }
            } else {
                Button("Connect Trakt") { accounts.traktSignIn() }
                    .disabled(!accounts.traktAvailable)
                if !accounts.traktStatus.isEmpty {
                    Text(accounts.traktStatus).foregroundStyle(theme.muted)
                }
            }
        } header: {
            Text("Trakt")
        } footer: {
            Text(accounts.traktAvailable
                 ? "Scrobbles what you watch here and brings in what you watched elsewhere, for this profile."
                 : "This build was made without a Trakt key.")
        }
    }

    // MARK: - Updates

    @ViewBuilder
    private var updatesSection: some View {
        let updates = model.updates
        Section {
            if let u = updates.available {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Neon Tokyo \(u.version) is out")
                        .font(.system(size: Metrics.bodySize, weight: .semibold))
                        .foregroundStyle(theme.primary)
                    #if os(tvOS)
                    HStack(alignment: .center, spacing: 24) {
                        QRCode(text: u.pageUrl, size: 180)
                        Text("Scan to open the release on your phone, then install \(u.assetName) with atvloadly as before. Profiles, history and addons stay.")
                            .font(.system(size: Metrics.bodySize * 0.75))
                            .foregroundStyle(theme.muted)
                    }
                    #else
                    Text("Install \(u.assetName) with Sideloadly as before. Profiles, history and addons stay.")
                        .font(.system(size: Metrics.bodySize * 0.8))
                        .foregroundStyle(theme.muted)
                    #endif
                }
                #if os(iOS)
                Button("Open the release") {
                    if let url = URL(string: u.pageUrl) { openURL(url) }
                }
                #endif
            }
            Button {
                Task { await updates.check() }
            } label: {
                HStack {
                    Text(updates.checking ? "Checking…" : "Check for updates")
                    if updates.checking { Spacer(); ProgressView() }
                }
            }
            .disabled(updates.checking)
            if !updates.status.isEmpty {
                Text(updates.status).foregroundStyle(theme.muted)
            }
        } header: {
            Text("Updates")
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

#if os(tvOS)
/// The left of Apple TV's Settings: the profile's badge lit like a sign, its name, and how the
/// accounts are doing at a glance.
private struct SettingsAside: View {
    @Environment(AppModel.self) private var model
    @Environment(\.neon) private var theme
    @State private var lit = false

    var body: some View {
        let accounts = model.accounts
        VStack(alignment: .leading, spacing: 30) {
            NeonTitle(text: "Settings", size: Metrics.titleSize * 1.9)
            if let p = model.activeProfile {
                HStack(spacing: 26) {
                    ProfileBadgeView(profile: p, size: 132, showName: false)
                        .shadow(color: theme.primary.opacity(theme.focus == .glow ? (lit ? 0.7 : 0.35) : 0.2), radius: lit ? 34 : 18)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(p.name)
                            .font(.system(size: Metrics.titleSize, weight: .bold))
                            .foregroundStyle(theme.text)
                        Text("Profile")
                            .font(.system(size: Metrics.bodySize * 0.8, weight: .medium))
                            .foregroundStyle(theme.muted)
                    }
                }
            }
            VStack(alignment: .leading, spacing: 14) {
                AccountChip(name: "Stremio", on: accounts.stremio != nil,
                            detail: accounts.stremio.map { $0.email } ?? "Not signed in")
                AccountChip(name: "Trakt", on: accounts.trakt != nil,
                            detail: accounts.trakt != nil ? "Connected" : (accounts.traktAvailable ? "Not connected" : "No key in this build"))
                if let u = model.updates.available {
                    AccountChip(name: "Update", on: true, detail: "\(u.version) is out", warm: true)
                }
            }
            Spacer(minLength: 0)
            Text("Neon Tokyo \(Bundle.main.shortVersion)")
                .font(.system(size: Metrics.bodySize * 0.7, weight: .medium))
                .foregroundStyle(theme.muted.opacity(0.7))
                .padding(.bottom, 60)
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 2.4).repeatForever(autoreverses: true)) { lit = true }
        }
    }
}

/// One account at a glance: a lamp that is lit when it is connected.
private struct AccountChip: View {
    let name: String
    let on: Bool
    let detail: String
    var warm = false
    @Environment(\.neon) private var theme

    var body: some View {
        let lamp = warm ? theme.warm : (on ? theme.secondary : theme.muted.opacity(0.35))
        HStack(spacing: 16) {
            Circle()
                .fill(lamp)
                .frame(width: 12, height: 12)
                .shadow(color: on ? lamp : .clear, radius: 7)
            Text(name)
                .font(.system(size: Metrics.bodySize * 0.85, weight: .semibold))
                .foregroundStyle(theme.text)
            Text(detail)
                .font(.system(size: Metrics.bodySize * 0.75))
                .foregroundStyle(theme.muted)
                .lineLimit(1)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(Capsule().fill(theme.panel.opacity(0.7)))
        .overlay(Capsule().strokeBorder(on ? lamp.opacity(0.35) : theme.text.opacity(0.08), lineWidth: 1))
    }
}

/// The glass the list sits on: the night, frosted, with a thin neon edge lit from the top.
private struct SettingsPane: View {
    @Environment(\.neon) private var theme

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 40, style: .continuous)
        shape
            .fill(.ultraThinMaterial)
            .overlay(shape.fill(theme.panel.opacity(0.72)))
            .overlay(shape.strokeBorder(LinearGradient(colors: [theme.primary.opacity(0.5), theme.secondary.opacity(0.12), .clear],
                                                       startPoint: .top, endPoint: .bottom), lineWidth: 1.5))
            .shadow(color: .black.opacity(0.55), radius: 40, y: 24)
    }
}
#endif

/// An account's line: its name, how sync went, a spinner while it runs.
private struct StatusLine: View {
    let title: String
    let detail: String
    let busy: Bool
    @Environment(\.neon) private var theme

    var body: some View {
        HStack(spacing: 14) {
            Circle()
                .fill(theme.secondary)
                .frame(width: 10, height: 10)
                .shadow(color: theme.secondary, radius: busy ? 8 : 3)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.system(size: Metrics.bodySize, weight: .semibold))
                if !detail.isEmpty {
                    Text(detail).font(.system(size: Metrics.bodySize * 0.75)).foregroundStyle(theme.muted)
                }
            }
            Spacer()
            if busy { ProgressView() }
        }
    }
}

/// The Trakt sign-in code, lit like a sign and breathing, with where to type it (and on Apple
/// TV a code to scan that opens that page on a phone).
private struct TraktCodeView: View {
    let code: TraktDeviceCode
    @State private var breathe = false
    @Environment(\.neon) private var theme

    var body: some View {
        HStack(alignment: .center, spacing: 28) {
            VStack(alignment: .leading, spacing: 10) {
                Text("Go to \(code.verificationUrl.replacingOccurrences(of: "https://", with: "")) and type")
                    .font(.system(size: Metrics.bodySize * 0.8))
                    .foregroundStyle(theme.muted)
                Text(code.userCode)
                    .font(.system(size: Metrics.titleSize * 1.3, weight: .bold, design: .monospaced))
                    .tracking(Metrics.titleSize * 0.15)
                    .foregroundStyle(theme.text)
                    .shadow(color: theme.primary.opacity(breathe ? 0.95 : 0.4), radius: breathe ? 18 : 8)
                Text("This page signs in by itself once the code is entered.")
                    .font(.system(size: Metrics.bodySize * 0.7))
                    .foregroundStyle(theme.muted)
            }
            #if os(tvOS)
            QRCode(text: code.verificationUrl, size: 170)
            #endif
        }
        .padding(.vertical, 8)
        .onAppear {
            withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) { breathe = true }
        }
    }
}

/// A QR code, white on the night, sharp at any size.
struct QRCode: View {
    let text: String
    let size: CGFloat

    var body: some View {
        if let img = QRCode.make(text) {
            Image(uiImage: img)
                .interpolation(.none)
                .resizable()
                .frame(width: size, height: size)
                .padding(10)
                .background(RoundedRectangle(cornerRadius: 12).fill(.white))
        }
    }

    static func make(_ text: String) -> UIImage? {
        let f = CIFilter.qrCodeGenerator()
        f.message = Data(text.utf8)
        f.correctionLevel = "M"
        guard let out = f.outputImage?.transformed(by: CGAffineTransform(scaleX: 8, y: 8)),
              let cg = CIContext().createCGImage(out, from: out.extent)
        else { return nil }
        return UIImage(cgImage: cg)
    }
}
