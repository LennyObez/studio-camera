import SwiftUI

@main
struct StudioCameraApp: App {

    init() {
        KoinInit.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
