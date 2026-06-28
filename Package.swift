// swift-tools-version:5.5
import PackageDescription

let package = Package(
    name: "OfdNetworkClient",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: "OfdNetworkClient",
            targets: ["OfdNetworkClient"]
        ),
    ],
    dependencies: [],
    targets: [
        .binaryTarget(
            name: "OfdNetworkClient",
            url: "https://github.com/texport/ofd-network-client/releases/download/v1.1.0/OfdNetworkClient.xcframework.zip",
            checksum: "74363b2bf3aab320f4af4a94a3108c79508a2657ac4febd5a4c03831e797ee36"
        )
    ]
)
