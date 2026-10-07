package castbridge.core.remote

import castbridge.core.tv.OpenTvReply
import castbridge.core.tv.OpenTvScreen
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * R-35 (audit anti-régression 2026-10-07 b, I-14) : la touche « TV » de la télécommande en Bluetooth ouvrait une SECONDE liaison RFCOMM vers le service que la télécommande tient déjà
 * (même canal, refusé) : « La TV ne répond pas : allumez-la » alors que la télécommande marchait. Liaison de la télécommande vivante ⇒ `open` passe dessus ; sinon la liaison à part.
 */
class OpenTvSessionLinkTest {
    private val ok = OpenTvReply(opened = true, how = "direct", needs = null)

    /** Une vraie liaison Bluetooth de télécommande (CBTR) dont la TV répond [reply] (une ligne) ; ce que le téléphone écrit est gardé dans [sent]. */
    private class Bt(reply: String, val sent: ByteArrayOutputStream = ByteArrayOutputStream(), val closed: AtomicInteger = AtomicInteger()) {
        val transport = RemoteBt.Transport(ByteArrayInputStream((reply + "\n").toByteArray()), sent, Closeable { closed.incrementAndGet() })
        val line: String get() = sent.toString("UTF-8").trim()
    }

    /** La liaison à part : compte ses appels et rend [answer]. */
    private class Fresh(val answer: OpenTvAnswer = OpenTvAnswer(200, OpenTvReply(true, "fresh", null))) : OpenTvLink {
        override val route = OpenTvRoute.BLUETOOTH
        val calls = mutableListOf<Long>()
        override fun open(screen: OpenTvScreen?, timeoutMs: Long): OpenTvAnswer { calls += timeoutMs; return answer }
    }

    @Test fun aLiveRemoteLinkCarriesTheOpenCommandAndNoSecondLinkIsDialled() {
        val bt = Bt("200 " + ok.toJson())
        val fresh = Fresh()
        val a = OpenTvSessionLink({ bt.transport }, fresh).open(OpenTvScreen.LIBRARY, 10_000)
        assertEquals(200, a.status)
        assertEquals(ok, a.reply)
        assertEquals("POST open?screen=library", bt.line, "la ligne `POST open?screen=…` du canal CBTR, sur la liaison de la télécommande")
        assertEquals(emptyList(), fresh.calls, "aucune seconde liaison vers le même service")
        assertEquals(0, bt.closed.get(), "la liaison de la télécommande reste ouverte")
    }

    @Test fun noLiveLinkMeansALinkOfItsOwnWithTheSameTime() {
        val fresh = Fresh()
        val a = OpenTvSessionLink({ null }, fresh).open(null, 7_000)
        assertEquals(200, a.status)
        assertEquals(listOf(7_000L), fresh.calls)
    }

    @Test fun aLiveLinkThatIsNotTheBluetoothChannelIsLeftAlone() {
        var touched = false
        val wifi = object : RemoteTransport {
            override val name = "Wi-Fi"
            override fun send(method: String, route: String, query: String): RemoteReply { touched = true; return RemoteReply(200, "{}") }
            override fun close() {}
        }
        val fresh = Fresh()
        OpenTvSessionLink({ wifi }, fresh).open(null, 5_000)
        assertEquals(listOf(5_000L), fresh.calls, "la voie Wi-Fi a la sienne ; seule une liaison CBTR est réutilisée")
        assertEquals(false, touched)
    }

    @Test fun theTvsRefusalOnTheRemoteLinkIsTheAnswer() {
        val bt = Bt("""401 {"error":"bad pin"}""")
        val fresh = Fresh()
        val a = OpenTvSessionLink({ bt.transport }, fresh).open(null, 10_000)
        assertEquals(401, a.status)
        assertEquals("bad pin", a.message)
        assertEquals(emptyList(), fresh.calls, "un refus de la TV n'est pas une liaison cassée : pas de seconde tentative")
    }

    @Test fun aLiveLinkThatBreaksUnderUsFallsBackOnALinkOfItsOwnWithWhatIsLeftOfTheTime() {
        // la TV a fermé la liaison : la lecture de la réponse échoue tout de suite
        val dead = RemoteBt.Transport(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), Closeable { })
        var t = 1_000L
        val fresh = Fresh()
        val a = OpenTvSessionLink({ dead }, fresh, now = { t.also { t += 1_500 } }).open(null, 10_000)
        assertEquals("fresh", a.reply?.how)
        assertEquals(1, fresh.calls.size)
        assertTrue(fresh.calls.single() in 1L..9_999L, "avec ce qui reste du temps : ${fresh.calls}")
    }

    @Test fun aLinkThatBreaksWithNoTimeLeftFailsLikeAnyOtherRoute() {
        val dead = RemoteBt.Transport(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), Closeable { })
        val fresh = Fresh()
        assertFailsWith<IOException> { OpenTvSessionLink({ dead }, fresh).open(null, 400) }
        assertEquals(emptyList(), fresh.calls, "400 ms ne suffisent pas à ouvrir une liaison Bluetooth : la voie échoue, comme les autres")
    }

    @Test fun aMuteRemoteLinkIsClosedAndTheRouteFailsWithinTheTimeGiven() {
        // la TV ne répond plus (éteinte, hors de portée) : la lecture bloque ; la voie rend la main à temps et ferme la liaison (la télécommande se rebranche seule)
        val closed = CountDownLatch(1)
        val blocking = object : InputStream() { override fun read(): Int { closed.await(); throw IOException("closed") } }
        val mute = RemoteBt.Transport(blocking, ByteArrayOutputStream(), Closeable { closed.countDown() })
        val fresh = Fresh()
        val t0 = System.nanoTime()
        assertFailsWith<IOException> { OpenTvSessionLink({ mute }, fresh).open(null, 300) }
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 3_000, "borné par le temps donné")
        assertTrue(closed.await(2, TimeUnit.SECONDS), "la liaison muette est fermée : elle est morte")
        assertEquals(emptyList(), fresh.calls)
    }

    @Test fun theRouteIsTheBluetoothOne() {
        assertSame(OpenTvRoute.BLUETOOTH, OpenTvSessionLink({ null }, Fresh()).route)
    }
}
