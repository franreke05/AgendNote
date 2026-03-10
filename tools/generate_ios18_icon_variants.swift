import AppKit
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let repositoryRoot: URL
if CommandLine.arguments.count > 1 {
    repositoryRoot = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
} else {
    repositoryRoot = URL(fileURLWithPath: FileManager.default.currentDirectoryPath, isDirectory: true)
}

let sourceURL: URL
if CommandLine.arguments.count > 2 {
    sourceURL = URL(fileURLWithPath: CommandLine.arguments[2], isDirectory: false)
} else {
    sourceURL = repositoryRoot.appendingPathComponent("iosApp/iosApp/Assets.xcassets/PrimaryAppIcon.appiconset/app-icon-1024.png")
}

let iconSetURL = repositoryRoot
    .appendingPathComponent("iosApp", isDirectory: true)
    .appendingPathComponent("iosApp", isDirectory: true)
    .appendingPathComponent("Assets.xcassets", isDirectory: true)
    .appendingPathComponent("PrimaryAppIcon.appiconset", isDirectory: true)

let outputURLs = [
    iconSetURL.appendingPathComponent("app-icon-1024.png"),
    iconSetURL.appendingPathComponent("app-icon-dark-1024.png"),
    iconSetURL.appendingPathComponent("app-icon-tinted-1024.png"),
]

guard
    let sourceData = try? Data(contentsOf: sourceURL),
    let sourceImage = NSImage(data: sourceData),
    let sourceCGImage = sourceImage.cgImage(forProposedRect: nil, context: nil, hints: nil)
else {
    fputs("Unable to load source icon at \(sourceURL.path)\n", stderr)
    exit(1)
}

let canvasSize = CGSize(width: 1024, height: 1024)
let sourceSize = CGSize(width: sourceCGImage.width, height: sourceCGImage.height)
let scale = min(canvasSize.width / sourceSize.width, canvasSize.height / sourceSize.height)
let scaledSize = CGSize(width: sourceSize.width * scale, height: sourceSize.height * scale)
let drawRect = CGRect(
    x: (canvasSize.width - scaledSize.width) / 2.0,
    y: (canvasSize.height - scaledSize.height) / 2.0,
    width: scaledSize.width,
    height: scaledSize.height
)

func writePng(to url: URL) {
    let bytesPerRow = Int(canvasSize.width) * 4
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    guard let context = CGContext(
        data: nil,
        width: Int(canvasSize.width),
        height: Int(canvasSize.height),
        bitsPerComponent: 8,
        bytesPerRow: bytesPerRow,
        space: colorSpace,
        bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue).union(.byteOrder32Big).rawValue
    ) else {
        fputs("Unable to create bitmap context for \(url.lastPathComponent)\n", stderr)
        exit(1)
    }

    context.clear(CGRect(origin: .zero, size: canvasSize))
    context.interpolationQuality = .high
    context.draw(sourceCGImage, in: drawRect)

    guard
        let cgImage = context.makeImage(),
        let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)
    else {
        fputs("Unable to prepare PNG output for \(url.lastPathComponent)\n", stderr)
        exit(1)
    }

    CGImageDestinationAddImage(destination, cgImage, nil)
    if !CGImageDestinationFinalize(destination) {
        fputs("Unable to write \(url.lastPathComponent)\n", stderr)
        exit(1)
    }
}

for url in outputURLs {
    writePng(to: url)
}

print("Generated icon variants from \(sourceURL.lastPathComponent)")
