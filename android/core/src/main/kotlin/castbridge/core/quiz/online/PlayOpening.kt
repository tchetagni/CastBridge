package castbridge.core.quiz.online

import castbridge.core.owner.Activation
import castbridge.core.tunnel.TunnelEnroll
import java.util.concurrent.atomic.AtomicInteger

/**
 * Règles pures de l'ouverture d'une partie Internet sur la TV (audit Opus M-4, M-5, M-6, C-1). Le câblage Android (`PlayHub`) ne fait que les appeler.
 */

/** M-5 : numéro de génération d'une ouverture. « Retour » ([cancel]) pendant que le fil attend le ticket invalide l'ouverture en cours : elle n'affecte ensuite ni `session` ni `relay` (salle fantôme). */
class OpenGate {
    private val generation = AtomicInteger()
    /** Une nouvelle ouverture commence (annule implicitement la précédente) ; rend son numéro. */
    fun begin(): Int = generation.incrementAndGet()
    /** « Retour » / arrêt : toute ouverture en cours est périmée. */
    fun cancel() { generation.incrementAndGet() }
    fun isCurrent(g: Int): Boolean = generation.get() == g
}

/**
 * M-6 : le ticket de la TV. [fresh] en demande toujours un (création et entrée : usage unique côté service) ; [reusable] rend le courant tant qu'il a moins de [maxAgeMs] (9 min, un
 * ticket vit 10 min) : rouvrir une session par `resume` ne consomme rien côté service, donc ne mange pas les plafonds de l'API (20 par heure et par appareil, 120 par adresse).
 */
class TicketCache(private val clock: () -> Long, private val maxAgeMs: Long = 9 * 60_000L, private val fetch: () -> String?) {
    private var ticket: String? = null
    private var at = 0L

    @Synchronized fun fresh(): String? = fetch()?.also { ticket = it; at = clock() }
    /** Un ticket déjà obtenu par ailleurs (le premier, demandé pour dire son refus à l'écran) entre dans le cache. */
    @Synchronized fun adopt(t: String) { ticket = t; at = clock() }
    @Synchronized fun reusable(): String? = ticket?.takeIf { clock() - at < maxAgeMs } ?: fresh()
}

object PlayActivation {
    /** Le service ne sait juger qu'une activation signée avec ses facteurs d'appareil : une clé courte synthétisée par la TV (sans signature) l'est invérifiable (M-4). */
    fun verifiable(a: Activation): Boolean = a.signature.isNotEmpty() && a.factors.isNotEmpty()

    /** La plus récente activation que le service peut vérifier, ou null (la tuile dit alors d'activer la TV avec le fichier). */
    fun pick(all: List<Activation>, nowMs: Long): Activation? = TunnelEnroll.pickActivation(all.filter { verifiable(it) }, nowMs)
}

/** C-1 : le nom que la TV envoie en entrant (un spectateur doit en avoir un ; sans nom valable : « TV »). */
object PlayTvName {
    const val DEFAULT = "TV"
    fun of(raw: String?): String = (Pseudonym.check(raw) as? Pseudonym.Result.Ok)?.name ?: DEFAULT
}
