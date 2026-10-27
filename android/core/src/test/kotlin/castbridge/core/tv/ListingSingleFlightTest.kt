package castbridge.core.tv

import castbridge.core.FakePlayer
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Défaut du 2026-10-27 (TV réelle) : chaque appel à la liste périmée relançait sa propre lecture de tous les volumes ; sur une clé exFAT lente les fils HTTP
 * s'empilaient et /api/hello ne répondait plus. Une seule lecture à la fois ; les lecteurs qui sondent reçoivent la liste précédente.
 */
class ListingSingleFlightTest {
    private val dir = kotlin.io.path.createTempDirectory("sf").toFile()
    private val lists = AtomicInteger()
    @Volatile private var gate: CountDownLatch? = null
    private val started = AtomicInteger()
    private val logs = java.util.Collections.synchronizedList(ArrayList<String>())

    private val slow = object : VolumeProvider {
        override fun scan(remeasure: Boolean) = listOf(StorageVolume("internal", "Mémoire interne", dir, VolumeKind.INTERNAL, Fs.UNKNOWN, 0, 0, false))
        override fun storeFor(volume: StorageVolume): VolumeStore = object : FileStore(volume) {
            override fun list(): List<StoreEntry> {
                lists.incrementAndGet(); started.incrementAndGet()
                gate?.await(10, TimeUnit.SECONDS)
                return super.list()
            }
        }
    }
    private val port = ServerSocket(0).use { it.localPort }
    private val server = ReceiverServer(VolumeRegistry(slow).also { it.refresh() }, FakePlayer(), port, pin = "123456", onLog = { logs += it }).apply { start(5000, false) }

    @kotlin.test.BeforeTest fun reset() { lists.set(0); started.set(0) }      // le registre a déjà lu les volumes une fois en se montant
    @AfterTest fun tearDown() { gate?.countDown(); server.stop(); dir.deleteRecursively() }

    private fun put(n: String) { File(dir, n).writeBytes(ByteArray(10)) }
    private fun waitFor(c: () -> Boolean) { val t = System.currentTimeMillis() + 5000; while (!c() && System.currentTimeMillis() < t) Thread.sleep(5) }

    @Test fun `huit appels en meme temps ne lisent qu une fois chaque volume`() {
        put("a.mp4")
        gate = CountDownLatch(1)
        val start = CountDownLatch(1)
        val ts = (1..8).map { thread { start.await(10, TimeUnit.SECONDS); server.libraryItems() } }
        start.countDown()
        waitFor { started.get() >= 1 }
        Thread.sleep(150)                                              // laisse aux autres le temps de (ne pas) lancer la leur
        assertEquals(1, lists.get(), "une seule lecture en cours")
        gate!!.countDown()
        ts.forEach { it.join(10_000) }
        assertEquals(1, lists.get(), "et une seule en tout")
    }

    @Test fun `un lecteur pendant une lecture en cours recoit la liste precedente sans attendre`() {
        put("a.mp4")
        assertEquals(listOf("a.mp4"), server.libraryItems().map { it.name })      // première liste (le portail est ouvert)
        put("b.mp4")
        Thread.sleep(1_200)                                                        // plus de infoCacheMs : la liste est périmée
        gate = CountDownLatch(1)
        val first = thread { server.libraryItems() }                               // lance la lecture lente
        waitFor { started.get() >= 2 }
        val t0 = System.nanoTime()
        val seen = server.libraryItems().map { it.name }                           // lecteur qui sonde
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 1_000, "rendu tout de suite")
        assertEquals(listOf("a.mp4"), seen, "la liste précédente")
        gate!!.countDown(); first.join(10_000)
    }

    @Test fun `apres invalidation l appelant voit le nouveau fichier meme pendant une lecture`() {
        put("a.mp4")
        server.libraryItems()
        gate = CountDownLatch(1)
        Thread.sleep(1_200)
        val slowReader = thread { server.libraryItems() }
        waitFor { started.get() >= 2 }
        put("b.mp4"); server.changed()
        var got: List<String>? = null
        val writer = thread { got = server.fileStillThereNames() }
        Thread.sleep(200)
        gate!!.countDown()
        writer.join(10_000); slowReader.join(10_000)
        assertEquals(listOf("a.mp4", "b.mp4"), got, "la lecture commencée avant l'invalidation ne suffit pas : une nouvelle est faite")
    }

    @Test fun `une lecture lente est consignee`() {
        put("a.mp4")
        gate = CountDownLatch(1)
        val t = thread { server.libraryItems() }
        waitFor { started.get() >= 1 }
        Thread.sleep(700); gate!!.countDown(); t.join(10_000)
        assertTrue(logs.any { it.startsWith("listing : 1 fichiers, 1 volumes, ") }, logs.toString())
    }
}
