package castbridge.core.owner

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Les deux exécuteurs du pilote de « Activer la TV » (`sender/ActivationDriver`) : un fil « machine » qui réduit les événements dans l'ordre, un pool pour ce qui bloque (HTTP, Bluetooth).
 * Audit anti-régression B1 (2026-10-07) : on quitte l'écran PENDANT « Installation… », le pilote est libéré (les deux exécuteurs sont arrêtés), puis la réponse de la TV arrive dans un fil du pool,
 * qui soumettait son résultat au fil « machine » arrêté : `RejectedExecutionException` jetée dans un fil de pool, non attrapée, Android tue le processus (file de copie et tuyau compris).
 * Ici la réponse tardive est REJOUÉE contre de vrais exécuteurs : rien ne se lance après la libération, rien n'est jeté, le gestionnaire d'exceptions non attrapées ne voit rien.
 */
class ActivationExecutorsTest {
    private val uncaught = CopyOnWriteArrayList<Throwable>()
    /** A pool whose threads report their uncaught exceptions here: in the app they kill the process. */
    private fun watchedPool() = Executors.newCachedThreadPool { r -> Thread(r, "test-io").apply { isDaemon = true; setUncaughtExceptionHandler { _, e -> uncaught += e } } }
    private fun machineThread() = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "test-machine").apply { isDaemon = true; setUncaughtExceptionHandler { _, e -> uncaught += e } } }
    private fun rig() = ActivationExecutors(machineThread(), watchedPool())

    /** A blocking socket read does not react to an interrupt: wait however long it takes, swallowing interrupts, like the HTTP call of the driver. */
    private fun CountDownLatch.awaitDeaf() { while (true) { try { await(); return } catch (e: InterruptedException) { /* a socket read ignores it */ } } }

    // ------------------------------------------------------------------ B1 : la réponse qui arrive après la libération

    @Test fun anAnswerThatArrivesAfterReleaseIsDroppedAndNeverThrowsIntoItsPoolThread() {
        val ex = rig()
        val started = CountDownLatch(1); val answer = CountDownLatch(1); val done = CountDownLatch(1); val reduced = AtomicBoolean(false)
        ex.onIo {                                                // « Installation… » : the HTTP call waits for the TV's answer (up to 8 + 32 s)
            started.countDown(); answer.awaitDeaf()              // the user leaves the screen meanwhile: the driver is released
            ex.onMachine { reduced.set(true) }                   // the answer arrives: submitted to the machine thread, which is gone
            done.countDown()
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        ex.release()
        answer.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS), "the pool thread went through its late submission")
        assertTrue(uncaught.isEmpty(), "nothing is thrown into a pool thread (it would kill the process): $uncaught")
        assertFalse(reduced.get(), "the late answer is dropped, never reduced")
    }

    @Test fun everyLateSubmissionAfterReleaseIsRefusedQuietly() {
        val ex = rig()
        ex.release()
        assertTrue(ex.released)
        assertFalse(ex.onMachine { error("must not run") }); assertFalse(ex.onIo { error("must not run") })
        assertNull(ex.every(10) { error("must not run") })
        assertTrue(uncaught.isEmpty(), "$uncaught")
    }

    @Test fun nothingNewRunsOnceReleasedEvenWhileTheMachineThreadIsStillBusyWithItsLastTask() {
        val machine = machineThread(); val io = watchedPool(); val ex = ActivationExecutors(machine, io)
        val gate = CountDownLatch(1); val ran = AtomicBoolean(false)
        ex.release { gate.awaitDeaf() }                          // the cleanup holds the machine thread: its executor does not refuse yet
        assertFalse(ex.onMachine { ran.set(true) }, "released: refused before the executor could even accept it")
        assertFalse(ex.onIo { ran.set(true) })
        gate.countDown()
        assertTrue(machine.awaitTermination(5, TimeUnit.SECONDS)); assertTrue(io.awaitTermination(5, TimeUnit.SECONDS))     // whatever had been accepted has run by now
        assertFalse(ran.get()); assertTrue(uncaught.isEmpty(), "$uncaught")
    }

    @Test fun theLastTaskRunsOnTheMachineThreadAfterWhatWasAlreadyQueuedThenItStops() {
        val machine = machineThread(); val ex = ActivationExecutors(machine, watchedPool())
        val order = CopyOnWriteArrayList<String>(); val first = CountDownLatch(1)
        ex.onMachine { first.awaitDeaf(); order += "queued:" + Thread.currentThread().name }
        ex.release { order += "last:" + Thread.currentThread().name }
        first.countDown()
        assertTrue(machine.awaitTermination(5, TimeUnit.SECONDS), "the machine executor is shut down once its last task is done")
        assertEquals(listOf("queued:test-machine", "last:test-machine"), order.toList())
    }

    @Test fun theCleanupFailingStillStopsTheMachineAndReleasingTwiceIsHarmless() {
        val machine = machineThread(); val ex = ActivationExecutors(machine, watchedPool())
        ex.release { error("cleanup bug") }
        assertTrue(machine.awaitTermination(5, TimeUnit.SECONDS))
        ex.release(); ex.release { error("never run twice") }
        assertTrue(uncaught.isEmpty(), "$uncaught")
    }

    @Test fun releaseNeverThrowsEvenWhenSomeoneElseAlreadyStoppedTheExecutors() {
        val machine = machineThread(); val io = watchedPool(); val ex = ActivationExecutors(machine, io)
        machine.shutdownNow(); io.shutdownNow()
        ex.release { error("cannot run: the executor is gone") }
        assertFalse(ex.onMachine { }); assertFalse(ex.onIo { })
        assertTrue(ex.released)
    }

    @Test fun aSubmissionToAnExecutorStoppedFromOutsideIsRefusedNotThrown() {
        val machine = machineThread(); val io = watchedPool(); val ex = ActivationExecutors(machine, io)
        machine.shutdownNow(); io.shutdownNow()                      // nobody called release(): the executors are gone all the same
        assertFalse(ex.released)
        assertFalse(ex.onMachine { error("must not run") }); assertFalse(ex.onIo { error("must not run") }); assertNull(ex.every(10) { error("must not run") })
        assertTrue(uncaught.isEmpty(), "$uncaught")
    }

    @Test fun theBlockedPoolTasksAreInterruptedAtReleaseButTheirLateAnswersAreStillDropped() {
        val ex = rig()
        val started = CountDownLatch(1); val interrupted = CountDownLatch(1); val done = CountDownLatch(1); val late = AtomicBoolean(false)
        ex.onIo {
            started.countDown()
            try { Thread.sleep(60_000) } catch (e: InterruptedException) { interrupted.countDown() }     // a polling loop (lanLoop) wakes up and ends
            late.set(ex.onMachine { }); done.countDown()
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        ex.release()
        assertTrue(interrupted.await(5, TimeUnit.SECONDS), "shutdownNow interrupts the pool")
        assertTrue(done.await(5, TimeUnit.SECONDS)); assertFalse(late.get()); assertTrue(uncaught.isEmpty(), "$uncaught")
    }

    // ------------------------------------------------------------------ avant la libération, rien ne change

    @Test fun beforeReleaseTasksRunInOrderOnOneMachineThreadAndTheIoPoolRunsInParallel() {
        val ex = rig()
        val order = CopyOnWriteArrayList<Int>(); val n = 20; val all = CountDownLatch(n); val threads = CopyOnWriteArrayList<String>()
        for (i in 1..n) assertTrue(ex.onMachine { order += i; threads += Thread.currentThread().name; all.countDown() })
        assertTrue(all.await(5, TimeUnit.SECONDS)); assertEquals((1..n).toList(), order.toList()); assertEquals(setOf("test-machine"), threads.toSet())
        val a = CountDownLatch(2); val both = CountDownLatch(2)
        for (i in 1..2) assertTrue(ex.onIo { a.countDown(); a.await(5, TimeUnit.SECONDS); both.countDown() })
        assertTrue(both.await(5, TimeUnit.SECONDS), "two blocking tasks run at the same time")
        assertFalse(ex.released); ex.release()
    }

    @Test fun theTickerRunsUntilReleaseAndAFailingTickNeverStopsIt() {
        val ex = rig(); val ticks = AtomicInteger()
        assertNotNull(ex.every(10) { ticks.incrementAndGet(); if (ticks.get() == 2) error("a bad tick") })
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (ticks.get() < 5 && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue(ticks.get() >= 5, "the ticker went on after a failing tick: ${ticks.get()}")
        ex.release()
        Thread.sleep(100); val after = ticks.get(); Thread.sleep(100)
        assertEquals(after, ticks.get(), "no tick after release")
        assertTrue(uncaught.isEmpty(), "$uncaught")
    }

    @Test fun theStandardPairNamesItsThreadsAndIsDaemon() {
        val ex = ActivationExecutors.standard()
        val names = CopyOnWriteArrayList<String>(); val daemon = CopyOnWriteArrayList<Boolean>(); val both = CountDownLatch(2)
        ex.onMachine { names += Thread.currentThread().name; daemon += Thread.currentThread().isDaemon; both.countDown() }
        ex.onIo { names += Thread.currentThread().name; daemon += Thread.currentThread().isDaemon; both.countDown() }
        assertTrue(both.await(5, TimeUnit.SECONDS)); ex.release()
        assertEquals(setOf("activation-machine", "activation-io"), names.toSet()); assertTrue(daemon.all { it })
    }
}
