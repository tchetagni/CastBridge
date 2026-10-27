package castbridge.core.tv

/**
 * Suppression en masse (décision du propriétaire, 2026-10-27) : logique PURE partagée par la TV, le téléphone et le serveur.
 * La suppression est DÉFINITIVE (pas la corbeille) : l'espace est libéré tout de suite, sans retour possible, après UNE confirmation
 * qui dit le nombre de fichiers et la taille totale. Les préréglages (vus, dossier, non lus depuis N mois, plus gros) ne font que
 * PRÉ-COCHER : l'utilisateur voit la liste cochée et peut décocher avant de confirmer. Voir docs/LIBRARY-AGENT.md « Suppression en masse ».
 */
object BulkDelete {
    /** Un fichier candidat, vu de la même façon par la TV ([LibraryItem]) et le téléphone (TvLibItem). */
    data class Entry(
        val name: String, val volume: String, val size: Long, val mtime: Long,
        val folder: String = "", val watched: Boolean = false, val playedAtMs: Long = 0, val playing: Boolean = false,
    ) {
        val key: String get() = key(volume, name)
    }

    /** Identité d'un fichier dans une sélection : le nom ne suffit pas (même nom sur la TV et sur la clé). */
    fun key(volume: String, name: String) = "$volume\u0000$name"

    fun LibraryItem.bulk() = Entry(name, volumeId, size, mtime, folder, meta.watched, meta.playedAtMs, playing)

    // ---------------------------------------------------------------- préréglages (ils ne font que cocher)

    /** Les vidéos déjà vues (le fichier en lecture n'est jamais coché d'office). */
    fun watched(items: List<Entry>): Set<String> = items.filter { it.watched && !it.playing }.map { it.key }.toSet()

    /** Tout un dossier, sous-dossiers compris (« Séries/Prison Break » prend « Séries/Prison Break/Saison 01 »). Le dossier racine ("") ne se coche pas ainsi. */
    fun inFolder(items: List<Entry>, folder: String): Set<String> {
        val f = folder.trim('/')
        if (f.isEmpty()) return emptySet()
        return items.filter { !it.playing && (it.folder == f || it.folder.startsWith("$f/")) }.map { it.key }.toSet()
    }

    /** Les dossiers qui contiennent au moins un fichier (sous-dossiers comptés dans leurs parents), avec nombre et taille ; ordre alphabétique. */
    data class FolderStat(val folder: String, val count: Int, val bytes: Long)
    fun folders(items: List<Entry>): List<FolderStat> {
        val count = HashMap<String, Int>(); val bytes = HashMap<String, Long>()
        for (e in items) {
            val parts = e.folder.trim('/').split('/').filter { it.isNotEmpty() }
            for (n in 1..parts.size) { val p = parts.take(n).joinToString("/"); count[p] = (count[p] ?: 0) + 1; bytes[p] = (bytes[p] ?: 0L) + e.size }
        }
        return count.keys.sortedWith(String.CASE_INSENSITIVE_ORDER).map { FolderStat(it, count.getValue(it), bytes.getValue(it)) }
    }

    const val DAY_MS = 86_400_000L

    /** 1, 3, 6, 12 mois en jours (un an = 365). */
    fun monthsToDays(months: Int): Int = if (months >= 12) 365 * (months / 12) else months * 30

    /** Dernier usage : la dernière lecture, sinon la date du fichier (0 = inconnue). */
    fun lastUse(e: Entry): Long = if (e.playedAtMs > 0) e.playedAtMs else e.mtime

    /** Non lus (ni ajoutés) depuis [days] jours. Un fichier sans aucune date connue n'est pas coché (dans le doute, on garde). */
    fun notPlayedSince(items: List<Entry>, days: Int, now: Long): Set<String> {
        val limit = now - days * DAY_MS
        return items.filter { !it.playing && lastUse(it) in 1..limit }.map { it.key }.toSet()
    }

    /** Du plus gros au plus petit (à égalité : par nom) : la liste dans laquelle l'utilisateur coche. */
    fun largestFirst(items: List<Entry>): List<Entry> = items.sortedWith(compareBy<Entry>({ -it.size }, { it.name.lowercase() }))

    // ---------------------------------------------------------------- sélection et textes

    fun totalSize(items: List<Entry>, selected: Set<String>): Long = items.filter { it.key in selected }.sumOf { it.size }
    fun count(items: List<Entry>, selected: Set<String>): Int = items.count { it.key in selected }

    /** « 14,2 Go », « 850 Mo », « 12 ko » : virgule française, même découpage que le reste de l'assistant. */
    fun size(b: Long): String = when {
        b >= 1L shl 30 -> String.format(java.util.Locale.FRANCE, "%.1f Go", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format(java.util.Locale.FRANCE, "%.0f Mo", b / (1L shl 20).toDouble())
        else -> "${b / 1024} ko"
    }

    fun files(n: Int) = if (n > 1) "$n fichiers" else "$n fichier"

    /** Barre d'en-tête : « 23 sélectionnés · 14,2 Go ». */
    fun headline(count: Int, bytes: Long) = "$count sélectionné${if (count > 1) "s" else ""} · ${size(bytes)}"

    /** LA confirmation, la même sur la TV et sur le téléphone. */
    fun confirmText(count: Int, bytes: Long) = "Supprimer définitivement ${files(count)} (${size(bytes)}) ? Sans retour possible."

    fun progressText(done: Int, total: Int) = "Suppression : $done / $total"

    /** Raisons d'un fichier laissé en place, en français. */
    fun reasonText(reason: String) = when (reason) {
        "moving" -> "déplacement en cours"
        "absent" -> "introuvable"
        "error" -> "suppression impossible"
        else -> reason
    }

    data class Skip(val name: String, val reason: String)

    /** Résultat court : « 23 fichiers supprimés, 14,2 Go libérés » + « 2 laissés : déplacement en cours (1), introuvable (1) ». */
    fun resultText(done: Int, freed: Long, skipped: List<Skip>): String {
        val head = "${files(done)} ${if (done > 1) "supprimés" else "supprimé"}, ${size(freed)} libérés"
        if (skipped.isEmpty()) return head
        val why = skipped.groupingBy { reasonText(it.reason) }.eachCount().entries.joinToString(", ") { "${it.key} (${it.value})" }
        return "$head. ${skipped.size} laissé${if (skipped.size > 1) "s" else ""} en place : $why"
    }

    // ---------------------------------------------------------------- le contrat de l'exécution

    /** Un fichier à supprimer : [volume] vide = toutes ses copies (comme /api/delete sans `volume`). */
    data class Target(val name: String, val volume: String = "")

    /** Ce que donne la suppression d'un fichier : des octets libérés, ou une raison de le laisser. */
    sealed class Outcome {
        data class Deleted(val freed: Long) : Outcome()
        data class Skipped(val reason: String) : Outcome()
    }

    /** Un travail en cours : lu par les fils HTTP (état), écrit par le fil de suppression. Les « k / n » avancent à chaque fichier. */
    class Job(val id: String, val total: Int) {
        @Volatile var done = 0
        @Volatile var freed = 0L
        @Volatile var finished = false
        private val skips = java.util.Collections.synchronizedList(ArrayList<Skip>())
        val skipped: List<Skip> get() = synchronized(skips) { ArrayList(skips) }
        internal fun skip(s: Skip) { skips += s }

        fun json(): String = "{\"job\":${ReceiverServer.q(id)},\"done\":$done,\"total\":$total,\"freed\":$freed,\"finished\":$finished,\"skipped\":" +
            skipped.joinToString(",", "[", "]") { "{\"name\":${ReceiverServer.q(it.name)},\"reason\":${ReceiverServer.q(it.reason)}}" } + "}"
    }

    /**
     * Passe [targets] un à un à [one] (qui supprime et dit ce qui en est) ; [done] compte aussi les fichiers laissés en place, pour que « k / n »
     * arrive à n. Une erreur sur un fichier ne coupe pas le reste : le fichier est laissé en place (« error »). [atEnd] est appelé UNE fois,
     * avant que le travail soit marqué terminé (la liste de la bibliothèque est invalidée une seule fois).
     */
    fun run(job: Job, targets: List<Target>, one: (Target) -> Outcome, atEnd: () -> Unit = {}) {
        try {
            for (t in targets) {
                val o = try { one(t) } catch (e: Exception) { Outcome.Skipped("error") }
                when (o) {
                    is Outcome.Deleted -> job.freed += o.freed
                    is Outcome.Skipped -> job.skip(Skip(t.name, o.reason))
                }
                job.done++
            }
        } finally {
            try { atEnd() } finally { job.finished = true }
        }
    }

    /** Lit le corps `{"files":[{"name":..,"volume":..},..]}` ; null si ce n'est pas ce format. Noms invalides écartés par l'appelant (safeName). */
    fun parseTargets(body: String): List<Target>? = try {
        val arr = castbridge.core.net.JsonLite.obj(body)["files"] as? List<*>
        arr?.map { f ->
            val m = f as? Map<*, *> ?: return null
            Target(m["name"] as? String ?: return null, (m["volume"] as? String).orEmpty())
        }
    } catch (e: Exception) { null }

    fun bodyOf(targets: List<Target>): String = castbridge.core.net.JsonLite.write(
        mapOf("files" to targets.map { mapOf("name" to it.name, "volume" to it.volume.ifEmpty { null }) }))
}
