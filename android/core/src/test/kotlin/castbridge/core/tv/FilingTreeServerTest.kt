package castbridge.core.tv

import castbridge.core.FakePlayer
import castbridge.core.owner.TrialPolicy
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random
import kotlin.test.*

/**
 * R-13 « le rangement ne crée pas d'arborescence de classement », sur la VRAIE ReceiverServer : une vidéo du téléphone crée Films/…, une file de plusieurs
 * envois aussi, « Copier et lire » est rangé à la fin de la lecture (il restait à plat pour toujours), le téléphone peut refuser le rangement (filing=0),
 * une TV d'essai ne crée rien, la clé USB exFAT reçoit les mêmes dossiers.
 */
class FilingTreeServerTest {
    private val root = kotlin.io.path.createTempDirectory("tree").toFile()
    private val internalDir = File(root, "internal").apply { mkdirs() }
    private val usbDir = File(root, "usb/Download/CastBridge/Bibliotheque").apply { mkdirs() }
    private var trial = false
    private val registry = VolumeRegistry(StaticVolumes {
        listOf(StorageVolume("internal", "Mémoire interne", internalDir, VolumeKind.INTERNAL, Fs.UNKNOWN, 0, 0, false),
            StorageVolume("usb-1", "Clé USB", usbDir, VolumeKind.REMOVABLE, Fs.EXFAT, 0, 0, true))
    }).also { it.refresh() }
    private val player = FakePlayer()
    private val server = ReceiverServer(registry, player, 0, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0, target = "internal"),
        routeGuard = { path -> if (trial && TrialPolicy.routeBlocked(path)) TrialPolicy.MESSAGE else null },
        filingLang = { "fr" }, hostCheck = false).apply { start(5000, false) }
    private val base = "http://127.0.0.1:${server.listeningPort}"
    private val tv = TvClient(base)
    private val data = Random(5).nextBytes(200_000)

    @AfterTest fun tearDown() { server.stop(); root.deleteRecursively() }

    private fun files(dir: File) = dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath }
        .filter { p -> p.split('/').none { it.startsWith(".") } }.toList().sorted()
    private fun put(name: String, offset: Long, total: Long, bytes: ByteArray, extra: String = ""): Int {
        val c = URL("$base/upload/${TvClient.enc(name)}?offset=$offset&total=$total$extra").openConnection() as HttpURLConnection
        c.requestMethod = "PUT"; c.doOutput = true; c.setFixedLengthStreamingMode(bytes.size); c.setRequestProperty("Connection", "close")
        c.outputStream.use { it.write(bytes) }
        return c.responseCode
    }
    private fun send(name: String, b: ByteArray = data) = tv.upload(name, 0, b.size.toLong(), ByteArrayInputStream(b)) {}

    @Test fun aVideoFromThePhoneCreatesTheFilmsTree() {
        assertTrue(send("ma_video.mp4").done)
        assertEquals(listOf("Films/ma_video.mp4"), files(internalDir))
        assertTrue(File(internalDir, "Films").isDirectory)
    }

    @Test fun severalSendsInARowAreAllFiled() {
        send("Prison.Break.S01E04.720p.mkv", data.copyOf(1000)); send("The.Matrix.1999.1080p.mkv", data.copyOf(2000))
        send("IMG_20240315_142233.jpg", data.copyOf(3000)); send("facture.pdf", data.copyOf(4000)); send("chanson.mp3", data.copyOf(5000))
        val got = files(internalDir)
        assertEquals(5, got.size, got.toString())
        assertTrue(got.contains("Séries/Prison Break/Saison 01/Prison Break – S01E04.mkv"), got.toString())
        assertTrue(got.contains("Films/The Matrix (1999).mkv"), got.toString())
        assertTrue(got.any { it.startsWith("Photos/2024-03/") }, got.toString())
        assertTrue(got.contains("Documents/facture.pdf") && got.any { it.startsWith("Musique/") }, got.toString())
        assertTrue(got.none { !it.contains('/') }, "rien ne reste à plat : $got")
    }

    @Test fun copyAndPlayIsFiledOnceThePlaybackStops() {
        val name = "Inception.2010.1080p.mkv"
        val total = data.size.toLong()
        assertEquals(200, put(name, 0, total, data.copyOfRange(0, 50_000)))
        player.st = PlayerState("playing", name, 0, 1000)                    // the TV plays the copy while it arrives (R-08)
        assertEquals(200, put(name, 50_000, total, data.copyOfRange(50_000, data.size)))
        assertEquals(listOf(name), files(internalDir), "pendant la lecture le fichier garde son nom (le lecteur le lit)")
        player.st = PlayerState()
        server.playbackChanged()
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && files(internalDir) == listOf(name)) Thread.sleep(50)
        assertEquals(listOf("Films/Inception (2010).mkv"), files(internalDir), "rangé dès la fin de la lecture")
        assertContentEquals(data, File(internalDir, "Films/Inception (2010).mkv").readBytes())
    }

    @Test fun thePhoneCanSwitchTheFilingOff() {
        assertEquals(200, put("Prison.Break.S01E05.720p.mkv", 0, data.size.toLong(), data, extra = "&filing=0"))
        assertEquals(listOf("Prison.Break.S01E05.720p.mkv"), files(internalDir))
        assertEquals(200, put("Prison.Break.S01E06.720p.mkv", 0, 1000, data.copyOf(1000)))
        assertTrue(files(internalDir).contains("Séries/Prison Break/Saison 01/Prison Break – S01E06.mkv"), "le choix vaut pour un envoi, pas pour les suivants")
    }

    @Test fun theFastMultiConnectionPathIsFiledAndHonoursTheSwitchToo() {
        val big = Random(9).nextBytes(3_000_000)
        val f = File(root, "src.bin").apply { writeBytes(big) }
        fun run(name: String, noFiling: Boolean) = java.nio.channels.FileChannel.open(f.toPath(), java.nio.file.StandardOpenOption.READ).use {
            castbridge.core.xfer.TransferClient(castbridge.core.xfer.HttpTransferApi({ base }, { null }, noFiling), castbridge.core.xfer.FileBlockSource(it), name, { id, max ->
                listOf(castbridge.core.xfer.WifiLane("wifi", "127.0.0.1:${server.listeningPort}", id, castbridge.core.xfer.HttpConn.tcp("127.0.0.1", server.listeningPort), { null }, maxStreams = max, fixedK = 2))
            }, retryDelayMs = 50).run()
        }
        assertEquals(castbridge.core.xfer.TransferClient.Result.Done, run("le_film_des_vacances_au_ski.mp4", noFiling = true))
        assertEquals(listOf("le_film_des_vacances_au_ski.mp4"), files(internalDir), "option éteinte sur le téléphone : à plat")
        File(internalDir, "le_film_des_vacances_au_ski.mp4").delete(); server.changed()
        assertEquals(castbridge.core.xfer.TransferClient.Result.Done, run("Dune.2021.1080p.mkv", noFiling = false))
        assertTrue(files(internalDir).contains("Films/Dune (2021).mkv"), files(internalDir).toString())
    }

    @Test fun aTrialTvCreatesNoTreeAndNoFile() {
        trial = true
        assertEquals(403, put("ma_video.mp4", 0, data.size.toLong(), data))
        assertEquals(emptyList(), files(internalDir))
        assertFalse(File(internalDir, "Films").exists())
    }

    @Test fun theExfatUsbKeyGetsTheSameTreeWithSafeNames() {
        assertTrue(server.setTargetValue("usb-1"))
        assertTrue(send("Amélie : le fabuleux destin ? (2001).mkv").done)
        val got = files(usbDir)
        assertEquals(1, got.size, got.toString())
        assertTrue(got[0].startsWith("Films/"), got.toString())
        assertFalse(got[0].any { it in ":?*\"<>|" }, got.toString())
        assertEquals(emptyList(), files(internalDir))
    }
}
