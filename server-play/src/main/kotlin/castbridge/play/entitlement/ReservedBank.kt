package castbridge.play.entitlement

import castbridge.core.quiz.Json
import castbridge.core.quiz.Question
import castbridge.core.quiz.QuizBank
import castbridge.core.quiz.QuizLotFormat
import castbridge.core.quiz.online.PlayRules
import java.io.File

/** D'où viennent les questions réservées : une liste de lots (portées) et leur lecture À LA DEMANDE. Jamais d'écriture, jamais de liste servie à un client. */
interface ReservedSource {
    fun scopes(): Set<String>
    fun load(scope: String): List<Question>
}

/**
 * Les paquets réservés du dossier monté EN LECTURE SEULE (`CASTBRIDGE_PLAY_RESERVED_DIR`) : `quiz-<portée>-reserved-p<N>-v<N>.quiz.zip`. L'index vient des NOMS de fichiers
 * (aucun lot lu au démarrage) ; un lot n'est lu que quand une salle en a le droit ([load]). Un lot illisible, de portée contradictoire avec son nom, ou d'une version plus
 * ancienne d'une même partie est ignoré ; une portée absente de l'index ne sort jamais du dossier.
 */
class DirReservedSource(dir: File?) : ReservedSource {
    private val byScope: Map<String, List<File>>

    init {
        val files = dir?.takeIf { it.isDirectory }?.listFiles { f -> f.isFile && NAME.matches(f.name) }?.sortedBy { it.name }.orEmpty()
        byScope = files.groupBy { NAME.matchEntire(it.name)!!.groupValues[1] }
    }

    override fun scopes(): Set<String> = byScope.keys

    override fun load(scope: String): List<Question> {
        val files = byScope[scope] ?: return emptyList()
        // la plus haute version de chaque partie
        val latest = files.groupBy { NAME.matchEntire(it.name)!!.groupValues[2] }.values.map { g -> g.maxBy { NAME.matchEntire(it.name)!!.groupValues[3].toInt() } }
        return latest.flatMap { f -> runCatching { QuizLotFormat.read(f, scope).bank.all }.getOrDefault(emptyList()) }
    }

    companion object {
        private val NAME = Regex("^quiz-([a-z0-9]+(?:-[a-z0-9]+)*)-reserved-p(\\d{1,3})-v(\\d{1,6})\\.quiz\\.zip$")
    }
}

/**
 * Les banques de questions du service. [freeBank] = la banque libre SANS aucun id réservé (un id réservé qui traîne dans une source libre est exclu : défense en profondeur).
 * [bankFor] rend, pour une salle dont l'hôte couvre [covered], la banque libre PLUS les réservées des seuls lots couverts ; sans droit : la banque libre elle-même (partagée,
 * aucune copie par salle). FERMÉ : sans la liste GELÉE des ids réservables (`reserved-ids.json`), rien de réservé n'est servi ; une question de paquet dont l'id n'est pas dans
 * le gel n'est pas servie. Les lots sont lus par portée, à la demande, et gardés dans un cache borné ([maxCachedScopes]).
 */
class ReservedBank(base: QuizBank, private val source: ReservedSource, reservedIds: Set<String>?, private val maxCachedScopes: Int = 8) {
    private val ids: Set<String> = reservedIds.orEmpty()
    val frozen: Boolean = ids.isNotEmpty()
    val freeBank: QuizBank = if (frozen) QuizBank(base.all.filter { it.id !in ids }, base.channel) else base
    private val channel = base.channel

    private val scopeCache = object : LinkedHashMap<String, List<Question>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Question>>?) = size > maxCachedScopes
    }
    private val bankCache = object : LinkedHashMap<String, QuizBank>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, QuizBank>?) = size > MAX_BANKS
    }

    fun cachedScopes(): Int = synchronized(scopeCache) { scopeCache.size }

    private fun loadScope(scope: String): List<Question> = synchronized(scopeCache) {
        scopeCache.getOrPut(scope) { source.load(scope).filter { it.id in ids } }
    }

    fun bankFor(covered: Set<String>): QuizBank {
        if (!frozen || covered.isEmpty()) return freeBank
        val known = source.scopes()
        val scopes = (if (PlayRules.ALL in covered) known else covered.filter { it in known }).toSortedSet()
        if (scopes.isEmpty()) return freeBank
        val key = scopes.joinToString(",")
        return synchronized(bankCache) { bankCache.getOrPut(key) { freeBank.merge(QuizBank(scopes.flatMap { loadScope(it) }, channel)) } }
    }

    companion object {
        private const val MAX_BANKS = 16
        private const val MAX_IDS_BYTES = 16L shl 20

        /** Le gel des ids réservables : `{"version":1,"ids":[…]}` ou une liste ; absent, illisible ou trop gros ⇒ null (fermé). */
        fun readIds(file: File?): Set<String>? = runCatching {
            if (file == null || !file.isFile || file.length() > MAX_IDS_BYTES) return@runCatching null
            val v = Json.parse(file.readText(Charsets.UTF_8))
            val list = (if (v is Map<*, *>) v["ids"] else v) as? List<*> ?: return@runCatching null
            list.map { it as? String ?: return@runCatching null }.toSet()
        }.getOrNull()
    }
}
