package castbridge.core.owner

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Les deux exécuteurs du pilote de « Activer la TV » (`sender/ActivationDriver`) : un fil « machine » qui réduit les événements dans l'ordre, et un pool pour ce qui bloque (HTTP, Bluetooth).
 *
 * Audit anti-régression B1 (2026-10-07) : on quitte l'écran PENDANT « Installation… », le pilote est libéré (les deux exécuteurs sont arrêtés), puis la réponse de la TV arrive dans un fil du pool
 * (un appel HTTP ou Bluetooth ne se laisse pas interrompre) qui soumet son résultat au fil « machine » arrêté : `RejectedExecutionException` jetée dans un fil de pool, non attrapée, Android tue le
 * processus (file de copie et tuyau compris). Ici TOUTE soumission passe par [onMachine] / [onIo] / [every] : une fois libéré, rien ne se lance et rien n'est jeté, la réponse tardive est ignorée ; un
 * exécuteur arrêté de l'extérieur est refusé de la même façon (retour false, jamais d'exception). Le pilote n'a plus aucun accès direct aux exécuteurs (`ActivationPhoneGuardTest` le garde).
 *
 * Pur (JDK seulement) : testé en JVM avec de vrais exécuteurs, y compris la réponse tardive rejouée.
 */
class ActivationExecutors(private val machine: ScheduledExecutorService, private val io: ExecutorService) {
    private val done = AtomicBoolean(false)

    /** Le pilote est libéré : plus rien ne se lance. */
    val released: Boolean get() = done.get()

    /** Lance [task] sur le fil « machine » (un seul fil, dans l'ordre). false = ignorée : pilote libéré ou exécuteur arrêté. Ne jette jamais. */
    fun onMachine(task: () -> Unit): Boolean = submit(machine, task)

    /** Lance [task] dans le pool de ce qui bloque. false = ignorée : pilote libéré ou exécuteur arrêté. Ne jette jamais. */
    fun onIo(task: () -> Unit): Boolean = submit(io, task)

    private fun submit(e: ExecutorService, task: () -> Unit): Boolean {
        if (done.get()) return false
        return try { e.execute(task); true } catch (_: RejectedExecutionException) { false }
    }

    /** Répète [task] sur le fil « machine » toutes les [periodMs] ms ; un tour qui échoue n'arrête pas les suivants. null = refusée (libéré ou arrêté). */
    fun every(periodMs: Long, task: () -> Unit): ScheduledFuture<*>? {
        if (done.get()) return null
        return try { machine.scheduleWithFixedDelay({ runCatching(task) }, periodMs, periodMs, TimeUnit.MILLISECONDS) } catch (_: RejectedExecutionException) { null }
    }

    /**
     * Libère : plus aucune soumission n'est acceptée (le drapeau tombe d'abord, avant tout arrêt), puis [last] (le nettoyage : rendre le réseau, annuler le métronome) est le dernier travail du fil « machine »,
     * qui s'arrête ensuite ; le pool est interrompu (`shutdownNow`). Sans effet la deuxième fois ; ne jette jamais, même si un exécuteur a déjà été arrêté ailleurs.
     */
    fun release(last: () -> Unit = {}) {
        if (!done.compareAndSet(false, true)) return
        try { machine.execute { try { last() } finally { machine.shutdown() } } } catch (_: RejectedExecutionException) { runCatching { machine.shutdown() } }
        runCatching { io.shutdownNow() }
    }

    companion object {
        /** Le couple du pilote : fils démons nommés « activation-machine » et « activation-io ». */
        fun standard(): ActivationExecutors = ActivationExecutors(
            Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "activation-machine").apply { isDaemon = true } },
            Executors.newCachedThreadPool { r -> Thread(r, "activation-io").apply { isDaemon = true } },
        )
    }
}
