package castbridge.core.tv

import castbridge.core.tv.Filing.Category.*
import java.io.File
import kotlin.test.*

/** Pure rules of the filing at reception (docs/STORAGE.md): category table, collisions, path safety, FAT32, idempotence, the on-demand plan, the index. */
class FilingTest {
    private data class Row(val name: String, val cat: Filing.Category, val folder: String, val clean: String)

    private val table = listOf(
        // series (English and French release names)
        Row("Prison.Break.S01E04.720p.HDTV.x264-GRP.mkv", SERIES, "Séries/Prison Break/Saison 01", "Prison Break – S01E04.mkv"),
        Row("Prison Break - 2x05 - Titre.avi", SERIES, "Séries/Prison Break/Saison 02", "Prison Break – S02E05 – Titre.avi"),
        Row("Game.of.Thrones.S08E06.1080p.WEB-DL.mkv", SERIES, "Séries/Game of Thrones/Saison 08", "Game of Thrones – S08E06.mkv"),
        Row("Les.Revenants.S02E03.FRENCH.720p.mkv", SERIES, "Séries/Les Revenants/Saison 02", "Les Revenants – S02E03.mkv"),
        Row("My.Show.2021.S03E10.720p.NF.WEBRip.mkv", SERIES, "Séries/My Show (2021)/Saison 03", "My Show (2021) – S03E10.mkv"),
        Row("Friends.1x01.The.Pilot.avi", SERIES, "Séries/Friends/Saison 01", "Friends – S01E01 – The Pilot.avi"),
        Row("Ghana.Must.Go.S01E03.NollyWood.mkv", SERIES, "Séries/Ghana Must Go/Saison 01", "Ghana Must Go – S01E03 – NollyWood.mkv"),
        Row("Show.Name.2024.03.15.HDTV.mkv", SERIES, "Séries/Show Name/Saison 2024", "Show Name – 2024-03-15.mkv"),
        Row("The Daily Show 2024-03-15.mp4", SERIES, "Séries/The Daily Show/Saison 2024", "The Daily Show – 2024-03-15.mp4"),
        Row("Prison.Break.S01E04.fr.srt", SERIES, "Séries/Prison Break/Saison 01", "Prison Break – S01E04.fr.srt"),
        // films, including Nigerian-release style names
        Row("The.Matrix.1999.1080p.BluRay.x264.mkv", FILMS, "Films", "The Matrix (1999).mkv"),
        Row("Inception (2010) MULTI 1080p.mkv", FILMS, "Films", "Inception (2010) [MULTI].mkv"),
        Row("Avatar.2009.TRUEFRENCH.DVDRip.avi", FILMS, "Films", "Avatar (2009).avi"),
        Row("Kill.Bill.2003.part1.mkv", FILMS, "Films", "Kill Bill (2003) - part1.mkv"),
        Row("Living.in.Bondage.Breaking.Free.2019.NGA.720p.WEBRip.mp4", FILMS, "Films", "Living in Bondage Breaking Free (2019).mp4"),
        Row("[Nollywood] Omo Ghetto 2020 HDRip.mp4", FILMS, "Films", "Omo Ghetto (2020).mp4"),
        Row("Nollywood.Movie.2022.Part.2.720p.mkv", FILMS, "Films", "Nollywood Movie (2022) - part2.mkv"),
        // music and clips
        Row("Daft Punk - Get Lucky.mp3", MUSIC, "Musique", "Daft Punk – Get Lucky.mp3"),
        Row("Fally Ipupa - Eloko Oyo.m4a", MUSIC, "Musique", "Fally Ipupa – Eloko Oyo.m4a"),
        Row("track.flac", MUSIC, "Musique", "track.flac"),
        Row("Burna Boy - Last Last (Official Video).mp4", MUSIC, "Musique/Clips", "Burna Boy – Last Last.mp4"),
        // photos, captures, family videos
        Row("IMG_20240315_142233.jpg", PHOTOS, "Photos", "Photo – 2024-03-15 14h22.jpg"),
        Row("PXL_20240315_142233123.jpg", PHOTOS, "Photos", "Photo – 2024-03-15 14h22.jpg"),
        Row("vacances.jpg", PHOTOS, "Photos", "vacances.jpg"),
        Row("Screenshot_20240315-142233.png", CAPTURES, "Captures", "Capture d'écran – 2024-03-15 14h22.png"),
        Row("Screen_Recording_20240315_142233.mp4", CAPTURES, "Captures", "Enregistrement d'écran – 2024-03-15 14h22.mp4"),
        Row("VID_20240315_142233.mp4", FAMILY, "Famille", "Vidéo – 2024-03-15 14h22.mp4"),
        Row("WhatsApp Video 2024-03-15 at 14.22.33.mp4", FAMILY, "Famille", "Vidéo WhatsApp – 2024-03-15 14h22.mp4"),
        // courses, documents, archives
        Row("Cours de maths terminale chapitre 3.mp4", COURSES, "Cours/Mathématiques", "Cours de maths terminale chapitre 3.mp4"),
        Row("Physics lecture 04 - Newton laws.mp4", COURSES, "Cours/Physique-Chimie", "Physics lecture 04 - Newton laws.mp4"),
        Row("facture.pdf", DOCUMENTS, "Documents", "facture.pdf"),
        Row("CV Esaie.docx", DOCUMENTS, "Documents", "CV Esaie.docx"),
        Row("budget.xlsx", DOCUMENTS, "Documents", "budget.xlsx"),
        Row("livre.epub", DOCUMENTS, "Documents", "livre.epub"),
        Row("backup.zip", ARCHIVES, "Archives", "backup.zip"),
        Row("archive.7z", ARCHIVES, "Archives", "archive.7z"),
        Row("film.iso", ARCHIVES, "Archives", "film.iso"),
        // the safe default: unknown, unsure, executable, anything the parser does not trust
        Row("ma_video.mp4", TO_SORT, "À trier", "ma_video.mp4"),
        Row("a.b.c.d.mkv", TO_SORT, "À trier", "a.b.c.d.mkv"),
        Row("Naruto Shippuden 245.mkv", TO_SORT, "À trier", "Naruto Shippuden 245.mkv"),
        Row("One.Piece.1045.VOSTFR.mkv", TO_SORT, "À trier", "One.Piece.1045.VOSTFR.mkv"),
        Row("unknown.xyz", TO_SORT, "À trier", "unknown.xyz"),
        Row("noext", TO_SORT, "À trier", "noext"),
        Row("setup.exe", TO_SORT, "À trier", "setup.exe"),
        Row("sous-titres.srt", TO_SORT, "À trier", "sous-titres.srt"),
    )

    @Test fun categoryTable() {
        assertTrue(table.size >= 40)
        for (r in table) {
            val x = Filing.classify(r.name)
            assertEquals(Triple(r.cat, r.folder, r.clean), Triple(x.category, x.folder, x.name), "classement de « ${r.name} »")
            assertFalse(x.keepFlat, r.name)
        }
    }

    @Test fun classifyIsIdempotent() {
        // what was filed and named once keeps its place and its name when seen again (a second « Ranger » changes nothing)
        for (r in table) {
            val x = Filing.classify(r.name)
            val y = Filing.classify(x.name)
            assertEquals(x.name, y.name, "nom de « ${r.name} » instable")
            assertEquals(x.folder, y.folder, "dossier de « ${r.name} » instable")
        }
    }

    @Test fun installersAndPackagesStayFlatOnReception() {
        for (n in listOf("installer.apk", "app-release.XAPK", "game.apks", "Lot-01.lot.zip", "pack.learn.zip", "q.quiz.zip")) {
            val x = Filing.classify(n)
            assertTrue(x.keepFlat, n); assertEquals("", x.folder); assertEquals(n, x.name)
            assertNull(Filing.place(x, Fs.EXT4, 10) { false }, "never placed on reception: $n")
        }
        assertEquals(APPS, Filing.classify("installer.apk").category)
    }

    @Test fun mimeGivesTheMissingExtension() {
        assertEquals("Photos", Filing.classify("IMG0001", "image/jpeg").folder)
        assertEquals("IMG0001.jpg", Filing.classify("IMG0001", "image/jpeg").name)
        assertEquals("Documents", Filing.classify("rapport", "application/pdf").folder)
        assertEquals("À trier", Filing.classify("mystère", "application/x-unknown").folder)
    }

    @Test fun englishFoldersWhenTheInterfaceIsEnglish() {
        val x = Filing.classify("Prison.Break.S01E04.720p.mkv", lang = "en")
        assertEquals("Series/Prison Break/Season 01", x.folder)
        assertEquals("Movies", Filing.classify("The.Matrix.1999.1080p.mkv", lang = "en").folder)
    }

    @Test fun traversalAndHostileNamesNeverEscape() {
        val root = kotlin.io.path.createTempDirectory("fil").toFile()
        try {
            for (n in listOf("../../etc/passwd", "..\\..\\evil.mkv", "C:\\Users\\x\\film.mkv", "/abs/olute.mp4", "a/../../b.mp4", "..", ".", "...", "\u0000x.mp4", "a:b?c*.mp4", "CON.mp4", "con.txt", ".hidden.mp4", "x".repeat(400) + ".mkv", "é".repeat(200) + ".mp4")) {
                val x = Filing.classify(n)
                assertFalse("/" in x.name || "\\" in x.name || x.name.startsWith("."), "nom sûr: ${x.name}")
                assertTrue(x.name.toByteArray().size <= 250, "255 octets: ${x.name.length}")
                val p = Filing.place(x, Fs.EXFAT, 1) { false } ?: continue
                val f = UsbPaths.resolve(root, p.rel)
                assertNotNull(f, p.rel)
                assertTrue(f.canonicalPath.startsWith(root.canonicalPath + File.separator))
                assertTrue(p.rel.split('/').size <= UsbPaths.MAX_DEPTH)
                assertTrue(p.rel.split('/').all { it.toByteArray().size <= 255 && UsbPaths.badSegment(it) == null })
            }
        } finally { root.deleteRecursively() }
        // a name that is a path keeps only its last segment
        assertEquals("passwd", Filing.classify("../../etc/passwd").name)
        assertEquals("evil.mkv", Filing.classify("..\\..\\evil.mkv").name)
    }

    @Test fun aFolderThatIsNotSafeIsRefused() {
        val bad = Filing.Result(DOCUMENTS, "Documents/../..", "x.pdf", "t")
        assertNull(Filing.place(bad, Fs.EXT4, 1) { false })
        assertNull(Filing.place(Filing.Result(DOCUMENTS, "/abs", "x.pdf", "t"), Fs.EXT4, 1) { false })
        assertNull(Filing.place(Filing.Result(DOCUMENTS, "a/b/c/d/e/f/g/h/i", "x.pdf", "t"), Fs.EXT4, 1) { false })
    }

    @Test fun collisionsAddANumberAndNeverOverwrite() {
        val r = Filing.classify("The.Matrix.1999.1080p.BluRay.x264.mkv")
        val used = mutableSetOf<String>()
        val names = (1..4).map { Filing.place(r, Fs.EXT4, 1) { n -> n.lowercase() in used.map { u -> u.lowercase() } }!!.also { used += it.name }.name }
        assertEquals(listOf("The Matrix (1999).mkv", "The Matrix (1999) (2).mkv", "The Matrix (1999) (3).mkv", "The Matrix (1999) (4).mkv"), names)
        // case-insensitive: "the matrix (1999).MKV" is the same name on a FAT/exFAT drive
        assertEquals("The Matrix (1999) (2).mkv", Filing.place(r, Fs.EXFAT, 1) { it.equals("THE MATRIX (1999).MKV", true) }!!.name)
        // a file never collides with itself (a second « Ranger » does not rename it to « (2) »)
        assertEquals("The Matrix (1999).mkv", Filing.place(r, Fs.EXT4, 1, self = "The Matrix (1999).mkv") { true }!!.name)
        // a very long name stays within 250 bytes after the number
        val long = Filing.Result(DOCUMENTS, "Documents", "é".repeat(120) + ".pdf", "t")
        val p = Filing.place(long, Fs.EXT4, 1) { n -> !n.contains("(2)") }!!
        assertTrue(p.name.contains("(2)") && p.name.toByteArray().size <= 250 && p.name.endsWith(".pdf"))
        assertTrue(p.renamedForCollision)
    }

    @Test fun fat32AwarenessAndNames() {
        val big = Filing.classify("film.2010.1080p.mkv")
        assertNotNull(Filing.sizeRefusal(Filing.FAT32_MAX + 1, Fs.FAT32))
        assertNull(Filing.sizeRefusal(Filing.FAT32_MAX, Fs.FAT32))
        assertNull(Filing.sizeRefusal(Filing.FAT32_MAX + 1, Fs.EXFAT))
        assertNull(Filing.place(big, Fs.FAT32, Filing.FAT32_MAX + 1) { false }, "never placed on FAT32 when it cannot be held")
        assertNotNull(Filing.place(big, Fs.EXFAT, Filing.FAT32_MAX + 1) { false })
        // forbidden characters of FAT/exFAT never reach the disk
        val p = Filing.place(Filing.Result(DOCUMENTS, "Documents", "a:b.pdf", "t"), Fs.FAT32, 1) { false }!!
        assertTrue(p.name.none { UsbPaths.badSegment(p.name) != null })
        assertNull(UsbPaths.badSegment(p.name))
    }

    @Test fun planShowsThePlanAndSkipsWhatMustNotMove() {
        val files = listOf(
            Filing.PlanFile("v", "Prison.Break.S01E04.720p.mkv", 10, Fs.EXT4),
            Filing.PlanFile("v", "Prison.Break.S01E04.1080p.WEB.mkv", 20, Fs.EXT4),          // same clean name: numbered
            Filing.PlanFile("v", "The.Matrix.1999.1080p.mkv", 10, Fs.EXT4, skip = "protégé"),
            Filing.PlanFile("v", "installer.apk", 5, Fs.EXT4),
            Filing.PlanFile("v", "pack.learn.zip", 5, Fs.EXT4),
            Filing.PlanFile("v", "big.2010.mkv", Filing.FAT32_MAX + 1, Fs.FAT32),
            Filing.PlanFile("v", "facture.pdf", 5, Fs.EXT4),
        )
        val plan = Filing.plan(files) { false }
        val byFrom = plan.moves.associateBy { it.from }
        assertEquals("Séries/Prison Break/Saison 01/Prison Break – S01E04.mkv", byFrom["Prison.Break.S01E04.1080p.WEB.mkv"]!!.rel)
        assertEquals("Séries/Prison Break/Saison 01/Prison Break – S01E04 (2).mkv", byFrom["Prison.Break.S01E04.720p.mkv"]!!.rel)
        assertEquals("Applications/installer.apk", byFrom["installer.apk"]!!.rel, "an installer is filed only on demand")
        assertEquals("Documents/facture.pdf", byFrom["facture.pdf"]!!.rel)
        val skipped = plan.skipped.toMap()
        assertEquals("protégé", skipped["The.Matrix.1999.1080p.mkv"])
        assertTrue("pack.learn.zip" in skipped && "big.2010.mkv" in skipped)
        assertTrue(plan.moves.none { it.from == "The.Matrix.1999.1080p.mkv" || it.from == "pack.learn.zip" })
        assertTrue(Filing.summary(plan).contains("à ranger"))
        assertEquals("Rien à ranger : tout est déjà en place.", Filing.summary(Filing.plan(emptyList()) { false }))
    }

    @Test fun planNamesAreAlsoUniqueAgainstTheLibrary() {
        val plan = Filing.plan(listOf(Filing.PlanFile("v", "The.Matrix.1999.mkv", 1, Fs.EXT4))) { it.equals("The Matrix (1999).mkv", true) }
        assertEquals("The Matrix (1999) (2).mkv", plan.moves.single().to)
    }

    // ---- the index of filed files ----

    @Test fun indexKeepsFilesAndOriginsAcrossRestarts() {
        val dir = kotlin.io.path.createTempDirectory("idx").toFile()
        try {
            File(dir, "Films").mkdirs(); File(dir, "Films/The Matrix (1999).mkv").writeText("x")
            val a = FiledIndex(dir)
            a.put("The Matrix (1999).mkv", "Films/The Matrix (1999).mkv", "The.Matrix.1999.1080p.mkv")
            val b = FiledIndex(dir)                                           // a new process
            assertEquals("Films/The Matrix (1999).mkv", b.locate("the matrix (1999).MKV"))
            assertEquals("The Matrix (1999).mkv", b.keyOfOrigin("The.Matrix.1999.1080p.mkv"))
            assertEquals("The.Matrix.1999.1080p.mkv", b.originOf("The Matrix (1999).mkv"))
            assertNull(b.keyOfOrigin("autre.mkv"))
            File(dir, "Films/The Matrix (1999).mkv").renameTo(File(dir, "Films/Matrix.mkv"))
            b.renamed("The Matrix (1999).mkv", "Matrix.mkv", "Films/Matrix.mkv")
            assertEquals("Matrix.mkv", FiledIndex(dir).keyOfOrigin("The.Matrix.1999.1080p.mkv"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun indexForgetsVanishedFilesButNotWhileTheVolumeIsAway() {
        val dir = kotlin.io.path.createTempDirectory("idx").toFile()
        try {
            File(dir, "Films").mkdirs(); val f = File(dir, "Films/a.mkv"); f.writeText("x")
            val i = FiledIndex(dir); i.put("a.mkv", "Films/a.mkv", "A.orig.mkv")
            f.delete()
            assertNull(i.locate("a.mkv")); assertNull(i.keyOfOrigin("A.orig.mkv")); assertEquals(0, i.size())
            // the drive is pulled: the folder is gone, nothing is forgotten
            val d2 = kotlin.io.path.createTempDirectory("idx2").toFile()
            File(d2, "Films").mkdirs(); File(d2, "Films/b.mkv").writeText("x")
            val j = FiledIndex(d2); j.put("b.mkv", "Films/b.mkv", null)
            val aside = File(d2.parentFile, d2.name + "-away"); d2.renameTo(aside)
            assertNull(j.locate("b.mkv")); assertEquals(1, j.size())
            aside.renameTo(d2); assertNotNull(j.locate("b.mkv"))
            d2.deleteRecursively()
        } finally { dir.deleteRecursively() }
    }

    @Test fun lostIndexIsRebuiltFromTheCategoryFolders() {
        val dir = kotlin.io.path.createTempDirectory("idx").toFile()
        try {
            File(dir, "Séries/Prison Break/Saison 01").mkdirs(); File(dir, "Séries/Prison Break/Saison 01/Prison Break – S01E04.mkv").writeText("x")
            File(dir, "Autre").mkdirs(); File(dir, "Autre/ignored.mkv").writeText("x")        // not a category folder: not ours
            File(dir, "flat.mkv").writeText("x")                                              // flat files are not "filed"
            File(dir, ".castbridge-trash").mkdirs(); File(dir, ".castbridge-trash/t__x.mkv").writeText("x")
            val i = FiledIndex(dir)
            assertEquals("Séries/Prison Break/Saison 01/Prison Break – S01E04.mkv", i.locate("Prison Break – S01E04.mkv"))
            assertEquals(1, i.size())
            // a cut between a rename and its entry: resync adds what the index does not know
            File(dir, "Films").mkdirs(); File(dir, "Films/late.mkv").writeText("x")
            assertNull(i.locate("late.mkv")); i.resync(); assertEquals("Films/late.mkv", i.locate("late.mkv"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun tvInfoFindsAFiledFileByTheNameItWasSentUnder() {
        val j = """{"files":[{"name":"The Matrix (1999).mkv","size":100,"received":100,"complete":true,"volume":"internal","duplicate":false,"folder":"Films","origin":"The.Matrix.1999.mkv"},""" +
            """{"name":"old.mkv","size":5,"received":5,"complete":true,"volume":"internal","duplicate":false}],"free":1,"used":1,"quota":1,"player":{"state":"idle"}}"""
        val info = TvInfo.parse(j)
        assertEquals("Films", info.file("The Matrix (1999).mkv")!!.folder)
        assertTrue(TvDedupe.alreadyThere(info.file("The.Matrix.1999.mkv"), 100))
        assertFalse(TvDedupe.alreadyThere(info.file("The.Matrix.1999.mkv"), 101), "another size is another file")
        assertEquals("", info.file("old.mkv")!!.folder); assertNull(info.file("old.mkv")!!.origin)   // an older TV says nothing more
    }
}
