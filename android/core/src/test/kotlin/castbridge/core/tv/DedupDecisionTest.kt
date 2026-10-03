package castbridge.core.tv

import castbridge.core.tv.DedupDecision.Action
import castbridge.core.tv.DedupDecision.Facts
import castbridge.core.tv.DedupDecision.Outcome
import castbridge.core.tv.DedupDecision.Tv
import kotlin.test.*

/** R-12 « éviter les doublons lors de la copie » : la table de décision pure (docs/agent-reports/copy-dedup.md). */
class DedupDecisionTest {
    private val sha = "a".repeat(64)
    private val other = "b".repeat(64)
    private val size = 1_234_567L
    private fun present(s: String = sha, sz: Long = size, complete: Boolean = true) = Tv.Present("Avatar (2009).mkv", "Films", sz, s, complete)

    @Test fun identicalPresentSkipsTheCopyAndSaysWhere() {
        val o = DedupDecision.decide(Facts(Action.COPY, size, sha, present()))
        assertIs<Outcome.Skip>(o)
        assertFalse(o.play); assertFalse(o.deleteSource)
        assertEquals("Avatar (2009).mkv", o.tvName)
        assertEquals("Déjà sur la TV : Films/Avatar (2009).mkv — contenu identique, non recopié.", o.text)
    }

    @Test fun identicalPresentWithCopyAndPlayPlaysTheTvFile() {
        val o = DedupDecision.decide(Facts(Action.COPY_AND_PLAY, size, sha, present()))
        assertIs<Outcome.Skip>(o); assertTrue(o.play); assertFalse(o.deleteSource)
        assertTrue(o.text.contains("lecture du fichier de la TV"), o.text)
    }

    @Test fun sameSizeDifferentHashCopies() {
        assertIs<Outcome.Copy>(DedupDecision.decide(Facts(Action.COPY, size, sha, present(s = other))))
        assertIs<Outcome.Copy>(DedupDecision.decide(Facts(Action.COPY, size, sha, present(sz = size + 1))))
    }

    @Test fun indexNotReadyCopies() {
        assertIs<Outcome.Copy>(DedupDecision.decide(Facts(Action.COPY, size, sha, Tv.Indexing(1))))
        assertIs<Outcome.Copy>(DedupDecision.decide(Facts(Action.COPY_AND_PLAY, size, sha, Tv.Candidates(1, true))))
        assertIs<Outcome.Copy>(DedupDecision.decide(Facts(Action.MOVE, size, sha, Tv.Unsupported)))
        assertIs<Outcome.Copy>(DedupDecision.decide(Facts(Action.COPY, size, sha, Tv.Absent)))
    }

    @Test fun aPartialFileOnTheTvNeverCounts() {
        assertIs<Outcome.Copy>(DedupDecision.decide(Facts(Action.COPY, size, sha, present(complete = false))))
        assertIs<Outcome.Copy>(DedupDecision.decide(Facts(Action.MOVE, size, sha, present(complete = false))))
    }

    @Test fun moveWithAVerifiedIdenticalCopyMayDeleteTheSourceAfterTheHashCheck() {
        val o = DedupDecision.decide(Facts(Action.MOVE, size, sha, present()))
        assertIs<Outcome.Skip>(o)
        assertTrue(o.deleteSource, "hash local = hash de la TV, fichier complet : suppression proposée (avec la confirmation d'Android)")
        assertFalse(o.play)
        assertTrue(o.text.contains("Android demande confirmation"), o.text)
    }

    @Test fun moveWithAnUnverifiableCopyKeepsTheSource() {
        // no local hash (cancelled, unreadable): never a skip, never a deletion
        assertIs<Outcome.Copy>(DedupDecision.decide(Facts(Action.MOVE, size, null, present())))
        assertIs<Outcome.Copy>(DedupDecision.decide(Facts(Action.MOVE, size, "pas-un-hash", present())))
        // a twin of this queue (two phone files compared together, the TV did not confirm a hash): skipped, the original STAYS
        val twin = DedupDecision.Twin("film-copie.mkv", "film.mkv", size, sha, onTv = true)
        val o = DedupDecision.decide(Facts(Action.MOVE, size, sha, Tv.Indexing(1), twin))
        assertIs<Outcome.Skip>(o)
        assertFalse(o.deleteSource)
        assertTrue(o.text.contains("l'original reste sur le téléphone"), o.text)
    }

    @Test fun aQueueTwinThatIsNotOnTheTvCopies() {
        val twin = DedupDecision.Twin("a.mkv", "a.mkv", size, sha, onTv = false)
        assertIs<Outcome.Copy>(DedupDecision.decide(Facts(Action.COPY, size, sha, Tv.Absent, twin)))
        val ok = DedupDecision.decide(Facts(Action.COPY, size, sha, Tv.Absent, twin.copy(onTv = true)))
        assertIs<Outcome.Skip>(ok); assertTrue(ok.text.startsWith("Même contenu que « a.mkv »"), ok.text)
    }

    @Test fun copyAnywayAlwaysCopies() {
        assertIs<Outcome.Copy>(DedupDecision.decide(Facts(Action.COPY, size, sha, present(), force = true)))
        assertIs<Outcome.Copy>(DedupDecision.decide(Facts(Action.MOVE, size, sha, present(), force = true)))
    }

    @Test fun theHashIsComputedOnlyWhenASameSizeCandidateExists() {
        assertFalse(DedupDecision.mustHash(Tv.Absent, sameSizeInQueue = false), "4 Go jamais hachés pour rien")
        assertFalse(DedupDecision.mustHash(Tv.Unsupported, sameSizeInQueue = false))
        assertTrue(DedupDecision.mustHash(Tv.Candidates(1, false), sameSizeInQueue = false))
        assertTrue(DedupDecision.mustHash(Tv.Absent, sameSizeInQueue = true))
        assertFalse(DedupDecision.mustHash(Tv.Candidates(1, false), sameSizeInQueue = true, force = true))
    }

    @Test fun parsesTheTvAnswersAndDistrustsBrokenOnes() {
        assertEquals(Tv.Absent, DedupDecision.parse("""{"state":"absent"}"""))
        assertEquals(Tv.Candidates(2, true), DedupDecision.parse("""{"state":"candidates","count":2,"indexing":true}"""))
        assertEquals(Tv.Indexing(1), DedupDecision.parse("""{"state":"indexing","pending":1}"""))
        assertEquals(present(), DedupDecision.parse("""{"state":"present","name":"Avatar (2009).mkv","folder":"Films","volume":"internal","size":$size,"sha256":"$sha","complete":true}"""))
        assertEquals(Tv.Unsupported, DedupDecision.parse("""{"state":"present","name":"x","size":3,"sha256":"court"}"""))
        assertEquals(Tv.Unsupported, DedupDecision.parse("""{"error":"not found"}"""))
        assertEquals(Tv.Unsupported, DedupDecision.parse("pas du json"))
    }

    /** La règle de suppression d'un DÉPLACEMENT sans copie, lisible en 10 lignes (MoveProof.byContentHash). */
    @Test fun moveProofByContentHash() {
        assertTrue(MoveProof.byContentHash(size, sha, size, sha, tvComplete = true))
        assertTrue(MoveProof.byContentHash(size, sha.uppercase(), size, sha, tvComplete = true))
        assertFalse(MoveProof.byContentHash(size, sha, size, other, tvComplete = true), "contenu différent")
        assertFalse(MoveProof.byContentHash(size, sha, size + 1, sha, tvComplete = true), "taille différente")
        assertFalse(MoveProof.byContentHash(size, sha, size, sha, tvComplete = false), "copie partielle")
        assertFalse(MoveProof.byContentHash(0, sha, 0, sha, tvComplete = true), "fichier vide")
        assertFalse(MoveProof.byContentHash(size, null, size, sha, tvComplete = true), "pas de hash local")
        assertFalse(MoveProof.byContentHash(size, sha, size, null, tvComplete = true), "la parole de la TV sans hash")
    }

    @Test fun streamingHashMatchesTheJdkAndCanBeCancelled() {
        val data = ByteArray(3_000_001) { (it * 31).toByte() }
        val expected = ContentHash.hex(java.security.MessageDigest.getInstance("SHA-256").digest(data))
        var last = 0L
        assertEquals(expected, ContentHash.sha256(data.inputStream(), data.size.toLong(), chunk = 65536) { r, _ -> last = r })
        assertEquals(data.size.toLong(), last)
        var n = 0
        assertNull(ContentHash.sha256(data.inputStream(), data.size.toLong(), chunk = 65536, cancelled = { ++n > 3 }), "annulé : aucun hash partiel")
        assertNull(ContentHash.sha256(data.inputStream(), data.size + 1L), "taille changée : aucun hash")
    }
}
