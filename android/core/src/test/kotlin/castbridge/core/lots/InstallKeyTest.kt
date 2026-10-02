package castbridge.core.lots

import castbridge.core.crypto.MemoryWrapper
import castbridge.core.crypto.PlainWrapper
import castbridge.core.owner.X25519
import java.io.File
import java.security.SecureRandom
import kotlin.test.*

class InstallKeyTest {
    private fun seeded(n: Int) = object : SecureRandom() { override fun nextBytes(bytes: ByteArray) { for (i in bytes.indices) bytes[i] = (n + i).toByte() } }
    private val wrapKey = ByteArray(32) { (it * 7 + 1).toByte() }

    @Test fun keyPairComesFromTheSeedAndTheIdIsAHashOfThePublicKey() {
        val k = InstallKey.fromSeed(ByteArray(32) { (it + 1).toByte() })
        assertContentEquals(X25519.publicKey(k.priv), k.pub)
        assertEquals(16, k.installId.length); assertTrue(k.installId.all { it in "0123456789abcdef" })
        assertEquals(k.installId, InstallKey.fromSeed(k.priv).installId)
        assertFailsWith<IllegalArgumentException> { InstallKey(ByteArray(31), ByteArray(32)) }
    }

    @Test fun createsThenReloadsTheSameKeyAfterARestart() {
        val dir = Kit.tmp(); val w = MemoryWrapper(wrapKey)
        val s1 = InstallKeyStore(dir, w, seeded(10)); val k1 = s1.loadOrCreate()
        assertEquals("nouvelle clé d'installation générée", s1.loadNote); assertEquals("memory", s1.protection)
        val s2 = InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(50)); val k2 = s2.loadOrCreate()
        assertContentEquals(k1.pub, k2.pub); assertContentEquals(k1.priv, k2.priv); assertNull(s2.loadNote)
        val text = File(dir, "install.key").readText()
        assertTrue(text.startsWith("castbridge-install-key-v1\npub=") && "wrap=memory" in text && "createdAt=" in text)
        assertFalse(text.contains(k1.priv.joinToString("") { "%02x".format(it) }), "the private key is only there wrapped")
    }

    @Test fun plainFallbackLabelsItselfAndReloads() {
        val dir = Kit.tmp(); val s = InstallKeyStore(dir, PlainWrapper(), seeded(3)); val k = s.loadOrCreate()
        assertEquals("plain", s.protection); assertTrue("wrap=plain" in File(dir, "install.key").readText())
        assertContentEquals(k.pub, InstallKeyStore(dir, PlainWrapper(), seeded(9)).loadOrCreate().pub)
    }

    @Test fun aCorruptedFileGivesANewKeyAndAJournalNoteNeverAnException() {
        val dir = Kit.tmp(); val first = InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(10)).loadOrCreate()
        File(dir, "install.key").writeText("garbage"); File(dir, "install.key.bak").delete()
        val s = InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(70)); val k = s.loadOrCreate()
        assertFalse(first.pub.contentEquals(k.pub)); assertTrue(s.loadNote!!.startsWith("clé d'installation illisible"))
        assertContentEquals(k.pub, InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(1)).loadOrCreate().pub, "the new key was saved")
    }

    @Test fun aFileWrappedByAnotherKeyIsUnreadableAndReplaced() {
        val dir = Kit.tmp(); val first = InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(10)).loadOrCreate()
        val s = InstallKeyStore(dir, MemoryWrapper(ByteArray(32) { 99 }), seeded(20)); val k = s.loadOrCreate()
        assertFalse(first.pub.contentEquals(k.pub)); assertNotNull(s.loadNote)
    }

    @Test fun aPublicKeyThatDoesNotMatchThePrivateOneIsRefused() {
        val dir = Kit.tmp(); val first = InstallKeyStore(dir, PlainWrapper(), seeded(10)).loadOrCreate()
        val f = File(dir, "install.key"); val lines = f.readText().lines().toMutableList()
        lines[1] = "pub=" + "00".repeat(32); f.writeText(lines.joinToString("\n")); File(dir, "install.key.bak").delete()
        val k = InstallKeyStore(dir, PlainWrapper(), seeded(40)).loadOrCreate()
        assertFalse(first.pub.contentEquals(k.pub))
    }

    @Test fun anUnwritableFolderStillGivesAKeyAndSaysSo() {
        val blocker = File(Kit.tmp(), "file").also { it.writeText("x") }                      // a FILE where the folder should be
        val s = InstallKeyStore(File(blocker, "rental"), PlainWrapper(), seeded(5)); val k = s.loadOrCreate()
        assertEquals(32, k.pub.size); assertTrue(s.loadNote!!.contains("écriture impossible"))
        assertSame(k, s.loadOrCreate(), "kept in memory for the session")
    }

    @Test fun twoNewStoresDrawDifferentKeys() {
        assertFalse(InstallKeyStore(Kit.tmp(), PlainWrapper()).loadOrCreate().pub.contentEquals(InstallKeyStore(Kit.tmp(), PlainWrapper()).loadOrCreate().pub))
    }

    @Test fun memoryWrapperRoundTripsAndRejectsTampering() {
        val w = MemoryWrapper(wrapKey); val blob = w.wrap(ByteArray(32) { 5 })
        assertContentEquals(ByteArray(32) { 5 }, w.unwrap(blob)); assertNotEquals(blob.toList(), w.wrap(ByteArray(32) { 5 }).toList(), "fresh iv each time")
        assertNull(w.unwrap(blob.also { it[20] = (it[20] + 1).toByte() })); assertNull(w.unwrap(ByteArray(5)))
        assertFailsWith<IllegalArgumentException> { MemoryWrapper(ByteArray(16)) }
    }
}
