package castbridge.play

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Le tick du service : toutes les `tickMs` (200 ms), `PlayHub.tick` fait avancer chaque salle (`ServerRoom.tick`), les pings et la purge. Une erreur ne l'arrête jamais. */
class Ticker(private val periodMs: Long, private val task: () -> Unit) : AutoCloseable {
    private val exec: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "play-ticker").apply { isDaemon = true } }

    fun start() {
        exec.scheduleWithFixedDelay({ try { task() } catch (e: Throwable) { System.err.println("tick : " + e.javaClass.simpleName) } }, periodMs, periodMs, TimeUnit.MILLISECONDS)
    }

    override fun close() { exec.shutdownNow() }
}
