package castbridge.core.tv

import castbridge.core.FakePlayer
import castbridge.core.xfer.*
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.random.Random
import kotlin.test.*

/**
 * Relecture Opus de ff3cf199 (BLOQUANT) : le repli « Déplacer devient une copie normale » ne doit jamais supprimer l'original quand la TV répond « done » pour
 * un fichier qu'elle avait déjà (même nom, même taille, aucun octet envoyé). Vraie ReceiverServer en boucle locale, vrais clients d'envoi.
 */
class MoveFallbackServerTest {
    private val dir = kotlin.io.path.createTempDirectory("movefb").toFile()
    private val server = ReceiverServer(VolumeRegistry.single(dir), FakePlayer(), 0, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0), hostCheck = false)
        .apply { start(5000, false) }
    private val base = "http://127.0.0.1:${server.listeningPort}"
    private val tv = TvClient(base)
    private val mine = Random(21).nextBytes(250_000)                       // the phone's photo (retouched)
    private val theirs = Random(22).nextBytes(250_000)                     // the TV's file: same name, same size, other content

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    private fun classic(name: String, bytes: ByteArray) =
        ResumableUpload(name, bytes.size.toLong(), { base }, { off -> ByteArrayInputStream(bytes, off.toInt(), bytes.size - off.toInt()) }, sleep = {})
    private fun tvNames() = TvInfo.parse(tv.info()).files.map { it.name.lowercase() }.toSet()

    @Test fun aDoneForAFileTheTvAlreadyHadKeepsTheOriginal() {
        tv.upload("photo.jpg", 0, theirs.size.toLong(), ByteArrayInputStream(theirs)) {}
        val up = classic("photo.jpg", mine)
        assertEquals(ResumableUpload.State.Done, up.run { }, "la TV répond « done » sans qu'un octet parte")
        assertEquals(-1L, up.firstOffset)
        assertFalse(up.sentWholeFile(true), "aucun octet envoyé par ce travail : aucune preuve")
        val f = TvInfo.parse(tv.info()).file("photo.jpg")!!
        assertEquals(MoveProof.AfterSend.KEEP_SAME_NAME_UNVERIFIED, MoveProof.afterSend(up.sentWholeFile(true), contentProof = false, tvComplete = f.complete, tvSize = f.size, localSize = mine.size.toLong()))
        assertContentEquals(theirs, File(dir, "photo.jpg").readBytes(), "rien n'est écrasé")
    }

    @Test fun aRealUploadOfEveryByteIsTheProofOfTheUsualMove() {
        val up = classic("vacances.jpg", mine)
        assertEquals(ResumableUpload.State.Done, up.run { })
        assertTrue(up.sentWholeFile(true))
        val f = TvInfo.parse(tv.info()).file("vacances.jpg")!!
        assertEquals(MoveProof.AfterSend.DELETE, MoveProof.afterSend(up.sentWholeFile(true), false, f.complete, f.size, mine.size.toLong()))
    }

    @Test fun theFastPathBeginDoneIsNoProofEither() {
        tv.upload("clip.mp4", 0, theirs.size.toLong(), ByteArrayInputStream(theirs)) {}
        val src = File(dir.parentFile, "src-${System.nanoTime()}.bin").apply { writeBytes(mine); deleteOnExit() }
        fun fast(name: String) = FileChannel.open(src.toPath(), StandardOpenOption.READ).use { ch ->
            TransferClient(HttpTransferApi(base) { null }, FileBlockSource(ch), name, { id, max ->
                listOf(WifiLane("wifi", "127.0.0.1:${server.listeningPort}", id, HttpConn.tcp("127.0.0.1", server.listeningPort), { null }, maxStreams = max, fixedK = 2))
            }, retryDelayMs = 50).let { tc -> tc.run() to tc.verifiedWhole }
        }
        val (r1, v1) = fast("clip.mp4")
        assertEquals(TransferClient.Result.Done, r1); assertFalse(v1, "« done » au begin : la TV avait déjà un fichier de ce nom et de cette taille")
        val (r2, v2) = fast("clip-neuf.mp4")
        assertEquals(TransferClient.Result.Done, r2); assertTrue(v2, "tous les blocs envoyés par ce passage, racine vérifiée par la TV")
    }

    @Test fun sameNameSameSizeOtherContentIsSentUnderAUniqueName() {
        tv.upload("photo.jpg", 0, theirs.size.toLong(), ByteArrayInputStream(theirs)) {}
        val names = tvNames()
        val sendAs = DedupDecision.uniqueName("photo.jpg") { it.lowercase() in names }
        assertEquals("photo (2).jpg", sendAs)
        val up = classic(sendAs, mine)
        assertEquals(ResumableUpload.State.Done, up.run { })
        assertTrue(up.sentWholeFile(true), "le contenu du téléphone est réellement envoyé")
        assertContentEquals(theirs, File(dir, "photo.jpg").readBytes(), "jamais d'écrasement")
        assertContentEquals(mine, File(dir, "photo (2).jpg").readBytes())
    }
}
