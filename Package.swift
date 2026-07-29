// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "RewordMe",
    platforms: [.macOS(.v14)],
    products: [
        .executable(name: "RewordMeApp", targets: ["RewordMeApp"])
    ],
    targets: [
        .target(name: "RewordMeCore"),
        .executableTarget(
            name: "RewordMeApp",
            dependencies: ["RewordMeCore"]
        ),
        .testTarget(
            name: "RewordMeCoreTests",
            dependencies: ["RewordMeCore"]
        )
    ],
    swiftLanguageModes: [.v5]
)
