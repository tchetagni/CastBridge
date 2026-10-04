package castbridge.core.trust

import castbridge.core.tv.Pin

/**
 * La saisie du code PIN de la TV directement dans la boite « Ouvrir avec CastBridge » (pure : l'écran ne fait que la brancher, voir `PinEntryStateTest`).
 * Le code n'apparaît dans aucun `toString` ni message : seule sa longueur. Les essais comptent comme à l'écran de saisie existant : la TV verrouille le
 * téléphone une minute après [PinEntry.MAX_ATTEMPTS] codes faux (`PinGuard`), et ce verrou fait foi ; ici on s'arrête au même nombre, jamais au-delà.
 */
sealed class PinEntryState {
    /** Rien de saisi. [failures]: codes faux déjà refusés par la TV. */
    class Idle(val failures: Int = 0) : PinEntryState() { override fun toString() = "Idle(failures=$failures)" }
    /** Saisie en cours : [digits] n'est jamais affiché. */
    class Entering(val digits: String, val failures: Int = 0) : PinEntryState() { override fun toString() = "Entering(length=${digits.length}, failures=$failures)" }
    /** Une seule vérification est partie vers la TV. */
    class Checking(val failures: Int = 0) : PinEntryState() { override fun toString() = "Checking(failures=$failures)" }
    /** Code refusé : champ vidé, [attemptsLeft] essais avant le verrou de la TV. */
    class Wrong(val attemptsLeft: Int) : PinEntryState() { override fun toString() = "Wrong(attemptsLeft=$attemptsLeft)" }
    /** La TV (ou le compte d'essais) bloque les codes pour [seconds] s : aucune saisie, aucun envoi. */
    class Locked(val seconds: Long) : PinEntryState() { override fun toString() = "Locked(seconds=$seconds)" }
    /** La TV n'a pas répondu : ce n'est pas un code faux, aucun essai consommé. */
    class Unreachable(val failures: Int = 0) : PinEntryState() { override fun toString() = "Unreachable(failures=$failures)" }
    object Accepted : PinEntryState() { override fun toString() = "Accepted" }
}

/** Ce que la TV a répondu à la vérification unique. */
sealed class PinVerdict {
    object Ok : PinVerdict()
    object BadPin : PinVerdict()
    class Locked(val seconds: Long) : PinVerdict() { override fun toString() = "Locked(seconds=$seconds)" }
    object Unreachable : PinVerdict()
}

object PinEntry {
    /** Comme `PinGuard` de la TV : cinq codes faux, puis une minute de verrou. */
    const val MAX_ATTEMPTS = 5
    const val LOCK_SECONDS = 60L

    private fun failuresOf(s: PinEntryState): Int = when (s) {
        is PinEntryState.Idle -> s.failures
        is PinEntryState.Entering -> s.failures
        is PinEntryState.Checking -> s.failures
        is PinEntryState.Wrong -> MAX_ATTEMPTS - s.attemptsLeft
        is PinEntryState.Unreachable -> s.failures
        is PinEntryState.Locked, PinEntryState.Accepted -> MAX_ATTEMPTS
    }

    /** État de départ : verrouillé si la TV l'est déjà ([lockedSec] vient de `PinStore.lockLeft`). */
    fun start(lockedSec: Long?): PinEntryState = if (lockedSec != null && lockedSec > 0) PinEntryState.Locked(lockedSec) else PinEntryState.Idle()

    /** Texte tapé : chiffres seulement, [Pin.LENGTH] au plus. Ignoré pendant la vérification, après l'acceptation et pendant un verrou. */
    fun input(s: PinEntryState, raw: String): PinEntryState {
        if (s is PinEntryState.Checking || s is PinEntryState.Accepted || s is PinEntryState.Locked) return s
        val d = raw.filter { it in '0'..'9' }.take(Pin.LENGTH)
        val f = failuresOf(s)
        return if (d.isEmpty()) PinEntryState.Idle(f) else PinEntryState.Entering(d, f)
    }

    /** Dernier chiffre tapé : la vérification part d'elle-même. */
    fun readyToCheck(s: PinEntryState) = s is PinEntryState.Entering && Pin.isValidFormat(s.digits)

    /** « Valider » (ou le dernier chiffre) : une seule vérification, seulement avec un code bien formé et hors verrou. */
    fun submit(s: PinEntryState): PinEntryState = if (readyToCheck(s)) PinEntryState.Checking(failuresOf(s)) else s

    /** La réponse de la TV à la vérification en cours (sans effet hors de [PinEntryState.Checking]). */
    fun result(s: PinEntryState, v: PinVerdict): PinEntryState {
        if (s !is PinEntryState.Checking) return s
        return when (v) {
            PinVerdict.Ok -> PinEntryState.Accepted
            PinVerdict.Unreachable -> PinEntryState.Unreachable(s.failures)
            is PinVerdict.Locked -> PinEntryState.Locked(v.seconds.coerceAtLeast(1))
            PinVerdict.BadPin -> {
                val f = s.failures + 1
                if (f >= MAX_ATTEMPTS) PinEntryState.Locked(LOCK_SECONDS) else PinEntryState.Wrong(MAX_ATTEMPTS - f)
            }
        }
    }

    /** Verrou observé ailleurs ([lockedSec] : secondes restantes, null = levé). Un verrou levé remet le compte d'essais à zéro (la TV a remis le sien). */
    fun tick(s: PinEntryState, lockedSec: Long?): PinEntryState = when {
        s is PinEntryState.Checking || s is PinEntryState.Accepted -> s
        lockedSec != null && lockedSec > 0 -> if (s is PinEntryState.Locked && s.seconds == lockedSec) s else PinEntryState.Locked(lockedSec)
        s is PinEntryState.Locked -> PinEntryState.Idle()
        else -> s
    }

    /** Rotation, passage en arrière-plan : les chiffres tapés disparaissent ; le compte d'essais et le verrou restent. */
    fun clear(s: PinEntryState): PinEntryState = when (s) {
        is PinEntryState.Entering -> PinEntryState.Idle(s.failures)
        is PinEntryState.Checking -> PinEntryState.Idle(s.failures)
        is PinEntryState.Wrong, is PinEntryState.Locked, is PinEntryState.Unreachable, is PinEntryState.Idle, PinEntryState.Accepted -> s
    }

    fun digitsOf(s: PinEntryState) = (s as? PinEntryState.Entering)?.digits.orEmpty()

    /** La ligne que la boite affiche sous le champ (jamais le code). */
    fun message(s: PinEntryState): String? = when (s) {
        is PinEntryState.Wrong -> "Code incorrect. " + (if (s.attemptsLeft == 1) "Il vous reste 1 essai" else "Il vous reste ${s.attemptsLeft} essais") + " : saisissez le code affiché sur la TV."
        is PinEntryState.Locked -> CredentialDecision.locked(s.seconds)
        is PinEntryState.Unreachable -> "La TV ne répond pas (même Wi-Fi ? CastBridge-TV ouvert ?) : réessayez."
        is PinEntryState.Checking -> "Vérification du code avec la TV…"
        else -> null
    }

    fun showsField(a: SendAction) = a == SendAction.ENTER_PIN

    const val FIELD_LABEL = "Code PIN de la TV (${Pin.LENGTH} chiffres)"
    const val SUBMIT_LABEL = "Valider"
    const val FIELD_DESCRIPTION = "Saisie du code PIN de la TV, ${Pin.LENGTH} chiffres, masqué"
}
