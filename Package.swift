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
            url: "https://github.com/texport/ofd-network-client/releases/download/v1.2.0/OfdNetworkClient.xcframework.zip",
            checksum: "b0aae2bd47ede806d3bc3f6d15130a65fee01d151e2886e5d1548edc635bdeaa"
        )
    ]
)
