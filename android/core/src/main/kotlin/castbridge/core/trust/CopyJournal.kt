package castbridge.core.trust

/** Une copie terminée (réussie ou non), telle que « Dernières copies » la montre. [cause] = « OK » ou le nom d'une [UploadFailure.Cause]. */
data class CopyEntry(val atMs: Long, val name: String, val ok: Boolean, val cause: String, val step: String, val percent: Int, val text: String)

/**
 * Ce que le téléphone se rappelle des copies vers la TV, sans donnée sensible :
 * un petit anneau de [RING] lignes (instant, étape, événement : `instant<TAB>étape<TAB>texte`) pour comprendre après coup où une copie s'est arrêtée,
 * et les [HISTORY] dernières copies (réussite ou échec + cause) pour l'écran « Dernières copies » et la boite « Ouvrir avec ».
 * Tout texte passe par [UploadFailure.sanitize] ; un nom de fichier n'est jamais un chemin ; une ligne abîmée est ignorée.
 */
class CopyJournal(private val ring: TrustPersistence, private val history: TrustPersistence, private val now: () -> Long = System::currentTimeMillis) {
    private fun load(p: TrustPersistence) = runCatching { p.load() }.getOrNull().orEmpty().lines().filter { it.isNotBlank() }
    private fun flat(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    @Synchronized fun log(step: CopyStep, event: String) {
        val line = "${now()}\t${step.label}\t${flat(UploadFailure.sanitize(event))}"
        runCatching { ring.save((load(ring) + line).takeLast(RING).joinToString("\n")) }
    }

    fun lines(): List<String> = load(ring)

    @Synchronized fun record(e: CopyEntry) {
        val name = flat(e.name.substringAfterLast('/'))
        val line = listOf(e.atMs, name, if (e.ok) 1 else 0, flat(e.cause), flat(e.step), e.percent, flat(UploadFailure.sanitize(e.text))).joinToString("\t")
        runCatching { history.save((load(history) + line).takeLast(HISTORY).joinToString("\n")) }
    }

    /** Les dernières copies, la plus récente d'abord. */
    fun recent(): List<CopyEntry> = load(history).mapNotNull { l ->
        val p = l.split('\t')
        if (p.size != 7) return@mapNotNull null
        CopyEntry(p[0].toLongOrNull() ?: return@mapNotNull null, p[1], p[2] == "1", p[3], p[4], p[5].toIntOrNull() ?: return@mapNotNull null, p[6])
    }.reversed()

    /** Le dernier résultat de ce fichier s'il est un échec de moins de [withinMs] ; null si le dernier résultat est une réussite ou trop ancien. */
    fun lastFailureFor(name: String, withinMs: Long): CopyEntry? {
        val n = name.substringAfterLast('/')
        val last = recent().firstOrNull { it.name == n } ?: return null
        return last.takeIf { !it.ok && now() - it.atMs in 0..withinMs }
    }

    companion object { const val RING = 200; const val HISTORY = 20 }
}
