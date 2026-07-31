package dev.mjablonski.rewordme.domain

/**
 * Decides whether a selection is worth acting on: rejects empty strings,
 * stray whitespace, punctuation runs, lone symbols, bare numbers.
 */
object SelectionFilter {
    fun isMeaningful(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 3) return false
        return trimmed.any(Char::isLetter)
    }
}
