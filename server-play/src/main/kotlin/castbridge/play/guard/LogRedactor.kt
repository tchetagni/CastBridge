package castbridge.play.guard

import castbridge.core.quiz.Json
import castbridge.core.quiz.online.PlayRedact
import java.time.Instant

/**
 * Journal du service de jeu : une ligne JSON par évènement (champs ECS : `@timestamp`, `log.level`, `event.action`, `message`), SANS secret (DESIGN-W20 § 3.5, T-18).
 * Le service n'écrit aucune ligne autrement : pas de requête journalisée, pas de message de joueur, pas de question ni de réponse.
 *
 * Règles par NOM de champ (tout le reste est un texte libre, passé par [PlayRedact.scrub]) :
 *  - `ip` : tronquée ; `code` : `ABCD-****` ; `name` / `pseudo` : empreinte de 8 hexadécimaux ; `device` / `deviceHash` : empreinte de 8 hexadécimaux ;
 *  - `roomId` : tel quel (c'est la clé de corrélation ; il ne permet pas d'entrer : on entre par le code ou le jeton) ;
 *  - nombres et booléens : tels quels ; une valeur inconnue qui n'est pas un texte devient son nom de classe (jamais son `toString`).
 * Le puits ([sink]) est injectable (tests) ; par défaut, la sortie d'erreur du conteneur. Cette classe ne lève jamais d'exception.
 */
class LogRedactor(private val sink: (String) -> Unit = { System.err.println(it) }, private val clock: () -> Long = System::currentTimeMillis) {
    /** Échantillonnage : au plus une ligne par seconde et par (action, adresse) ; le nombre de lignes supprimées est dit sur la suivante (un journal ne se laisse pas inonder). */
    private val last = object : LinkedHashMap<String, LongArray>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LongArray>?): Boolean = size > MAX_KEYS
    }

    private fun sampled(action: String, fields: Map<String, Any?>): Long? = synchronized(last) {
        val now = clock()
        val k = action + "|" + (fields["ip"] ?: "")
        val e = last.getOrPut(k) { longArrayOf(Long.MIN_VALUE / 2, 0L) }
        if (now - e[0] < 1_000L) { e[1]++; return null }
        e[0] = now
        val dropped = e[1]; e[1] = 0L
        dropped
    }

    fun event(action: String, message: String = "", fields: Map<String, Any?> = emptyMap(), level: String = "info") {
        try {
            val dropped = sampled(action, fields) ?: return
            val line = LinkedHashMap<String, Any?>()
            line["@timestamp"] = Instant.ofEpochMilli(clock()).toString()
            line["log.level"] = level
            line["event.action"] = PlayRedact.scrub(action).take(64)
            if (message.isNotEmpty()) line["message"] = PlayRedact.scrub(message).take(300)
            for ((k, v) in fields) line[k.take(40)] = field(k, v)
            if (dropped > 0) line["suppressed"] = dropped
            sink(Json.write(line))
        } catch (_: Throwable) { /* un journal ne fait jamais tomber le service */ }
    }

    private fun day(): Long = clock() / 86_400_000L

    private fun field(k: String, v: Any?): Any? = when {
        v == null -> null
        k == "ip" -> PlayRedact.ip(v.toString())
        k == "code" -> PlayRedact.code(v.toString())
        k == "name" || k == "pseudo" -> PlayRedact.pseudo(v.toString(), day())
        k == "device" || k == "deviceHash" -> PlayRedact.device(v.toString(), day())
        k == "roomId" -> v.toString().takeIf { Regex("[0-9a-f]{32}").matches(it) } ?: PlayRedact.scrub(v.toString())
        v is Number || v is Boolean -> v
        v is String -> PlayRedact.scrub(v).take(300)
        v is Collection<*> -> v.take(20).map { field(k, it) }
        else -> v.javaClass.simpleName
    }

    companion object {
        private const val MAX_KEYS = 5_000
        /** Un journal qui ne dit rien (tests, hub construit sans service). */
        fun silent() = LogRedactor({ })
    }
}
