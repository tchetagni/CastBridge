package castbridge.core.tv

import castbridge.core.FakePlayer
import java.io.File
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Accueil de la TV en lecture progressive (2026-10-27) : « Reprendre » lue dans la base seule (au plus 10, regardés en partie, derniers lus d'abord),
 * liste de la bibliothèque sans rouvrir chaque fichier (date relevée avec la liste du dossier), empreinte qui ne change que si la liste change.
 */
class HomeFastPathTest {
    private val dirs = ArrayList<File>()
    private fun tmp(): File = kotlin.io.path.createTempDirectory("home").toFile().also { dirs += it }
    @AfterTest fun clean() { dirs.forEach { it.deleteRecursively() } }

    private var t = 1_000L
    private fun db(f: File? = null) = LibraryDb(f, now = { t++ })
    private val usb = LibraryDb.Place("usb-1", "Clé", VolumeKind.REMOVABLE, "Séries")

    private fun watchedPartly(d: LibraryDb, name: String, size: Long = 100, pos: Long = 60_000, place: LibraryDb.Place? = usb) {
        d.onPlayStarted(name, size, place); d.onStopped(name, size, pos, 600_000)
    }

    @Test fun `Reprendre de la base - seulement les fichiers regardes en partie, derniers lus d abord`() {
        val d = db()
        watchedPartly(d, "ancien.mp4"); watchedPartly(d, "recent.mp4")
        d.onPlayStarted("neuf.mp4", 100, usb)                                     // lu 0 s : rien à reprendre
        d.onPlayStarted("fini.mp4", 100, usb); d.onEnded("fini.mp4", 100, 600_000)  // terminé : exclu
        watchedPartly(d, "debut.mp4", pos = 3_000)                                // moins de 10 s : on recommence, exclu
        watchedPartly(d, "note.pdf")                                              // pas un media : exclu
        val l = d.resumeItems()
        assertEquals(listOf("recent.mp4", "ancien.mp4"), l.map { it.name })
        assertEquals(60_000, l[0].meta.resumeMs)
        assertEquals(listOf("usb-1", "Clé", "Séries"), listOf(l[0].volumeId, l[0].volumeLabel, l[0].folder))
        assertEquals(VolumeKind.REMOVABLE, l[0].volumeKind)
    }

    @Test fun `la regle est celle de LibrarySections RESUME`() {
        val d = db()
        for (i in 1..6) watchedPartly(d, "v$i.mp4", size = i.toLong())
        d.onEnded("v3.mp4", 3, 600_000)
        val viaSections = LibrarySections.build(d.resumeItems(99)).first { it.id == LibrarySections.RESUME }.items
        assertEquals(viaSections.map { it.name }, d.resumeItems(99).map { it.name })
    }

    @Test fun `au plus 10 fichiers`() {
        val d = db()
        for (i in 1..15) watchedPartly(d, "v$i.mp4", size = i.toLong())
        assertEquals(LibrarySections.QUICK_RESUME_MAX, 10)
        val l = d.resumeItems()
        assertEquals(10, l.size)
        assertEquals((15 downTo 6).map { "v$it.mp4" }, l.map { it.name }, "les 10 derniers lus")
    }

    @Test fun `le lieu est garde sur disque pour le prochain demarrage, et une ancienne base se relit`() {
        val f = File(tmp(), "library.db")
        watchedPartly(db(f), "a.mp4")
        val l = db(f).resumeItems()
        assertEquals(listOf("a.mp4"), l.map { it.name }); assertEquals("usb-1", l[0].volumeId); assertEquals("Séries", l[0].folder)
        // une ligne à 6 colonnes (écrite avant ce changement)
        f.writeText("100%3Aold.mp4\t600000\t60000\t0\t5\t\n")
        val old = db(f).resumeItems()
        assertEquals(listOf("old.mp4"), old.map { it.name }); assertEquals("", old[0].volumeId)
    }

    @Test fun `la generation de la base change a chaque modification`() {
        val d = db(); val g0 = d.generation
        watchedPartly(d, "a.mp4"); val g1 = d.generation
        assertNotEquals(g0, g1)
        d.deleted("a.mp4", 100); assertNotEquals(g1, d.generation)
    }

    // ---- la vraie ReceiverServer ----

    private class Rig {
        val dir = kotlin.io.path.createTempDirectory("lib").toFile()
        val port = ServerSocket(0).use { it.localPort }
        val server = ReceiverServer(VolumeRegistry.single(dir), FakePlayer(), port, pin = "123456").apply { start(5000, false) }
        fun put(n: String, b: Int, modified: Long) { File(dir, n).writeBytes(ByteArray(b)); File(dir, n).setLastModified(modified) }
        fun close() { server.stop(); dir.deleteRecursively() }
    }

    @Test fun `la liste donne noms tailles et dates du disque sans rouvrir les fichiers`() {
        val r = Rig(); try {
            r.put("b.mp4", 20, 2_000_000); r.put("a.mp4", 10, 3_000_000); r.put("c.mp4", 5, 1_000_000)
            val items = r.server.libraryItems()
            assertEquals(listOf("a.mp4", "b.mp4", "c.mp4"), items.map { it.name }, "le plus récent d'abord")
            for (i in items) {
                val f = File(r.dir, i.name)
                assertEquals(f.length(), i.size, i.name); assertEquals(f.lastModified(), i.mtime, i.name)
            }
        } finally { r.close() }
    }

    @Test fun `l empreinte ne change que si la liste change`() {
        val r = Rig(); try {
            r.put("a.mp4", 10, 1_000_000)
            val s0 = r.server.libraryStamp()
            assertEquals(s0, r.server.libraryStamp(), "rien n'a changé")
            r.server.changed(); assertEquals(s0, r.server.libraryStamp(), "liste relue mais identique")
            r.put("b.mp4", 10, 2_000_000); r.server.changed()
            val s1 = r.server.libraryStamp()
            assertNotEquals(s0, s1, "un fichier de plus")
            r.put("b.mp4", 10, 9_000_000); r.server.changed()
            assertNotEquals(s1, r.server.libraryStamp(), "un fichier modifié")
            assertTrue(r.server.fileStillThere("a.mp4", "internal") || r.server.fileStillThere("a.mp4", ""))
            assertEquals(false, r.server.fileStillThere("absent.mp4", ""))
        } finally { r.close() }
    }
}
