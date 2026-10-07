package castbridge.core.remote

import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * R-35 (audit anti-régression 2026-10-07 b, I-14) : la session de la télécommande montre la liaison qu'elle tient en ce moment ([RemoteSession.liveTransport]) pour que « Ouvrir sur la TV »
 * envoie sa commande dessus au lieu d'ouvrir une seconde liaison vers le même service Bluetooth. Rien tant qu'elle se connecte, se reconnecte ou est arrêtée.
 */
class RemoteSessionLiveTransportTest {
    private class T(val id: Int) : RemoteTransport {
        override val name = "fake$id"
        val broken = AtomicBoolean(false)
        val closed = AtomicBoolean(false)
        override fun send(method: String, route: String, query: String): RemoteReply { if (broken.get()) throw IOException("link lost"); return RemoteReply(200, "{}") }
        override fun close() { closed.set(true) }
    }

    private val listener = object : RemoteSession.Listener { override fun status(s: RemoteSession.Status) {} }

    private fun until(ms: Long = 5_000, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(5) }
        return cond()
    }

    @Test fun theSessionShowsTheLinkItHoldsAndNothingOnceStopped() {
        val made = CopyOnWriteArrayList<T>()
        var n = 0
        val s = RemoteSession(RemoteQueue("p"), { T(++n).also { made += it } }, listener, pingMs = 40)
        assertNull(s.liveTransport(), "avant le démarrage : aucune liaison")
        s.start()
        assertTrue(until { s.liveTransport() != null }, "la liaison de la session")
        assertSame(made.first(), s.liveTransport())
        s.stop()
        assertNull(s.liveTransport(), "arrêtée : rien (la liaison est sur le point d'être fermée)")
    }

    @Test fun nothingWhileTheSessionIsStillConnecting() {
        val gate = CountDownLatch(1)
        val s = RemoteSession(RemoteQueue("p"), { gate.await(); T(1) }, listener, pingMs = 40)
        s.start()
        Thread.sleep(150)
        assertNull(s.liveTransport(), "la connexion n'est pas finie : aucune liaison à réutiliser")
        gate.countDown()
        assertTrue(until { s.liveTransport() != null })
        s.stop()
    }

    @Test fun aBrokenLinkIsNoLongerShownAndTheNewOneReplacesIt() {
        val made = CopyOnWriteArrayList<T>()
        var n = 0
        val s = RemoteSession(RemoteQueue("p"), { T(++n).also { made += it } }, listener, pingMs = 30)
        s.start()
        assertTrue(until { s.liveTransport() != null })
        val first = made.first()
        first.broken.set(true)                                         // the TV went away: the next ping fails and the session reconnects
        assertTrue(until { made.size >= 2 && s.liveTransport() === made[1] }, "la session montre la NOUVELLE liaison")
        assertTrue(first.closed.get(), "l'ancienne est fermée")
        assertNotNull(s.liveTransport())
        s.stop()
    }
}
