package castbridge.core.library.agent

import castbridge.core.parental.ParentalConfig
import castbridge.core.parental.ParentalRules
import castbridge.core.parental.Rating
import castbridge.core.parental.RuleKind

/**
 * Plugs the assistant into the parental control (docs/PARENTAL.md, docs/LIBRARY-AGENT.md "Contrôle parental").
 *
 * A video is PROTECTED, and then the assistant never lists it, renames it, moves it, puts it in the bin, reads it (fingerprint)
 * or sends its name anywhere (AI layer), when the control is on and:
 *  - its rating is above the age band of the reference profile: the ACTIVE profile, else the youngest profile (a parent who
 *    unlocked the TV for a while does not make an adult film "organisable" for the children who come back);
 *  - "not classified" counts as [ParentalConfig.unrated] (adult by default): a new video stays protected until a parent looked at it;
 *  - or a rule is attached to this very file name (renaming would silently drop the classification, which follows the name).
 * A subtitle follows the protected video it belongs to. Photos, music, documents and apps are not classified: not protected.
 *
 * [childProfileActive]: a child profile is active right now (no parent session open): every change is refused.
 * Pure: no clock, no storage. The TV evaluates it (it owns the configuration) and tells the phone through `/api/library`
 * (`protected`, `childActive`), so the phone never needs the parental PIN for this.
 */
class ParentalContentGuard(
    private val cfg: ParentalConfig,
    /** True when a child profile is active now (not in a parent session). */
    private val childActive: Boolean,
    /** Label of the volume for the volume rules ("Clé USB"...), by volume id. */
    private val volumeLabel: (String) -> String? = { null },
    /** Every file of the library, so that a subtitle follows its video. */
    allFiles: Collection<FileRef> = emptyList(),
) : ContentGuard {
    override val childProfileActive: Boolean get() = cfg.enabled && childActive

    private val refAge: Int = if (!cfg.enabled) Int.MAX_VALUE
        else (cfg.active() ?: cfg.profiles.minByOrNull { it.age.max.age })?.age?.max?.age ?: Rating.ADULT.age
    private val fileRules: Set<String> = cfg.rules.filter { it.kind == RuleKind.FILE }.map { it.match.lowercase() }.toSet()

    private val protectedVideoStems: Set<String> by lazy {
        allFiles.filter { NameParser.mediaOf(it.ext) == Media.VIDEO && videoProtected(it) }.map { stem(it.name) }.toSet()
    }

    private fun stem(n: String) = n.substringBeforeLast('.', n).lowercase()

    private fun videoProtected(f: FileRef): Boolean =
        ParentalRules.ratingOf(cfg, f.name, volumeLabel(f.volumeId)).age > refAge || f.name.lowercase() in fileRules

    override fun isProtected(file: FileRef): Boolean {
        if (file.guarded) return true
        if (!cfg.enabled) return false
        return when (NameParser.mediaOf(file.ext)) {
            Media.VIDEO -> videoProtected(file)
            Media.SUBTITLE -> {
                // "Titre (2010).fr.srt" follows "Titre (2010).mkv"
                var s = stem(file.name)
                var hit = s in protectedVideoStems
                while (!hit && s.contains('.')) { s = s.substringBeforeLast('.'); hit = s in protectedVideoStems }
                hit
            }
            else -> false
        }
    }
}

/**
 * What the phone knows about the control from the TV's file list. A TV that says nothing (older version) is treated as
 * "everything protected": the assistant refuses to act rather than guess.
 */
object TvGuardNotes {
    const val UNSUPPORTED = "Cette TV ne dit pas si le contrôle parental est actif : mettez CastBridge-TV à jour. L'assistant ne touche à rien."
    fun protectedNote(n: Int) = "$n fichier${if (n > 1) "s" else ""} protégé${if (n > 1) "s" else ""} par le contrôle parental : l'assistant n'y touche pas et ne les affiche pas. " +
        "Classez-les dans Contrôle parental pour qu'il s'en occupe."
}

/** [castbridge.core.tv.ContentFlags] over the TV's [castbridge.core.parental.ParentalEngine]. The control counts only once a parental PIN exists (like everywhere else). */
class EngineContentFlags(private val engine: castbridge.core.parental.ParentalEngine) : castbridge.core.tv.ContentFlags {
    private fun cfg() = engine.config().let { c -> if (engine.hasPin()) c else c.copy(enabled = false) }

    override fun childActive(): Boolean = engine.activeProfile() != null

    override fun protectedNames(items: List<castbridge.core.tv.LibraryItem>): Set<String> {
        val cfg = cfg()
        if (!cfg.enabled) return emptySet()
        val labels = items.associate { it.volumeId to it.volumeLabel }
        val refs = items.map { FileRef(Origin.TV, it.name, it.size, it.mtime, volumeId = it.volumeId) }
        val g = ParentalContentGuard(cfg, childActive = false, volumeLabel = { labels[it] }, allFiles = refs)
        return refs.filter { g.isProtected(it) }.map { it.name }.toSet()
    }
}
