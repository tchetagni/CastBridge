package castbridge.core.library.agent

/** Words used in names and folders, in the language of the interface. */
class Labels(val lang: String) {
    private val fr = lang != "en"
    val series get() = if (fr) "Séries" else "Series"
    val movies get() = if (fr) "Films" else "Movies"
    val music get() = if (fr) "Musique" else "Music"
    val clips get() = "Clips"
    val family get() = if (fr) "Famille" else "Family"
    val courses get() = if (fr) "Cours" else "Courses"
    val documents get() = "Documents"
    val apps get() = if (fr) "Applications" else "Apps"
    val archives get() = "Archives"
    val photos get() = "Photos"
    val captures get() = if (fr) "Captures" else "Screenshots"
    val toSort get() = if (fr) "À trier" else "To sort"
    val season get() = if (fr) "Saison" else "Season"
    val video get() = if (fr) "Vidéo" else "Video"
    val audio get() = "Audio"
    val photo get() = "Photo"
    val screenshot get() = if (fr) "Capture d'écran" else "Screenshot"
    val screenRecording get() = if (fr) "Enregistrement d'écran" else "Screen recording"
    val trash get() = if (fr) "Corbeille CastBridge" else "CastBridge Trash"
}

/** Where and under which name the agent would put a file. [name] is the complete new file name; [folder] is relative ("" = root). */
data class Proposal(val name: String, val folder: String, val rule: String, val note: String = "")

/** Builds the readable, homogeneous names ("Prison Break – S01E04") and the folders. Pure function of [Parsed]. */
object Namer {
    const val SEP = " – "
    private val NEUTRAL = setOf(Kind.UNKNOWN, Kind.DOCUMENT, Kind.APP, Kind.ARCHIVE)

    /**
     * @param hideAudio the language tag not worth writing because it is the user's usual one
     * @param learned what the user corrected before (may be null)
     * @param mtimeDate "2024-03-15" from the modification time, used for camera files whose name carries no date
     */
    fun propose(p: Parsed, original: FileRef, labels: Labels, hideAudio: Audio = Audio.VF, learned: LearnedRules? = null, mtimeDate: String? = null): Proposal {
        val r = Build(p, original, labels, hideAudio, learned, mtimeDate).run()
        // Neutral kinds: never "rename" a file only to change its case.
        if (p.kind in NEUTRAL && r.name.equals(original.name, ignoreCase = true)) return r.copy(name = original.name)
        return r
    }

    private class Build(val p: Parsed, val original: FileRef, val l: Labels, hideAudio: Audio, val learned: LearnedRules?, val mtimeDate: String?) {
        val tag = p.audio?.takeIf { it != hideAudio }?.let { " [${it.tag}]" } ?: ""
        val title = learned?.aliasFor(p.titleKey) ?: p.title
        val forced = learned?.folderFor(p.titleKey)

        fun file(base: String, sub: String? = null, raw: Boolean = false): String {
            val b0 = if (raw) base else base.replace(" - ", SEP)
            val b = if (sub != null) "$b0.$sub" else b0
            return SafeName.fileName(b, p.ext).takeIf { SafeName.checkName(it) == null } ?: original.name
        }

        fun folder(vararg parts: String) = SafeName.folder(parts.map { it.replace(" - ", SEP) })

        fun run(): Proposal = when (p.kind) {
            Kind.SERIES -> series()
            Kind.MOVIE -> {
                val dir = title + (p.year?.let { " ($it)" } ?: "")
                // « Kill Bill (2003) - part1 »: the media-server convention for a film in several files (a hyphen, not our dash)
                val name = dir.replace(" - ", SEP) + (p.part?.let { " - part$it" } ?: "") + tag
                Proposal(file(name, p.subLang, raw = true), forced ?: folder(l.movies, dir), p.rule)
            }
            Kind.MUSIC, Kind.CLIP -> {
                val base = when {
                    p.artist != null -> p.artist + SEP + p.title
                    p.track != null -> Text.pad2(p.track) + SEP + p.title
                    else -> p.title
                }
                Proposal(file(base), forced ?: folder(if (p.kind == Kind.CLIP) l.clips else l.music), p.rule)
            }
            Kind.COURSE -> Proposal(file(p.title), forced ?: folder(l.courses, p.subject.orEmpty()), p.rule)
            Kind.PERSONAL, Kind.PHOTO -> personal()
            Kind.DOCUMENT -> Proposal(file(p.stem.ifBlank { p.title }), forced ?: folder(l.documents), p.rule)
            Kind.APP -> Proposal(file(p.stem.ifBlank { p.title }), folder(l.apps), p.rule)
            Kind.ARCHIVE -> Proposal(file(p.stem.ifBlank { p.title }), folder(l.archives), p.rule)
            Kind.UNKNOWN -> Proposal(file(p.stem.ifBlank { p.title }, p.subLang), folder(l.toSort), p.rule)
        }

        fun series(): Proposal {
            if (p.date != null && title.isNotBlank())   // daily show: the date is the episode, the year is the "season"
                return Proposal(file(title + SEP + p.date + (p.episodeTitle?.let { SEP + it } ?: "") + tag, p.subLang), forced ?: folder(l.series, title, "${l.season} ${p.date.take(4)}"), p.rule)
            if (title.isBlank())
                return Proposal(file(p.stem.ifBlank { original.name.substringBeforeLast('.') }, p.subLang), folder(l.toSort), p.rule, "série non identifiée")
            val name = buildString {
                append(title); p.year?.let { append(" ($it)") }
                append(SEP)
                when {
                    p.season != null && p.episode != null -> {
                        append("S${Text.pad2(p.season)}E${Text.pad2(p.episode)}")
                        p.episodeEnd?.let { append("-E${Text.pad2(it)}") }
                    }
                    p.season != null -> append("S${Text.pad2(p.season)}")
                    p.episode != null -> append("E${Text.pad2(p.episode)}")
                }
                p.episodeTitle?.let { append(SEP).append(it) }
                append(tag)
            }
            val dir = title + (p.year?.let { " ($it)" } ?: "")
            val f = forced ?: if (p.season != null) folder(l.series, dir, "${l.season} ${Text.pad2(p.season)}") else folder(l.series, dir)
            return Proposal(file(name, p.subLang), f, p.rule)
        }

        fun personal(): Proposal {
            val noun = when {
                p.origin == "Capture" -> l.screenshot
                p.origin == "Écran" -> l.screenRecording
                p.media == Media.AUDIO -> l.audio
                p.media == Media.IMAGE || p.kind == Kind.PHOTO -> l.photo
                else -> l.video
            }
            val origin = when (p.origin) { "WhatsApp", "Telegram" -> " ${p.origin}"; else -> "" }
            val date = p.date ?: mtimeDate
            val folder = when (p.origin) { "Capture", "Écran" -> folder(l.captures); else -> folder(l.family) }
            if (date == null) return Proposal(original.name, folder, p.rule, "sans date : nom conservé")
            if (p.rule == "personal.own") return Proposal(original.name, folder, p.rule)   // already in our format: never touched again
            val time = p.time?.replace(':', 'h')
            val seq = if (time == null) p.seq?.let { " ($it)" } ?: "" else ""
            val base = noun + origin + SEP + date + (time?.let { " $it" } ?: "") + seq
            return Proposal(file(base), folder, p.rule)
        }
    }
}
