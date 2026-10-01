package castbridge.core

import castbridge.core.tv.*
import java.io.File
import kotlin.test.*

/** What the screens show in plain words, and the phone's parsing of the real server answers. */
class UxTest {
    private val r = Rig()
    @AfterTest fun tearDown() = r.close()

    @Test fun receivedFilesAndDrivesAreAnnouncedInPlainWords() {
        TvClient(r.base).upload("Mon_film.mkv", 0, 5, "hello".byteInputStream()) {}
        TvClient(r.base).upload("notes.pdf", 0, 3, "abc".byteInputStream()) {}
        assertTrue(r.notices.contains("Vidéo reçue ✓  Mon film"), r.notices.toString())
        assertTrue(r.notices.contains("Fichier reçu ✓  notes"), r.notices.toString())
        r.unplug(); r.replug()
        assertTrue(r.notices.any { it.startsWith("Clé USB branchée") } && r.notices.any { it.startsWith("Clé USB retirée") }, r.notices.toString())
        assertTrue(r.notices.none { it.contains(".part") || it.contains("usb-1234") || it.contains("409") }, "no jargon: ${r.notices}")
    }

    @Test fun aCopyInProgressIsShownEvenWhenTheSameNameAlreadyExistsComplete() {
        // e.g. a series episode sent again, or a move target: the TV screen must still show the progress of what arrives
        File(r.usbDir, "ep.mkv").writeBytes(ByteArray(10))
        val status = r.put("ep.mkv", 0, 100, ByteArray(40)).first
        val shown = r.server.receiving()
        assertEquals(listOf(Triple("ep.mkv", 40L, 100L)), shown, "progress hidden (upload status $status)")
    }

    @Test fun phoneParsesTheRealInfoAnswer() {
        File(r.usbDir, "a.mp4").writeBytes(ByteArray(10))
        assertEquals(200, r.put("b.mkv", 0, 100, ByteArray(40)).first)      // still arriving
        val i = TvInfo.parse(r.tv.info())
        assertEquals(setOf("a.mp4", "b.mkv"), i.files.map { it.name }.toSet(), "entries carry volume/duplicate after 'complete'")
        assertEquals(TvFile("b.mkv", 100, 40, false), i.file("b.mkv"))
        assertEquals(listOf(Triple("b.mkv", 40L, 100L)), r.server.receiving())
    }
}
