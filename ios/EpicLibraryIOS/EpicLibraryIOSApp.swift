import SwiftUI

@main
struct EpicLibraryIOSApp: App {
    @StateObject private var model = EpicAppModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(model)
        }
    }
}
