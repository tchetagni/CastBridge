package castbridge.core.tv

import castbridge.core.tv.activation.LineTone
import castbridge.core.ux.SignalLevel
import castbridge.core.ux.UsbNote

/**
 * « Pouvoir lire la clé USB même si elle a été mal éjectée » (2026-10-07, docs/STORAGE.md § « Clé USB mal éjectée : ce que la TV peut et ne peut pas faire »).
 *
 * Ce qu'Android dit d'un volume : `Environment.MEDIA_*`, `StorageVolume.getState()` et les diffusions `ACTION_MEDIA_*`. Au branchement `vold` lance `fsck` (état `checking`), puis monte
 * (`mounted`) ou refuse (`unmountable`). Une application ne peut ni réparer ni démonter un volume (`MOUNT_UNMOUNT_FILESYSTEMS` est une permission système) : elle peut dire ce qui se passe,
 * attendre la fin de la vérification, guider vers les réglages de stockage de la TV, et réduire la casse en vidant ses écritures.
 */
enum class MediaState(val wire: String) {
    UNKNOWN("unknown"), REMOVED("removed"), UNMOUNTED("unmounted"), CHECKING("checking"), NOFS("nofs"),
    MOUNTED("mounted"), MOUNTED_READ_ONLY("mounted_ro"), SHARED("shared"), BAD_REMOVAL("bad_removal"), UNMOUNTABLE("unmountable"), EJECTING("ejecting");

    /** Monté, en lecture-écriture ou en lecture seule : les fichiers se lisent. */
    val mounted: Boolean get() = this == MOUNTED || this == MOUNTED_READ_ONLY

    companion object {
        /** Les mots d'Android, exactement (`Environment.MEDIA_*`) ; tout le reste est inconnu : on ne devine pas. */
        fun parse(s: String?): MediaState = entries.firstOrNull { it.wire == s } ?: UNKNOWN

        /** L'état qu'annonce une diffusion `ACTION_MEDIA_*` (null = pas une diffusion de volume) ; [readOnly] = l'extra `read-only` de la diffusion. */
        fun ofAction(action: String?, readOnly: Boolean = false): MediaState? = when (action) {
            "android.intent.action.MEDIA_CHECKING" -> CHECKING
            "android.intent.action.MEDIA_MOUNTED" -> if (readOnly) MOUNTED_READ_ONLY else MOUNTED
            "android.intent.action.MEDIA_UNMOUNTABLE" -> UNMOUNTABLE
            "android.intent.action.MEDIA_BAD_REMOVAL" -> BAD_REMOVAL
            "android.intent.action.MEDIA_EJECT" -> EJECTING
            "android.intent.action.MEDIA_UNMOUNTED" -> UNMOUNTED
            "android.intent.action.MEDIA_REMOVED" -> REMOVED
            "android.intent.action.MEDIA_NOFS" -> NOFS
            "android.intent.action.MEDIA_SHARED" -> SHARED
            else -> null
        }
    }
}

/** Où en est la clé, du point de vue de la personne devant la TV. */
enum class UsbPhase {
    /** Rien à dire : pas de clé, retrait propre, ou avis périmé. */
    ABSENT,
    /** Android vérifie le système de fichiers de la clé (`fsck`) avant de la monter. */
    CHECKING,
    /** La vérification vient de finir par un montage : « prête », dit quelques secondes. */
    JUST_READY,
    READY,
    /** Une copie écrit sur la clé : ne pas la retirer. */
    WRITING,
    /** « Préparer le retrait » est fait : les copies sont en pause, la clé peut être retirée. */
    PULL_READY,
    /** Montée en lecture seule : on lit, on n'y copie rien. */
    READ_ONLY,
    /** Android n'a pas pu la monter ni la réparer (`unmountable`). */
    DAMAGED,
    /** Aucun système de fichiers reconnu (`nofs`). */
    NO_FILESYSTEM,
    EJECTING,
    /** Démontée proprement : on peut la retirer. */
    EJECTED,
    /** Retirée sans éjection (`bad_removal`). */
    REMOVED_BADLY,
    /** Retirée après « Préparer le retrait » : Android parle de retrait brutal (seule une éjection est propre pour lui), mais tout était écrit : rien n'est perdu. */
    REMOVED_PREPARED,
    /** Partagée avec un ordinateur (mode stockage USB) : illisible ici. */
    SHARED,
}

/** Ce que la TV propose de faire : attendre, ou ouvrir les réglages de stockage (jamais plus : une application ne répare rien). */
enum class UsbAction { NONE, WAIT, OPEN_STORAGE_SETTINGS }

/**
 * Les faits d'un volume : l'état d'Android et ce que la TV en a retenu. [sinceMs] = depuis quand [state] dure (même horloge que `now`, null = inconnu : relevé au démarrage) ;
 * [afterCheck] = le montage vient d'une vérification ; [badRemovalSeen] = on SAIT que la clé a été retirée sans éjection (diffusion vue, ou retenue d'un démarrage à l'autre) ;
 * [interruptedCopy] = le nom (jamais un chemin) du fichier dont la copie a été coupée par le retrait ; [writing] = copies qui écrivent sur la clé maintenant ;
 * [pullReady] = « Préparer le retrait » est terminé ; [removalPrepared] = la clé a été retirée alors que « Préparer le retrait » était terminé (sert à `bad_removal`).
 */
data class UsbFacts(
    val label: String, val state: MediaState, val sinceMs: Long?, val afterCheck: Boolean = false, val badRemovalSeen: Boolean = false,
    val interruptedCopy: String? = null, val writing: Int = 0, val pullReady: Boolean = false, val removalPrepared: Boolean = false,
)

/**
 * La réponse : [line] (français, vide si rien à dire), [tone], [action] et son libellé de bouton, [readable] = la TV peut lire la clé maintenant, [notable] = mérite une ligne sur l'accueil.
 * [short] = la même chose en deux lignes au plus, pour la puce de l'accueil (le guide entier reste sur la bibliothèque, l'explorateur et l'écran de retrait) ; égale à [line] quand elle est courte.
 */
data class UsbVerdict(
    val phase: UsbPhase, val line: String, val tone: LineTone, val action: UsbAction, val actionLabel: String?, val readable: Boolean, val notable: Boolean,
    val short: String = line,
)

object UsbVolumeState {
    /** Une vérification plus longue que cela est dite avec sa durée ; au-delà de [LONG_CHECK_MS] on dit quoi faire si elle ne finit pas. */
    const val SLOW_CHECK_MS = 20_000L
    const val LONG_CHECK_MS = 120_000L
    /** « Clé prête » est dit ce temps-là après la fin de la vérification, puis la ligne se tait. */
    const val READY_NOTICE_MS = 10_000L
    /** L'avis « retirée sans éjection » reste 10 minutes ; « éjectée, vous pouvez la retirer » une minute. */
    const val BAD_REMOVAL_KEEP_MS = 10 * 60_000L
    const val EJECTED_KEEP_MS = 60_000L

    const val SETTINGS_LABEL = "Ouvrir les réglages de stockage"
    const val PREPARE_LABEL = "Préparer le retrait de la clé USB"
    /**
     * La ligne de la tuile « Clé USB » et de MENU qui ouvre le guide ENTIER (la puce de l'accueil n'en garde que deux lignes). Elle commence par « Clé USB » : c'est là que les avis
     * (« le guide : MENU > Clé USB ») envoient la personne.
     */
    const val GUIDE_LABEL = "Clé USB : que faire de la clé ? (le guide)"
    const val WRITING_LINE = "Ne retirez pas la clé : copie en cours"
    const val CAN_REMOVE = "Vous pouvez retirer la clé"

    private const val REPAIR = "Sur un ordinateur : Mac › Utilitaire de disque › S.O.S ; Windows › clic droit › Propriétés › Outils › Vérifier ; " +
        "ou Réglages de la TV › Stockage › Réparer/Formater (le formatage efface tout)"

    /** « Clé « Lexar » », ou « Clé » quand Android ne donne aucun nom. */
    fun subject(label: String): String = label.trim().let { if (it.isEmpty()) "Clé" else "Clé « $it »" }

    private fun duration(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return if (s < 60) "$s s" else "${s / 60} min" }

    private fun v(phase: UsbPhase, line: String, tone: LineTone, readable: Boolean, notable: Boolean, action: UsbAction = UsbAction.NONE, label: String? = null, short: String? = null) =
        UsbVerdict(phase, line, tone, action, label, readable, notable, short ?: line)

    private fun settings(phase: UsbPhase, line: String, short: String) =
        v(phase, line, LineTone.WARN, readable = false, notable = true, action = UsbAction.OPEN_STORAGE_SETTINGS, label = SETTINGS_LABEL, short = short)

    private val ABSENT = v(UsbPhase.ABSENT, "", LineTone.INFO, readable = false, notable = false)

    fun judge(f: UsbFacts, nowMs: Long): UsbVerdict {
        val s = subject(f.label)
        val elapsed = f.sinceMs?.let { (nowMs - it).coerceAtLeast(0) } ?: 0L
        return when (f.state) {
            MediaState.CHECKING -> {
                val cause = if (f.badRemovalSeen) " (elle a été retirée sans éjection)" else if (elapsed >= SLOW_CHECK_MS) " (sans doute retirée sans éjection)" else ""
                val line = when {
                    elapsed >= LONG_CHECK_MS -> "$s : vérification par Android depuis ${duration(elapsed)}$cause : c'est long. Laissez-la faire encore quelques minutes ; sinon retirez-la et vérifiez-la sur un ordinateur"
                    elapsed >= SLOW_CHECK_MS -> "$s : vérification par Android en cours depuis ${duration(elapsed)}$cause… patientez, ne la retirez pas"
                    else -> "$s : vérification par Android$cause… patientez"
                }
                v(UsbPhase.CHECKING, line, LineTone.INFO, readable = false, notable = true, action = UsbAction.WAIT,
                    short = if (elapsed >= LONG_CHECK_MS) "$s : vérification par Android depuis ${duration(elapsed)} : c'est long, laissez-la faire encore un peu" else null)
            }
            MediaState.MOUNTED, MediaState.MOUNTED_READ_ONLY -> when {
                f.pullReady -> v(UsbPhase.PULL_READY, "$CAN_REMOVE${f.label.trim().let { if (it.isEmpty()) "" else " « $it »" }} (les copies sont en pause)", LineTone.GOOD, readable = true, notable = true)
                f.writing > 0 -> v(UsbPhase.WRITING, if (f.writing == 1) WRITING_LINE else "Ne retirez pas la clé : ${f.writing} copies en cours", LineTone.WARN, readable = true, notable = true)
                f.state == MediaState.MOUNTED_READ_ONLY ->
                    v(UsbPhase.READ_ONLY, "$s : lecture seule. Les vidéos se lisent, mais rien ne peut y être copié. Retirez le verrou de la clé, ou réparez-la sur un ordinateur", LineTone.WARN, readable = true, notable = false)
                f.afterCheck && f.sinceMs != null && elapsed < READY_NOTICE_MS -> v(UsbPhase.JUST_READY, "$s prête", LineTone.GOOD, readable = true, notable = true)
                else -> v(UsbPhase.READY, "$s prête", LineTone.GOOD, readable = true, notable = false)
            }
            MediaState.UNMOUNTABLE -> settings(UsbPhase.DAMAGED, "$s illisible : Android n'a pas pu la réparer. $REPAIR", "$s illisible : Android n'a pas pu la réparer (le guide : MENU › Clé USB)")
            MediaState.NOFS -> settings(UsbPhase.NO_FILESYSTEM,
                "$s : format non reconnu par la TV (clé vierge, ou format qu'Android ne lit pas). Sur un ordinateur, formatez-la en exFAT (le formatage efface tout), ou Réglages de la TV › Stockage › Formater",
                "$s : format non reconnu par la TV (le guide : MENU › Clé USB)")
            MediaState.BAD_REMOVAL ->
                if (f.sinceMs != null && elapsed >= (if (f.removalPrepared) EJECTED_KEEP_MS else BAD_REMOVAL_KEEP_MS)) ABSENT
                else if (f.removalPrepared) v(UsbPhase.REMOVED_PREPARED, "$s retirée : elle était préparée, rien n'est perdu. Au prochain branchement Android la vérifiera : c'est normal sans éjection", LineTone.GOOD, readable = false, notable = true)
                else v(UsbPhase.REMOVED_BADLY,
                    f.interruptedCopy?.let { "Clé retirée pendant une copie : le fichier « $it » est incomplet, il sera repris" }
                        ?: "$s retirée sans éjection. La prochaine fois : MENU › $PREPARE_LABEL", LineTone.WARN, readable = false, notable = true)
            MediaState.EJECTING -> v(UsbPhase.EJECTING, "$s : éjection en cours, ne la retirez pas encore", LineTone.INFO, readable = false, notable = true)
            MediaState.UNMOUNTED ->
                if (f.sinceMs != null && elapsed >= EJECTED_KEEP_MS) ABSENT
                else v(UsbPhase.EJECTED, "$s éjectée : vous pouvez la retirer", LineTone.GOOD, readable = false, notable = true)
            MediaState.SHARED -> v(UsbPhase.SHARED, "$s : partagée avec un ordinateur (mode stockage USB), la TV ne peut pas la lire", LineTone.INFO, readable = false, notable = false)
            MediaState.REMOVED, MediaState.UNKNOWN -> ABSENT
        }
    }

    /** Ce qui presse le plus, d'abord : une écriture en cours, un problème, une vérification, puis les avis de passage. */
    private val URGENCY = listOf(UsbPhase.WRITING, UsbPhase.DAMAGED, UsbPhase.NO_FILESYSTEM, UsbPhase.CHECKING, UsbPhase.REMOVED_BADLY,
        UsbPhase.PULL_READY, UsbPhase.EJECTING, UsbPhase.JUST_READY, UsbPhase.EJECTED, UsbPhase.REMOVED_PREPARED)
    private val URGENT = setOf(UsbPhase.WRITING, UsbPhase.DAMAGED, UsbPhase.NO_FILESYSTEM, UsbPhase.CHECKING, UsbPhase.REMOVED_BADLY)

    /** La clé dont l'accueil parle (la plus urgente parmi celles qui méritent une ligne), ou null. */
    fun pick(verdicts: List<UsbVerdict>): UsbVerdict? =
        verdicts.filter { it.notable }.minByOrNull { URGENCY.indexOf(it.phase).let { i -> if (i < 0) Int.MAX_VALUE else i } }

    /**
     * La ligne d'état de l'accueil. Une copie qui écrit sur la clé met « Ne retirez pas la clé : copie en cours » DEVANT la ligne de réception ; une réception vers la mémoire interne
     * ne dit rien de plus ; sans réception, un problème de clé passe avant la ligne d'activation, un avis de passage après.
     */
    fun chipLine(receiving: String?, verdicts: List<UsbVerdict>, activationLine: String?): String? {
        verdicts.firstOrNull { it.phase == UsbPhase.WRITING }?.let { w -> return if (receiving != null) "${w.short}   ·   $receiving" else w.short }
        if (receiving != null) return receiving
        pick(verdicts.filter { it.phase in URGENT })?.let { return it.short }
        return activationLine ?: pick(verdicts)?.short
    }

    /** La pastille de l'accueil : la clé la plus urgente parmi celles qui ont quelque chose à dire à la pastille (une copie vers une clé ne cache pas la vérification d'une autre). */
    fun signal(verdicts: List<UsbVerdict>): UsbNote? =
        verdicts.filter { it.notable }.sortedBy { URGENCY.indexOf(it.phase).let { i -> if (i < 0) Int.MAX_VALUE else i } }.firstNotNullOfOrNull { note(it) }

    /** Ce que la pastille de l'accueil en retient : seulement ce qui demande de l'attention (orange) ou dit « prête à retirer » (vert) ; une copie en cours n'allume rien (la ligne le dit). */
    fun note(v: UsbVerdict): UsbNote? = when (v.phase) {
        UsbPhase.CHECKING -> UsbNote(SignalLevel.ORANGE, "Clé USB en vérification par Android", "Patientez : la clé sera prête dans un instant")
        UsbPhase.DAMAGED, UsbPhase.NO_FILESYSTEM -> UsbNote(SignalLevel.ORANGE, "Clé USB illisible", "Réparez-la sur un ordinateur, ou ouvrez les réglages de stockage : MENU > Clé USB")
        UsbPhase.REMOVED_BADLY -> UsbNote(SignalLevel.ORANGE, "Clé retirée sans éjection", "La prochaine fois : MENU > $PREPARE_LABEL")
        UsbPhase.PULL_READY -> UsbNote(SignalLevel.GREEN, "Clé prête à être retirée", null)
        else -> null
    }
}
