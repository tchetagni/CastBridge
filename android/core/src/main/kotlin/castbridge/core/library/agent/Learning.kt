package castbridge.core.library.agent

import castbridge.core.connect.KeyValueStore
import castbridge.core.net.JsonLite

/**
 * What the agent learns from the user's corrections, stored on the phone only (private preferences) and erasable.
 *
 *  - alias: "prison break" -> "PB": the user edited the series / film title of a proposal, the next files of the same title get it;
 *  - folder: "prison break" -> "Séries/Mes séries": the user chose another folder;
 *  - ignored: names or titles the user asked never to touch again.
 *
 * Nothing learned ever leaves the phone (the optional server model only receives cleaned names, see [NamingModel]).
 */
class LearnedRules(private val kv: KeyValueStore, private val max: Int = 300) {
    private val alias = LinkedHashMap<String, String>()
    private val folder = LinkedHashMap<String, String>()
    private val ignored = LinkedHashSet<String>()

    init { load() }

    @Synchronized fun aliasFor(titleKey: String): String? = alias[titleKey]
    @Synchronized fun folderFor(titleKey: String): String? = folder[titleKey]
    @Synchronized fun isIgnored(key: String): Boolean = key in ignored
    @Synchronized fun size(): Int = alias.size + folder.size + ignored.size

    /**
     * The user replaced [proposed] by [edited] (a whole file name). If the title part differs, the new title is remembered for
     * every future file of the same title. Returns true if something was learned.
     */
    @Synchronized fun recordEdit(proposedTitleKey: String, editedFileName: String): Boolean {
        if (proposedTitleKey.isBlank()) return false
        val ep = NameParser.parse(editedFileName)
        val newTitle = when (ep.kind) {
            Kind.SERIES, Kind.MOVIE -> ep.title
            else -> ep.stem.ifBlank { ep.title }
        }.trim()
        if (newTitle.isBlank() || SafeName.checkName(newTitle) != null || newTitle.length > 100) return false
        if (Text.key(newTitle) == proposedTitleKey && alias[proposedTitleKey] == null) return false
        put(alias, proposedTitleKey, newTitle)
        save()
        return true
    }

    @Synchronized fun recordFolder(titleKey: String, folderPath: String) {
        if (titleKey.isBlank() || SafeName.checkFolder(folderPath) != null) return
        put(folder, titleKey, folderPath); save()
    }

    @Synchronized fun ignore(key: String) { if (key.isNotBlank()) { ignored.remove(key); ignored.add(key); while (ignored.size > max) ignored.remove(ignored.first()); save() } }

    @Synchronized fun forget(titleKey: String) { alias.remove(titleKey); folder.remove(titleKey); ignored.remove(titleKey); save() }

    /** "Effacer ce que l'assistant a appris". */
    @Synchronized fun clear() { alias.clear(); folder.clear(); ignored.clear(); kv.put(KEY, null) }

    private fun put(m: LinkedHashMap<String, String>, k: String, v: String) { m.remove(k); m[k] = v; while (m.size > max) m.remove(m.keys.first()) }

    private fun save() = kv.put(KEY, JsonLite.write(linkedMapOf("alias" to alias, "folder" to folder, "ignored" to ignored.toList())))

    @Suppress("UNCHECKED_CAST")
    private fun load() {
        val m = runCatching { JsonLite.obj(kv.get(KEY) ?: return) }.getOrNull() ?: return
        (m["alias"] as? Map<String, Any?>)?.forEach { (k, v) -> (v as? String)?.let { alias[k] = it } }
        (m["folder"] as? Map<String, Any?>)?.forEach { (k, v) -> (v as? String)?.let { if (SafeName.checkFolder(it) == null) folder[k] = it } }
        (m["ignored"] as? List<Any?>)?.forEach { (it as? String)?.let(ignored::add) }
    }

    companion object { const val KEY = "agent.learned" }
}
