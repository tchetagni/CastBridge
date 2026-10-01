package castbridge.core

import castbridge.core.library.agent.SeriesClassifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeriesClassifierTest {
    // the real names found on the owner's TV on 2026-10-01
    private val real = (1..11).map { "Prison Break [S01-E${"%02d".format(it)}].avi" }

    @Test fun anEpisodeGoesToTitleSlashSeason() {
        assertEquals("Prison Break/Saison 01", SeriesClassifier.folderFor("Prison Break [S01-E08].avi"))
        assertEquals("Game of Thrones/Saison 08", SeriesClassifier.folderFor("Game.of.Thrones.S08E06.1080p.WEB-DL.mkv"))
        assertEquals("The Flash/Saison 02", SeriesClassifier.folderFor("The Flash (2014) 2x04.mp4"))
        assertEquals("Prison Break/Season 03", SeriesClassifier.folderFor("Prison Break S03E01.avi", fr = false))
    }

    @Test fun theOwnersLibraryIsClassifiedAndTheStrayFileIsLeftAlone() {
        val files = real.map { it to "" } + ("4_5988009679999993065.mp4" to "")
        val plan = SeriesClassifier.plan(files)
        assertEquals(11, plan.size, plan.toString())
        assertTrue(plan.all { it.folder == "Prison Break/Saison 01" })
        assertTrue(plan.none { it.name.startsWith("4_") }, "a file whose name says nothing stays at the root")
    }

    @Test fun moviesMusicAndUnclearNamesAreNotClassified() {
        assertNull(SeriesClassifier.folderFor("Inception (2010).mkv"))
        assertNull(SeriesClassifier.folderFor("Artiste - Titre.mp3"))
        assertNull(SeriesClassifier.folderFor("video.mp4"))
        assertNull(SeriesClassifier.folderFor("notes de cours.pdf"))
    }

    @Test fun whatTheUserAlreadyOrganisedIsNeverReshuffled() {
        val plan = SeriesClassifier.plan(listOf("Prison Break S01E01.avi" to "Mes séries/PB", "Prison Break S01E02.avi" to ""))
        assertEquals(listOf("Prison Break S01E02.avi"), plan.map { it.name })
    }

    @Test fun oneSpellingForTheWholeSeriesAndSeveralSeasons() {
        val plan = SeriesClassifier.plan(listOf("Breaking Bad S01E01.mkv", "Breaking Bad S01E02.mkv", "breaking.bad.S02E01.mkv", "Breaking Bad S02E02.mkv").map { it to "" })
        assertEquals(setOf("Breaking Bad/Saison 01", "Breaking Bad/Saison 02"), plan.map { it.folder }.toSet(), plan.toString())
    }

    @Test fun unknownSeasonKeepsTheTitleFolderOnly() {
        val f = SeriesClassifier.folderFor("[Groupe] One Piece - 1045.mkv")
        assertTrue(f == null || f == "One Piece", "anime numbering has no season: title folder only, never a made-up season ($f)")
    }

    @Test fun undoPutsEverythingBackAtTheRoot() {
        val moves = SeriesClassifier.plan(real.map { it to "" })
        assertTrue(SeriesClassifier.undo(moves).all { it.folder == "" && it.name in real })
    }

    @Test fun forbiddenCharactersNeverReachAFolderName() {
        val f = SeriesClassifier.folderFor("Série: le test? S01E01.mkv")
        assertTrue(f == null || f.none { it in "\\:*?\"<>|" }, f)
    }
}
