package dev.mjablonski.rewordme.android.features.floating.presentation

/**
 * The icon lives in an accessibility service, its popup in an activity; this
 * in-process seam carries the popup's outcome back to the service so it can
 * write the rewrite into the host field. The service hands each tap a random
 * token and accepts only that token, so no other caller of the (exported)
 * popup can make it edit someone's field.
 */
class FloatingHandoff {

    fun interface Receiver {
        /** True when the service owned [token] and took the outcome. */
        fun finished(token: String, replacement: String?): Boolean
    }

    @Volatile
    private var receiver: Receiver? = null

    fun attach(receiver: Receiver) {
        this.receiver = receiver
    }

    fun detach(receiver: Receiver) {
        if (this.receiver === receiver) this.receiver = null
    }

    /**
     * Reports how the popup ended: [replacement] is the text to put back, or
     * null when it closed without one. False when no service took it - the
     * service was switched off meanwhile - so the caller falls back to Copy.
     */
    fun finish(token: String, replacement: String?): Boolean =
        receiver?.finished(token, replacement) ?: false
}
