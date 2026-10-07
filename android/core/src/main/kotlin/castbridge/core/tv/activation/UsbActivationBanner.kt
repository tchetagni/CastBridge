package castbridge.core.tv.activation

import castbridge.core.owner.TrialPolicy
import castbridge.core.tv.UsbPhase

/** How a line of the activation screen is coloured: green = done or ready, grey = information, amber = a reason to act. */
enum class LineTone { GOOD, INFO, WARN }

/**
 * The USB banner of the activation screen (F5, docs/coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md): the TV looks for the `activation` file when a key is plugged in
 * (broadcast « media mounted »), when the activation screen opens and on the « Chercher » button, and says EXACTLY what it found:
 * « Clé USB : activation trouvée pour cette TV › Activer » (the banner is a focusable button: OK installs the key) or the precise reason (another TV's key, expired, Download hidden by
 * Android and the folder to use instead, …). No silent search every 15 s any more.
 *
 * A key that Android is still CHECKING (fsck after an unclean removal) cannot be read yet: [State.WAITING_CHECK] says so and why, the search resumes at the mount (docs/STORAGE.md « Clé USB mal
 * éjectée »). A key Android could not mount explains why no key was found ([State.DAMAGED], the guide of `UsbVolumeState`).
 *
 * Pure: it is built from the FACTS of the lookup ([LookupFacts]: paths, counts, file states), never from a key, so no key text can reach a banner, a log or a status line.
 * The verdict of a file is the one of [ActivationLookup] (the same verifier as a pasted key); the order of precedence is the one of [ActivationLookupReport].
 */
object UsbActivationBanner {
    enum class State {
        /** Nothing searched yet (no banner). */
        IDLE,
        /** The terms of use are not accepted on this TV: nothing is read before. */
        TERMS_PENDING,
        SEARCHING,
        /** No key detected. */
        NO_KEY,
        /** A file that VERIFIES for this TV: the button installs it. */
        FOUND,
        WRONG_TV, EXPIRED, NOT_VALID, TOO_BIG, EMPTY, UNREADABLE,
        /** Android 11+ hides Download from the app and nothing was readable: the folder of the app is the way. */
        HIDDEN,
        /** A key is there, no activation file in the places the TV can read. */
        NOT_FOUND,
        /** Android is checking the key (fsck): nothing can be read until it is mounted; the search resumes then. */
        WAITING_CHECK,
        /** Android could not mount the key (unreadable, or no file system it knows): the guide says what to do. */
        DAMAGED,
    }

    /** What the TV knows of the key ITSELF, not of its files (from `UsbVolumeState`): the phase and its French line. */
    data class KeyNote(val phase: UsbPhase, val line: String)

    /**
     * [text] = the banner (« Clé USB : … »); [canActivate] = it is the « Activer » button; [keyPresent] = a USB key is plugged in; [presence] = the line of the key's way
     * (« Clé détectée : A379-E209 » / « Aucune clé USB détectée … »).
     */
    data class View(val state: State, val text: String, val tone: LineTone, val canActivate: Boolean, val keyPresent: Boolean, val presence: String)

    private const val DROP = ActivationLookup.OWN_DIR_TEXT + "/"
    private const val TERMS_LINE = "Clé USB : cochez d'abord les conditions d'usage, la clé sera lue ensuite"
    private const val NO_KEY_LINE = "Aucune clé USB détectée : branchez-la, la TV la lit aussitôt"
    private val READ = setOf(Probe.ACCEPTED, Probe.NOT_VALID, Probe.WRONG_DEVICE, Probe.EXPIRED, Probe.EMPTY, Probe.TOO_BIG)

    fun idle() = View(State.IDLE, "", LineTone.INFO, false, false, NO_KEY_LINE)
    fun termsPending() = View(State.TERMS_PENDING, TERMS_LINE, LineTone.INFO, false, false, "Cochez d'abord les conditions d'usage : la clé USB sera lue ensuite")
    fun searching(keyPresent: Boolean = false, presence: String = NO_KEY_LINE) = View(State.SEARCHING, "Clé USB : recherche…", LineTone.INFO, false, keyPresent, presence)

    /** Android checks the key: [keyLine] (« Clé « Lexar » : vérification par Android… patientez ») is the reason, and the search says it resumes by itself. */
    fun waitingForCheck(keyLine: String) = View(State.WAITING_CHECK, "$keyLine La recherche de l'activation reprend dès qu'elle est prête.", LineTone.INFO, false, true, keyLine)

    /** The line of the « clé USB » way: which key is detected, or that none is. [facts] null = nothing searched yet. */
    fun presence(facts: LookupFacts?): String = if (facts == null || facts.volumes.isEmpty()) NO_KEY_LINE else ActivationLookupReport.lines(facts).first()

    /** Where nothing was found: while Android checks a key this is not an answer yet. */
    private val NOTHING_FOUND = setOf(State.NO_KEY, State.NOT_FOUND, State.HIDDEN, State.UNREADABLE)

    /**
     * The banner for what the last lookup saw. [key] = what the TV knows of the key itself: a key under check turns « nothing found » (or a refusal to read) into [State.WAITING_CHECK]; a key Android
     * could not mount explains « no key » ([State.DAMAGED]). A verdict on a file that WAS read, and a healthy key without the file, are never overridden.
     */
    fun from(facts: LookupFacts, key: KeyNote? = null): View {
        val states = facts.probes.map { it.probe }.toSet()
        val keyPresent = facts.volumes.isNotEmpty()
        val state = when {
            Probe.ACCEPTED in states -> State.FOUND
            Probe.WRONG_DEVICE in states -> State.WRONG_TV
            Probe.EXPIRED in states -> State.EXPIRED
            Probe.NOT_VALID in states -> State.NOT_VALID
            !keyPresent -> State.NO_KEY
            facts.access == StorageAccess.MISSING && states.none { it in READ } -> State.HIDDEN
            Probe.UNREADABLE in states -> State.UNREADABLE
            Probe.TOO_BIG in states -> State.TOO_BIG
            Probe.EMPTY in states -> State.EMPTY
            else -> State.NOT_FOUND
        }
        if (key != null && state in NOTHING_FOUND && key.phase == UsbPhase.CHECKING) return waitingForCheck(key.line)
        if (key != null && state == State.NO_KEY && (key.phase == UsbPhase.DAMAGED || key.phase == UsbPhase.NO_FILESYSTEM))
            return View(State.DAMAGED, key.line, LineTone.WARN, false, true, key.line)
        return View(state, textOf(state), toneOf(state), state == State.FOUND, keyPresent, presence(facts))
    }

    private fun textOf(s: State): String = when (s) {
        State.IDLE -> ""
        State.TERMS_PENDING -> TERMS_LINE
        State.SEARCHING -> searching().text
        State.NO_KEY -> "Clé USB : aucune clé détectée : branchez-la, la TV la lit aussitôt"
        State.FOUND -> "Clé USB : activation trouvée pour cette TV › Activer"
        State.WRONG_TV -> "Clé USB : clé d'une autre TV : refaites la demande avec le code d'appareil de cette TV"
        State.EXPIRED -> "Clé USB : clé périmée (valable 48 h) : demandez-en une nouvelle"
        State.NOT_VALID -> "Clé USB : le fichier « activation » n'est pas une clé valable (texte incomplet ou abîmé)"
        State.TOO_BIG -> "Clé USB : le fichier « activation » est trop gros (16 ko au plus) : ce n'est pas une clé"
        State.EMPTY -> "Clé USB : le fichier « activation » est vide"
        State.UNREADABLE -> "Clé USB : fichier « activation » présent mais Android refuse de le lire : déposez-le dans $DROP"
        State.HIDDEN -> "Clé USB : dossier Download invisible : déposez le fichier dans $DROP"
        State.NOT_FOUND -> "Clé USB : aucun fichier « activation » trouvé : déposez-le dans $DROP"
        State.WAITING_CHECK -> "Clé USB : vérification par Android… patientez, la recherche reprend dès que la clé est prête"
        State.DAMAGED -> "Clé USB : illisible, Android n'a pas pu la réparer (réglages de stockage de la TV, ou un ordinateur)"
    }

    private fun toneOf(s: State): LineTone = when (s) {
        State.FOUND -> LineTone.GOOD
        State.IDLE, State.TERMS_PENDING, State.SEARCHING, State.NO_KEY, State.WAITING_CHECK -> LineTone.INFO
        else -> LineTone.WARN
    }

    /** States where a file named « activation » was really seen: the only ones worth a line on the home screen (a key plugged in to watch videos must not nag it). */
    private val SEEN = setOf(State.FOUND, State.WRONG_TV, State.EXPIRED, State.NOT_VALID, State.TOO_BIG, State.EMPTY, State.UNREADABLE)

    /**
     * The one status line of the HOME screen when the activation screen is not open (trial or grace TV: the home is reachable): null unless a file named « activation » was really seen.
     * The home has no « Activer » button: it points at the tile that opens the activation screen.
     */
    fun homeLine(v: View): String? = when {
        v.state == State.FOUND -> "${v.text.removeSuffix(" › Activer")} › ouvrez « ${TrialPolicy.UPGRADE_LABEL} » (Accueil)"
        v.state in SEEN -> v.text
        else -> null
    }

    /** Delay before the next search at plug-in, or null: the volume needs a moment to settle, so an empty answer is retried twice (1.5 s, then 2.5 s later: the banner is there within 5 s), never in a loop. */
    fun nextRetryDelayMs(s: State, retriesDone: Int): Long? =
        if (s != State.NO_KEY && s != State.NOT_FOUND && s != State.HIDDEN) null else when (retriesDone) { 0 -> 1_500L; 1 -> 2_500L; else -> null }
}
