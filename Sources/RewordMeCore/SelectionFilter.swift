import Foundation

/// Decides whether a selection is worth popping up for. Filters out the
/// noise a mouse produces all day: empty strings, stray whitespace,
/// punctuation runs, lone symbols, bare numbers.
public enum SelectionFilter {
    public static func isMeaningful(_ text: String) -> Bool {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 3 else { return false }
        guard trimmed.rangeOfCharacter(from: .letters) != nil else { return false }
        return true
    }
}
