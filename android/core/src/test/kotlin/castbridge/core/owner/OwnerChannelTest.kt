package castbridge.core.owner

import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The owner Bluetooth channel end to end (console <-> TV) over in-memory pipes, with the REAL issuer and verifier: nothing mocked on the cryptography. */
class OwnerChannelTest {
    private val now = 1_800_000_000_000L
    private val fp = Fingerprints(mapOf(FactorKind.FLASH to "a".repeat(32), FactorKind.ETHERNET to "b".repeat(32), FactorKind.SYSTEM_SERIAL to "c".repeat(32)))
    private val signer = Ed25519Signer(ByteArray(32) { (it + 7).toByte() })
    private val ring = KeyRing(listOf(signer.trusted()))
    private val receiver = ActivationReceiver(ring, listOf(signer.trusted()), fp)

    private class Link(server: OwnerChannelServer) {
        val c2s = PipedOutputStream(); val s2c = PipedOutputStream()
        val serverIn = PipedInputStream(c2s, 1 shl 16); val clientIn = PipedInputStream(s2c, 1 shl 16)
        val t = thread { runCatching { server.serve(serverIn, s2c) }; runCatching { s2c.close() } }
        val client = OwnerChannelClient(clientIn, c2s)
        fun close() { runCatching { c2s.close() }; t.join(3000) }
    }

    private fun server(accepted: MutableList<String> = ArrayList(), refusals: IntArray = IntArray(1)) = OwnerChannelServer(
        deviceInfo = { OwnerFrames.deviceInfo(DeviceCode.of(fp), fp) },
        activate = { token -> receiver.receive(Channel.MANUAL, token.toByteArray(), now).also { if (it is ActivationResult.Accepted) accepted += token } },
        onRefusal = { refusals[0]++ },
    )

    @Test fun theConsoleReadsTheDeviceRequestAndTheTvActivatesWithTheTokenSent() {
        val accepted = ArrayList<String>(); val l = Link(server(accepted))
        assertTrue(l.client.hello())
        val info = assertNotNull(l.client.deviceInfo())
        val (code, _, parsed) = assertNotNull(OwnerFrames.parseDeviceInfo(info))
        assertEquals(DeviceCode.of(fp), code); assertEquals(fp, parsed)                  // what the console needs to build a FULL activation
        val token = ActivationIssuer(signer).issue(ActivationIssuer.Request(ActivationKind.TRIAL, code, parsed, issuedAt = now, windowDays = 30)).token
        val a = l.client.sendActivation(token)
        assertTrue(a.ok, a.message); assertEquals(listOf(token), accepted)
        l.close()
    }

    @Test fun aCompactKeyTypedOnThePhoneIsAcceptedToo() {
        val l = Link(server()); l.client.hello()
        val key = ActivationIssuer(signer).issueCompact(ActivationKind.TRIAL, DeviceCode.of(fp), ((now - CompactActivation.EPOCH_MS) / 86_400_000L).toInt(), 30)
        assertTrue(l.client.sendActivation(key).ok); l.close()
    }

    @Test fun anActivationForAnotherTvIsRefusedWithAReasonAndHangsUpAfterThreeRefusals() {
        val refusals = IntArray(1); val l = Link(server(refusals = refusals)); l.client.hello()
        val other = Fingerprints(mapOf(FactorKind.FLASH to "d".repeat(32), FactorKind.ETHERNET to "e".repeat(32), FactorKind.SYSTEM_SERIAL to "f".repeat(32)))
        val token = ActivationIssuer(signer).issue(ActivationIssuer.Request(ActivationKind.TRIAL, DeviceCode.of(other), other, issuedAt = now, windowDays = 30)).token
        val a = l.client.sendActivation(token); assertFalse(a.ok); assertTrue(a.message.isNotBlank())
        l.client.sendActivation(token); l.client.sendActivation(token)
        assertEquals(3, refusals[0])
        assertFalse(l.client.sendActivation(token).ok, "the TV hung up after three refusals on one link")
        l.close()
    }

    @Test fun aForgedOrGarbageTokenNeverActivates() {
        val accepted = ArrayList<String>(); val l = Link(server(accepted)); l.client.hello()
        assertFalse(l.client.sendActivation("cbx1.AAAA.BBBB-pas-un-vrai-jeton").ok)
        assertFalse(l.client.sendActivation("n'importe quoi de plus de vingt caractères").ok)
        assertTrue(accepted.isEmpty()); l.close()
    }

    @Test fun anotherProtocolOnTheSameServiceIsDroppedAtOnce() {
        val s2c = PipedOutputStream(); val sink = java.io.ByteArrayOutputStream()
        server().serve(java.io.ByteArrayInputStream("GET / HTTP/1.1\r\n".toByteArray()), sink)
        assertEquals(0, sink.size(), "no answer to a peer that does not speak CBTO"); s2c.close()
    }

    @Test fun unknownFramesAreAnsweredNotExecuted() {
        val l = Link(server()); l.client.hello()
        l.c2s.write(OwnerFrames.encode(OwnerFrames.COMMAND, "cbo1.pas-implemente")); l.c2s.flush()
        val f = assertNotNull(OwnerFrames.read(l.clientIn)); assertEquals(OwnerFrames.RESULT, f.type); assertEquals(0, f.payload[0].toInt())
        l.close()
    }

    @Test fun theChannelUuidDiffersFromEveryOtherService() {
        val others = listOf(castbridge.core.tv.BtProtocol.SERVICE_UUID, castbridge.core.tv.BtProtocol.SSH_SERVICE_UUID, castbridge.core.tv.BtProtocol.API_SERVICE_UUID, castbridge.core.tv.BtProtocol.API_MUX_SERVICE_UUID)
        assertTrue(OwnerFrames.SERVICE_UUID !in others, "two services with the same UUID: a phone would reach the wrong one")
    }
}
