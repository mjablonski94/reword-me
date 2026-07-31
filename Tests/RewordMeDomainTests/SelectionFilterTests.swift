import XCTest
@testable import RewordMeDomain

final class SelectionFilterTests: XCTestCase {
    func testMeaningfulTextPasses() {
        XCTAssertTrue(SelectionFilter.isMeaningful("Hello world"))
        XCTAssertTrue(SelectionFilter.isMeaningful("fix"))
        XCTAssertTrue(SelectionFilter.isMeaningful("  padded sentence.  "))
        XCTAssertTrue(SelectionFilter.isMeaningful("Zażółć gęślą jaźń"))
        XCTAssertTrue(SelectionFilter.isMeaningful("a1b2c3"))
    }

    func testEmptyAndWhitespaceAreRejected() {
        XCTAssertFalse(SelectionFilter.isMeaningful(""))
        XCTAssertFalse(SelectionFilter.isMeaningful("   "))
        XCTAssertFalse(SelectionFilter.isMeaningful("\n\t \n"))
    }

    func testTooShortSelectionsAreRejected() {
        XCTAssertFalse(SelectionFilter.isMeaningful("a"))
        XCTAssertFalse(SelectionFilter.isMeaningful("ab"))
        XCTAssertFalse(SelectionFilter.isMeaningful("  ab  "))
    }

    func testSymbolAndNumberNoiseIsRejected() {
        XCTAssertFalse(SelectionFilter.isMeaningful("..."))
        XCTAssertFalse(SelectionFilter.isMeaningful("12345"))
        XCTAssertFalse(SelectionFilter.isMeaningful("-> -> ->"))
        XCTAssertFalse(SelectionFilter.isMeaningful("!!! ??? ***"))
        XCTAssertFalse(SelectionFilter.isMeaningful("(){}[]"))
    }
}
