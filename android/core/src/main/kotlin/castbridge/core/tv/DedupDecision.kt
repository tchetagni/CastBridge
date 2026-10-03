package castbridge.core.tv

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.bool
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str

/**
 * « Éviter les doublons lors de la copie » (docs/agent-reports/copy-dedup.md, R-12): before a COPY, a « Copier et lire » or a MOVE leaves the phone, is the
 * same CONTENT already on the TV (under any name, in any folder)? Pure: the phone's runner feeds it the TV's answers to GET /api/have and its own SHA-256.
 *
 * Identical = same size AND same SHA-256 (a SHA-256 collision between two different files of the same size is treated as impossible).
 * Anything unsure copies: an old TV, an index not ready, a candidate of the same size but another hash, a partial file on the TV (never « present »).
 * Never blocking the user: the worst case of a doubt is a copy (the TV then flags the duplicate in its library, as before).
 */
object DedupDecision {
    enum class Action { COPY, COPY_AND_PLAY, MOVE }

    /** What the TV said about a size (and a SHA-256). */
    sealed interface Tv {
        /** A TV without the route (older CastBridge-TV), or no answer: nothing is known. */
        object Unsupported : Tv
        object Absent : Tv
        data class Candidates(val count: Int, val indexing: Boolean) : Tv
        data class Indexing(val pending: Int) : Tv
        /** [complete] false would be a partial copy: never counts (the TV never answers it, the phone checks it anyway). */
        data class Present(val name: String, val folder: String, val size: Long, val sha256: String, val complete: Boolean = true) : Tv {
            val where: String get() = if (folder.isEmpty()) name else "$folder/$name"
        }
    }

    /** The same content sent earlier in this very queue ([tvName] = the name the TV holds it under; [onTv] = the TV lists it, complete, of that size). */
    data class Twin(val name: String, val tvName: String, val size: Long, val sha256: String, val onTv: Boolean)

    data class Facts(val action: Action, val localSize: Long, val localSha: String?, val tv: Tv, val twin: Twin? = null, val force: Boolean = false)

    sealed interface Outcome {
        /** Copy as usual; [why] for the log. */
        data class Copy(val why: String) : Outcome
        /**
         * Nothing is copied. [tvName] = the TV's file to use; [play] = start it on the TV (« Copier et lire »); [deleteSource] = MOVE whose TV copy is PROVEN
         * identical by hash ([MoveProof.byContentHash]): the phone may then ask Android to delete the original (with its confirmation). [text] = French, for the user.
         */
        data class Skip(val tvName: String, val where: String, val play: Boolean, val deleteSource: Boolean, val text: String) : Outcome
    }

    /** Hash the local file only when it can matter: the TV holds a file of that size, or the queue holds another file of that size. Never for a forced copy. */
    fun mustHash(sizeAnswer: Tv, sameSizeInQueue: Boolean, force: Boolean = false): Boolean =
        !force && (sizeAnswer is Tv.Candidates || sameSizeInQueue)

    fun decide(f: Facts): Outcome {
        if (f.force) return Outcome.Copy("copie demandée malgré le doublon")
        val sha = f.localSha?.lowercase()
        if (f.localSize <= 0 || !ContentHash.valid(sha)) return Outcome.Copy("empreinte du fichier inconnue")
        val tv = f.tv
        if (tv is Tv.Present && tv.complete && tv.size == f.localSize && tv.sha256.lowercase() == sha) {
            val proof = MoveProof.byContentHash(f.localSize, sha, tv.size, tv.sha256, tv.complete)
            return when (f.action) {
                Action.COPY -> Outcome.Skip(tv.name, tv.where, play = false, deleteSource = false, text = DedupTexts.alreadyThere(tv.where))
                Action.COPY_AND_PLAY -> Outcome.Skip(tv.name, tv.where, play = true, deleteSource = false, text = DedupTexts.playing(tv.where))
                Action.MOVE -> Outcome.Skip(tv.name, tv.where, play = false, deleteSource = proof, text = if (proof) DedupTexts.moved(tv.where) else DedupTexts.kept(tv.where))
            }
        }
        val t = f.twin
        if (t != null && t.onTv && t.size == f.localSize && t.sha256.lowercase() == sha) {
            // same content as a file this queue sent: the phone compared two of ITS files, the TV did not confirm the hash -> a MOVE never deletes on this
            return when (f.action) {
                Action.COPY -> Outcome.Skip(t.tvName, t.tvName, play = false, deleteSource = false, text = DedupTexts.twin(t.name, t.tvName))
                Action.COPY_AND_PLAY -> Outcome.Skip(t.tvName, t.tvName, play = true, deleteSource = false, text = DedupTexts.playing(t.tvName))
                Action.MOVE -> Outcome.Skip(t.tvName, t.tvName, play = false, deleteSource = false, text = DedupTexts.kept(t.tvName))
            }
        }
        return Outcome.Copy(when (tv) {
            Tv.Unsupported -> "TV sans index de contenu"
            Tv.Absent -> "contenu absent de la TV"
            is Tv.Indexing -> "empreintes de la TV pas encore prêtes"
            is Tv.Candidates -> "même taille seulement"
            is Tv.Present -> "même taille, contenu différent"
        })
    }

    /** Parses GET /api/have. Anything unreadable is [Tv.Unsupported] (copy). */
    fun parse(json: String?): Tv {
        val o = runCatching { JsonLite.obj(json ?: return Tv.Unsupported) }.getOrNull() ?: return Tv.Unsupported
        return when (o.str("state")) {
            "absent" -> Tv.Absent
            "candidates" -> Tv.Candidates((o.long("count") ?: 0).toInt(), o.bool("indexing") == true)
            "indexing" -> Tv.Indexing((o.long("pending") ?: 0).toInt())
            "present" -> {
                val name = o.str("name").orEmpty(); val sha = o.str("sha256").orEmpty().lowercase(); val size = o.long("size") ?: 0
                if (name.isEmpty() || !ContentHash.valid(sha) || size <= 0) Tv.Unsupported else Tv.Present(name, o.str("folder").orEmpty(), size, sha, o.bool("complete") != false)
            }
            else -> Tv.Unsupported
        }
    }
}

/** French texts of the content dedup (one place, tested). */
object DedupTexts {
    fun alreadyThere(where: String) = "Déjà sur la TV : $where — contenu identique, non recopié."
    fun playing(where: String) = "Déjà sur la TV : $where — lecture du fichier de la TV, sans copie."
    fun moved(where: String) = "Déjà sur la TV : $where — contenu identique vérifié : l'original peut être retiré du téléphone (Android demande confirmation)."
    fun kept(where: String) = "Déjà sur la TV : $where — non recopié ; l'original reste sur le téléphone (la TV n'a pas confirmé l'empreinte)."
    fun twin(first: String, tvName: String) = "Même contenu que « $first », déjà envoyé dans cette file (sur la TV : $tvName) : non recopié."
    fun checking(name: String, pct: Int) = "Vérification de « $name » (doublon ?) : $pct %"
    const val COPY_ANYWAY = "Copier quand même"
}
