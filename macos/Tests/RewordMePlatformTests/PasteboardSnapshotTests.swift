import AppKit
import XCTest
@testable import RewordMePlatform

final class PasteboardSnapshotTests: XCTestCase {
    /// A private named pasteboard so tests never touch the user's clipboard.
    private var pasteboard: NSPasteboard!

    override func setUp() {
        super.setUp()
        pasteboard = NSPasteboard(name: NSPasteboard.Name("rewordme-tests-\(UUID().uuidString)"))
    }

    override func tearDown() {
        pasteboard.releaseGlobally()
        super.tearDown()
    }

    func testRestoreBringsBackTheCapturedContents() {
        pasteboard.clearContents()
        pasteboard.setString("original clipboard", forType: .string)

        let snapshot = PasteboardSnapshot.capture(from: pasteboard)
        pasteboard.clearContents()
        pasteboard.setString("temporary copy", forType: .string)
        snapshot.restore()

        XCTAssertEqual(pasteboard.string(forType: .string), "original clipboard")
    }

    func testRestorePreservesMultipleRepresentations() {
        pasteboard.clearContents()
        let item = NSPasteboardItem()
        item.setString("plain", forType: .string)
        item.setString("<b>rich</b>", forType: .html)
        pasteboard.writeObjects([item])

        let snapshot = PasteboardSnapshot.capture(from: pasteboard)
        pasteboard.clearContents()
        snapshot.restore()

        XCTAssertEqual(pasteboard.string(forType: .string), "plain")
        XCTAssertEqual(pasteboard.string(forType: .html), "<b>rich</b>")
    }

    func testEmptySnapshotRestoresToEmpty() {
        pasteboard.clearContents()
        let snapshot = PasteboardSnapshot.capture(from: pasteboard)

        pasteboard.setString("added later", forType: .string)
        snapshot.restore()

        XCTAssertNil(pasteboard.string(forType: .string))
    }

    func testDelayedRestoreOnlyRunsWhileTemporaryClipboardIsStillCurrent() {
        XCTAssertTrue(ClipboardRestorePolicy.shouldRestore(
            temporaryChangeCount: 42,
            currentChangeCount: 42
        ))
        XCTAssertFalse(ClipboardRestorePolicy.shouldRestore(
            temporaryChangeCount: 42,
            currentChangeCount: 43
        ))
    }

    func testSelectionCopyUsesThePostModifierClipboardAsItsBaseline() {
        // An external copy made while modifiers are being released is part of
        // the baseline captured afterward, not evidence of synthetic Cmd+C.
        XCTAssertFalse(ClipboardCopyPolicy.observedSyntheticCopy(
            baseline: 43,
            current: 43
        ))
        XCTAssertTrue(ClipboardCopyPolicy.observedSyntheticCopy(
            baseline: 43,
            current: 44
        ))
        XCTAssertFalse(ClipboardRestorePolicy.shouldRestore(
            temporaryChangeCount: 44,
            currentChangeCount: 45
        ))
    }
}
