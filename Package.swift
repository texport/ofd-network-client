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
            checksum: "43bf0344a1665ba217100cc788377f570524258b1de0a9b73ca2ea4ad3746a74"
        )
    ]
)
