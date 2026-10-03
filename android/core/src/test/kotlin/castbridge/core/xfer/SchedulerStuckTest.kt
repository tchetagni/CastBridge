package castbridge.core.xfer

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

/** R-17 : la relance bornée dans l'ordonnanceur (horloge simulée : chaque pause avance l'heure, rien n'attend pour de vrai). */
class SchedulerStuckTest {
    private val mib = 1L shl 20
    private object Src : BlockSource { override val size = Long.MAX_VALUE; override fun read(pos: Long, buf: ByteArray, off: Int, len: Int) = len }

    private fun run(vararg lanes: Lane, blocks: Int = 4): Scheduler.Result {
        val m = Manifest("a.bin", blocks * mib, mib.toInt()); val ns = AtomicLong()
        return Scheduler(m, BlockMap(m.blocks), lanes.toList(), { c -> SendContext(m, Src, HashBook(m, Src), c, false) },
            clock = { ns.get() }, sleepMs = { ns.addAndGet(it * 1_000_000); Thread.yield() }).run { false }
    }

    private fun lane(name: String, isSlow: Boolean = false, okEvery: Int = 0, reason: String = "Broken pipe (SocketException)") = object : Lane {
        override val id = name; override val maxWorkers = 1; override val sent = AtomicLong(); override val slow = isSlow
        private val n = AtomicInteger()
        override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome =
            if (okEvery > 0 && n.incrementAndGet() % okEvery == 0) Outcome.Ok(mib) else Outcome.Failed(reason)
    }

    @Test fun aLaneBrokenForeverBecomesAVisibleFailureWithTheFrenchReason() {
        val r = run(lane("wifi"))
        assertTrue(r is Scheduler.Result.Failed, r.toString())
        assertEquals("La TV ferme la connexion pendant l'envoi (cause inconnue) : vérifiez la TV puis relancez", (r as Scheduler.Result.Failed).reason)
    }

    @Test fun progressOnAnyLaneKeepsItGoing() {
        // le Wi-Fi tombe toujours, Bluetooth avance (un bloc sur deux) : pas d'échec, le transfert se termine
        val r = run(lane("wifi"), lane("bluetooth", isSlow = true, okEvery = 2), blocks = 6)
        assertEquals(Scheduler.Result.Done, r)
    }

    private class Clocked { val ns = AtomicLong() }

    @Test fun aSlowLaneThatMovesBytesIsNotStuckEvenWithoutAConfirmedBlockForMinutes() {
        val f = Clocked()
        val m = Manifest("a.bin", 2 * mib, mib.toInt())
        val wifi = lane("wifi")
        val bt = object : Lane {
            override val id = "bluetooth"; override val maxWorkers = 1; override val sent = AtomicLong(); override val slow = true
            val moved = AtomicLong(); private val n = AtomicInteger()
            override fun bytesMoved() = moved.get()
            override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome {
                Thread.sleep(3); moved.addAndGet(100 * 1024); f.ns.addAndGet(200_000_000_000L)       // 200 s for 100 Kio acknowledged
                return if (n.incrementAndGet() % 3 == 0) Outcome.Ok(mib) else Outcome.Failed("Read timed out (SocketTimeoutException)")
            }
        }
        val r = Scheduler(m, BlockMap(m.blocks), listOf(wifi, bt), { c -> SendContext(m, Src, HashBook(m, Src), c, false) },
            clock = { f.ns.get() }, sleepMs = { Thread.sleep(1) }).run { false }      // time only moves when the slow lane works
        assertEquals(Scheduler.Result.Done, r)
    }

    @Test fun alternatingBrokenPipeAndResetIsStillOneStreak() {
        val n = AtomicInteger()
        val l = object : Lane {
            override val id = "wifi"; override val maxWorkers = 1; override val sent = AtomicLong()
            override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome =
                Outcome.Failed(if (n.incrementAndGet() % 2 == 0) "Broken pipe (SocketException)" else "Connection reset (SocketException)")
        }
        assertTrue(run(l) is Scheduler.Result.Failed)
    }

    @Test fun anAlreadyAnswerIsProgress() {
        // 5 échecs identiques puis « déjà là » (50 s par appel) : jamais 6 de suite, le transfert finit
        val ns = AtomicLong(); val n = AtomicInteger()
        val l = object : Lane {
            override val id = "wifi"; override val maxWorkers = 1; override val sent = AtomicLong()
            override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome {
                ns.addAndGet(50_000_000_000L)
                return if (n.incrementAndGet() % 6 == 0) Outcome.Already else Outcome.Failed("Broken pipe (SocketException)")
            }
        }
        val m = Manifest("a.bin", 3 * mib, mib.toInt())
        val r = Scheduler(m, BlockMap(m.blocks), listOf(l), { c -> SendContext(m, Src, HashBook(m, Src), c, false) },
            clock = { ns.get() }, sleepMs = { ns.addAndGet(it * 1_000_000); Thread.yield() }).run { false }
        assertEquals(Scheduler.Result.Done, r)
    }
}
