package castbridge.core.library.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** « Prison Break » written five ways is one folder; two series of the same name are never merged; the optional alias table is checked. */
class SeriesGroupingTest {
    private fun folders(vararg names: String, aliases: SeriesAliases = SeriesAliases.NONE): Map<String, String> =
        SeriesClassifier.plan(names.map { it to "" }, true, aliases).associate { it.name to it.folder }

    @Test fun oneSeriesWrittenManyWaysIsOneFolder() {
        val names = arrayOf("Prison Break S01E01.mkv", "Prison.Break.S01E02.720p.HDTV.x264.mkv", "PrisonBreak.S01E03.mkv", "Prison Break (2005) S01E04.mkv", "prison break s01e05.avi", "PRISON_BREAK_S01E06.avi", "Prison-Break-S01E07.mkv")
        val f = folders(*names)
        assertEquals(names.size, f.size)
        assertEquals(setOf("Prison Break/Saison 01"), f.values.toSet())
    }

    @Test fun theDefaultFolderNameIsUnchangedWhenThereIsNoAmbiguity() {
        assertEquals("The Flash/Saison 02", SeriesClassifier.folderFor("The Flash (2014) 2x04.mp4"))
        assertEquals(setOf("The Flash/Saison 02"), folders("The Flash (2014) 2x04.mp4", "The Flash (2014) 2x05.mp4").values.toSet())
    }

    @Test fun twoSeriesOfTheSameNameAndDifferentYearsAreNeverMerged() {
        val f = folders("The Flash (1990) S01E01.mkv", "The Flash (1990) S01E02.mkv", "The Flash (2014) S01E01.mkv", "The Flash 2014 S01E02.mkv")
        assertEquals("The Flash (1990)/Saison 01", f["The Flash (1990) S01E01.mkv"])
        assertEquals("The Flash (2014)/Saison 01", f["The Flash (2014) S01E01.mkv"])
        assertEquals("The Flash (2014)/Saison 01", f["The Flash 2014 S01E02.mkv"])
        val d = folders("Doctor Who (1963) S01E01.mkv", "Doctor Who (2005) S01E01.mkv")
        assertNotEquals(d["Doctor Who (1963) S01E01.mkv"], d["Doctor Who (2005) S01E01.mkv"])
    }

    @Test fun aYearlessFileThatCollidesWithADatedOneStaysApart() {
        val f = folders("Doctor Who (2005) S01E01.mkv", "Doctor Who S01E01.mkv")
        assertEquals("Doctor Who (2005)/Saison 01", f["Doctor Who (2005) S01E01.mkv"])
        assertEquals("Doctor Who/Saison 01", f["Doctor Who S01E01.mkv"])
        // no collision: the year-less file may join the only dated series
        val g = folders("Doctor Who (2005) S01E01.mkv", "Doctor Who S01E02.mkv")
        assertEquals(setOf("Doctor Who/Saison 01"), g.values.toSet())
    }

    @Test fun nearbyTitlesAreNotMerged() {
        val f = folders("CSI S01E01.mkv", "CSI Miami S01E01.mkv", "The Office (US) S01E01.mkv", "The Office (UK) S01E01.mkv", "Shameless (US) S01E01.mkv", "Shameless S01E01.mkv")
        assertEquals(f.size, f.values.toSet().size, f.toString())
    }

    @Test fun aSpellingKeepsTheMostFrequentForm() {
        val f = folders("Prison Break S01E01.mkv", "Prison Break S01E02.mkv", "PrisonBreak S01E03.mkv")
        assertEquals(setOf("Prison Break/Saison 01"), f.values.toSet())
    }

    // ------------------------------------------------------------------ the optional alias table
    private val table get() = SeriesAliases.builtin()

    @Test fun theBuiltInTableMergesATranslatedTitleOnlyWhenBothAreInTheLibrary() {
        assertTrue(table.size >= 10)
        val f = folders("La Casa de Papel S01E01.mkv", "Money Heist S01E02.mkv", "Money Heist S01E03.mkv", aliases = table)
        assertEquals(setOf("Money Heist/Saison 01"), f.values.toSet())
        // without the table, or with a lone translated file, nothing is renamed or merged
        assertEquals(2, folders("La Casa de Papel S01E01.mkv", "Money Heist S01E02.mkv").values.toSet().size)
        assertEquals("La Casa de Papel/Saison 01", folders("La Casa de Papel S01E01.mkv", aliases = table)["La Casa de Papel S01E01.mkv"])
    }

    @Test fun theBuiltInTableNeverMergesDistinctSeries() {
        for ((a, b) in listOf("The Flash" to "The Flash 2014", "Doctor Who" to "Doctor Who 2005", "CSI" to "CSI Miami", "Les Experts" to "Les Experts Miami", "The Office US" to "The Office UK",
            "House" to "House of Cards", "Les Revenants" to "The Returned", "Dr House" to "Dr Who", "Bureau" to "The Bureau of Magical Things")) {
            assertNotEquals(table.groupKey(a), table.groupKey(b), "$a / $b")
        }
        assertEquals(table.groupKey("House"), table.groupKey("Dr House"))
        assertEquals(table.groupKey("Money Heist"), table.groupKey("La Casa de Papel"))
    }

    @Test fun theTableFileIsWellFormed() {
        val text = SeriesAliases::class.java.getResourceAsStream("/castbridge/library/series-aliases.tsv")!!.readBytes().toString(Charsets.UTF_8)
        SeriesAliases.parse(text)   // throws on a year in a title or a title listed twice
        assertFailsWith<IllegalArgumentException> { SeriesAliases.parse("The Flash (1990)\tFlash 1990") }
        assertFailsWith<IllegalArgumentException> { SeriesAliases.parse("A\tB\nC\tB") }
        assertFailsWith<IllegalArgumentException> { SeriesAliases.parse("A\tB\nB\tC") }
        assertFailsWith<IllegalArgumentException> { SeriesAliases.parse("A | B") }
    }
}
