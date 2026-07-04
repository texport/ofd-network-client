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
            checksum: "8d2e471e3e99f1f572328729a9f574bf62a649b0953f186611f6f788d8171596"
        )
    ]
)
