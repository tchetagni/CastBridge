package castbridge.core.tv

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * R-28 (relay-R4, inventory I-8): the sshd of the remote-assistance tunnel (loopback) and the SSH of the development app « CastBridge Dev » (all interfaces) were both on 2223 on
 * the development TV: whoever started second failed to listen. Every port the TV listens on is read from the sources and must be distinct (the apps share the device's ports).
 */
class TvPortsTest {
    private fun source(path: String) = File("../$path").takeIf { it.isFile }?.readText() ?: error("source not found: $path (from ${File(".").absolutePath})")
    private fun port(path: String, name: String): Int =
        Regex("const val ${Regex.escape(name)}\\s*=\\s*(\\d+)").find(source(path))?.groupValues?.get(1)?.toInt() ?: error("$name not found in $path")

    /** Every TCP port that a CastBridge app listens on, on the TV itself. */
    private fun tvPorts(): Map<String, Int> = mapOf(
        "user SSH (SshControl, TvSshServer.DEFAULT_PORT)" to port("sshd/src/main/kotlin/castbridge/sshd/TvSshServer.kt", "DEFAULT_PORT"),
        "remote-assistance tunnel sshd (TunnelHub.LOCAL_PORT)" to port("receiver/src/main/kotlin/castbridge/receiver/TunnelHub.kt", "LOCAL_PORT"),
        "CastBridge Dev SSH (DevService.PORT)" to port("devbridge/src/main/kotlin/castbridge/dev/DevService.kt", "PORT"),
        "HTTP API (ReceiverServer.PORT)" to ReceiverServer.PORT,
        "gateway SOCKS5 (BtGatewayHost.PORT)" to port("receiver/src/main/kotlin/castbridge/receiver/BtGatewayHost.kt", "PORT"),
    )

    @Test fun noTwoListenersOfTheTvShareAPort() {
        val clashes = tvPorts().entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }
        assertEquals(emptyMap(), clashes, "two listeners on the same port of the TV: $clashes")
    }

    @Test fun theTunnelSshdKeepsOutOfTheNeighbouringPortsOfTheOtherSshServers() {
        val p = tvPorts()
        val tunnel = p.entries.first { "tunnel" in it.key }.value
        assertTrue(tunnel !in setOf(2222, 2223), "the tunnel sshd is on its own port, not the user's SSH (2222) nor CastBridge Dev (2223): $tunnel")
        assertEquals(2223, p.entries.first { "Dev" in it.key }.value, "CastBridge Dev keeps its documented port (docs/DEV-BRIDGE.md, ssh -p 2223)")
    }
}
