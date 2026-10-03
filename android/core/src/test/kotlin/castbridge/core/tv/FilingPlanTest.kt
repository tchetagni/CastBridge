package castbridge.core.tv

import castbridge.core.library.agent.SafeName
import kotlin.test.*

/** R-13 « le rangement ne crée pas d'arborescence de classement » : le plan pur (catégorie + titre propre), docs/agent-reports/filing-tree.md. */
class FilingPlanTest {
    private fun plan(name: String, mime: String? = null, size: Long = 1000, title: String? = null, artist: String? = null, album: String? = null, date: String? = null) =
        FilingPlan.plan(FilingPlan.Input(name, mime, size, title, artist, album, date), currentYear = 2026)
    private fun rel(r: Filing.Result) = if (r.folder.isEmpty()) r.name else "${r.folder}/${r.name}"

    @Test fun anUnidentifiedVideoGoesToFilmsNotToATrier() {
        assertEquals("Films/ma_video.mp4", rel(plan("ma_video.mp4")))
        assertEquals("Films/Avatar.mp4", rel(plan("Avatar.mp4")))
        assertEquals(Filing.Category.FILMS, plan("Avatar.mp4").category)
    }

    @Test fun genericAndContentIdNamesUseTheMediaTitleNeverARandomId() {
        assertEquals("Films/Avatar.mp4", rel(plan("video.mp4", "video/mp4", title = "Avatar")), "nom générique + titre des métadonnées")
        assertEquals("Films/Le Roi Lion.mp4", rel(plan("1000023456", "video/mp4", title = "Le Roi Lion")), "identifiant de contenu : le titre")
        assertEquals("Famille", plan("1000023457", "video/mp4", title = "Mariage de Paul").folder, "le titre décide aussi de la catégorie")
        val noTitle = plan("msf:1234", "video/mp4", date = "2026-10-03")
        assertFalse(noTitle.name.contains("1234"), "jamais un identifiant aléatoire : ${noTitle.name}")
        assertTrue(noTitle.name.endsWith(".mp4") && noTitle.folder == "Films", rel(noTitle))
        assertFalse(plan("1000023456", "image/jpeg").name.contains("1000023456"))
        assertTrue(FilingPlan.looksLikeId("1000023456")); assertTrue(FilingPlan.looksLikeId("msf:1234")); assertTrue(FilingPlan.looksLikeId("video:42"))
        assertTrue(FilingPlan.looksLikeId("1000023456.mp4")); assertFalse(FilingPlan.looksLikeId("Avatar.mp4")); assertFalse(FilingPlan.looksLikeId("2012.mkv"))
        assertFalse(FilingPlan.looksLikeId("IMG_1234.jpg"), "un nom d'appareil photo n'est pas un identifiant")
    }

    @Test fun seriesGoToTheirTitleAndSeason() {
        assertEquals("Séries/Prison Break/Saison 01/Prison Break – S01E04.mkv", rel(plan("Prison.Break.S01E04.720p.HDTV.x264-GRP.mkv")))
    }

    @Test fun filmsWithAYearKeepTheirCleanName() {
        assertEquals("Films/The Matrix (1999).mkv", rel(plan("The.Matrix.1999.1080p.BluRay.x264.mkv")))
    }

    @Test fun musicUsesArtistAndAlbumTagsWhenTheyExist() {
        assertEquals("Musique/Burna Boy/Love, Damini", plan("piste01.mp3", artist = "Burna Boy", album = "Love, Damini").folder)
        assertEquals("Musique/Burna Boy", plan("piste01.mp3", artist = "Burna Boy").folder)
        assertEquals("Musique", plan("chanson.mp3").folder)
        assertEquals("Musique", plan("1000023999", "audio/mpeg").folder)
    }

    @Test fun photosGoToTheirMonthWhenTheDateIsKnown() {
        assertTrue(plan("IMG_20240315_142233.jpg").folder.startsWith("Photos/2024-03"), plan("IMG_20240315_142233.jpg").folder)
        assertEquals("Photos/2023-07", plan("vacances.jpg", date = "2023-07-02").folder)
        assertEquals("Photos", plan("vacances.jpg").folder)
    }

    @Test fun documentsAndUnknownFiles() {
        assertEquals("Documents/facture.pdf", rel(plan("facture.pdf")))
        assertEquals("À trier/notes.xyz", rel(plan("notes.xyz")), "inconnu : « À trier » (le dossier « Autres » de la bibliothèque)")
        assertTrue(plan("installer.apk").keepFlat, "un installateur reste à plat (lu par son nom)")
        assertTrue(plan("pack.learn.zip").keepFlat)
    }

    @Test fun unicodeLongAndExfatForbiddenNamesArePlacedSafely() {
        val r = plan("Amélie : le fabuleux destin ? (2001).mkv")
        assertEquals("Films", r.folder)
        assertNull(SafeName.checkName(r.name), r.name)
        val pl = Filing.place(r, Fs.EXFAT, 1000) { false }!!
        assertFalse(pl.rel.any { it in ":?*\"<>|\\" }, pl.rel)
        val long = "Un très long titre de vidéo de randonnée en montagne ".repeat(12) + ".mp4"
        val lr = plan(long)
        assertEquals("Films", lr.folder)
        val lp = Filing.place(lr, Fs.EXFAT, 1000) { false }!!
        assertTrue(lp.name.toByteArray(Charsets.UTF_8).size <= 250, "${lp.name.length}")
        assertTrue(lp.name.endsWith(".mp4"))
        assertEquals("Films/日本の夏.mp4", rel(plan("日本の夏.mp4")))
    }

    @Test fun duplicateNamesGetANumberAndNothingIsOverwritten() {
        val r = plan("Prison.Break.S01E04.1080p.WEB.mkv")
        val taken = setOf("prison break – s01e04.mkv")
        val pl = Filing.place(r, Fs.EXFAT, 1000) { it.lowercase() in taken }!!
        assertEquals("Prison Break – S01E04 (2).mkv", pl.name)
        assertTrue(pl.renamedForCollision)
    }

    @Test fun planningTheResultAgainChangesNothing() {
        for (n in listOf("Prison.Break.S01E04.720p.mkv", "The.Matrix.1999.1080p.mkv", "ma_video.mp4", "facture.pdf", "IMG_20240315_142233.jpg")) {
            val r = plan(n); val again = plan(r.name, date = if (r.folder.startsWith("Photos/")) "2024-03-15" else null)
            assertEquals(r.folder, again.folder, n); assertEquals(r.name, again.name, n)
        }
    }

    @Test fun theSameTreeOnThePhoneUnderDownloadCastBridge() {
        assertEquals("CastBridge/Séries/Prison Break/Saison 01", FilingPlan.phoneDir("Prison Break – S01E04.mkv"))
        assertEquals("CastBridge/Films", FilingPlan.phoneDir("ma_video.mp4"))
        assertEquals("CastBridge/Documents", FilingPlan.phoneDir("facture.pdf"))
        assertEquals("CastBridge", FilingPlan.phoneDir("installer.apk"), "un installateur reste à la racine")
        assertEquals("CastBridge/À trier", FilingPlan.phoneDir("notes.xyz"))
    }

    @Test fun theFolderTreeIsOnByDefaultForNewCopiesAndCanBeSwitchedOff() {
        val s = castbridge.core.library.agent.AgentSettings(castbridge.core.connect.MemoryKeyValueStore())
        assertTrue(s.fileTree, "par défaut : les nouvelles copies sont classées")
        assertFalse(s.autoRename, "le renommage à l'envoi reste désactivé par défaut")
        s.fileTree = false; assertFalse(s.fileTree)
        s.fileTree = true; assertTrue(s.fileTree)
        s.fileTree = false; s.clearAll(); assertTrue(s.fileTree, "« Effacer » revient au défaut")
    }

    @Test fun thePhoneSendsAMeaningfulNameNeverAContentId() {
        assertEquals("Mariage de Paul.mp4", FilingPlan.sendName("1000023456", "video/mp4", "Mariage de Paul", "2026-10-03"))
        // no title: a STABLE name (resume after a cut, never NAME_TAKEN between two videos of the same day); the TV files it under « Vidéo… », never the id
        assertEquals("msf_1234.mp4", FilingPlan.sendName("msf:1234", "video/mp4", null, "2026-10-03"))
        assertEquals("1000023456.mp4", FilingPlan.sendName("1000023456", "video/mp4", null, "2026-10-03"))
        assertTrue(FilingPlan.looksLikeId("msf_1234.mp4"))
        assertFalse(plan("msf_1234.mp4").name.contains("1234"))
        assertEquals("Mon film.mkv", FilingPlan.sendName("Mon film", "video/x-matroska", null, "2026-10-03"), "extension ajoutée d'après le type")
        assertEquals("Photo du 2026-10-03.jpg", FilingPlan.sendName(null, "image/jpeg", null, "2026-10-03"))
        assertEquals("Avatar.mp4", FilingPlan.sendName("Avatar.mp4", "video/mp4", "Autre titre", "2026-10-03"), "un vrai nom ne change pas")
        assertEquals("Avatar.mp4", FilingPlan.sendName("video.mp4", "video/mp4", "Avatar", "2026-10-03"), "nom générique : le titre")
        assertEquals("video.mp4", FilingPlan.sendName("video.mp4", "video/mp4", null, "2026-10-03"), "sans titre, un nom générique valide reste")
        val cleaned = FilingPlan.sendName("1000023456", "video/mp4", "A/B : C?", "2026-10-03")
        assertNull(SafeName.checkName(cleaned), cleaned); assertTrue(cleaned.endsWith(".mp4"))
    }
}
