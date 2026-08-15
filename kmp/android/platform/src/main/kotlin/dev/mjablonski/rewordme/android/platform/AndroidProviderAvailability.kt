package dev.mjablonski.rewordme.android.platform

import dev.mjablonski.rewordme.domain.ProviderAvailability
import dev.mjablonski.rewordme.models.ProviderAccess
import dev.mjablonski.rewordme.models.ProviderKind

/**
 * Android can serve every plain-HTTP provider, including Ollama (it is just a
 * server address, usually another machine on the LAN). Account CLIs and the
 * managed llama.cpp runtime need locally installed desktop software, so they
 * stay desktop-only.
 */
class AndroidProviderAvailability : ProviderAvailability {
    override fun isAvailable(provider: ProviderKind): Boolean = when (provider.access) {
        ProviderAccess.API_KEY, ProviderAccess.EXTERNAL_LOCAL -> true
        ProviderAccess.ACCOUNT, ProviderAccess.MANAGED_LOCAL -> false
    }
}
