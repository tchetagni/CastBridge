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
        /**
         * [complete] false would be a partial copy: never counts. [fresh] = the TV hashed the file's bytes in this run (never a cached hash): required for a MOVE.
         * [masked] = a child profile is active on the TV: no name nor folder is given (the phone says « Déjà sur la TV » without a place, never plays nor deletes on it).
         */
        data class Present(val name: String, val folder: String, val size: Long, val sha256: String, val complete: Boolean = true,
                           val fresh: Boolean = false, val masked: Boolean = false) : Tv {
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
         * Nothing is copied. [tvName] = the TV's file to use; [play] = start it on the TV (« Copier et lire »); [deleteSource] = MOVE whose TV copy passed the FRESH
         * hash proof ([MoveProof.byContentHash]): still a candidate only, the phone must then compare the edges itself ([afterEdges]) before asking the user.
         */
        data class Skip(val tvName: String, val where: String, val play: Boolean, val deleteSource: Boolean, val text: String) : Outcome
    }

    /** Hash the local file only when it can matter: the TV holds a file of that size, or the queue holds another file of that size. Never for a forced copy. */
    fun mustHash(sizeAnswer: Tv, sameSizeInQueue: Boolean, force: Boolean = false): Boolean =
        !force && (sizeAnswer is Tv.Candidates || sameSizeInQueue)

    /** A hash kept with the queue may be reused to recognise a twin for a copy, NEVER for a MOVE (the file may have changed since: recomputed at the decision). */
    fun mayReuseHash(action: Action, stored: String?): Boolean = action != Action.MOVE && ContentHash.valid(stored)

    /**
     * The last step of a MOVE without copy: the phone read the first and last bytes of the TV's file itself (/stream/ Range) and compared them to its own
     * ([MoveProof.byEdges]). Without that second proof the original STAYS.
     */
    fun afterEdges(s: Outcome.Skip, edgesMatch: Boolean): Outcome.Skip =
        if (!s.deleteSource) s else if (MoveProof.mayDeleteWithoutCopy(hashProof = true, edgesProof = edgesMatch)) s
        else s.copy(deleteSource = false, text = DedupTexts.kept(s.where))

    fun decide(f: Facts): Outcome {
        if (f.force) return Outcome.Copy("copie demandée malgré le doublon")
        val sha = f.localSha?.lowercase()
        if (f.localSize <= 0 || !ContentHash.valid(sha)) return Outcome.Copy("empreinte du fichier inconnue")
        val tv = f.tv
        if (tv is Tv.Present && tv.masked && tv.complete && tv.size == f.localSize && tv.sha256.lowercase() == sha) {
            return when (f.action) {
                Action.COPY -> Outcome.Skip("", "", play = false, deleteSource = false, text = DedupTexts.ALREADY_THERE_MASKED)
                Action.COPY_AND_PLAY -> Outcome.Copy("profil enfant : fichier de la TV non désigné")
                Action.MOVE -> Outcome.Skip("", "", play = false, deleteSource = false, text = DedupTexts.KEPT_MASKED)
            }
        }
        if (tv is Tv.Present && tv.complete && tv.size == f.localSize && tv.sha256.lowercase() == sha) {
            val proof = MoveProof.byContentHash(f.localSize, sha, tv.size, tv.sha256, tv.complete, tv.fresh)
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
                val masked = o.bool("masked") == true
                if ((name.isEmpty() && !masked) || !ContentHash.valid(sha) || size <= 0) Tv.Unsupported
                else Tv.Present(if (masked) "" else name, if (masked) "" else o.str("folder").orEmpty(), size, sha, o.bool("complete") != false, o.bool("fresh") == true, masked)
            }
            else -> Tv.Unsupported
        }
    }
}

/** French texts of the content dedup (one place, tested). */
object DedupTexts {
    fun alreadyThere(where: String) = "Déjà sur la TV : $where — contenu identique, non recopié."
    fun playing(where: String) = "Déjà sur la TV : $where — lecture du fichier de la TV, sans copie."
    fun moved(where: String) = "Déjà sur la TV : $where — contenu identique vérifié (empreinte recalculée et début/fin du fichier) : la suppression de l'original vous est proposée, avec confirmation."
    const val ALREADY_THERE_MASKED = "Déjà sur la TV — contenu identique, non recopié."
    const val KEPT_MASKED = "Déjà sur la TV — non recopié ; l'original reste sur le téléphone (profil enfant actif sur la TV)."
    fun kept(where: String) = "Déjà sur la TV : $where — non recopié ; l'original reste sur le téléphone (la TV n'a pas confirmé l'empreinte)."
    fun twin(first: String, tvName: String) = "Même contenu que « $first », déjà envoyé dans cette file (sur la TV : $tvName) : non recopié."
    fun checking(name: String, pct: Int) = "Vérification de « $name » (doublon ?) : $pct %"
    const val COPY_ANYWAY = "Copier quand même"
}
