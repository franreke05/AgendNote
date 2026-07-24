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

enum IconVariant {
    case standard
    case dark
    case tinted
}

let outputs: [(url: URL, variant: IconVariant)] = [
    (iconSetURL.appendingPathComponent("app-icon-1024.png"), .standard),
    (iconSetURL.appendingPathComponent("app-icon-dark-1024.png"), .dark),
    (iconSetURL.appendingPathComponent("app-icon-tinted-1024.png"), .tinted),
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

// Applies a per-pixel transform to `image` and returns a new CGImage, or nil on failure.
// The transform receives premultiplied-last RGBA byte components (0...255) for a single
// pixel and must return the replacement RGBA components in the same order.
func transformedImage(
    _ image: CGImage,
    transform: (_ r: UInt8, _ g: UInt8, _ b: UInt8, _ a: UInt8) -> (UInt8, UInt8, UInt8, UInt8)
) -> CGImage? {
    let width = image.width
    let height = image.height
    let bytesPerPixel = 4
    let bytesPerRow = width * bytesPerPixel
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue).union(.byteOrder32Big).rawValue

    guard let context = CGContext(
        data: nil,
        width: width,
        height: height,
        bitsPerComponent: 8,
        bytesPerRow: bytesPerRow,
        space: colorSpace,
        bitmapInfo: bitmapInfo
    ) else {
        return nil
    }

    context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))

    guard let data = context.data else { return nil }
    let buffer = data.bindMemory(to: UInt8.self, capacity: height * bytesPerRow)

    for pixelIndex in 0..<(width * height) {
        let offset = pixelIndex * bytesPerPixel
        let r = buffer[offset]
        let g = buffer[offset + 1]
        let b = buffer[offset + 2]
        let a = buffer[offset + 3]
        let (nr, ng, nb, na) = transform(r, g, b, a)
        buffer[offset] = nr
        buffer[offset + 1] = ng
        buffer[offset + 2] = nb
        buffer[offset + 3] = na
    }

    return context.makeImage()
}

// Multiplies RGB by `factor` to darken the icon while preserving alpha.
// Values are premultiplied-alpha, so darkening the RGB channels directly
// (without touching alpha) darkens the visible color correctly.
func darkened(_ image: CGImage, factor: Double) -> CGImage? {
    transformedImage(image) { r, g, b, a in
        let nr = UInt8(min(255.0, Double(r) * factor).rounded())
        let ng = UInt8(min(255.0, Double(g) * factor).rounded())
        let nb = UInt8(min(255.0, Double(b) * factor).rounded())
        return (nr, ng, nb, a)
    }
}

// Converts to grayscale using standard luminance weights, leaving alpha untouched.
// iOS 18 tinted icons are expected to be a monochrome base that the system tints,
// so this produces the neutral grayscale source Apple's rendering expects.
func grayscaled(_ image: CGImage) -> CGImage? {
    transformedImage(image) { r, g, b, a in
        let luminance = 0.299 * Double(r) + 0.587 * Double(g) + 0.114 * Double(b)
        let value = UInt8(max(0.0, min(255.0, luminance)).rounded())
        return (value, value, value, a)
    }
}

func writePng(to url: URL, variant: IconVariant) {
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

    guard let baseImage = context.makeImage() else {
        fputs("Unable to render base image for \(url.lastPathComponent)\n", stderr)
        exit(1)
    }

    let variantImage: CGImage?
    switch variant {
    case .standard:
        variantImage = baseImage
    case .dark:
        // Darken by ~30% so the dark-mode icon reads as a deliberately dimmer
        // variant rather than a duplicate of the standard icon.
        variantImage = darkened(baseImage, factor: 0.65)
    case .tinted:
        // Apple applies its own tint on top of a monochrome source at render time.
        variantImage = grayscaled(baseImage)
    }

    guard
        let cgImage = variantImage,
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

for output in outputs {
    writePng(to: output.url, variant: output.variant)
}

print("Generated icon variants from \(sourceURL.lastPathComponent)")
