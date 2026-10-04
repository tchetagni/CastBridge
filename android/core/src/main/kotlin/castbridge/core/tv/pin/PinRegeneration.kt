package castbridge.core.tv.pin

import castbridge.core.tv.Pin
import castbridge.core.tv.PinGuard
import java.security.MessageDigest

/** Affichage du code de connexion de la TV : regroupé par 3 chiffres pour la lecture, masqué sous un profil enfant. Le code n'est montré qu'à l'écran de la TV. */
object PinDisplay {
    private const val MASK = "••••••"

    fun grouped(pin: String): String = if (Pin.isValidFormat(pin)) pin.chunked(3).joinToString(" ") else pin
    fun masked(): String = MASK
    fun shown(pin: String, masked: Boolean): String = if (masked) MASK else grouped(pin)
    /** Statut de la tuile « Code PIN » de l'accueil. */
    fun tileStatus(pin: String, masked: Boolean): String = "Code PIN · " + shown(pin, masked)
}

enum class PinRefusal { CHILD_PROFILE, TRANSFER_IN_PROGRESS, RATE_LIMITED }

/** Ce que la TV sait au moment du clic : un profil enfant est actif ; une copie venant d'un téléphone est en cours. */
data class PinContext(val childProfileActive: Boolean = false, val transferInProgress: Boolean = false)

sealed interface PinDecision {
    object Allowed : PinDecision
    data class Refused(val reason: PinRefusal, val text: String, val retryAfterMs: Long = 0) : PinDecision
}

object PinRules {
    const val MAX_PER_HOUR = 3
    const val WINDOW_MS = 3_600_000L
    /** Nombre d'anciens codes dont on retient l'empreinte (en plus du code actuel) pour ne jamais les redonner. */
    const val REMEMBERED = 2

    const val CHILD_TEXT = "Un profil enfant est actif : le code PIN de la TV ne peut pas être changé."
    const val TRANSFER_TEXT = "Une copie est en cours : attendez qu'elle se termine pour générer un nouveau PIN."

    /** Priorité : profil enfant, copie en cours, puis limite de 3 par heure glissante (horloge injectée). */
    fun decide(ctx: PinContext, times: List<Long>, now: Long): PinDecision {
        if (ctx.childProfileActive) return PinDecision.Refused(PinRefusal.CHILD_PROFILE, CHILD_TEXT)
        if (ctx.transferInProgress) return PinDecision.Refused(PinRefusal.TRANSFER_IN_PROGRESS, TRANSFER_TEXT)
        val recent = pruneTimes(times, now)
        if (recent.size >= MAX_PER_HOUR) {
            val wait = (recent.min() + WINDOW_MS - now).coerceAtLeast(0)
            val min = (wait + 59_999) / 60_000
            return PinDecision.Refused(PinRefusal.RATE_LIMITED, "Trois nouveaux PIN ont déjà été générés cette heure : réessayez dans $min min.", wait)
        }
        return PinDecision.Allowed
    }

    /** Empreinte (SHA-256 tronquée) d'un ancien code : on retient de quoi ne pas le redonner, pas le code. */
    fun fingerprint(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(("castbridge-pin:$pin").toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }

    /** Un code valide, différent de l'actuel et des [REMEMBERED] précédents. */
    fun nextPin(current: String, previousFingerprints: List<String>, random: java.util.Random = java.security.SecureRandom()): String {
        while (true) {
            val p = Pin.generate(random)
            if (p != current && fingerprint(p) !in previousFingerprints) return p
        }
    }

    /** Empreintes retenues après avoir remplacé [replaced] : le plus récent d'abord, [REMEMBERED] au plus. */
    fun pushFingerprints(old: List<String>, replaced: String): List<String> = (listOf(fingerprint(replaced)) + old).take(REMEMBERED)

    fun pruneTimes(times: List<Long>, now: Long): List<Long> = times.filter { it > now - WINDOW_MS }
}

/** Stockage du code : [commit] écrit tout ou rien (TvPrefs : commit synchrone) et dit si l'écriture a réussi. */
interface PinStore {
    fun current(): String
    fun fingerprints(): List<String>
    fun times(): List<Long>
    fun commit(pin: String, fingerprints: List<String>, times: List<Long>): Boolean
}

sealed interface PinOutcome {
    data class Done(val pin: String, val at: Long) : PinOutcome
    data class Refused(val decision: PinDecision.Refused) : PinOutcome
    object WriteFailed : PinOutcome
}

/**
 * Régénère le code : règles, nouveau code, écriture, PUIS (seulement si elle a réussi) le garde ([PinGuard.rotate] : ancien code refusé,
 * compteurs et blocages d'essais effacés), le service ([onApplied]) et le journal (« PIN régénéré », jamais la valeur).
 * Les jetons des téléphones de confiance (Bluetooth) ne dérivent pas du code : ils ne sont pas touchés.
 */
class PinRegenerator(
    private val store: PinStore, private val guard: PinGuard, private val audit: (String) -> Unit,
    private val onApplied: (String) -> Unit, private val now: () -> Long, private val random: java.util.Random = java.security.SecureRandom(),
) {
    @Synchronized fun regenerate(ctx: PinContext): PinOutcome {
        val t = now()
        val d = PinRules.decide(ctx, store.times(), t)
        if (d is PinDecision.Refused) return PinOutcome.Refused(d)
        val old = store.current()
        val fresh = PinRules.nextPin(old, store.fingerprints(), random)
        val times = PinRules.pruneTimes(store.times(), t) + t
        if (!store.commit(fresh, PinRules.pushFingerprints(store.fingerprints(), old), times)) return PinOutcome.WriteFailed
        guard.rotate(fresh)
        onApplied(fresh)
        audit("PIN régénéré")
        return PinOutcome.Done(fresh, t)
    }
}

enum class PinPhase { SHOWING, CONFIRMING, SAVING, DONE }
enum class PinEvent { GENERATE, CANCEL, CONFIRM, SAVED, SAVE_FAILED }

/** [write] = true : l'écran doit lancer l'écriture maintenant (une seule fois par confirmation). */
data class PinFlowState(val phase: PinPhase = PinPhase.SHOWING, val notice: String? = null, val write: Boolean = false)

object PinFlow {
    const val CONFIRM_TEXT = "Les téléphones qui ont enregistré l'ancien code devront saisir le nouveau. Les téléphones de confiance liés par Bluetooth ne sont pas touchés."
    const val FAILED_TEXT = "Le nouveau code n'a pas pu être enregistré : l'ancien code reste actif."

    /** [decision] : la décision de [PinRules.decide] au moment de l'événement (elle est revérifiée à la confirmation). */
    fun step(s: PinFlowState, e: PinEvent, decision: PinDecision): PinFlowState = when (e) {
        PinEvent.GENERATE ->
            if (s.phase == PinPhase.CONFIRMING || s.phase == PinPhase.SAVING) s
            else if (decision is PinDecision.Refused) PinFlowState(PinPhase.SHOWING, decision.text)
            else PinFlowState(PinPhase.CONFIRMING)
        PinEvent.CANCEL -> PinFlowState(PinPhase.SHOWING)
        PinEvent.CONFIRM ->
            if (s.phase != PinPhase.CONFIRMING) s.copy(write = false)
            else if (decision is PinDecision.Refused) PinFlowState(PinPhase.SHOWING, decision.text)
            else PinFlowState(PinPhase.SAVING, write = true)
        PinEvent.SAVED -> PinFlowState(PinPhase.DONE)
        PinEvent.SAVE_FAILED -> PinFlowState(PinPhase.SHOWING, FAILED_TEXT)
    }
}
