package castbridge.core.tv

/**
 * R-22: a file shared from another app (Telegram: `content://org.telegram.messenger.provider/...`, ACTION_SEND) is readable only while that app's
 * temporary grant lives, and the grant dies with the process. The queue must therefore anchor each file DURABLY at the moment it is queued, while
 * the right to read still exists. Pure rules (no Android): the phone's code gives the facts, these objects decide.
 */
enum class Anchor {
    /** takePersistableUriPermission worked (ACTION_OPEN_DOCUMENT, Files, some galleries): the original URI itself stays readable. */
    PERSISTED,
    /** The same file was found in MediaStore (same name, same size, same first 64 KiB): that `content://media/...` URI is read from now on. */
    MEDIASTORE,
    /** The file was copied to `cacheDir/queue/` while the grant existed (the only way for a provider that keeps its files private). */
    CACHE,
    /** The copy to the cache is running (the item waits, « Préparation du fichier… n % »). A restart during it leaves nothing readable. */
    PENDING,
    /**
     * H1 (audit 2026-10-07): nothing durable could be made (unknown size, over 2 GiB, over a quarter of the free space), but the file is STILL readable:
     * the original URI is kept and the queue sends it now. It is « à repartager » only if `readable()` fails when the send starts.
     */
    VOLATILE,
    /** The file was lost (grant gone, copy failed): it must be shared again (with the reason, never a loop). */
    RESHARE
}

object SourceAnchor {
    /** What the phone found out about a file at the moment it is queued. */
    data class Facts(
        val persisted: Boolean,           // takePersistableUriPermission succeeded
        val mediaStoreMatch: Boolean,     // a MediaStore row with the same name AND size, and the same first 64 KiB
        val size: Long,                   // bytes, 0 = unknown
        val freeBytes: Long,              // free space of the cache directory
    )

    /** (a) persistable, else (b) MediaStore, else (c) cache copy when it fits, else (d) volatile: the original URI, sent without delay (never a refusal in advance). */
    fun choose(f: Facts): Anchor = when {
        f.persisted -> Anchor.PERSISTED
        f.mediaStoreMatch -> Anchor.MEDIASTORE
        CacheGuard.fits(f.size, f.freeBytes) -> Anchor.CACHE
        else -> Anchor.VOLATILE
    }

    /** Why a file could not be kept (used when a copy fails for lack of space, or by [ReshareTexts.notAnchored]): was chosen, in French: the size is unknown, over 2 GiB, or more than a quarter of the free space. */
    fun reshareReason(size: Long): String = when {
        size <= 0 -> "la taille du fichier est inconnue, il ne peut pas être gardé sur le téléphone"
        size > CacheGuard.MAX_BYTES -> "le fichier dépasse 2 Go, trop gros pour être gardé le temps de la file"
        else -> "il n'y a pas assez de place libre sur le téléphone pour en garder une copie le temps de la file"
    }
}

/** Space rule of the cache copy: at most a quarter of the free space and at most 2 GiB (never fill the phone). */
object CacheGuard {
    const val MAX_BYTES = 2L * 1024 * 1024 * 1024
    fun fits(size: Long, freeBytes: Long): Boolean = size > 0 && size <= MAX_BYTES && freeBytes > 0 && size <= freeBytes / 4

    /** Files of the cache directory that no queue item refers to any more (finished, cancelled, gone): to delete at start. */
    fun orphans(filesInCache: List<String>, referenced: Set<String>): List<String> = filesInCache.filter { it !in referenced }
}

/** Finding the same file again in MediaStore: the candidates come from the phone's query, the decision is here. */
object MediaMatch {
    const val HEAD_BYTES = 64 * 1024
    data class Candidate(val uri: String, val name: String, val size: Long)

    /** Same `_display_name` AND same `_size` (size must be known): only then is the head compared. */
    fun sameNameSize(c: Candidate, name: String, size: Long) = size > 0 && c.size == size && c.name == name

    fun candidates(all: List<Candidate>, name: String, size: Long) = all.filter { sameNameSize(it, name, size) }

    /** The first [HEAD_BYTES] (or the whole file when shorter) are identical; both heads must be non-empty. */
    fun sameHead(a: ByteArray, b: ByteArray): Boolean = a.isNotEmpty() && a.contentEquals(b)

    /** On resume the original is gone, so there is no head to compare: only ONE candidate with that name and size is accepted (two = ambiguous = null). */
    fun unique(all: List<Candidate>, name: String, size: Long): Candidate? = candidates(all, name, size).singleOrNull()

    /** The first candidate whose head [headOf] equals [mine]; null = not found (never a guess on name and size alone). */
    fun pick(all: List<Candidate>, name: String, size: Long, mine: ByteArray, headOf: (Candidate) -> ByteArray?): Candidate? =
        candidates(all, name, size).firstOrNull { c -> headOf(c)?.let { sameHead(mine, it) } == true }
}

/** French texts of R-22 (one place, tested). The phone says « CastBridge », never « sender ». */
object ReshareTexts {
    /** « partagé depuis Telegram » when the provider is recognised, else a neutral wording. */
    fun origin(uri: String): String {
        val auth = uri.substringAfter("://", "").substringBefore('/').lowercase()
        return when {
            "telegram" in auth -> "partagé depuis Telegram"
            "whatsapp" in auth -> "partagé depuis WhatsApp"
            else -> "partagé depuis une autre application"
        }
    }

    fun lost(uri: String) = "Le téléphone n'a plus accès à ce fichier (${origin(uri)}) : rouvrez-le avec « Ouvrir avec CastBridge »"
    fun notAnchored(uri: String, why: String) = "Ce fichier ne peut pas être gardé (${origin(uri)}) : $why. Rouvrez-le avec « Ouvrir avec CastBridge » quand vous voulez l'envoyer"
    const val PICK_BUTTON = "Choisir le fichier"
    const val PREPARING = "Préparation du fichier…"
    fun preparing(pct: Int) = "$PREPARING ${pct.coerceIn(0, 100)} %"
    /** Asked once, at the first need of MediaStore (never in a loop). */
    const val MEDIA_PERMISSION_WHY = "CastBridge cherche ce fichier dans la galerie du téléphone pour le retrouver même après un redémarrage."

    /** ONE grouped notification for N files: « 5 fichiers à repartager » (one file: its name). */
    fun groupTitle(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> "CastBridge : « ${names[0]} » est à repartager"
        else -> "CastBridge : ${names.size} fichiers à repartager"
    }
    fun groupText(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> "Le téléphone n'a plus accès à ce fichier : touchez pour le choisir à nouveau."
        else -> "Le téléphone n'a plus accès à ces fichiers : touchez pour les choisir à nouveau (" + names.take(3).joinToString(", ") { "« $it »" } +
            if (names.size > 3) ", …)" else ")"
    }
}
