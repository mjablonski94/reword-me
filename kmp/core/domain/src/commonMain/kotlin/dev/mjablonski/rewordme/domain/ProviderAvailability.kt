package dev.mjablonski.rewordme.domain

import dev.mjablonski.rewordme.models.ProviderKind

/**
 * Which providers a platform can actually serve. Account CLIs and the managed
 * llama.cpp runtime need to spawn OS processes, so mobile platforms exclude
 * them; the settings screen only offers what this reports.
 */
interface ProviderAvailability {
    fun isAvailable(provider: ProviderKind): Boolean

    val available: List<ProviderKind>
        get() = ProviderKind.entries.filter(::isAvailable)
}
