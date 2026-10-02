package castbridge.core.tunnel

import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.KeyScope
import castbridge.core.tunnel.ExpertsList.Expert
import java.nio.ByteBuffer
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExpertsListTest {
    private val signer = Ed25519Signer(ByteArray(32) { (it + 7).toByte() })       // throwaway test key
    private val trusted = signer.trusted(KeyScope.ALL)
    private val k1 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8g alice@laptop"
    private val k2 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj9A"
    private val now = 1_800_000_000_000L

    private fun blob(type: String, len: Int): String {
        val t = type.toByteArray(); val b = ByteBuffer.allocate(8 + t.size + len).putInt(t.size).put(t).putInt(len).put(ByteArray(len)).array()
        return "$type ${Base64.getEncoder().encodeToString(b)}"
    }

    private fun list() = listOf(Expert("bob", k2, 0L), Expert("alice", k1, now + 1000))

    @Test fun signedTextKnownAnswer() {
        val l = ExpertsList(1_800_000_000_000L, "kid", list())
        assertEquals("castbridge-experts-v1\ngeneratedAt=1800000000000\n" +
            "expert=alice|$k1|1800000001000\nexpert=bob|$k2|0", l.signedText())
    }

    @Test fun signVerifyRoundTripAndTamper() {
        val s = ExpertsList.sign(signer, signer.keyId, list(), now)
        val json = s.toJson()
        val v = ExpertsList.verify(json, listOf(trusted)).list
        assertEquals(listOf("alice", "bob"), v.experts.map { it.id }); assertEquals(now, v.generatedAt)
        assertTrue(assertFailsWith<ExpertsList.Refused> { ExpertsList.verify(json.replace("\"notAfter\":0", "\"notAfter\":5"), listOf(trusted)) }.message!!.contains("Signature"))
        assertTrue(assertFailsWith<ExpertsList.Refused> { ExpertsList.verify(json.replace("alice", "mallory"), listOf(trusted)) }.message!!.contains("Signature"))
        assertTrue(assertFailsWith<ExpertsList.Refused> { ExpertsList.verify(s.copy(signature = "").toJson(), listOf(trusted)) }.message!!.contains("non signée"))
        val other = Ed25519Signer(ByteArray(32) { 1 })
        assertTrue(assertFailsWith<ExpertsList.Refused> { ExpertsList.verify(json, listOf(other.trusted())) }.message!!.contains("inconnue"))
        val forged = ExpertsList.sign(other, signer.keyId, list(), now).toJson()      // claims the desk kid, signed by another key
        assertTrue(assertFailsWith<ExpertsList.Refused> { ExpertsList.verify(forged, listOf(trusted)) }.message!!.contains("Signature"))
    }

    @Test fun needsRegistryScopeAndRefusesOlder() {
        val json = ExpertsList.sign(signer, signer.keyId, list(), now).toJson()
        val noReg = signer.trusted(KeyScope.ALL - KeyScope.REGISTRY)
        assertTrue(assertFailsWith<ExpertsList.Refused> { ExpertsList.verify(json, listOf(noReg)) }.message!!.contains("REGISTRY"))
        ExpertsList.verify(json, listOf(trusted), notOlderThan = now)
        assertTrue(assertFailsWith<ExpertsList.Refused> { ExpertsList.verify(json, listOf(trusted), notOlderThan = now + 1) }.message!!.contains("plus ancienne"))
    }

    @Test fun authorizedKeysOnlyNonExpiredAndRestricted() {
        val l = ExpertsList(now, "kid", list())
        assertEquals(listOf("restrict,pty ssh-ed25519 ${k1.split(' ')[1]} alice", "restrict,pty ssh-ed25519 ${k2.split(' ')[1]} bob"), l.authorizedKeysLines(now))
        assertEquals(listOf("restrict,pty ssh-ed25519 ${k2.split(' ')[1]} bob"), l.authorizedKeysLines(now + 1000))
        assertTrue(l.authorizedKeysLines(now).none { "forward" in it || "port" in it })
    }

    @Test fun refusesBadKeysIdsDuplicatesAndTooMany() {
        listOf(blob("ssh-rsa", 256), blob("ecdsa-sha2-nistp256", 65), blob("ssh-dss", 100), "ssh-ed25519 pas-du-base64!", "ssh-ed25519", blob("ssh-ed25519", 31),
            "$k2\nssh-ed25519 AAAA", "$k2|x", " $k2", "$k2  x").forEach { k -> assertFailsWith<ExpertsList.Refused>(k) { Expert("ok", k) } }
        listOf("", "-a", "A", "a_b", "a".repeat(33), "é").forEach { id -> assertFailsWith<ExpertsList.Refused>(id) { Expert(id, k2) } }
        Expert("a".repeat(32), k2); Expert("0-9", k2)
        assertFailsWith<ExpertsList.Refused> { ExpertsList.sign(signer, "k", listOf(Expert("a", k1), Expert("a", k2)), now) }
        assertFailsWith<ExpertsList.Refused> { ExpertsList.sign(signer, "k", listOf(Expert("a", k1), Expert("b", k1)), now) }
        val many = (0..50).map { i -> Expert("e$i", "ssh-ed25519 " + Base64.getEncoder().encodeToString(ByteBuffer.allocate(51).putInt(11).put("ssh-ed25519".toByteArray()).putInt(32).put(ByteArray(28)).putInt(i).array())) }
        assertFailsWith<ExpertsList.Refused> { ExpertsList.sign(signer, "k", many, now) }
        ExpertsList.sign(signer, "k", many.take(50), now)
    }
}
