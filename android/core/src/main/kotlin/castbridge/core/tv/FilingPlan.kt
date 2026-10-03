package castbridge.core.tv

import castbridge.core.library.agent.Labels
import castbridge.core.library.agent.Media
import castbridge.core.library.agent.NameParser
import castbridge.core.library.agent.SafeName

/**
 * « Le rangement ne crée pas d'arborescence de classement » (R-13, docs/agent-reports/filing-tree.md): the category tree a NEW copy from the phone gets,
 * on the TV (and its USB key) and on the phone (Téléchargements/CastBridge/…). Pure, built on [Filing.classify] (same parser, same clean names,
 * same « never overwrite » placement), with what a phone's files need:
 *  - an unidentified video goes to `Films/` (it used to land in « À trier »), audio to `Musique/`, an image to `Photos/`, a document to `Documents/`;
 *    `À trier` stays for what is truly unknown (it is the library's « Autres » folder: the index, the bin and « Ranger ma bibliothèque » know it);
 *  - series `Séries/<Titre>/Saison NN`, films with a year `Films/` (unchanged); music `Musique/<Artiste>/<Album>` when tags exist; photos `Photos/<AAAA-MM>` when the date is known;
 *  - the best available name: the media title when the phone only has a content id (`1000023456`, `msf:1234`) or a generic name (`video.mp4`), never a random id;
 *  - installers and packs read by their flat name stay flat ([Filing.keepsFlat]).
 */
object FilingPlan {
    /** What is known of a file: its [name] (display name), [mime], [size], and optional metadata ([title], [artist], [album], [date] "AAAA-MM-JJ"). */
    data class Input(val name: String, val mime: String? = null, val size: Long = 0, val title: String? = null, val artist: String? = null,
                     val album: String? = null, val date: String? = null, val durationMs: Long = 0)

    private val ID_RX = listOf(
        Regex("^\\d{5,}$"),                                         // MediaStore / Downloads ids
        Regex("^(?i:msf|document|video|image|audio|media|primary|raw|content)[:%_-]\\w*\\d+$"),   // "msf:1234", "video:42", "primary%3A12", "msf_1234"
        Regex("^[0-9a-fA-F]{16,}$"),
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"),
    )
    private val GENERIC = Regex("^(video|vidéo|vid|movie|film|fichier|file|download|media|média|image|img|photo|picture|audio|sound|son|document|doc|untitled|sans titre|nouveau|new)([ _-]?\\(?\\d{0,3}\\)?)?$",
        RegexOption.IGNORE_CASE)
    private val DATE = Regex("^\\d{4}-\\d{2}(-\\d{2})?$")

    private fun stemOf(base: String) = NameParser.splitExt(base).let { (s, e) -> if (e.isEmpty()) base else s }

    /** A name that is only a content id of the phone's storage (no title in it). */
    fun looksLikeId(name: String): Boolean { val st = stemOf(Filing.baseName(name)); return ID_RX.any { it.matches(st) } }

    /** A name that says nothing of the content (« video.mp4 », « Fichier (2).pdf »). */
    fun isGeneric(name: String): Boolean = GENERIC.matches(stemOf(Filing.baseName(name)).trim())

    private fun extFor(base: String, mime: String?): String =
        NameParser.splitExt(base).second.ifEmpty { Filing.MIME_EXT[mime?.substringBefore(';')?.trim()?.lowercase()] ?: "" }

    private fun mediaOf(ext: String, mime: String?): Media {
        if (ext.isNotEmpty()) NameParser.parse("x.$ext").media.takeIf { it != Media.OTHER }?.let { return it }
        val t = mime?.lowercase().orEmpty()
        return when {
            t.startsWith("video/") -> Media.VIDEO
            t.startsWith("audio/") -> Media.AUDIO
            t.startsWith("image/") -> Media.IMAGE
            t == "application/pdf" || t.startsWith("text/") -> Media.DOC
            else -> Media.OTHER
        }
    }

    private fun word(m: Media, l: Labels) = when (m) {
        Media.VIDEO -> l.video; Media.AUDIO -> l.audio; Media.IMAGE -> l.photo; Media.DOC -> "Document"
        else -> if (l.lang == "en") "File" else "Fichier"
    }

    /** [synthesized] = the name was made here (the phone only had a content id and no title): its category comes from the kind of file only. */
    private class Best(val name: String, val synthesized: Boolean)

    private fun best(raw: String?, mime: String?, title: String?, date: String?, lang: String): Best {
        val base = raw?.let { Filing.baseName(it) }.orEmpty()
        val ext = extFor(base, mime)
        val cleanTitle = title?.let { SafeName.clean(it) }?.takeIf { it.isNotBlank() && !looksLikeId(it) }
        val id = base.isEmpty() || looksLikeId(base)
        val generic = !id && isGeneric(base)
        fun withExt(stem: String) = SafeName.fileName(stem, ext)
        return when {
            (id || generic) && cleanTitle != null -> Best(withExt(cleanTitle), false)
            id -> {
                val l = Labels(lang); val w = word(mediaOf(ext, mime), l)
                val d = date?.takeIf { DATE.matches(it) }
                Best(withExt(if (d == null) w else if (lang == "en") "$w $d" else "$w du $d"), true)
            }
            else -> Best(base, false)
        }
    }

    /**
     * The name the phone SENDS for a file: its own name (with the extension of its type when it has none); the media title when it only has a content id or
     * a generic name; a content id without title stays a STABLE name (`msf_1234.mp4`: the same at every attempt, so a cut copy resumes, and two videos never
     * collide on one name), the TV files it under « Vidéo… » ([plan]); no name at all: « Vidéo du AAAA-MM-JJ ».
     */
    fun sendName(displayName: String?, mime: String?, title: String?, today: String): String {
        val base = displayName?.let { Filing.baseName(it) }.orEmpty()
        val ext = extFor(base, mime)
        val cleanTitle = title?.let { SafeName.clean(it) }?.takeIf { it.isNotBlank() && !looksLikeId(it) }
        val id = base.isEmpty() || looksLikeId(base)
        return when {
            (id || isGeneric(base)) && cleanTitle != null -> SafeName.fileName(cleanTitle, ext)
            base.isEmpty() -> SafeName.fileName("${word(mediaOf(ext, mime), Labels("fr"))} du $today", ext)
            id -> SafeName.fileName(stemOf(base).replace(':', '_').replace('%', '_'), ext)
            NameParser.splitExt(base).second.isEmpty() && ext.isNotEmpty() -> SafeName.fileName(base, ext)
            else -> base
        }
    }

    private fun monthFolder(l: Labels, date: String?): String =
        date?.takeIf { DATE.matches(it) }?.let { SafeName.folder(listOf(l.photos, it.substring(0, 7))) } ?: l.photos

    private fun byMedia(media: Media, name: String, l: Labels, date: String?, rule: String): Filing.Result = when (media) {
        Media.VIDEO -> Filing.Result(Filing.Category.FILMS, l.movies, name, rule)
        Media.AUDIO -> Filing.Result(Filing.Category.MUSIC, l.music, name, rule)
        Media.IMAGE -> Filing.Result(Filing.Category.PHOTOS, monthFolder(l, date), name, rule)
        Media.DOC -> Filing.Result(Filing.Category.DOCUMENTS, l.documents, name, rule)
        else -> Filing.Result(Filing.Category.TO_SORT, l.toSort, name, rule.ifEmpty { "default" })
    }

    /** Where a new copy goes: folder relative to the volume (or to Téléchargements/CastBridge on the phone) and clean name. Deterministic and idempotent. */
    fun plan(i: Input, lang: String = "fr", currentYear: Int = java.time.LocalDate.now().year): Filing.Result {
        val l = Labels(lang)
        val b = best(i.name, i.mime, i.title, i.date, lang)
        val media = mediaOf(NameParser.splitExt(b.name).second, i.mime)
        if (b.synthesized) return byMedia(media, b.name, l, i.date, "id")
        val r = Filing.classify(b.name, i.mime, i.size, Filing.Meta(i.durationMs, i.date), lang, currentYear)
        if (r.keepFlat) return r
        val date = i.date ?: NameParser.parse(b.name, "", i.durationMs, currentYear).date
        val placed = if (r.category == Filing.Category.TO_SORT) byMedia(media, r.name, l, date, r.rule) else r
        return when {
            placed.category == Filing.Category.PHOTOS && placed.folder == l.photos -> placed.copy(folder = monthFolder(l, date))
            placed.category == Filing.Category.MUSIC && placed.folder == l.music && !i.artist.isNullOrBlank() ->
                placed.copy(folder = SafeName.folder(listOfNotNull(l.music, i.artist, i.album?.takeIf { it.isNotBlank() })))
            else -> placed
        }.let { res -> if (res.folder.isNotEmpty() && SafeName.checkFolder(res.folder) != null) r else res }
    }

    /** The folder of the phone (under Téléchargements) where a file received from the TV goes: "CastBridge/Séries/Titre/Saison 01"; "CastBridge" if it stays flat. */
    fun phoneDir(name: String, size: Long = 0, lang: String = "fr", currentYear: Int = java.time.LocalDate.now().year): String {
        val r = plan(Input(name, size = size), lang, currentYear)
        return if (r.keepFlat || r.folder.isEmpty()) ROOT else "$ROOT/${r.folder}"
    }

    const val ROOT = "CastBridge"
}
