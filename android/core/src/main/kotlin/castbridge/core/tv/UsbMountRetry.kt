package castbridge.core.tv

/**
 * « Tolérance de lecture » (docs/STORAGE.md § « Clé USB mal éjectée »). Ce qui lit une clé (la recherche du fichier d'activation, l'explorateur de fichiers, la bibliothèque)
 * peut échouer pendant qu'Android vérifie la clé (`checking`) : au `mounted`, il réessaie tout seul. UN SEUL réessai par montage : une lecture qui échoue encore sur la clé montée
 * n'est pas relancée (pas de boucle) ; un nouveau montage (Android revérifie la clé) renouvelle le crédit.
 *
 * Pure (aucune horloge, aucun fil) : la TV appelle [checkingStarted] quand un volume passe en `checking`, [failed] quand un lecteur échoue, [mounted] quand le volume est monté
 * (la diffusion et le rappel du système peuvent arriver tous les deux : un seul réessai) et [gone] quand il part ou n'est pas montable. Un lecteur = un nom (« activation »,
 * « explorateur », « bibliothèque »).
 */
class MountRetry {
    private class Vol {
        /** Les lecteurs qui ont échoué pendant la vérification et attendent le montage (dans l'ordre). */
        val waiting = LinkedHashSet<String>()
        /** Les lecteurs déjà réessayés pour le montage en cours. */
        val retried = HashSet<String>()
    }

    private val vols = HashMap<String, Vol>()

    /** Android vérifie [volumeId] : un nouveau montage commence, chaque lecteur retrouve son réessai. */
    @Synchronized fun checkingStarted(volumeId: String) { vols.getOrPut(volumeId) { Vol() }.retried.clear() }

    /**
     * [reader] a échoué sur [volumeId] alors qu'Android disait [stateAtFailure]. Vrai = il attend le montage. Un échec qui n'a pas eu lieu pendant une vérification, ou d'un lecteur
     * qui a déjà utilisé son réessai pour ce montage, n'attend rien.
     */
    @Synchronized fun failed(reader: String, volumeId: String, stateAtFailure: MediaState): Boolean {
        if (stateAtFailure != MediaState.CHECKING) return false
        val v = vols.getOrPut(volumeId) { Vol() }
        if (reader in v.retried) return false
        v.waiting += reader
        return true
    }

    /** [volumeId] est monté : les lecteurs qui attendaient, chacun une seule fois pour ce montage (vide si rien n'attend ou si le réessai est déjà donné). */
    @Synchronized fun mounted(volumeId: String): List<String> {
        val v = vols[volumeId] ?: return emptyList()
        val now = v.waiting.toList()                   // [failed] never queues a reader that already retried: nothing to filter
        v.retried += now
        v.waiting.clear()
        return now
    }

    /** [volumeId] est parti ou n'est pas montable : personne n'attend plus son montage. */
    @Synchronized fun gone(volumeId: String) { vols.remove(volumeId) }
}
