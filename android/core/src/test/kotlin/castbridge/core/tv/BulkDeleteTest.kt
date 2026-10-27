package castbridge.core.tv

import castbridge.core.tv.BulkDelete.Entry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Suppression en masse : les préréglages (qui ne font que cocher), les textes et le contrat d'exécution. */
class BulkDeleteTest {
    private val now = 1_800_000_000_000L
    private val day = BulkDelete.DAY_MS
    private val gb = 1L shl 30
    private fun e(name: String, size: Long = 1000, folder: String = "", vol: String = "internal", mtime: Long = now, watched: Boolean = false, played: Long = 0, playing: Boolean = false) =
        Entry(name, vol, size, mtime, folder, watched, played, playing)

    @Test fun `les videos deja vues`() {
        val l = listOf(e("a", watched = true), e("b"), e("c", watched = true, playing = true))
        assertEquals(setOf(l[0].key), BulkDelete.watched(l))
    }

    @Test fun `un dossier prend ses sous-dossiers mais pas un dossier voisin`() {
        val l = listOf(e("1", folder = "Séries/Prison Break/Saison 01"), e("2", folder = "Séries/Prison Break"), e("3", folder = "Séries/Prison Breaker"), e("4"), e("5", folder = "Séries"))
        assertEquals(setOf(l[0].key, l[1].key), BulkDelete.inFolder(l, "Séries/Prison Break"))
        assertEquals(emptySet(), BulkDelete.inFolder(l, ""), "la racine ne se coche pas comme un dossier")
        assertEquals(setOf(l[0].key, l[1].key, l[2].key, l[4].key), BulkDelete.inFolder(l, "Séries/"))
    }

    @Test fun `la liste des dossiers compte les sous-dossiers dans leurs parents`() {
        val l = listOf(e("1", 10, "A/B"), e("2", 5, "A"), e("3", 1))
        assertEquals(listOf(BulkDelete.FolderStat("A", 2, 15), BulkDelete.FolderStat("A/B", 1, 10)), BulkDelete.folders(l))
    }

    @Test fun `non lus depuis N mois utilise la derniere lecture sinon la date du fichier`() {
        val l = listOf(
            e("vieux-jamais-lu", mtime = now - 100 * day),
            e("vieux-mais-lu-hier", mtime = now - 400 * day, played = now - day),
            e("recent", mtime = now - 5 * day),
            e("lu-il-y-a-4-mois", mtime = now - 10 * day, played = now - 120 * day),
            e("sans-date", mtime = 0),
            e("en-lecture", mtime = now - 400 * day, playing = true),
        )
        val three = BulkDelete.notPlayedSince(l, BulkDelete.monthsToDays(3), now)
        assertEquals(setOf(l[0].key, l[3].key), three)
        assertEquals(setOf(l[0].key, l[3].key), BulkDelete.notPlayedSince(l, BulkDelete.monthsToDays(1), now) - l[2].key)
        assertEquals(setOf(l[0].key), BulkDelete.notPlayedSince(l.filter { it.name != "lu-il-y-a-4-mois" }, 90, now))
        assertEquals(emptySet(), BulkDelete.notPlayedSince(l, BulkDelete.monthsToDays(12), now), "aucun n'a plus d'un an sans usage")
        assertEquals(listOf(30, 90, 180, 365), listOf(1, 3, 6, 12).map(BulkDelete::monthsToDays))
    }

    @Test fun `les plus gros d abord`() {
        val l = listOf(e("petit", 1), e("gros", 9), e("b", 5), e("a", 5))
        assertEquals(listOf("gros", "a", "b", "petit"), BulkDelete.largestFirst(l).map { it.name })
    }

    @Test fun `le meme nom sur deux volumes sont deux fichiers`() {
        val l = listOf(e("x.mp4", 10, vol = "internal"), e("x.mp4", 20, vol = "usb"))
        val sel = setOf(l[1].key)
        assertEquals(1, BulkDelete.count(l, sel)); assertEquals(20, BulkDelete.totalSize(l, sel))
    }

    @Test fun `les textes`() {
        assertEquals("Supprimer définitivement 23 fichiers (14,2 Go) ? Sans retour possible.", BulkDelete.confirmText(23, (14.2 * gb).toLong()))
        assertEquals("Supprimer définitivement 1 fichier (850 Mo) ? Sans retour possible.", BulkDelete.confirmText(1, 850L shl 20))
        assertEquals("1 sélectionné · 12 ko", BulkDelete.headline(1, 12_288))
        assertEquals("23 sélectionnés · 14,2 Go", BulkDelete.headline(23, (14.2 * gb).toLong()))
        assertEquals("Suppression : 4 / 23", BulkDelete.progressText(4, 23))
        assertEquals("23 fichiers supprimés, 14,2 Go libérés", BulkDelete.resultText(23, (14.2 * gb).toLong(), emptyList()))
        assertEquals("2 fichiers supprimés, 2,0 Go libérés. 2 laissés en place : déplacement en cours (1), introuvable (1)",
            BulkDelete.resultText(2, 2 * gb, listOf(BulkDelete.Skip("a", "moving"), BulkDelete.Skip("b", "absent"))))
    }

    @Test fun `le corps du lot va et vient`() {
        val t = listOf(BulkDelete.Target("a é.mp4", "usb-1"), BulkDelete.Target("b \"x\".mkv"))
        assertEquals(t, BulkDelete.parseTargets(BulkDelete.bodyOf(t)))
        assertEquals(null, BulkDelete.parseTargets("pas du json"))
        assertEquals(null, BulkDelete.parseTargets("""{"files":[{"volume":"x"}]}"""))
    }

    @Test fun `l execution compte aussi les fichiers laisses en place, survit a une erreur et finit une fois`() {
        val job = BulkDelete.Job("j", 4)
        var ends = 0
        val seen = ArrayList<Int>()
        BulkDelete.run(job, listOf("a", "b", "c", "d").map { BulkDelete.Target(it) }, { t ->
            seen += job.done
            when (t.name) { "a" -> BulkDelete.Outcome.Deleted(10); "b" -> BulkDelete.Outcome.Skipped("moving"); "c" -> error("disque"); else -> BulkDelete.Outcome.Deleted(5) }
        }, atEnd = { ends++; assertTrue(!job.finished, "terminé seulement après l'invalidation") })
        assertEquals(listOf(0, 1, 2, 3), seen)
        assertEquals(4, job.done); assertEquals(15, job.freed); assertEquals(1, ends); assertTrue(job.finished)
        assertEquals(listOf(BulkDelete.Skip("b", "moving"), BulkDelete.Skip("c", "error")), job.skipped)
        assertTrue(job.json().contains("\"finished\":true") && job.json().contains("\"reason\":\"moving\""))
    }
}
