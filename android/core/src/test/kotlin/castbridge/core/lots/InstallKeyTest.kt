package castbridge.core.lots

import castbridge.core.crypto.MemoryWrapper
import castbridge.core.crypto.PlainWrapper
import castbridge.core.crypto.SecretWrapper
import castbridge.core.lots.InstallKeyPolicy.KeyState
import castbridge.core.owner.X25519
import java.io.File
import java.security.SecureRandom
import kotlin.test.*

class InstallKeyTest {
    private fun seeded(n: Int) = object : SecureRandom() { override fun nextBytes(bytes: ByteArray) { for (i in bytes.indices) bytes[i] = (n + i).toByte() } }
    private val wrapKey = ByteArray(32) { (it * 7 + 1).toByte() }

    /** What one unwrap call does: answer, answer null (lost for good: bad tag), or THROW (transient). */
    private enum class Do { OK, LOST, THROW }

    /** A scripted Keystore: [wrapFails] = creation impossible; [script] is consumed one entry per unwrap call (alias absent then present = THROW, OK; exception then success likewise), then [default]. */
    private class Fake(var default: Do = Do.OK, var script: MutableList<Do> = mutableListOf(), var wrapFails: Boolean = false, key: ByteArray) : SecretWrapper {
        private val inner = MemoryWrapper(key)
        override fun wrap(plain: ByteArray): ByteArray = if (wrapFails) throw IllegalStateException("coffre indisponible") else inner.wrap(plain)
        override fun unwrap(blob: ByteArray): ByteArray? = when (if (script.isNotEmpty()) script.removeAt(0) else default) {
            Do.OK -> inner.unwrap(blob); Do.LOST -> null; Do.THROW -> throw IllegalStateException("coffre indisponible")
        }
        override val label get() = "memory"
    }

    private fun snapshot(dir: File) = dir.list()!!.sorted().associateWith { File(dir, it).readText() }
    private fun created(dir: File = Kit.tmp(), seed: Int = 10): Pair<File, InstallKey> = dir to InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(seed)).loadOrCreate()

    @Test fun keyPairComesFromTheSeedAndTheIdIsAHashOfThePublicKey() {
        val k = InstallKey.fromSeed(ByteArray(32) { (it + 1).toByte() })
        assertContentEquals(X25519.publicKey(k.priv), k.pub)
        assertEquals(16, k.installId.length); assertTrue(k.installId.all { it in "0123456789abcdef" })
        assertFailsWith<IllegalArgumentException> { InstallKey(ByteArray(31), ByteArray(32)) }
    }

    // ---- state machine: ABSENT -> READY, PENDING, UNAVAILABLE, UNREADABLE ----

    @Test fun absentThenFirstCreationThenTheSameKeyAfterARestart() {
        val dir = Kit.tmp(); val s1 = InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(10))
        assertEquals(KeyState.PENDING, s1.state); assertTrue(s1.isAbsent())
        val k1 = s1.loadOrCreate()
        assertEquals(KeyState.READY, s1.state); assertFalse(s1.isAbsent()); assertEquals("nouvelle clé d'installation générée", s1.loadNote)
        val s2 = InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(50)); val k2 = s2.loadOrCreate()
        assertContentEquals(k1.priv, k2.priv); assertNull(s2.loadNote)
        val text = File(dir, "install.key").readText()
        assertTrue(text.startsWith("castbridge-install-key-v1\npub=") && "wrap=memory" in text)
        assertFalse(text.contains(k1.priv.joinToString("") { "%02x".format(it) }), "the private key is only there wrapped")
        assertEquals(text, File(dir, "install.key.bak").readText(), "a copy for the backup")
    }

    @Test fun firstCreationNeverWritesAPlainKeyWhateverTheWrapperDoes() {
        val dir = Kit.tmp(); val w = Fake(key = wrapKey, wrapFails = true)
        val s = InstallKeyStore(dir, w, seeded(10))
        repeat(3) { assertFailsWith<InstallKeyUnavailableException> { s.loadOrCreate() }; assertEquals(KeyState.UNAVAILABLE, s.state) }
        assertEquals(0, dir.list()?.size ?: 0, "nothing at all was written: no plain key, no temporary file")
        // a wrapper that wraps but cannot read its own blob back is a failing wrapper too
        val garbled = Fake(default = Do.LOST, key = wrapKey)
        assertFailsWith<InstallKeyUnavailableException> { InstallKeyStore(dir, garbled, seeded(10)).loadOrCreate() }
        assertEquals(0, dir.list()?.size ?: 0)
        // the Keystore comes back: the key is created then (nothing v2 existed, nothing was lost by waiting)
        w.wrapFails = false; assertEquals(KeyState.READY.also { s.loadOrCreate() }, s.state)
        assertTrue("wrap=memory" in File(dir, "install.key").readText())
    }

    @Test fun productionDefaultReadersNeverReadAnotherLabelThanTheCreators() {
        val (dir, _) = created()
        val s = InstallKeyStore(dir, PlainWrapper(), seeded(1))                                  // creator labelled `plain` cannot read a `memory` file: foreign envelope
        assertFailsWith<InstallKeyUnreadableException> { s.loadOrCreate() }; assertEquals(KeyState.UNREADABLE, s.state)
    }

    @Test fun anExistingKeyIsNeverOverwrittenMovedOrCreatedUnderAnyExceptionSequence() {
        val sequences = listOf(
            listOf(Do.THROW), List(10) { Do.THROW }, listOf(Do.THROW, Do.THROW, Do.OK), listOf(Do.OK), listOf(Do.THROW, Do.LOST), listOf(Do.LOST), listOf(Do.LOST, Do.OK, Do.THROW),
            listOf(Do.THROW, Do.OK, Do.LOST, Do.OK),
        )
        for (seq in sequences) for (backup in listOf(true, false)) {
            val (dir, first) = created(); if (!backup) File(dir, "install.key.bak").delete()
            val before = snapshot(dir)
            val w = Fake(key = wrapKey, script = seq.toMutableList()); val s = InstallKeyStore(dir, w, seeded(99), now = { 4242L })
            var got: InstallKey? = null
            repeat(seq.size + 2) {
                got = try { s.loadOrCreate() } catch (e: InstallKeyUnavailableException) { got } catch (e: InstallKeyUnreadableException) { got }
                assertEquals(before, snapshot(dir), "files untouched after every attempt of $seq backup=$backup")
            }
            got?.let { assertContentEquals(first.priv, it.priv, "$seq: never another key") }
        }
    }

    @Test fun transientThenReadyAndLostIsStickyUntilARestartOrReset() {
        val (dir, first) = created()
        val w = Fake(key = wrapKey, script = mutableListOf(Do.THROW)); val s = InstallKeyStore(dir, w, seeded(2))
        assertFailsWith<InstallKeyUnavailableException> { s.loadOrCreate() }; assertEquals(KeyState.UNAVAILABLE, s.state)
        assertContentEquals(first.priv, s.loadOrCreate().priv); assertEquals(KeyState.READY, s.state)
        val lost = InstallKeyStore(dir, Fake(Do.LOST, key = wrapKey), seeded(2))
        assertFailsWith<InstallKeyUnreadableException> { lost.loadOrCreate() }; assertEquals(KeyState.UNREADABLE, lost.state)
        assertFailsWith<InstallKeyUnreadableException> { lost.loadOrCreate() }                       // sticky: no new attempt
        assertContentEquals(first.priv, InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(2)).loadOrCreate().priv, "after a restart that reads, it is the same key")
    }

    @Test fun aDamagedMainFileFallsBackToTheBackupAndBothDamagedIsUnreadableNeverReplaced() {
        val (dir, first) = created()
        val f = File(dir, "install.key"); val lines = f.readText().lines().toMutableList(); lines[3] = "priv=" + "ab".repeat(60); f.writeText(lines.joinToString("\n"))
        val s = InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(2))
        assertContentEquals(first.priv, s.loadOrCreate().priv); assertEquals("clé d'installation relue depuis la copie de sécurité", s.loadNote)
        File(dir, "install.key.bak").writeText("garbage"); val before = snapshot(dir)
        assertFailsWith<InstallKeyUnreadableException> { InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(3)).loadOrCreate() }
        assertEquals(before, snapshot(dir), "never regenerated")
    }

    // ---- explicit owner reset ----

    @Test fun resetRenamesTheCurrentFilesNeverDeletesAndInstallsAFreshKey() {
        val (dir, first) = created(); val before = snapshot(dir)
        val s = InstallKeyStore(dir, Fake(Do.LOST, key = wrapKey), seeded(2)); assertFailsWith<InstallKeyUnreadableException> { s.loadOrCreate() }
        val ok = InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(77), now = { 1000L })
        assertEquals(InstallKeyPolicy.RESET_NOTE, ok.reset()); assertEquals(KeyState.READY, ok.state)
        val fresh = InstallKey.generate(seeded(77))
        assertContentEquals(fresh.priv, ok.loadOrCreate().priv); assertFalse(first.pub.contentEquals(fresh.pub))
        assertEquals(before["install.key"], File(dir, "install.key.reset-1000").readText(), "the old main file is kept")
        assertEquals(before["install.key.bak"], File(dir, "install.key.bak.reset-1000").readText(), "the old backup is kept")
        assertContentEquals(fresh.priv, InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(1)).loadOrCreate().priv)
        // a second reset at the same millisecond: still nothing deleted, free names
        InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(78), now = { 1000L }).reset()
        val kept = dir.list()!!.filter { ".reset-" in it }
        assertEquals(4, kept.size); assertTrue(before.values.all { v -> kept.any { File(dir, it).readText() == v } })
    }

    @Test fun resetWithAFailingWrapperChangesNothing() {
        val (dir, _) = created(); val before = snapshot(dir)
        assertFailsWith<InstallKeyUnavailableException> { InstallKeyStore(dir, Fake(key = wrapKey, wrapFails = true), seeded(2)).reset() }
        assertEquals(before, snapshot(dir))
    }

    @Test fun aPowerCutAtAnyStepOfTheResetLeavesAReadableKeyOldOrNewNeverAThirdOne() {
        for (backup in listOf(true, false)) for (cut in 1..5) {
            val (dir, first) = created(); if (!backup) File(dir, "install.key.bak").delete()
            val originals = snapshot(dir).values
            val fresh = InstallKey.generate(seeded(77))
            assertFailsWith<IllegalStateException>("cut $cut") { InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(77), now = { 5L }).reset { if (it == cut) throw IllegalStateException("coupure") } }
            val k = InstallKeyStore(dir, MemoryWrapper(wrapKey), seeded(123)).loadOrCreate()          // the restart: never a first creation (a third key)
            assertTrue(k.priv.contentEquals(first.priv) || k.priv.contentEquals(fresh.priv), "cut $cut backup=$backup: old or new key")
            val all = dir.list()!!.map { File(dir, it).readText() }
            assertTrue(originals.all { it in all }, "cut $cut: the old key text is still somewhere in the folder")
        }
    }
}
