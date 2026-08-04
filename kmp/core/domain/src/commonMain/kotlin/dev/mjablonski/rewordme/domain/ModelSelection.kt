package dev.mjablonski.rewordme.domain

import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind

/**
 * Picks the default model for a provider: the least costly tier,
 * preferring stable releases, newest first.
 */
object ModelSelection {
    fun defaultModel(kind: ProviderKind, models: List<ModelInfo>): ModelInfo? {
        if (models.isEmpty()) return null
        // Local models are all free; Ollama lists the most recently
        // pulled/updated model first, which is the best default.
        if (kind.access != dev.mjablonski.rewordme.models.ProviderAccess.API_KEY) return models.first()

        val lowestTier = models.minOf { costTier(kind, it.id) }
        val candidates = models.filter { costTier(kind, it.id) == lowestTier }
        val stable = candidates.filterNot { isPreview(it.id) }
        return (stable.ifEmpty { candidates }).maxByOrNull { it.id }
    }

    /** Lower tier = cheaper. Unknown names land mid-tier. */
    internal fun costTier(kind: ProviderKind, id: String): Int {
        val lower = id.lowercase()
        return when (kind) {
            ProviderKind.ANTHROPIC, ProviderKind.CLAUDE_ACCOUNT -> when {
                "haiku" in lower -> 0
                "sonnet" in lower -> 1
                "opus" in lower -> 2
                "fable" in lower || "mythos" in lower -> 3
                else -> 2
            }
            ProviderKind.OPENAI, ProviderKind.CODEX -> when {
                "nano" in lower -> 0
                "mini" in lower -> 1
                else -> 2
            }
            ProviderKind.GEMINI -> when {
                "flash" in lower && "lite" in lower -> 0
                "flash" in lower -> 1
                "pro" in lower -> 2
                else -> 3
            }
            ProviderKind.MISTRAL -> when {
                "ministral" in lower || "tiny" in lower -> 0
                "nemo" in lower || "small" in lower -> 1
                "medium" in lower -> 2
                "large" in lower -> 3
                else -> 2
            }
            ProviderKind.XAI -> when {
                "mini" in lower -> 0
                "fast" in lower -> 1
                else -> 2
            }
            ProviderKind.DEEPSEEK -> if ("chat" in lower) 0 else 1
            ProviderKind.LOCAL, ProviderKind.OLLAMA -> 0
        }
    }

    internal fun isPreview(id: String): Boolean {
        val lower = id.lowercase()
        return "preview" in lower || "-exp" in lower || "latest" in lower
    }
}
