package castbridge.play

import castbridge.core.quiz.EmbeddedQuestionSource
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** La banque du service : questions libres intégrées + lots `.quiz.zip` d'un dossier en LECTURE SEULE ; jamais le contenu réservable (w20-04), jamais d'écriture. */
class LotsBankTest {
    private val base = EmbeddedQuestionSource(levels = null).bank().all.size

    @Test fun withoutADirectoryTheBankIsTheFreeBaseOnly() {
        assertTrue(base >= 50, "banque de base : $base questions")
        assertEquals(base, PlayServer.loadBank(null).all.size)
        assertEquals(base, PlayServer.loadBank(File("/n/existe/pas")).all.size)
    }

    @Test fun unreadableLotsAreSkippedAndTheDirectoryIsNeverWritten() {
        val dir = kotlin.io.path.createTempDirectory("lots").toFile()
        try {
            File(dir, "quiz-x-p1-v1.quiz.zip").writeText("pas un zip"); File(dir, "notes.txt").writeText("ignoré")
            dir.listFiles()!!.forEach { it.setReadOnly() }; dir.setReadOnly()
            val before = dir.list()!!.sorted()
            assertEquals(base, PlayServer.loadBank(dir).all.size, "lot illisible ignoré")
            assertEquals(before, dir.list()!!.sorted(), "aucun fichier créé, déplacé ni supprimé (dossier en lecture seule)")
        } finally { dir.setWritable(true); dir.listFiles()?.forEach { it.setWritable(true); it.delete() }; dir.delete() }
    }
}
