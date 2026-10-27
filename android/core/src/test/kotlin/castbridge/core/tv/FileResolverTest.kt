package castbridge.core.tv

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Défaut du 2026-10-27 (TV réelle, 172 fichiers sur une clé exFAT) : l'accueil restait vide des minutes parce que chaque fichier de la bibliothèque
 * relisait tout le dossier de la clé. Le résolveur lit le dossier une fois par liste et répond comme `fileOf`.
 */
class FileResolverTest {
    private val dirs = ArrayList<File>()
    private fun tmp(): File = kotlin.io.path.createTempDirectory("resolver").toFile().also { dirs += it }
    @AfterTest fun clean() { dirs.forEach { it.deleteRecursively() } }

    private class CountingStore(v: StorageVolume) : FileStore(v) {
        var listings = 0
        override fun listDisk(): Array<String>? { listings++; return super.listDisk() }
    }

    private fun usb(d: File) = StorageVolume("usb-1", "Clé", d, VolumeKind.REMOVABLE, Fs.EXFAT, 0, 0, true)

    @Test fun `le resolveur rend le meme fichier que fileOf`() {
        val d = tmp()
        File(d, "Film.mp4").writeText("a")
        File(d, "Autre.mkv.part").writeText("b")
        File(d, "Séries/Titre").mkdirs()
        File(d, "Séries/Titre/Ep1.mp4").writeText("c")
        val st = FileStore(usb(d))
        st.filing.put("Ep1.mp4", "Séries/Titre/Ep1.mp4", null)
        val r = st.fileResolver()
        for (n in listOf("Film.mp4", "film.MP4", "Ep1.mp4", "ep1.mp4", "absent.mp4", "Autre.mkv", "autre.MKV")) assertEquals(st.fileOf(n), r(n), n)
        assertEquals(File(d, "Film.mp4"), r("film.MP4"))
        assertEquals(File(d, "Séries/Titre/Ep1.mp4"), r("Ep1.mp4"))
        assertEquals(File(d, "absent.mp4"), r("absent.mp4"))
        assertEquals(File(d, "Autre.mkv"), r("autre.MKV"))
    }

    @Test fun `le dossier est lu une fois pour N noms`() {
        val d = tmp()
        val names = (1..50).map { "f$it.mp4" }
        names.forEach { File(d, it).writeText("x") }
        val st = CountingStore(usb(d))
        val r = st.fileResolver()
        names.forEach { r(it.uppercase()) }
        assertEquals(1, st.listings)
        names.forEach { st.fileOf(it) }
        assertEquals(51, st.listings, "fileOf seul reste une lecture par appel")
    }
}
