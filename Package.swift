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
            checksum: "3ceff123ef31929283214f61f8241ca04f78a73d68933a4bd89d5bc50b944d6f"
        )
    ]
)
