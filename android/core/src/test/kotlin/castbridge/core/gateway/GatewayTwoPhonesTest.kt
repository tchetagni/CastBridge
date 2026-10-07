package castbridge.core.gateway

import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * R-43 (audit anti-régression 2026-10-07 b, I-11, REPRODUIT) : deux téléphones attachés à la passerelle de la TV. Avant : le second remplaçait le premier SANS fermer son lien, et la
 * fin du lien remplacé (le téléphone s'arrête 10 minutes plus tard) terminait TOUS les flux, requêtes et mesures du lien courant (la partie ou le tunnel tombe) ; variante : le courant
 * tombe et `mux = null` alors que l'autre vit. Maintenant : la TV sert UN téléphone à la fois et ne coupe jamais un lien sain pour un second (un téléphone dont le lien est fermé se
 * reconnecte tout seul : deux téléphones se reprendraient le tuyau sans fin), un second téléphone reçoit « occupée » ; le MÊME téléphone qui revient remplace son ancien lien, qui est
 * fermé, et la fin de ce lien ne touche à rien du nouveau.
 */
class GatewayTwoPhonesTest {
    /** « Internet » : renvoie ce qu'on lui envoie. */
    private val echo = ServerSocket(0).also { ss ->
        thread(isDaemon = true) {
            while (!ss.isClosed) {
                val c = runCatching { ss.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    runCatching { c.use { s -> val i = s.getInputStream(); val o = s.getOutputStream(); val b = ByteArray(1024); while (true) { val n = i.read(b); if (n < 0) break; o.write(b, 0, n); o.flush() } } }
                }
            }
        }
    }
    private val entry = Entry({ it == "123456" }, port = 0)
    private var socksPort = 0

    @BeforeTest fun setUp() { socksPort = entry.startSocks() }
    @AfterTest fun tearDown() { entry.stop(); echo.close() }

    /** Un téléphone : les deux bouts de son lien Bluetooth, et ce que son `Exit` a rendu. */
    private class Phone(val phoneEnd: Socket) {
        @Volatile var failure: Throwable? = null
        @Volatile var ended = false
        /** Le bout du téléphone meurt (arrêt sur inactivité, hors de portée) : la TV le voit comme une fin de lien. */
        fun cut() { runCatching { phoneEnd.close() } }
    }

    private fun phone(name: String, id: String?, pin: String = "123456"): Phone {
        ServerSocket(0).use { ss ->
            val a = Socket("127.0.0.1", ss.localPort); val b = ss.accept()
            val p = Phone(a)
            thread(isDaemon = true) { runCatching { entry.attach(Mux(b.getInputStream(), b.getOutputStream()), name, id) } }
            thread(isDaemon = true) { try { Exit(Mux(a.getInputStream(), a.getOutputStream()), pin).run() } catch (e: Throwable) { p.failure = e } finally { p.ended = true } }
            return p
        }
    }

    private fun waitFor(what: String, cond: () -> Boolean) { repeat(250) { if (cond()) return; Thread.sleep(20) }; fail("délai dépassé : $what") }

    private fun viaTv(): Socket = Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))).apply {
        soTimeout = 5_000; connect(InetSocketAddress("127.0.0.1", echo.localPort), 5_000)
    }

    private fun echoWorks(s: Socket, word: String) {
        s.getOutputStream().write(word.toByteArray()); s.getOutputStream().flush()
        val got = ByteArray(word.length); DataInputStream(s.getInputStream()).readFully(got)
        assertEquals(word, String(got))
    }

    @Test fun aSecondPhoneIsToldBusyAndTheFirstKeepsItsStreams() {
        val a = phone("Téléphone A", "AA:AA"); waitFor("A attaché") { entry.connected && entry.peerName == "Téléphone A" }
        val s1 = viaTv(); echoWorks(s1, "un")
        val b = phone("Téléphone B", "BB:BB"); waitFor("B reçoit une réponse") { b.ended }
        val f = b.failure
        assertTrue(f is GatewayRefused && f.busy, "B est refusé « occupée » : $f")
        assertEquals(Gw.BUSY, f?.message)
        assertEquals("Téléphone A", entry.peerName, "la TV sert toujours A")
        assertEquals(1, entry.attaches, "un seul tuyau : l'identité du tuyau n'a pas changé")
        echoWorks(s1, "deux")                                           // le flux de A n'a pas bougé
        viaTv().use { echoWorks(it, "trois") }                          // et un nouveau flux passe par A
        assertNotNull(entry.ping(2_000), "le PING du lien courant est répondu : rien n'a été annulé par la tentative de B")
        s1.close(); a.cut()
    }

    @Test fun thePhoneThatComesBackReplacesItsOwnStaleLinkAndTheEndOfTheOldOneKillsNothing() {
        val a1 = phone("A", "AA:AA"); waitFor("A attaché") { entry.connected && entry.attaches == 1 }
        val old = viaTv(); echoWorks(old, "avant")
        val a2 = phone("A", "AA:AA")                                    // le même appareil se reconnecte : son ancien lien est resté ouvert côté TV
        waitFor("le nouveau lien remplace l'ancien") { entry.attaches == 2 }
        waitFor("l'ancien lien est fermé par la TV") { a1.ended }       // fermé proprement : le téléphone le voit tout de suite
        assertTrue(runCatching { old.getInputStream().read() }.getOrDefault(-1) == -1, "le flux de l'ancien lien est terminé avec lui")
        val keep = viaTv(); echoWorks(keep, "garde")                    // un flux du NOUVEAU lien
        a1.cut()                                                        // la fin définitive du vieux lien (arrêt sur inactivité, hors de portée…)
        Thread.sleep(400)
        echoWorks(keep, "toujours")                                     // avant le correctif : le `finally` de l'ancien lien terminait ce flux
        assertTrue(entry.connected); assertEquals("A", entry.peerName)
        assertNotNull(entry.ping(2_000), "la mesure du nouveau lien n'a pas été annulée")
        keep.close(); a2.cut()
    }

    @Test fun whenTheCurrentLinkEndsAnotherPhoneCanAttach() {
        val a = phone("A", "AA:AA"); waitFor("A attaché") { entry.connected }
        a.cut(); waitFor("la TV voit la fin de A") { !entry.connected }
        val b = phone("B", "BB:BB"); waitFor("B attaché") { entry.connected && entry.peerName == "B" }
        viaTv().use { echoWorks(it, "b") }
        b.cut()
    }

    @Test fun aPhoneWhoseIdIsUnknownIsNeverTakenForTheCurrentOne() {
        val a = phone("A", "AA:AA"); waitFor("A attaché") { entry.connected }
        val x = phone("A", null); waitFor("réponse") { x.ended }          // même nom, adresse inconnue : rien ne prouve que c'est le même appareil
        assertTrue((x.failure as? GatewayRefused)?.busy == true, "${x.failure}")
        assertEquals(1, entry.attaches)
        a.cut()
    }

    @Test fun aWrongPinKeepsTheMessageThePhoneLooksFor() {
        // BtGatewayService arrête le service quand le message contient « PIN » : ce mot ne doit pas changer
        val p = phone("A", "AA:AA", pin = "000000"); waitFor("refus") { p.ended }
        val f = assertFailsWith<GatewayRefused> { throw p.failure!! }
        assertTrue("PIN" in f.message.orEmpty() && !f.busy, f.message)
        assertTrue(!entry.connected)
    }

    @Test fun theBusyAnswerDoesNotContainTheWordThePhoneTreatsAsAWrongPin() {
        // sinon un second téléphone arrêterait son service comme si son code était faux (BtGatewayService : e.message.contains("PIN"))
        assertTrue("PIN" !in Gw.BUSY)
    }
}
