import AppKit

/// Saves and restores the full pasteboard contents so the copy/paste
/// fallbacks never clobber whatever the user had on the clipboard.
public struct PasteboardSnapshot {
    private let items: [[NSPasteboard.PasteboardType: Data]]
    private let pasteboard: NSPasteboard

    public static func capture(from pasteboard: NSPasteboard = .general) -> PasteboardSnapshot {
        let items = (pasteboard.pasteboardItems ?? []).map { item in
            var entry: [NSPasteboard.PasteboardType: Data] = [:]
            for type in item.types {
                if let data = item.data(forType: type) {
                    entry[type] = data
                }
            }
            return entry
        }
        return PasteboardSnapshot(items: items, pasteboard: pasteboard)
    }

    public func restore() {
        pasteboard.clearContents()
        guard !items.isEmpty else { return }
        let restored = items.map { entry in
            let item = NSPasteboardItem()
            for (type, data) in entry {
                item.setData(data, forType: type)
            }
            return item
        }
        pasteboard.writeObjects(restored)
    }
}
