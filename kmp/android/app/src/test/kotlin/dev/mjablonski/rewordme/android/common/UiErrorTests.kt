package dev.mjablonski.rewordme.android.common

import dev.mjablonski.rewordme.models.RewordError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals

class UiErrorTests {

    @Test
    fun slowProvidersReadAsTimeouts() {
        assertEquals(
            UiError.Network(timedOut = true),
            UiError.of(HttpRequestTimeoutException("https://provider/v1", 180_000L))
        )
        assertEquals(UiError.Network(timedOut = true), UiError.of(SocketTimeoutException()))
    }

    @Test
    fun unreachableHostsPointToSettings() {
        val connect = UiError.of(ConnectTimeoutException("connect timed out"))
        assertEquals(UiError.Network(timedOut = false), connect)
        assertEquals(true, connect.needsSettings)
        assertEquals(UiError.Network(timedOut = false), UiError.of(UnknownHostException("offline")))
    }

    @Test
    fun wrappedTransportFailuresAreStillRecognized() {
        val wrapped = IllegalStateException("engine failed", SocketTimeoutException("read"))
        assertEquals(UiError.Network(timedOut = true), UiError.of(wrapped))
    }

    @Test
    fun typedAndUnknownFailuresKeepTheirOwnShape() {
        assertEquals(UiError.Known(RewordError.InvalidApiKey), UiError.of(RewordError.InvalidApiKey))
        assertEquals(UiError.Raw("boom"), UiError.of(IllegalArgumentException("boom")))
    }
}
