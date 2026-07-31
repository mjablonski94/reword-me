// swift-tools-version: 6.0
import PackageDescription

// Layered clean architecture. Dependencies point inward only:
//
//   Models <- Domain <- Data          (pure -> business rules -> IO)
//   Models <- Platform                (macOS capabilities: AX, hotkeys, pasteboard)
//   everything <- RewordMeApp         (presentation + composition root)
let package = Package(
    name: "RewordMe",
    platforms: [.macOS(.v14)],
    products: [
        .executable(name: "RewordMeApp", targets: ["RewordMeApp"])
    ],
    targets: [
        // Pure value types: provider kinds, config, errors. No IO, no AppKit.
        .target(name: "RewordMeModels"),
        // Business rules and ports (protocols the outer layers implement).
        .target(name: "RewordMeDomain", dependencies: ["RewordMeModels"]),
        // IO implementations: HTTP provider clients, Keychain, config file.
        .target(name: "RewordMeData", dependencies: ["RewordMeModels", "RewordMeDomain"]),
        // macOS capabilities: AX selection, paste, global hotkey, pasteboard.
        .target(name: "RewordMePlatform", dependencies: ["RewordMeModels"]),
        // Presentation (SwiftUI views + view models) and the composition root.
        .executableTarget(
            name: "RewordMeApp",
            dependencies: [
                "RewordMeModels",
                "RewordMeDomain",
                "RewordMeData",
                "RewordMePlatform"
            ]
        ),
        .testTarget(
            name: "RewordMeDomainTests",
            dependencies: ["RewordMeDomain", "RewordMeModels"]
        ),
        .testTarget(
            name: "RewordMeDataTests",
            dependencies: ["RewordMeData", "RewordMeDomain", "RewordMeModels"]
        ),
        .testTarget(
            name: "RewordMePlatformTests",
            dependencies: ["RewordMePlatform"]
        )
    ],
    swiftLanguageModes: [.v5]
)
