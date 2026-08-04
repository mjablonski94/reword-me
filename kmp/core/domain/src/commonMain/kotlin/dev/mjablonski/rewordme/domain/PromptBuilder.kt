package dev.mjablonski.rewordme.domain

import dev.mjablonski.rewordme.models.RewriteRule

/**
 * Assembles the system prompt from four layers:
 * 1. the fixed core instruction, 2. enabled user-written rules,
 * 3. the user's freeform base prompt, 4. one-shot steering from the popup.
 */
object PromptBuilder {
    const val CORE_INSTRUCTION =
        "You rewrite text. Rewrite the text the user gives you while preserving its meaning " +
            "and its language. Match the original register and tone unless instructed otherwise. " +
            "Keep formatting, line breaks, placeholders, code, URLs and @mentions intact. " +
            "Output only the rewritten text - no preamble, no surrounding quotes, no explanations."

    fun systemPrompt(rules: List<RewriteRule>, basePrompt: String, steering: String?): String {
        val sections = mutableListOf(CORE_INSTRUCTION)

        val enabledRules = enabledTexts(rules)
        if (enabledRules.isNotEmpty()) sections += "Rules:\n" + bulleted(enabledRules)

        basePrompt.trim().takeIf(String::isNotEmpty)?.let { sections += it }
        steering?.trim()?.takeIf(String::isNotEmpty)?.let {
            sections += "Additional instruction for this rewrite only: $it"
        }

        return sections.joinToString("\n\n")
    }

    private fun enabledTexts(rules: List<RewriteRule>): List<String> =
        rules.filter { it.isEnabled }
            .map { it.text.trim() }
            .filter(String::isNotEmpty)

    private fun bulleted(lines: List<String>): String =
        lines.joinToString("\n") { "- $it" }
}
