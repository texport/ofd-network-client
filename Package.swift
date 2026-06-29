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
            checksum: "e130d66aec34ce4c8b3350c114e10ec1e0b0362c78b2015c0fa3aeed218911a6"
        )
    ]
)
