package castbridge.core.gateway

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R-42 (audit anti-régression 2026-10-07 b, I-12) : la passerelle de la TV mourait au premier redémarrage du Bluetooth. La boucle d'acceptation sortait au premier `accept()` échoué,
 * mais l'hôte gardait `running = true` et la socket morte : `start()` ne faisait plus rien, l'écran disait « prête », la TV continuait de frapper aux téléphones. Ici la boucle, avec de
 * fausses sockets serveur et des pauses simulées : elle réécoute un nombre borné de fois, prévient l'hôte quand elle renonce, et ne bouge pas quand on la ferme exprès.
 */
class AcceptLoopTest {
    /** Une socket serveur scriptée : chaque pas est une liaison (String) ou un échec (IOException) ; le script épuisé arrête l'hôte (comme un `stop()`). */
    private class Script(val host: Host, val name: String, vararg val steps: Any) : Acceptor<String> {
        private var i = 0
        var closed = false
        override fun accept(): String {
            val s = steps.getOrNull(i++)
            if (s == null) { host.running = false; throw IOException("fin du script $name") }
            if (s is IOException) throw s
            return s as String
        }
        override fun close() { closed = true }
    }

    private class Host {
        @Volatile var running = true
        val served = ArrayList<String>(); val sleeps = ArrayList<Long>(); val log = ArrayList<String>()
        var gaveUp = 0
        val next = ArrayDeque<Script?>()                 // ce que reopen() rend, dans l'ordre (null = le Bluetooth est éteint)
        var reopens = 0
        var current: Acceptor<String>? = null
        var refuseReplace = false
        var onSleep: () -> Unit = {}
        fun loop(): AcceptLoop<String> = AcceptLoop(
            wanted = { running && current === it },
            reopen = { reopens++; next.removeFirstOrNull() },
            replace = { old, new -> if (refuseReplace || current !== old) false else { current = new; true } },
            serve = { served += it },
            gaveUp = { gaveUp++; running = false },
            sleep = { sleeps += it; onSleep() },
            log = { log += it },
        )
        fun run(first: Script) { current = first; loop().run(first) }
    }

    private fun fail() = IOException("socket fermée")

    @Test fun aFailedAcceptListensAgainAndKeepsServing() {
        val h = Host(); val first = Script(h, "1", "A", fail()); val second = Script(h, "2", "B")
        h.next += second
        h.run(first)
        assertEquals(listOf("A", "B"), h.served, "la liaison d'après l'échec est servie : la passerelle n'est pas morte")
        assertEquals(listOf(2_000L), h.sleeps)
        assertTrue(first.closed, "la socket morte est fermée")
        assertEquals(0, h.gaveUp)
        assertTrue(h.log.any { "listening again" in it }, "l'événement est dit au journal")
    }

    @Test fun withBluetoothStayingOffTheLoopGivesUpAfterAboutHalfAMinuteAndTellsTheHost() {
        val h = Host(); h.run(Script(h, "1", fail()))
        assertEquals(listOf(2_000L, 4_000L, 6_000L, 8_000L, 10_000L), h.sleeps, "pauses croissantes, bornées")
        assertEquals(30_000L, h.sleeps.sum())
        assertEquals(5, h.reopens, "une réécoute par pause")
        assertEquals(1, h.gaveUp, "l'hôte est prévenu : running = false, un nouveau start() est possible")
        assertEquals(emptyList(), h.served)
    }

    @Test fun aLinkAcceptedResetsTheFailureCount() {
        val h = Host()
        // échec, réécoute, une liaison, échec, réécoute, une liaison, échec... : jamais plus d'une pause de 2 s, la boucle ne renonce pas
        val l1 = Script(h, "1", fail()); val l2 = Script(h, "2", "A", fail()); val l3 = Script(h, "3", "B", fail()); val l4 = Script(h, "4", "C")
        h.next += listOf(l2, l3, l4)
        h.run(l1)
        assertEquals(listOf("A", "B", "C"), h.served)
        assertEquals(listOf(2_000L, 2_000L, 2_000L), h.sleeps)
        assertEquals(0, h.gaveUp)
    }

    @Test fun failuresWithoutAnyLinkBetweenDoCountUp() {
        // le Bluetooth revient mais chaque nouvelle écoute meurt aussitôt : le compte monte, la boucle finit par renoncer
        val h = Host()
        val s = (1..8).map { Script(h, "$it", fail()) }
        h.next += s.drop(1)
        h.run(s.first())
        assertEquals(listOf(2_000L, 4_000L, 6_000L, 8_000L, 10_000L), h.sleeps)
        assertEquals(1, h.gaveUp)
    }

    @Test fun aListenerClosedOnPurposeEndsTheLoopQuietly() {
        // `sshBluetoothChanged` retire la socket de sa table avant de la fermer : l'échec de accept() qui s'ensuit n'est pas une panne
        val h = Host(); val live = java.util.concurrent.atomic.AtomicBoolean(true)
        val closing = object : Acceptor<String> {
            override fun accept(): String { live.set(false); throw fail() }          // la table de l'hôte n'a plus cette socket, puis elle est fermée
            override fun close() {}
        }
        val loop = AcceptLoop<String>(wanted = { live.get() }, reopen = { h.reopens++; null }, replace = { _, _ -> true },
            serve = { h.served += it }, gaveUp = { h.gaveUp++ }, sleep = { h.sleeps += it }, log = { h.log += it })
        loop.run(closing)
        assertEquals(0, h.reopens, "aucune réécoute d'une socket fermée exprès")
        assertEquals(emptyList(), h.sleeps)
        assertEquals(0, h.gaveUp)
        assertTrue(h.log.isEmpty(), "rien à dire")
    }

    @Test fun aStopDuringThePauseDoesNotListenAgain() {
        val h = Host(); h.next += Script(h, "2", "B")
        h.onSleep = { h.running = false }                  // stop() pendant la pause
        h.run(Script(h, "1", fail()))
        assertEquals(0, h.reopens, "l'hôte a été arrêté : pas de nouvelle écoute qui reviendrait après stop()")
        assertEquals(0, h.gaveUp)
        assertEquals(emptyList(), h.served)
    }

    @Test fun aNewListenerIsClosedWhenTheHostStoppedMeanwhile() {
        val h = Host(); val second = Script(h, "2", "B"); h.next += second
        h.refuseReplace = true                             // l'hôte a changé de table entre l'ouverture et la publication
        h.run(Script(h, "1", fail()))
        assertTrue(second.closed, "la socket ouverte pour rien est fermée")
        assertEquals(emptyList(), h.served)
        assertEquals(0, h.gaveUp)
    }

    @Test fun aReopenThatThrowsCountsAsAFailureNotACrash() {
        val h = Host(); var calls = 0
        val loop = AcceptLoop<String>(wanted = { h.running }, reopen = { calls++; throw SecurityException("permission Bluetooth retirée") }, replace = { _, _ -> true },
            serve = { h.served += it }, gaveUp = { h.gaveUp++; h.running = false }, sleep = { h.sleeps += it })
        loop.run(Script(h, "1", fail()).also { h.current = it })
        assertEquals(5, calls)
        assertEquals(1, h.gaveUp)
    }

    @Test fun aServeThatThrowsDoesNotKillTheLoop() {
        val h = Host()
        val loop = AcceptLoop<String>(wanted = { h.running }, reopen = { null }, replace = { _, _ -> true },
            serve = { if (it == "A") throw IllegalStateException("plus de fil") else h.served += it }, gaveUp = { h.gaveUp++ }, sleep = { h.sleeps += it }, log = { h.log += it })
        loop.run(Script(h, "1", "A", "B"))
        assertEquals(listOf("B"), h.served, "la liaison d'après est servie")
        assertTrue(h.log.any { "IllegalStateException" in it })
    }

    @Test fun theBudgetIsTheOneOfTheOtherBluetoothBridges() {
        assertEquals(5, AcceptLoop.MAX_FAILURES)
        assertEquals(2_000L, AcceptLoop.PAUSE_MS)
        assertFalse(AcceptLoop.MAX_FAILURES < 1)
    }
}
