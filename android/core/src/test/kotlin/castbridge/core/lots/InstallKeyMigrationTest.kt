package castbridge.core.lots

import castbridge.core.crypto.MemoryWrapper
import castbridge.core.crypto.PlainWrapper
import castbridge.core.crypto.SecretWrapper
import java.io.File
import java.security.SecureRandom
import kotlin.test.*

/** Audit w4-03 (fix2): plain → Keystore migration, transient Keystore failures, retry of the `.unreadable-*` files, assumed regeneration. Fake wrappers only (no real key material). */
class InstallKeyMigrationTest {
    private fun seeded(n: Int) = object : SecureRandom() { override fun nextBytes(bytes: ByteArray) { for (i in bytes.indices) bytes[i] = (n + i).toByte() } }

    /** A model of the Keystore wrapper: label `keystore`; [down] = every call throws (transient), [lost] = the alias is gone (unwrap answers null). */
    private class FakeKeystore(var down: Boolean = false, var lost: Boolean = false) : SecretWrapper {
        private val inner = MemoryWrapper(ByteArray(32) { (it * 3 + 5).toByte() })
        override fun wrap(plain: ByteArray): ByteArray { if (down) throw IllegalStateException("coffre indisponible"); return inner.wrap(plain) }
        override fun unwrap(blob: ByteArray): ByteArray? { if (down) throw IllegalStateException("coffre indisponible"); return if (lost) null else inner.unwrap(blob) }
        override val label = "keystore"
    }

    private fun store(dir: File, ks: FakeKeystore, preferKeystore: Boolean, rnd: Int = 90, now: Long = 7000L) =
        InstallKeyStore(dir, if (preferKeystore) ks else PlainWrapper(), seeded(rnd), now = { now }, readers = { l -> when (l) { "keystore" -> ks; "plain" -> PlainWrapper(); else -> null } })

    private fun wrapOf(f: File) = f.readText().lines().first { it.startsWith("wrap=") }.substringAfter("=")

    @Test fun aPlainKeyFromAFailedFirstBootMovesIntoTheKeystoreWithTheSameKey() {
        val dir = Kit.tmp(); val ks = FakeKeystore()
        val first = store(dir, ks, preferKeystore = false, rnd = 10).loadOrCreate()             // first boot: the probe failed, plain
        assertEquals("plain", wrapOf(File(dir, "install.key")))
        val s = store(dir, ks, preferKeystore = true); val k = s.loadOrCreate()                   // next boot: the Keystore works
        assertContentEquals(first.pub, k.pub, "same installPub: no rental lost"); assertContentEquals(first.priv, k.priv)
        assertEquals("keystore", s.protection); assertTrue(s.loadNote!!.contains("même clé")); assertFalse("illisible" in s.loadNote!!)
        assertEquals("keystore", wrapOf(File(dir, "install.key"))); assertEquals("keystore", wrapOf(File(dir, "install.key.bak")), "the plain copy is gone once the new file reads back")
        assertTrue(dir.list()!!.none { ".unreadable-" in it })
        val again = store(dir, ks, preferKeystore = true, rnd = 1); assertContentEquals(first.pub, again.loadOrCreate().pub); assertNull(again.loadNote)
    }

    @Test fun aKeystoreFailingDuringTheMigrationKeepsThePlainKeyReadableAndUnchanged() {
        val dir = Kit.tmp(); val ks = FakeKeystore()
        val first = store(dir, ks, preferKeystore = false, rnd = 10).loadOrCreate()
        val original = File(dir, "install.key").readText()
        ks.down = true
        val s = store(dir, ks, preferKeystore = true); val k = s.loadOrCreate()
        assertContentEquals(first.pub, k.pub); assertEquals("plain", s.protection)
        assertEquals(original, File(dir, "install.key").readText(), "unchanged")
        ks.down = false
        assertContentEquals(first.pub, store(dir, ks, preferKeystore = true).loadOrCreate().pub, "migrated at the next start")
        assertEquals("keystore", wrapOf(File(dir, "install.key")))
    }

    @Test fun aNewFileThatDoesNotReadBackIsUndone() {
        val dir = Kit.tmp()
        val first = InstallKeyStore(dir, PlainWrapper(), seeded(10)).loadOrCreate()
        val original = File(dir, "install.key").readText()
        // writes fine, but reads back nothing (lost at once): the plain file must be put back
        val broken = object : SecretWrapper { override fun wrap(plain: ByteArray) = ByteArray(40) { 1 }; override fun unwrap(blob: ByteArray): ByteArray? = null; override val label = "keystore" }
        val s = InstallKeyStore(dir, broken, seeded(20)); val k = s.loadOrCreate()
        assertContentEquals(first.pub, k.pub); assertEquals("plain", s.protection)
        assertEquals(original, File(dir, "install.key").readText())
    }

    @Test fun aKeystoreKeyIsNeverReadThroughPlainAndATransientFailureThrows() {
        val dir = Kit.tmp(); val ks = FakeKeystore()
        val first = store(dir, ks, preferKeystore = true, rnd = 10).loadOrCreate()
        ks.down = true
        val before = dir.list()!!.sorted()
        assertFailsWith<InstallKeyUnavailableException> { store(dir, ks, preferKeystore = false).loadOrCreate() }   // the probe failed too: plain chosen, still not used to read
        assertEquals(before, dir.list()!!.sorted()); assertEquals("keystore", wrapOf(File(dir, "install.key")))
        ks.down = false
        assertContentEquals(first.pub, store(dir, ks, preferKeystore = false).loadOrCreate().pub, "read through its own envelope even when the probe failed")
        assertEquals("keystore", wrapOf(File(dir, "install.key")), "never rewritten as plain")
    }

    @Test fun aLostAliasGivesANewKeyAndANote() {
        val dir = Kit.tmp(); val ks = FakeKeystore()
        val first = store(dir, ks, preferKeystore = true, rnd = 10).loadOrCreate()
        ks.lost = true
        val s = store(dir, ks, preferKeystore = true, rnd = 30); val k = s.loadOrCreate()
        assertFalse(first.pub.contentEquals(k.pub)); assertTrue(s.loadNote!!.startsWith("clé d'installation illisible"))
        assertTrue(File(dir, "install.key.unreadable-7000").isFile)
    }

    @Test fun anAssumedRegenerationKeepsTheOldFileAndItIsRestoredWhenTheKeystoreComesBack() {
        val dir = Kit.tmp(); val ks = FakeKeystore()
        val first = store(dir, ks, preferKeystore = true, rnd = 10).loadOrCreate()
        ks.down = true
        val s = store(dir, ks, preferKeystore = false, rnd = 40, now = 8000L)
        assertFailsWith<InstallKeyUnavailableException> { s.loadOrCreate() }
        val fresh = s.regenerateAssumingLost()                                                     // what RentalHub does once InstallKeyPolicy.mustRegenerate says so
        assertFalse(first.pub.contentEquals(fresh.pub)); assertEquals(InstallKeyStore.ASSUMED_NOTE, s.loadNote); assertTrue("illisible" in s.loadNote!!)
        assertEquals("plain", wrapOf(File(dir, "install.key"))); assertTrue(File(dir, "install.key.unreadable-8000").isFile)
        // next start, Keystore still down: the new key is used, the old file kept (retried, not destroyed)
        val s2 = store(dir, ks, preferKeystore = false, rnd = 1, now = 9000L)
        assertContentEquals(fresh.pub, s2.loadOrCreate().pub); assertTrue(File(dir, "install.key.unreadable-8000").isFile)
        // the Keystore is back: the original key is restored, the regenerated one kept aside
        ks.down = false
        val s3 = store(dir, ks, preferKeystore = true, rnd = 2, now = 9500L); val back = s3.loadOrCreate()
        assertContentEquals(first.pub, back.pub); assertTrue(s3.loadNote!!.startsWith("ancienne clé d'installation retrouvée"))
        assertFalse(File(dir, "install.key.unreadable-8000").exists()); assertTrue(File(dir, "install.key.superseded-9500").isFile)
        assertEquals("keystore", wrapOf(File(dir, "install.key")))
        val s4 = store(dir, ks, preferKeystore = true, rnd = 3, now = 9900L)
        assertContentEquals(first.pub, s4.loadOrCreate().pub); assertNull(s4.loadNote, "stable afterwards")
    }

    @Test fun anUnreadableCopyOfTheCurrentKeyIsJustRemoved() {
        val dir = Kit.tmp(); val ks = FakeKeystore()
        val first = store(dir, ks, preferKeystore = true, rnd = 10).loadOrCreate()
        File(dir, "install.key").copyTo(File(dir, "install.key.unreadable-1"))
        assertContentEquals(first.pub, store(dir, ks, preferKeystore = true).loadOrCreate().pub)
        assertFalse(File(dir, "install.key.unreadable-1").exists())
    }

    @Test fun aNewKeyFallsBackToPlainWhenTheKeystoreCannotWrapAndMigratesLater() {
        val dir = Kit.tmp(); val ks = FakeKeystore(down = true)
        val s = store(dir, ks, preferKeystore = true, rnd = 10); val k = s.loadOrCreate()
        assertEquals("plain", s.protection); assertEquals("plain", wrapOf(File(dir, "install.key"))); assertFalse(s.loadNote!!.contains("écriture impossible"))
        ks.down = false
        assertContentEquals(k.pub, store(dir, ks, preferKeystore = true).loadOrCreate().pub); assertEquals("keystore", wrapOf(File(dir, "install.key")))
    }
}
