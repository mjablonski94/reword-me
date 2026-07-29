import AppKit
import CoreGraphics

// Renders AppIcon.iconset: a big white R inside a circular-arrows ring
// on a purple-indigo gradient tile.
let files: [(String, Int)] = [
    ("icon_16x16.png", 16), ("icon_16x16@2x.png", 32),
    ("icon_32x32.png", 32), ("icon_32x32@2x.png", 64),
    ("icon_128x128.png", 128), ("icon_128x128@2x.png", 256),
    ("icon_256x256.png", 256), ("icon_256x256@2x.png", 512),
    ("icon_512x512.png", 512), ("icon_512x512@2x.png", 1024)
]

func png(px: Int) -> Data {
    let dim = CGFloat(px)
    let context = CGContext(
        data: nil, width: px, height: px, bitsPerComponent: 8, bytesPerRow: 0,
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    )!

    // Tile with a diagonal purple -> indigo gradient.
    let tile = CGRect(x: dim * 0.06, y: dim * 0.06, width: dim * 0.88, height: dim * 0.88)
    let tilePath = CGPath(roundedRect: tile, cornerWidth: dim * 0.22, cornerHeight: dim * 0.22, transform: nil)
    context.saveGState()
    context.addPath(tilePath)
    context.clip()
    let gradient = CGGradient(
        colorsSpace: CGColorSpaceCreateDeviceRGB(),
        colors: [
            CGColor(red: 0.58, green: 0.33, blue: 0.93, alpha: 1),
            CGColor(red: 0.28, green: 0.30, blue: 0.86, alpha: 1)
        ] as CFArray,
        locations: [0, 1]
    )!
    context.drawLinearGradient(
        gradient,
        start: CGPoint(x: tile.minX, y: tile.maxY),
        end: CGPoint(x: tile.maxX, y: tile.minY),
        options: []
    )
    context.restoreGState()

    let center = CGPoint(x: dim / 2, y: dim / 2)
    let ringRadius = dim * 0.315
    let ringWidth = dim * 0.042
    let white = CGColor(red: 1, green: 1, blue: 1, alpha: 0.96)

    // Two clockwise arcs with a gap for each arrowhead.
    func radians(_ degrees: CGFloat) -> CGFloat { degrees * .pi / 180 }
    for (startDeg, endDeg) in [(150.0, 30.0), (330.0, 210.0)] {
        context.setStrokeColor(white)
        context.setLineWidth(ringWidth)
        context.setLineCap(.round)
        context.addArc(
            center: center,
            radius: ringRadius,
            startAngle: radians(CGFloat(startDeg)),
            endAngle: radians(CGFloat(endDeg)),
            clockwise: true
        )
        context.strokePath()

        // Arrowhead at the arc's end, pointing along the rotation.
        let angle = radians(CGFloat(endDeg))
        let point = CGPoint(
            x: center.x + ringRadius * cos(angle),
            y: center.y + ringRadius * sin(angle)
        )
        let tangent = CGPoint(x: sin(angle), y: -cos(angle)) // clockwise direction
        let normal = CGPoint(x: cos(angle), y: sin(angle))   // radial direction
        let length = dim * 0.085
        let width = dim * 0.055
        context.setFillColor(white)
        context.beginPath()
        context.move(to: CGPoint(
            x: point.x + tangent.x * length,
            y: point.y + tangent.y * length
        ))
        context.addLine(to: CGPoint(x: point.x + normal.x * width, y: point.y + normal.y * width))
        context.addLine(to: CGPoint(x: point.x - normal.x * width, y: point.y - normal.y * width))
        context.closePath()
        context.fillPath()
    }

    // The R, drawn via AppKit text on top of the CGContext.
    let graphicsContext = NSGraphicsContext(cgContext: context, flipped: false)
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = graphicsContext
    let baseFont = NSFont.systemFont(ofSize: dim * 0.34, weight: .heavy)
    let font = NSFont(
        descriptor: baseFont.fontDescriptor.withDesign(.rounded) ?? baseFont.fontDescriptor,
        size: dim * 0.34
    ) ?? baseFont
    let text = NSAttributedString(string: "R", attributes: [
        .font: font,
        .foregroundColor: NSColor.white
    ])
    let size = text.size()
    text.draw(at: NSPoint(x: (dim - size.width) / 2, y: (dim - size.height) / 2))
    NSGraphicsContext.restoreGraphicsState()

    let rep = NSBitmapImageRep(cgImage: context.makeImage()!)
    return rep.representation(using: .png, properties: [:])!
}

let iconset = "AppIcon.iconset"
try? FileManager.default.createDirectory(atPath: iconset, withIntermediateDirectories: true)
for (name, px) in files {
    try! png(px: px).write(to: URL(fileURLWithPath: "\(iconset)/\(name)"))
}
print("wrote \(iconset)")
