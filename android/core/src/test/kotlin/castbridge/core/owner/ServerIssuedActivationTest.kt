package castbridge.core.owner

import castbridge.core.net.JsonLite
import java.io.File
import kotlin.test.*

/**
 * End to end with the SERVER: tools/activation/server-issued.json holds activations, a revocation list and an order produced by the backend's own code (module `licenses`, Java, key
 * "server" of the test vectors, scopes of the server) by `ServerIssuedVectorsTest`. Here the REAL verifiers of the TV/phone ([ActivationVerifier], [RevocationNotice], [OrderVerifier])
 * must accept them, bit for bit, and refuse what the server key is not allowed to sign. The key is a TEST key (derived from a public text).
 */
class ServerIssuedActivationTest {
    private fun file(): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "tools/activation").isDirectory) d = d.parentFile
        return File(d ?: File("."), "tools/activation/server-issued.json")
    }

    @Suppress("UNCHECKED_CAST")
    private val root = JsonLite.obj(file().readText())
    @Suppress("UNCHECKED_CAST")
    private val server = root["server"] as Map<String, Any?>
    private val now = (root["nowMs"] as Number).toLong()
    private val serverScopes = (server["scopes"] as List<*>).map { KeyScope.valueOf(it as String) }.toSet()
    private val trusted = TrustedKey(server["kid"] as String, server["publicKey"] as String, serverScopes)
    private fun ring(revoked: Set<String> = emptySet()) = KeyRing(listOf(trusted), revoked)

    @Suppress("UNCHECKED_CAST")
    private val cases = (root["cases"] as List<Map<String, Any?>>)

    @Suppress("UNCHECKED_CAST")
    private fun fingerprints(c: Map<String, Any?>) = Fingerprints((c["fingerprints"] as Map<String, String>).mapKeys { FactorKind.valueOf(it.key) })

    @Test
    fun theKeyOfTheFileIsTheServerKeyWithTheServerScopesOnly() {
        assertEquals(KeyRing.idOf(server["publicKey"] as String), server["kid"])
        assertTrue(KeyScope.ISSUE_TRIAL in serverScopes && KeyScope.ISSUE_PRODUCTION in serverScopes && KeyScope.REVOKE in serverScopes && KeyScope.POLICY in serverScopes)
        assertFalse(KeyScope.TRANSFER in serverScopes, "the server key never transfers")
        assertFalse(KeyScope.COMMAND_OPEN_ALL in serverScopes, "the server key never opens everything")
    }

    @Test
    fun everyActivationIssuedByTheServerIsAcceptedByTheRealVerifier() {
        val acts = cases.filter { it["type"] == "activation" }
        assertTrue(acts.size >= 5)
        for (c in acts) {
            val id = c["id"] as String
            @Suppress("UNCHECKED_CAST") val e = c["expect"] as Map<String, Any?>
            val subject = Subject.valueOf((c["subject"] as String).uppercase())
            val r = ActivationVerifier(ring(), expect = subject).verify(c["token"] as String, fingerprints(c), now)
            assertTrue(r is ActivationResult.Accepted, "$id : $r")
            val a = (r as ActivationResult.Accepted).activation
            assertEquals(e["kind"], a.kind.name.lowercase(), id)
            assertEquals(e["license"], a.license, id)
            assertEquals(e["seat"], a.seat, id)
            assertEquals((e["rights"] as List<*>), a.rights.map { Activation.rightLine(it) }.sorted(), id)
            assertEquals(server["kid"], a.keyId, id)
            // the strict decoder rebuilds the very same text: nothing the issuer could write differently
            assertEquals(c["token"], a.encode(), id)
        }
    }

    @Test
    fun theSeatOfAReplacedModuleIsTheSameAndASecondTokenOfTheSameKeyIsNotStale() {
        val byId = cases.associateBy { it["id"] }
        assertEquals((byId["server-production-purchase"]!!["expect"] as Map<*, *>)["seat"], (byId["server-reissue-module-replaced"]!!["expect"] as Map<*, *>)["seat"])
        // the same file installed twice (reinstalled app) and then another activation of the same key: sequence numbers never go back
        val seq = SeqState()
        val v = ActivationVerifier(ring(), seqState = seq)
        val p = byId["server-production-purchase"]!!
        assertTrue(v.verify(p["token"] as String, fingerprints(p), now) is ActivationResult.Accepted)
        assertTrue(v.verify(p["token"] as String, fingerprints(p), now) is ActivationResult.Accepted, "same file: accepted again")
        val s = byId["server-production-subscription"]!!
        assertTrue(v.verify(s["token"] as String, fingerprints(s), now) is ActivationResult.Accepted)
        assertEquals(Envelope.decode(s["token"] as String)!!.seq, seq.last(server["kid"] as String))
    }

    @Test
    fun theRevocationListAndTheOrderOfTheServerAreAccepted() {
        val rev = cases.first { it["type"] == "revocation" }
        val state = assertNotNull(RevocationNotice.verify(rev["token"] as String, ring()))
        @Suppress("UNCHECKED_CAST") val e = rev["expect"] as Map<String, Any?>
        assertEquals((e["keys"] as List<*>).toSet(), state.keys)
        @Suppress("UNCHECKED_CAST") assertEquals((e["seats"] as Map<String, Number>).mapValues { it.value.toLong() }, state.seats)

        val ord = cases.first { it["type"] == "order" }
        val r = OrderVerifier(ring(), SeqState()).verify(ord["token"] as String, DeviceContext(fingerprints(ord)), now)
        assertTrue(r is OrderResult.Accepted, "$r")
        val accepted = r as OrderResult.Accepted
        @Suppress("UNCHECKED_CAST") val oe = ord["expect"] as Map<String, Any?>
        assertEquals(oe["action"], accepted.order.action)
        assertEquals(oe["params"], accepted.order.params)
        assertEquals((oe["seq"] as Number).toLong(), accepted.envelope.seq)
    }

    @Test
    fun aRingThatDoesNotGiveTheServerTheScopeRefusesItsMessages() {
        val trial = cases.first { it["id"] == "server-trial" }
        val noTrial = KeyRing(listOf(TrustedKey(server["kid"] as String, server["publicKey"] as String, serverScopes - KeyScope.ISSUE_TRIAL)))
        val r = ActivationVerifier(noTrial).verify(trial["token"] as String, fingerprints(trial), now)
        assertTrue(r is ActivationResult.Rejected && r.reason == Rejection.KEY_NOT_ALLOWED, "$r")
        val ord = cases.first { it["type"] == "order" }
        val noPolicy = KeyRing(listOf(TrustedKey(server["kid"] as String, server["publicKey"] as String, serverScopes - KeyScope.POLICY)))
        val o = OrderVerifier(noPolicy, SeqState()).verify(ord["token"] as String, DeviceContext(fingerprints(ord)), now)
        assertTrue(o is OrderResult.Rejected && o.reason == Rejection.KEY_NOT_ALLOWED, "$o")
        // a revoked server key: everything it signed is refused
        val p = cases.first { it["id"] == "server-production-purchase" }
        val revoked = ActivationVerifier(ring(setOf(server["kid"] as String))).verify(p["token"] as String, fingerprints(p), now)
        assertTrue(revoked is ActivationResult.Rejected && revoked.reason == Rejection.REVOKED_KEY)
    }
}
