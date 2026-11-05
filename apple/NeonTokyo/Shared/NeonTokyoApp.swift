import SwiftUI

@main
struct NeonTokyoApp: App {
    @State private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .environment(\.neon, model.theme)
                .preferredColorScheme(.dark)
                .tint(model.theme.primary)
        }
    }
}
