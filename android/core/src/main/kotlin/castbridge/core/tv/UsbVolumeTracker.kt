package castbridge.core.tv

/**
 * L'historique des clés USB : ce que la TV retient des états qu'Android annonce (diffusions `ACTION_MEDIA_*`, lecture de `StorageManager`), pour que [UsbVolumeState] dise la bonne chose
 * au bon moment : depuis quand dure la vérification, si elle fait suite à un retrait sans éjection (retenu d'un démarrage à l'autre par [remembered] / [onRemember]), si la clé vient
 * d'être montée, quel fichier un retrait brutal a coupé, si « Préparer le retrait » est fini.
 *
 * Pure : l'horloge est donnée ([now], la même pour tous les faits). Les clés sont connues par un identifiant stable (l'UUID du volume). Une clé retirée proprement est oubliée ;
 * l'avis d'un retrait brutal reste [UsbVolumeState.BAD_REMOVAL_KEEP_MS], celui d'une éjection [UsbVolumeState.EJECTED_KEEP_MS].
 */
class UsbVolumeTracker(
    private val now: () -> Long,
    remembered: Set<String> = emptySet(),
    /** Appelé avec l'ensemble des clés dont on sait qu'elles ont été retirées sans éjection, à chaque changement (à écrire dans les préférences). */
    private val onRemember: (Set<String>) -> Unit = {},
) {
    /** Un changement d'état : [from] null = première fois qu'on voit cette clé ; [afterCheck] = le montage vient d'une vérification. */
    class Change(val id: String, val from: MediaState?, val to: MediaState, val afterCheck: Boolean)

    class Entry(val id: String, val facts: UsbFacts, val verdict: UsbVerdict)

    private class Vol(var label: String, var state: MediaState, var since: Long) {
        var afterCheck = false
        var interrupted: String? = null
        var pullReady = false
        /** Pulled after « Préparer le retrait » was done (set at `bad_removal`). */
        var prepared = false
    }

    private val vols = LinkedHashMap<String, Vol>()
    private val dirty = LinkedHashSet(remembered)

    private fun remember(id: String) {
        if (id in dirty) return
        dirty += id
        while (dirty.size > MAX_REMEMBERED) dirty.remove(dirty.first())
        onRemember(dirty.toSet())
    }

    private fun forget(id: String) { if (dirty.remove(id)) onRemember(dirty.toSet()) }

    /**
     * Une observation : une diffusion, un rappel du système ou la lecture de `StorageManager`. Rend le changement, ou null si rien n'a changé (la diffusion et la lecture disent la même
     * chose : l'horloge de la vérification ne repart pas). [interruptedCopy] = le fichier dont la copie a été coupée (seulement pour `bad_removal`).
     */
    @Synchronized fun seen(id: String, label: String, state: MediaState, interruptedCopy: String? = null): Change? {
        val t = now()
        val v = vols[id]
        if (v == null) {
            if (state == MediaState.UNKNOWN || state == MediaState.REMOVED) return null
            vols[id] = Vol(label.trim(), state, t).also { if (state == MediaState.BAD_REMOVAL) it.interrupted = interruptedCopy }
            when {
                state == MediaState.BAD_REMOVAL -> remember(id)
                state.mounted || state == MediaState.EJECTING || state == MediaState.UNMOUNTED -> forget(id)
            }
            return Change(id, null, state, afterCheck = false)
        }
        if (label.isNotBlank()) v.label = label.trim()
        if (state == MediaState.UNKNOWN) return null
        if (state == v.state) {
            if (state == MediaState.BAD_REMOVAL && v.interrupted == null && interruptedCopy != null) v.interrupted = interruptedCopy
            return null
        }
        val from = v.state
        // Android suit un retrait brutal de « unmounted » puis « removed » : l'avis du retrait brutal reste
        if (from == MediaState.BAD_REMOVAL && (state == MediaState.REMOVED || state == MediaState.UNMOUNTED)) return null
        if (state == MediaState.REMOVED) { vols.remove(id); return Change(id, from, state, afterCheck = false) }
        val wasPrepared = state == MediaState.BAD_REMOVAL && v.pullReady && from.mounted
        v.state = state; v.since = t; v.pullReady = false
        v.prepared = wasPrepared
        v.afterCheck = from == MediaState.CHECKING && state.mounted
        v.interrupted = if (state == MediaState.BAD_REMOVAL) interruptedCopy else null
        when {
            state == MediaState.BAD_REMOVAL -> remember(id)
            state.mounted || state == MediaState.EJECTING || state == MediaState.UNMOUNTED -> forget(id)      // montée : la vérification a réglé la question ; éjectée proprement : la prochaine fois sera normale
        }
        return Change(id, from, state, v.afterCheck)
    }

    /** [present] = les clés que le système liste encore : les autres sont parties (la vérification de l'une d'elles est finie). L'avis d'un retrait brutal ou d'une éjection reste. */
    @Synchronized fun gone(present: Set<String>): List<Change> {
        val out = ArrayList<Change>()
        for ((id, v) in vols.toList()) {
            if (id in present || v.state == MediaState.BAD_REMOVAL || v.state == MediaState.UNMOUNTED) continue
            vols.remove(id)
            out += Change(id, v.state, MediaState.REMOVED, afterCheck = false)
        }
        return out
    }

    /** « Préparer le retrait » est fini ([on]) ou n'a plus lieu d'être : seulement pour une clé montée ; tout changement d'état y met fin. */
    @Synchronized fun setPullReady(id: String, on: Boolean) {
        val v = vols[id] ?: return
        v.pullReady = on && v.state.mounted
    }

    @Synchronized fun isPullReady(id: String): Boolean = vols[id]?.pullReady == true

    @Synchronized fun stateOf(id: String): MediaState? = vols[id]?.state

    /**
     * Les clés qui ont quelque chose à dire, avec leurs faits et leur verdict à cet instant. [writing] = combien de copies écrivent sur la clé (demandé à chaque question : c'est ce que
     * la TV fait maintenant, pas un souvenir). Une clé dont l'avis est périmé est oubliée.
     */
    @Synchronized fun entries(writing: (String) -> Int = { 0 }): List<Entry> {
        val t = now()
        val out = ArrayList<Entry>()
        for ((id, v) in vols.toList()) {
            val f = UsbFacts(v.label, v.state, v.since, v.afterCheck, id in dirty, v.interrupted, if (v.state.mounted) writing(id) else 0, v.pullReady, v.prepared)
            val verdict = UsbVolumeState.judge(f, t)
            if (verdict.phase == UsbPhase.ABSENT) { vols.remove(id); continue }
            out += Entry(id, f, verdict)
        }
        return out
    }

    companion object {
        /** On ne retient que les 8 derniers retraits brutaux (les préférences ne grossissent pas). */
        const val MAX_REMEMBERED = 8
    }
}
