package castbridge.play.entitlement

import castbridge.core.lots.ClockDoubt
import castbridge.core.lots.RentalEngine
import castbridge.core.lots.RentalInputs
import castbridge.core.lots.JudgedTime
import castbridge.core.owner.Activation
import castbridge.core.owner.ActivationResult
import castbridge.core.owner.ActivationVerifier
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.Fingerprints
import castbridge.core.owner.KeyRing
import castbridge.core.owner.RevocationState
import castbridge.core.owner.SeqState
import castbridge.core.owner.Subject
import castbridge.core.owner.TvAccess
import castbridge.core.owner.TvGate
import castbridge.core.quiz.online.HostEdition
import castbridge.core.quiz.online.PlayReason
import castbridge.core.quiz.online.PlayRules

/**
 * Ce que le SERVICE conclut des preuves jointes à `create` (jamais du ticket) : [edition], les lots dont les questions réservées sont servies ([coveredScopes], « tout » = tous),
 * salons publics permis, parties par jour. [note] = le texte français à montrer (refus, ou « Location terminée : questions libres »). [identity] = code d'appareil de l'activation
 * (clé du compte de parties d'essai : il suit l'activation signée, pas l'appareil API).
 */
class HostRights(val edition: HostEdition, val coveredScopes: Set<String>, val publicAllowed: Boolean, val maxGamesPerDay: Int, val reason: PlayReason?, val note: String,
                 val identity: String?, val clockDoubt: Boolean = false) {
    val actor: PlayRules.Actor get() = PlayRules.actorOf(edition, coveredScopes.isNotEmpty(), clockDoubt)

    companion object {
        fun none(note: String, clockDoubt: Boolean = false) = HostRights(HostEdition.NONE, emptySet(), false, 0, PlayReason.PLAY_SCOPE_FORBIDDEN, note, null, clockDoubt)
    }
}

/**
 * Évalue `activation` (`cbx1`) et lignes de location jointes à `create`, avec l'HORLOGE DU SERVICE : signatures vérifiées par les clés publiques des émetteurs de confiance,
 * clé et poste non révoqués, identité = celle du ticket, puis `TvGate` et `RentalEngine` du cœur (les mêmes règles que sur la TV, sans la fenêtre d'installation de 48 h : elle ne
 * sert qu'à INSTALLER). L'usage par minutes d'une location n'est pas connu du service : seule sa date compte. Fermé : tout doute donne [HostEdition.NONE].
 */
class HostRightsEvaluator(private val ring: KeyRing, private val revocations: () -> RevocationState) {
    fun evaluate(deviceCode: String?, tokens: List<String>, nowMs: Long, clockDoubt: ClockDoubt? = null): HostRights {
        val code = deviceCode?.let { DeviceCode.parse(it) }
        if (tokens.isEmpty() || code == null) return HostRights.none(PlayRules.MSG_ACTIVATE)
        if (tokens.size > MAX_TOKENS) return HostRights.none("Trop d'activations jointes : gardez la plus récente.")
        if (clockDoubt != null) return HostRights.none(TvAccess.CHECK_CLOCK_LABEL, clockDoubt = true)
        val rev = revocations()
        val accepted = ArrayList<Activation>()
        var otherTv = false
        for (t in tokens) {
            if (t.length > MAX_TOKEN_LENGTH) continue
            val a = Activation.decode(t) ?: continue
            val fp = Fingerprints(a.factors)
            if (DeviceCode.of(fp) != code) { otherTv = true; continue }
            // une SeqState neuve par jeton : l'ordre des jetons ne change rien ; le temps de vérification est celui de l'émission (la fenêtre de 48 h n'est pas jugée ici)
            val r = ActivationVerifier(ring, revocations = rev, expect = Subject.TV, seqState = SeqState()).verify(t, fp, maxOf(a.issuedAt, a.notBefore))
            if (r is ActivationResult.Accepted) accepted += a
        }
        if (accepted.isEmpty()) return HostRights.none(if (otherTv) "Cette activation n'est pas celle de cette TV." else PlayRules.MSG_ACTIVATE)
        if (accepted.any { it.issuedAt > nowMs + CLOCK_SKEW_MS }) return HostRights.none(TvAccess.CHECK_CLOCK_LABEL, clockDoubt = true)

        val statuses = RentalEngine.evaluate(RentalEngine.contracts(accepted), RentalInputs(JudgedTime(nowMs, null), superUnlimited = RentalEngine.superUnlimited(accepted)))
        val tv = TvGate.evaluate(accepted, emptyList(), nowMs, statuses)
        if (!tv.keyInstalled) return HostRights.none(PlayRules.MSG_ACTIVATE)
        if (tv.suspended) return HostRights.none(TvAccess.CHECK_CLOCK_LABEL, clockDoubt = true)
        if (tv.trial) return HostRights(HostEdition.TRIAL, emptySet(), false, PlayRules.TRIAL_GAMES_PER_DAY, null, PlayRules.MSG_TRIAL_PRIVATE, code)
        val covered = tv.access.granted
        val edition = if (tv.access.inGrace) HostEdition.GRACE else HostEdition.PROD
        val note = if (covered.isEmpty() && statuses.isNotEmpty() && statuses.none { it.usable }) PlayRules.MSG_RENTAL_ENDED else ""
        return HostRights(edition, covered, true, Int.MAX_VALUE, null, note, code)
    }

    companion object {
        const val MAX_TOKENS = 4
        const val MAX_TOKEN_LENGTH = 8_192
        /** Une activation émise plus de 24 h dans le futur du service : l'horloge du service ou l'activation est fausse ; dans le doute, refus. */
        const val CLOCK_SKEW_MS = 24L * 3600 * 1000
    }
}
