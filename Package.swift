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
            url: "https://github.com/texport/ofd-network-client/releases/download/v1.2.1/OfdNetworkClient.xcframework.zip",
            checksum: "451a730a404d31b74ef266163f572291d676846884d15005d22d48f26dcf6a7b"
        )
    ]
)
