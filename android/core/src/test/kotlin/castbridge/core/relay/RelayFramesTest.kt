package castbridge.core.relay

import castbridge.core.owner.ActivationResult
import castbridge.core.owner.OwnerChannelClient
import castbridge.core.owner.OwnerChannelServer
import castbridge.core.owner.OwnerFrames
import castbridge.core.owner.Rejection
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** relay-R1 § 2 : les trames du canal propriétaire (service …0005, types libres 11-15) pour la demande de tuyau et l'état du relais. */
class RelayFramesTest {
    // ------------------------------------------------------------------ codec

    @Test fun typesAreInTheFreeRangeOfTheOwnerChannel() {
        assertEquals(11, OwnerFrames.RELAY_ASK_PIPE)
        assertEquals(12, OwnerFrames.RELAY_STATE)
        val used = listOf(OwnerFrames.CHALLENGE_REQUEST, OwnerFrames.CHALLENGE, OwnerFrames.COMMAND, OwnerFrames.RESULT, OwnerFrames.DEVICE_INFO_REQUEST, OwnerFrames.DEVICE_INFO,
            OwnerFrames.PAIR, OwnerFrames.ACTIVATION, OwnerFrames.PROOF_REQUEST, OwnerFrames.PROOF)
        assertTrue(OwnerFrames.RELAY_ASK_PIPE !in used && OwnerFrames.RELAY_STATE !in used, "aucun type existant n'est réutilisé")
        assertTrue(OwnerFrames.RELAY_ASK_PIPE in 11..15 && OwnerFrames.RELAY_STATE in 11..15)
    }

    @Test fun anAskRoundTrips() {
        val a = RelayFrames.Ask(listOf(PipeNeed.PLAY, PipeNeed.UPDATE_NOW), ttlSec = 90)
        assertEquals(a, RelayFrames.decodeAsk(RelayFrames.encodeAsk(a)))
        val text = String(RelayFrames.encodeAsk(a), Charsets.UTF_8)
        assertTrue(text.lines().contains("v=1") && text.lines().contains("need=play,update") && text.lines().contains("ttl=90"), text)
    }

    @Test fun anAskToleratesWhatFutureVersionsAdd() {
        val d = RelayFrames.decodeAsk("v=2\nneed=play,nouveau,assist\nttl=60\nplus=tard\n".toByteArray())
        assertEquals(RelayFrames.Ask(listOf(PipeNeed.PLAY, PipeNeed.ASSIST), 60), d, "besoin inconnu ignoré, clé inconnue ignorée")
        assertEquals(listOf(PipeNeed.PLAY), RelayFrames.decodeAsk("v=1\nneed=nouveau\nttl=60".toByteArray())!!.needs, "un besoin inconnu seul : traité comme petit (le plafond d'octets reste)")
        assertEquals(RelayFrames.DEFAULT_TTL_SEC, RelayFrames.decodeAsk("v=1\nneed=play".toByteArray())!!.ttlSec)
        assertEquals(RelayFrames.MAX_TTL_SEC, RelayFrames.decodeAsk("v=1\nneed=play\nttl=99999".toByteArray())!!.ttlSec)
        assertEquals(RelayFrames.MIN_TTL_SEC, RelayFrames.decodeAsk("v=1\nneed=play\nttl=0".toByteArray())!!.ttlSec)
        assertEquals(RelayFrames.DEFAULT_TTL_SEC, RelayFrames.decodeAsk("v=1\nneed=play\nttl=abc".toByteArray())!!.ttlSec)
    }

    @Test fun garbageIsNotAnAsk() {
        assertNull(RelayFrames.decodeAsk(ByteArray(0)))
        assertNull(RelayFrames.decodeAsk("n'importe quoi".toByteArray()))
        assertNull(RelayFrames.decodeAsk(ByteArray(5000) { 'a'.code.toByte() }), "plus grand qu'une trame")
        assertNull(RelayFrames.decodeAsk(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 1)))
    }

    @Test fun aStateRoundTrips() {
        for (s in listOf(
            RelayFrames.State(RelayFrames.Phase.IDLE),
            RelayFrames.State(RelayFrames.Phase.OPENING),
            RelayFrames.State(RelayFrames.Phase.OPEN, metered = false),
            RelayFrames.State(RelayFrames.Phase.REFUSED, RelayReason.CAP, metered = true, leftKb = 0),
            RelayFrames.State(RelayFrames.Phase.REFUSED, RelayReason.OPTED_OUT),
        )) assertEquals(s, RelayFrames.decodeState(RelayFrames.encodeState(s)), s.toString())
    }

    @Test fun aStateKeepsAnUnknownReasonReadable() {
        val d = RelayFrames.decodeState("v=1\nstate=refused\nwhy=raison_future\n".toByteArray())!!
        assertEquals(RelayFrames.Phase.REFUSED, d.phase)
        assertNull(d.reason, "motif inconnu : refus sans motif connu, jamais une erreur")
        assertNull(RelayFrames.decodeState("v=1\nstate=inconnu\n".toByteArray()), "une phase inconnue n'est pas un état")
        assertNull(RelayFrames.decodeState(ByteArray(0)))
        assertNull(RelayFrames.decodeState("pas un état".toByteArray()))
    }

    @Test fun framesContainNoNameNoCodeNoAddress() {
        val all = String(RelayFrames.encodeAsk(RelayFrames.Ask(PipeNeed.values().toList(), 120)), Charsets.UTF_8) +
            String(RelayFrames.encodeState(RelayFrames.State(RelayFrames.Phase.REFUSED, RelayReason.CAP, true, 12)), Charsets.UTF_8)
        assertTrue(Regex("^[a-z0-9_=,\\n]*$").matches(all), all)
    }

    // ------------------------------------------------------------------ canal propriétaire, bout en bout (tuyaux en mémoire)

    private class Host(val wants: Boolean = true) : RelayChannelHost {
        val seen = ArrayList<Pair<String?, RelayFrames.State>>()
        var trusted = true
        override fun isTrusted(peer: String?) = trusted
        override fun onPhoneState(peer: String?, state: RelayFrames.State): RelayFrames.Ask? { seen += peer to state; return if (wants) RelayFrames.Ask(listOf(PipeNeed.PLAY), 90) else null }
    }

    private class Link(server: OwnerChannelServer, peer: String? = "AA:BB:CC:DD:EE:01") {
        val c2s = PipedOutputStream(); val s2c = PipedOutputStream()
        val serverIn = PipedInputStream(c2s, 1 shl 16); val clientIn = PipedInputStream(s2c, 1 shl 16)
        val t = thread { runCatching { server.serve(serverIn, s2c, peer) }; runCatching { s2c.close() } }
        val client = OwnerChannelClient(clientIn, c2s)
        fun close() { runCatching { c2s.close() }; t.join(3000) }
    }

    private fun server(host: RelayChannelHost?) = OwnerChannelServer(
        deviceInfo = { "code=X" }, activate = { ActivationResult.Rejected(Rejection.MALFORMED, "non") }, relay = host,
    )

    @Test fun aTrustedPhonePollsAndLearnsThatTheTvWantsAPipe() {
        val host = Host(wants = true); val l = Link(server(host))
        assertTrue(l.client.hello())
        val a = l.client.relayState(RelayFrames.State(RelayFrames.Phase.IDLE))
        assertEquals(RelayAnswer.Wanted(RelayFrames.Ask(listOf(PipeNeed.PLAY), 90)), a)
        assertEquals(listOf<Pair<String?, RelayFrames.State>>("AA:BB:CC:DD:EE:01" to RelayFrames.State(RelayFrames.Phase.IDLE)), host.seen, "la TV sait QUEL téléphone parle (l'adresse du socket, pas une déclaration)")
        l.close()
    }

    @Test fun whenTheTvWantsNothingTheAnswerIsPlainOk() {
        val l = Link(server(Host(wants = false))); l.client.hello()
        assertEquals(RelayAnswer.NotWanted, l.client.relayState(RelayFrames.State(RelayFrames.Phase.OPEN, metered = false)))
        l.close()
    }

    @Test fun aRefusalIsReportedToTheTvAndNotWantedAnymoreIsFine() {
        val host = Host(wants = false); val l = Link(server(host)); l.client.hello()
        l.client.relayState(RelayFrames.State(RelayFrames.Phase.REFUSED, RelayReason.OPTED_OUT))
        assertEquals(RelayReason.OPTED_OUT, host.seen.single().second.reason)
        l.close()
    }

    @Test fun anOldTvAnswersNotSupportedAndThePhoneDoesNothing() {
        val l = Link(server(null)); l.client.hello()
        assertEquals(RelayAnswer.Unsupported, l.client.relayState(RelayFrames.State(RelayFrames.Phase.IDLE)), "TV ancienne : « Non pris en charge », comportement actuel (manuel)")
        l.close()
    }

    @Test fun aPeerThatIsNotTrustedLearnsNothingAndTheTvHearsNothing() {
        val host = Host(wants = true).apply { trusted = false }; val l = Link(server(host)); l.client.hello()
        assertEquals(RelayAnswer.Unsupported, l.client.relayState(RelayFrames.State(RelayFrames.Phase.IDLE)), "même réponse qu'une TV qui ne sait pas : on n'apprend pas qu'elle veut un tuyau")
        assertTrue(host.seen.isEmpty())
        l.close()
    }

    @Test fun anUnreadableStateIsRefusedWithoutReachingTheHost() {
        val host = Host(); val l = Link(server(host)); l.client.hello()
        l.c2s.write(OwnerFrames.encode(OwnerFrames.RELAY_STATE, "pas un état")); l.c2s.flush()
        val f = OwnerFrames.read(l.clientIn)
        assertNotNull(f); assertEquals(OwnerFrames.RESULT, f.type); assertEquals(0, f.payload[0].toInt())
        assertTrue(host.seen.isEmpty())
        l.close()
    }

    @Test fun relayFramesShareTheLinkWithTheOwnerFrames() {
        val l = Link(server(Host())); l.client.hello()
        assertEquals("code=X", l.client.deviceInfo())
        assertTrue(l.client.relayState(RelayFrames.State(RelayFrames.Phase.IDLE)) is RelayAnswer.Wanted)
        assertEquals("code=X", l.client.deviceInfo(), "la demande de tuyau ne perturbe pas le canal d'activation")
        l.close()
    }

    @Test fun theChannelStillClosesAfterItsFrameBudget() {
        val l = Link(server(Host())); l.client.hello()
        var last: RelayAnswer? = null
        repeat(OwnerChannelServer.MAX_FRAMES + 3) { last = l.client.relayState(RelayFrames.State(RelayFrames.Phase.IDLE)) }
        assertEquals(RelayAnswer.LinkLost, last, "un lien ne sert qu'un nombre borné de trames : le téléphone rouvre un lien neuf")
        l.close()
    }
}
