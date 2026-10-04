package castbridge.core.trust

/** Un refus de la TV (code [castbridge.core.tv.BtProtocol] ERR_*) et le moment où il a été reçu. Jamais de PIN ni de jeton ici. */
data class RefusalRecord(val code: Int, val atMs: Long)

/**
 * Dernier refus reçu de chaque TV (clé = adresse Bluetooth normalisée), gardé sur le téléphone : « Ouvrir avec CastBridge » s'en sert pour ne pas
 * promettre une copie que la TV a déjà refusée ([SendChoices.decide]). Effacé après un code PIN accepté ou une liaison réussie.
 * Texte : une ligne `clé<TAB>code<TAB>instant` ; une ligne abîmée est ignorée ; au plus [MAX] TV gardées (les plus récentes).
 */
class LinkRefusals(private val persistence: TrustPersistence, private val now: () -> Long = System::currentTimeMillis) {
    private fun key(k: String) = k.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    @Synchronized private fun read(): LinkedHashMap<String, RefusalRecord> {
        val m = LinkedHashMap<String, RefusalRecord>()
        for (line in runCatching { persistence.load() }.getOrNull().orEmpty().lines()) {
            val p = line.split('\t')
            if (p.size != 3 || p[0].isEmpty()) continue
            val code = p[1].toIntOrNull() ?: continue
            val at = p[2].toLongOrNull() ?: continue
            m[p[0]] = RefusalRecord(code, at)
        }
        return m
    }

    private fun write(m: Map<String, RefusalRecord>) {
        val keep = m.entries.sortedByDescending { it.value.atMs }.take(MAX).reversed()
        runCatching { persistence.save(keep.joinToString("\n") { "${it.key}\t${it.value.code}\t${it.value.atMs}" }) }
    }

    @Synchronized fun record(tvKey: String, code: Int, atMs: Long = now()) { val m = read(); m.remove(key(tvKey)); m[key(tvKey)] = RefusalRecord(code, atMs); write(m) }
    fun latest(tvKey: String): RefusalRecord? = read()[key(tvKey)]
    /** Le refus le plus récent, de n'importe quelle TV (téléphone sans TV enregistrée). */
    fun latestAny(): RefusalRecord? = read().values.maxByOrNull { it.atMs }
    @Synchronized fun clear(tvKey: String) { val m = read(); if (m.remove(key(tvKey)) != null) write(m) }
    @Synchronized fun clearAll() { runCatching { persistence.save("") } }

    companion object { const val MAX = 16 }
}
