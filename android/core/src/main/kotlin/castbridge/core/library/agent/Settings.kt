package castbridge.core.library.agent

import castbridge.core.connect.KeyValueStore
import castbridge.core.net.JsonLite

/**
 * The assistant's own settings, in the phone's private preferences. Everything that sends or changes something is OFF by default:
 *  - [autoRename]: rename new uploads when the rules are sure (undoable). Off.
 *  - the AI layer: needs its own explicit consent, separate from the app's usage statistics consent. Off.
 */
class AgentSettings(private val kv: KeyValueStore, private val now: () -> Long = System::currentTimeMillis) {
    var autoRename: Boolean
        get() = kv.get(K_AUTO) == "1"
        set(v) = kv.put(K_AUTO, if (v) "1" else null)

    /** Consent to the optional AI layer, valid only for the wording version it was given for ([AiConsent.VERSION]). */
    val aiEnabled: Boolean get() = kv.get(K_AI) == AiConsent.VERSION
    fun grantAi() = kv.put(K_AI, AiConsent.VERSION)
    fun revokeAi() = kv.put(K_AI, null)

    /** Also read the phone's own folders (picked with the system picker), not only the TV library. */
    var phoneTreeUri: String?
        get() = kv.get(K_TREE)
        set(v) = kv.put(K_TREE, v)

    /** Advice dismissed or snoozed: id -> time until which it stays hidden. */
    fun hiddenUntil(): Map<String, Long> {
        val m = runCatching { JsonLite.obj(kv.get(K_HIDE) ?: return emptyMap()) }.getOrNull() ?: return emptyMap()
        return m.mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toLong() } }.toMap().filterValues { it > now() }
    }

    fun snooze(id: String, days: Int = 7) {
        val cur = hiddenUntil().toMutableMap()
        cur[id] = now() + days * 86_400_000L
        kv.put(K_HIDE, JsonLite.write(cur))
    }

    /** "Effacer": everything the assistant remembers, except the journal (which has its own button). */
    fun clearAll() { listOf(K_AUTO, K_AI, K_TREE, K_HIDE, LearnedRules.KEY).forEach { kv.put(it, null) } }

    companion object {
        const val K_AUTO = "agent.auto"
        const val K_AI = "agent.ai.consent"
        const val K_TREE = "agent.tree"
        const val K_HIDE = "agent.hide"
    }
}

/** The "rangement automatique des nouveaux envois": only a safe, boring subset of the rules, and always undoable. */
object AutoRename {
    private val SAFE = setOf(Kind.SERIES, Kind.MOVIE, Kind.PERSONAL)

    /** The name a file arriving on the TV should get, or null to leave it exactly as it is. Never a folder, never a trash. */
    fun nameFor(original: String, hideAudio: Audio = Audio.VF, learned: LearnedRules? = null, currentYear: Int = java.time.LocalDate.now().year): String? {
        val p = NameParser.parse(original, currentYear = currentYear)
        if (p.kind !in SAFE || p.confidence < 0.9 || p.title.isBlank() && p.kind != Kind.PERSONAL) return null
        if (p.kind == Kind.PERSONAL && p.date == null) return null
        if (p.kind == Kind.SERIES && (p.season == null || p.episode == null)) return null
        if (learned?.isIgnored(p.titleKey) == true || learned?.isIgnored(original.lowercase()) == true) return null
        val n = Namer.propose(p, FileRef(Origin.TV, original, 0), Labels("fr"), hideAudio, learned).name
        if (n == original || n.equals(original, ignoreCase = true) || SafeName.checkName(n) != null) return null
        return n
    }
}
