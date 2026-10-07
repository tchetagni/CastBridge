package castbridge.core.connect

import castbridge.core.device.DeviceClient
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** relay-R1 : `Routes` lit la vérité unique (quel chemin d'abord) et dit à la TV quand un appel réel échoue à travers le tuyau. */
class RoutesRelayTest {
    private val gw = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1080))

    private fun order(r: Routes, directWorks: Boolean = true): List<String> {
        val calls = ArrayList<String>()
        r.call { p -> calls += if (p == null) "direct" else "gw"; if (p == null && !directWorks) throw IOException("hors ligne") else "ok" }
        return calls
    }

    @Test fun theTruthSaysViaRelayThenThePipeIsTriedFirstWithoutWaitingForTheStickyTimer() {
        val r = Routes(gateway = { gw }, preferred = { Routes.Via.GATEWAY })
        assertEquals(listOf("gw"), order(r), "NetState = via_relay : on ne perd pas une tentative directe qui ne peut pas aboutir")
    }

    @Test fun theTruthSaysDirectOrNothingKeepsTheOldOrder() {
        assertEquals(listOf("direct"), order(Routes(gateway = { gw }, preferred = { Routes.Via.DIRECT })))
        assertEquals(listOf("direct"), order(Routes(gateway = { gw }, preferred = { null })))
        assertEquals(listOf("direct", "gw"), order(Routes(gateway = { gw }, preferred = { Routes.Via.DIRECT }), directWorks = false), "le repli par le téléphone existe toujours")
    }

    @Test fun aPreferenceForAMissingPipeIsIgnored() {
        assertEquals(listOf("direct"), order(Routes(gateway = { null }, preferred = { Routes.Via.GATEWAY })))
    }

    @Test fun aBrokenPreferenceNeverBreaksACall() {
        assertEquals(listOf("direct"), order(Routes(gateway = { gw }, preferred = { throw IllegalStateException("boum") })))
    }

    @Test fun thePipeFailureIsReportedOnlyForARealPipeFailure() {
        var reported = 0
        val r = Routes(gateway = { gw }, preferred = { Routes.Via.GATEWAY }, onGatewayFailure = { reported++ })
        assertFailsWith<IOException> { r.call<String> { p -> if (p != null) throw IOException("délai dépassé") else throw IOException("direct aussi") } }
        assertEquals(1, reported, "l'appel par le tuyau a échoué (le direct, essayé ensuite, n'est pas compté)")
        // une réponse du serveur n'est pas une panne du tuyau
        assertFailsWith<DeviceClient.ServerError> { r.call<String> { throw DeviceClient.ServerError(403, "bloqué") } }
        assertEquals(1, reported)
        // un échec direct seul n'est pas non plus une panne du tuyau
        val direct = Routes(gateway = { gw }, preferred = { Routes.Via.DIRECT }, onGatewayFailure = { reported++ })
        direct.call<String> { p -> if (p == null) throw IOException("hors ligne") else "ok" }
        assertEquals(1, reported)
    }

    @Test fun aResultThatMeansUnreachableThroughThePipeIsAPipeFailureToo() {
        var reported = 0
        val r = Routes(gateway = { gw }, preferred = { Routes.Via.GATEWAY }, onGatewayFailure = { reported++ })
        val out = r.call({ it == "injoignable" }) { p -> if (p != null) "injoignable" else "ok" }
        assertEquals("ok", out)
        assertEquals(1, reported)
    }

    @Test fun aListenerThatThrowsDoesNotBreakTheCall() {
        val r = Routes(gateway = { gw }, preferred = { Routes.Via.GATEWAY }, onGatewayFailure = { throw IllegalStateException("boum") })
        assertEquals("ok", r.call { p -> if (p != null) throw IOException("x") else "ok" })
    }

    @Test fun aFailedDirectCallIsReportedSoTheTvChecksItsNetworkAtOnce() {
        var direct = 0; var pipe = 0
        val r = Routes(gateway = { gw }, preferred = { Routes.Via.DIRECT }, onGatewayFailure = { pipe++ }, onDirectFailure = { direct++ })
        r.call<String> { p -> if (p == null) throw IOException("hors ligne") else "ok" }
        assertEquals(1, direct, "l'appel direct a échoué (le repli par le tuyau a réussi)")
        assertEquals(0, pipe)
        assertFailsWith<DeviceClient.ServerError> { r.call<String> { throw DeviceClient.ServerError(403, "bloqué") } }
        assertEquals(1, direct, "une réponse du serveur n'est pas une panne du réseau")
        assertEquals("ok", Routes(gateway = { gw }, preferred = { Routes.Via.DIRECT }, onDirectFailure = { throw IllegalStateException("boum") }).call { p -> if (p == null) throw IOException("x") else "ok" })
    }

    @Test fun lastViaStillTellsWhichPathWorked() {
        val r = Routes(gateway = { gw }, preferred = { Routes.Via.GATEWAY })
        r.call { "ok" }
        assertEquals(Routes.Via.GATEWAY, r.lastVia)
    }
}
