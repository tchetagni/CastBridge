package castbridge.core.library.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * « A false positive is worse than an omission »: names that LOOK like an episode, a date, a part, a language or a track but are none. For each, the engine
 * must NOT answer SERIES (a movie filed in a series folder is the worst error) and must never invent a season / episode. Written from the families of
 * docs/NAMING-PATTERNS.md; part of the regression suite.
 */
class FalsePositiveTest {
    private fun p(name: String, folder: String = "") = NameParser.parse(name, folder, 0, 2026)

    /** Movies and other files that carry numbers resembling episode markers. */
    private val NOT_SERIES = listOf(
        "Blade.Runner.2049.2017.1080p.BluRay.x264.mkv", "Fahrenheit.451.1080p.BluRay.x264.mkv", "Fahrenheit 451 WEB-DL x264.mkv", "Apollo 13 1995 720p.mkv",
        "Apollo.13.HDTV.mkv", "1917.2019.1080p.mkv", "1917.mkv", "300.2006.720p.BluRay.mkv", "300.mkv", "2012.2009.720p.mkv", "21.Jump.Street.2012.720p.mkv",
        "10 Cloverfield Lane 2016.mkv", "Room 237 2012 1080p.mkv", "District 9 2009.mkv", "Catch 22 1970 720p.mkv", "Ocean's 11 2001.mkv", "Se7en 1995.mkv",
        "Special Forces 2011 1080p BluRay.mkv", "Special Delivery 1955.mkv", "Special 26 2013 720p.mkv", "Rocky Special Edition 1976.mkv", "Over the Top Special Edition 1987.mkv",
        "The Gamer OVAL 2010.mkv", "SPECTRE 2015 1080p.mkv", "Dune Part Two 2024 1080p.mkv", "Kill Bill Vol. 2 2004.mkv", "Harry Potter Deathly Hallows Part 2 2011.mkv",
        "Mission Impossible 7 2023 1080p.mkv", "Toy Story 4 2019.mkv", "Part 3 2005.mkv", "Season of the Witch 2011 720p.mkv", "Seasons 2021.mkv", "Saison 2021.mkv",
        "Episode 1 2019 1080p.mkv", "Star Wars Episode 1 The Phantom Menace 1999.mkv", "Chapitre 3 2019.mkv", "Folge 2012.mkv", "Temporada 2020.mkv",
        "Mission 2 2016 1080p.mkv", "Alien 3 1992 Extended.mkv", "Terminator 2 1991 720p BluRay.mkv", "Die Hard 2 1990.mkv", "Rush Hour 3 2007 720p.mkv",
        "Ford v Ferrari 2019 2160p.mkv", "Jackass 3.5 2011.mkv", "The 100.mkv", "Hotel 101.mkv", "Room 101 2012 720p.mkv", "Apartment 1303 2012.mkv", "1408 2007 720p.mkv",
        "Burna Boy - Last Last (Live 2023).mp3", "Track 2024.mp3", "Album 2020 03 15.mp3", "01 - 03 - 2024.mp3", "Rapport 2024-03-15.pdf", "Facture 15-03-2024.pdf",
        "Releve 20240315.pdf", "Scan 2024.03.15.jpg", "Cours 2024 03 15.pdf", "IMG_20240315_142211.jpg", "VID-20240315-WA0012.mp4", "WhatsApp Video 2024-03-15 at 14.22.11.mp4",
        "Anniversaire Maman 2024.mp4", "Mariage Jean et Sophie S2.mp4".replace("S2", "2022"), "Formation Excel Module 3.mp4", "Leçon 5 - Les fractions.mp4",
        "The Lord of the Rings 1 The Fellowship of the Ring 2001.mkv", "Matrix 4 Resurrections 2021.mkv", "Vol 2 2017 1080p.mkv", "Naruto the Movie OVA.mkv".replace("OVA", "2004"),
        "Inception.2010.1080p.BluRay.fr.srt", "Moana 2016 CD1.avi", "Heat 1995 Part 1.avi", "Paris 13 2021 720p.mkv", "Kingdom 2 2019 1080p.mkv", "Soul 2020 3D.mkv",
        "Sing 2 2021.mkv", "Pitch Perfect 3 2017.mkv", "Zootopia 2016 720p WEB-DL.mkv", "Mad Max 2 1981.mkv", "Rambo 4 2008 720p.mkv", "Scream 4 2011 1080p.mkv",
    )

    @Test fun nothingThatLooksLikeAnEpisodeIsAnEpisode() {
        val bad = NOT_SERIES.map { it to p(it) }.filter { (_, r) -> r.kind == Kind.SERIES }.map { (n, r) -> "$n -> ${r.kind} ${r.title} S${r.season}E${r.episode} date=${r.date}" }
        assertTrue(bad.isEmpty(), "taken for a series:\n" + bad.joinToString("\n"))
    }

    @Test fun ambiguousDatesAreNeverGuessed() {
        for (n in listOf("The Daily Show 03-04-2024.mp4", "The Daily Show 01-02-2024.mp4", "The Daily Show 12-12-2024.mp4", "Late Show 03.04.2024.mp4")) {
            assertEquals(null, p(n).date, n)
            assertTrue(p(n).kind != Kind.SERIES, n)
        }
        assertEquals("2024-03-15", p("The Daily Show 15-03-2024.mp4").date)
        assertEquals("2024-03-15", p("The Daily Show 03-15-2024.mp4").date)   // 15 cannot be a month: unambiguous
        assertTrue(p("The Daily Show 31-02-2024.mp4").date == null)            // not a real day
    }

    @Test fun threeDigitNumbersNeedProofOfASeries() {
        assertEquals(1 to 8, p("Prison.Break.108.HDTV.XviD.avi").let { it.season to it.episode })
        assertEquals(10 to 4, p("the.big.bang.theory.1004.hdtv-lol.mp4").let { it.season to it.episode })
        for (n in listOf("Prison.Break.108.mkv", "Prison.Break.108.BluRay.x264.mkv", "Prison.Break.108.2005.HDTV.mkv", "Prison Break 880 HDTV.mkv", "Prison Break 720 HDTV.mkv", "Prison Break 1080 HDTV.mkv", "Prison Break 2049 HDTV.mkv", "Prison Break 1917 HDTV.mkv"))
            assertTrue(p(n).kind != Kind.SERIES, n)
    }

    @Test fun aLanguageWordIsOnlyALanguageAfterADot() {
        assertEquals("Stranger Than It", p("Stranger Than It.srt").title.ifBlank { p("Stranger Than It.srt").stem })
        assertEquals(null, p("Stranger Than It.srt").subLang)
        assertEquals("en.forced", p("Inception.2010.1080p.BluRay.en.forced.srt").subLang)
        assertEquals(null, p("Inception.2010.1080p.BluRay.xx.srt").subLang)
    }

    @Test fun movieEditionsAndPartsKeepTwoFilesApart() {
        val a = p("Blade.Runner.1982.Directors.Cut.1080p.mkv"); val b = p("Blade.Runner.1982.Theatrical.Cut.1080p.mkv"); val c = p("Blade.Runner.1982.1080p.mkv")
        assertTrue(a.identity != b.identity && a.identity != c.identity && b.identity != c.identity, "${a.identity} ${b.identity} ${c.identity}")
        assertTrue(p("Kill Bill 2003 CD1.avi").identity != p("Kill Bill 2003 CD2.avi").identity)
        // a second pass over our own name changes nothing
        val one = p("Kill Bill 2003 CD1.avi")
        val name = Namer.propose(one, FileRef(Origin.PHONE, "Kill Bill 2003 CD1.avi", 1L shl 20), Labels("fr")).name
        assertEquals("Kill Bill (2003) - part1.avi", name)
        val again = Namer.propose(p(name), FileRef(Origin.PHONE, name, 1L shl 20), Labels("fr")).name
        assertEquals(name, again)
    }
}
