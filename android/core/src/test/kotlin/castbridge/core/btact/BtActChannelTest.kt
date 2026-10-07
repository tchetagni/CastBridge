package castbridge.core.btact

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import kotlin.test.*

/** The encrypted frames of « activer par Bluetooth sans appairage » on their own: AES-256-GCM, a counter for a nonce, one key per direction, the length authenticated. */
class BtActChannelTest {
    private val keyA = ByteArray(32) { (it + 1).toByte() }
    private val keyB = ByteArray(32) { (it + 101).toByte() }

    private class Wire { val bytes = ByteArrayOutputStream() }

    private fun writer(w: Wire, send: ByteArray, receive: ByteArray) = BtActChannel(DataInputStream(ByteArrayInputStream(ByteArray(0))), DataOutputStream(w.bytes), send, receive)
    private fun reader(bytes: ByteArray, send: ByteArray, receive: ByteArray) = BtActChannel(DataInputStream(ByteArrayInputStream(bytes)), DataOutputStream(ByteArrayOutputStream()), send, receive)

    @Test fun framesTravelInOrderAndComeOutUnchanged() {
        val w = Wire(); val a = writer(w, keyA, keyB)
        a.send(0x12, "bonjour".toByteArray()); a.send(0x13); a.send(0x14, ByteArray(BtActWire.MAX_PAYLOAD) { it.toByte() })
        val b = reader(w.bytes.toByteArray(), keyB, keyA)
        b.receive().let { assertEquals(0x12, it.type); assertEquals("bonjour", String(it.payload)) }
        b.receive().let { assertEquals(0x13, it.type); assertEquals(0, it.payload.size) }
        b.receive().let { assertEquals(0x14, it.type); assertContentEquals(ByteArray(BtActWire.MAX_PAYLOAD) { i -> i.toByte() }, it.payload) }
        assertFailsWith<EOFException> { b.receive() }
    }

    @Test fun theNonceIsNeverSentAndTwoEqualFramesLookDifferent() {
        val w = Wire(); val a = writer(w, keyA, keyB)
        a.send(0x12, "x".toByteArray()); a.send(0x12, "x".toByteArray())
        val all = w.bytes.toByteArray()
        val one = (2 + 1 + 1 + BtActWire.GCM_TAG_BYTES)
        assertEquals(2 * one, all.size, "u16 longueur + type + octet + étiquette : aucun nonce ni compteur sur le lien")
        assertFalse(all.copyOfRange(0, one).contentEquals(all.copyOfRange(one, 2 * one)), "même contenu, même clé, autre compteur : autre texte chiffré")
    }

    @Test fun aFrameReplayedDuplicatedDroppedOrSwappedDoesNotAuthenticate() {
        val w = Wire(); val c = writer(w, keyA, keyB)
        val frames = (0 until 3).map { i -> val before = w.bytes.size(); c.send(0x21, byteArrayOf(i.toByte())); w.bytes.toByteArray().copyOfRange(before, w.bytes.size()) }      // counters 0, 1, 2
        fun tryRead(vararg order: Int): List<Int> {
            val r = reader(order.fold(ByteArray(0)) { acc, i -> acc + frames[i] }, keyB, keyA)
            val got = ArrayList<Int>()
            try { repeat(order.size) { got += r.receive().payload[0].toInt() } } catch (e: java.io.IOException) { }
            return got
        }
        assertEquals(listOf(0, 1, 2), tryRead(0, 1, 2), "dans l'ordre, tout passe")
        assertEquals(listOf(0), tryRead(0, 0), "la même trame deux fois : la seconde est refusée")
        assertEquals(emptyList(), tryRead(1), "une trame qui saute le compteur 0")
        assertEquals(emptyList(), tryRead(1, 0), "deux trames échangées")
        assertEquals(listOf(0, 1), tryRead(0, 1, 1), "rejouée après coup")
        assertEquals(listOf(0), tryRead(0, 2), "un trou de compteur : refusée")
    }

    @Test fun aBitFlippedAnywhereInAFrameIsRefused() {
        val w = Wire(); writer(w, keyA, keyB).send(0x14, "la clé".toByteArray())
        val good = w.bytes.toByteArray()
        for (i in good.indices) for (bit in 0..7) {
            val bad = good.copyOf().also { it[i] = (it[i].toInt() xor (1 shl bit)).toByte() }
            assertFailsWith<java.io.IOException>("octet $i bit $bit") { reader(bad, keyB, keyA).receive() }
        }
    }

    @Test fun aFrameCutAnywhereIsAnEndOfStreamNeverAHalfMessage() {
        val w = Wire(); writer(w, keyA, keyB).send(0x14, "la clé".toByteArray())
        val good = w.bytes.toByteArray()
        for (n in 0 until good.size) assertFailsWith<java.io.IOException>("n=$n") { reader(good.copyOf(n), keyB, keyA).receive() }
        assertEquals(0x14, reader(good, keyB, keyA).receive().type)
    }

    @Test fun eachDirectionHasItsOwnKeyAndAFrameReflectedBackIsRefused() {
        val w = Wire(); writer(w, keyA, keyB).send(0x12, "x".toByteArray())
        // the sender's own frame handed back to the sender as if it came from the peer: the receive key is the other one
        assertFailsWith<BtActWire.ProtocolException> { reader(w.bytes.toByteArray(), keyA, keyB).receive() }
        // a frame under a third key
        assertFailsWith<BtActWire.ProtocolException> { reader(w.bytes.toByteArray(), keyB, ByteArray(32)).receive() }
    }

    @Test fun theLengthIsAuthenticatedAndBoundsAreChecked() {
        val w = Wire(); writer(w, keyA, keyB).send(0x12, "x".toByteArray())
        val good = w.bytes.toByteArray()
        // a longer length that still has bytes to read: the tag does not verify
        val longer = good.copyOf().also { it[1] = (it[1] + 1).toByte() } + byteArrayOf(0)
        assertFailsWith<BtActWire.ProtocolException> { reader(longer, keyB, keyA).receive() }
        // below the minimum, and above the maximum: refused before anything is allocated or read
        for (len in listOf(0, 1, BtActWire.GCM_TAG_BYTES, BtActWire.MAX_SECURE + 1, 65_535)) {
            val b = ByteArrayOutputStream(); DataOutputStream(b).writeShort(len)
            assertFailsWith<BtActWire.ProtocolException>("len=$len") { reader(b.toByteArray(), keyB, keyA).receive() }
        }
    }

    @Test fun aPayloadThatDoesNotFitIsRefusedBySend() {
        assertFailsWith<IllegalArgumentException> { writer(Wire(), keyA, keyB).send(0x14, ByteArray(BtActWire.MAX_PAYLOAD + 1)) }
    }

    @Test fun wipeEmptiesTheKeysOfTheChannel() {
        val w = Wire(); val a = writer(w, keyA, keyB)
        a.wipe(); a.send(0x12)                                                      // the key held here is all zeros now
        assertFailsWith<BtActWire.ProtocolException> { reader(w.bytes.toByteArray(), keyB, keyA).receive() }
    }

    @Test fun keysAreDerivedPerRoleAndPerSession() {
        val isk = ByteArray(64) { it.toByte() }; val sid = ByteArray(16) { 1 }; val ya = ByteArray(32) { 2 }; val yb = ByteArray(32) { 3 }
        val k = BtActKeys.derive(isk, sid, ya, yb)
        assertEquals(4, setOf(k.phoneToTv.toList(), k.tvToPhone.toList(), k.tagPhone().toList(), k.tagTv().toList()).size, "quatre valeurs distinctes : pas de clé pour deux usages, pas de confirmation reflétée")
        val again = BtActKeys.derive(isk, sid, ya, yb)
        assertContentEquals(k.phoneToTv, again.phoneToTv); assertContentEquals(k.tagTv(), again.tagTv())
        assertFalse(k.tagPhone().contentEquals(BtActKeys.derive(isk, sid, ya, ByteArray(32) { 4 }).tagPhone()), "un autre point : autre confirmation")
        assertFalse(k.phoneToTv.contentEquals(BtActKeys.derive(ByteArray(64) { 9 }, sid, ya, yb).phoneToTv), "un autre secret de session : autres clés")
        assertTrue(BtActKeys.same(k.tagTv(), again.tagTv())); assertFalse(BtActKeys.same(k.tagTv(), k.tagPhone()))
        k.wipe(); assertTrue(k.phoneToTv.all { it == 0.toByte() } && k.tvToPhone.all { it == 0.toByte() })
    }
}
