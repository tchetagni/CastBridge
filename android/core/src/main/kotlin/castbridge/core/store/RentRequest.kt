package castbridge.core.store

import castbridge.core.lots.RentalDurations
import java.security.MessageDigest

/** Pourquoi une demande de location est refusée (cœur, avant toute écriture). L'ordre de contrôle est celui de [RentRequests.create]. */
enum class Refusal { MALFORMED, BAD_CHOICE, TRIAL_TV, KID_PROFILE, FREE_BUNDLE, UNKNOWN_FAMILY, OVER_LIMIT, SAME_BUNDLE_OTHER_UNIT, PILOT_ENDED, DUPLICATE, QUEUE_FULL, ENDED, NO_CONTRACT, STORAGE }

/**
 * Une demande de location (`castbridge-rent-request-v1`, DESIGN-W17 § 4.1) : l'intention de la TV, ou du téléphone, de louer ou de prolonger UN bouquet pour un choix W16.
 * Ce n'est ni un droit, ni un paiement, ni une signature : elle ne contient ni code parental, ni jeton, ni clé, et ne vaut rien tant qu'un adulte ne l'a pas confirmée sur
 * le téléphone et que l'émetteur ne l'a pas exécutée.
 *
 * Forme canonique (seule forme acceptée par [parse]) : la ligne d'en-tête [FORMAT], puis les huit champs `clé=valeur` triés par clé, séparés par `\n`, sans espace,
 * sans retour à la ligne final : `at`, `bundle`, `choice`, `kind`, `nonce`, `origin`, `period`, `tv`.
 */
data class RentRequest(val tv: String, val bundle: String, val choice: String, val kind: Kind, val period: Long, val nonce: String, val at: Long, val origin: Origin) {
    enum class Kind(val wire: String) { NEW("new"), EXTEND("extend") }
    enum class Origin(val wire: String) { TV("tv"), PHONE("phone") }
    enum class Unit { DAYS, HOURS }

    /** Résultat de [parse] : la demande, ou le motif (toujours [Refusal.MALFORMED]) avec une phrase française. */
    sealed class Parsed {
        data class Ok(val request: RentRequest) : Parsed()
        data class Bad(val refusal: Refusal, val message: String) : Parsed()
    }

    /** Le texte canonique, octet pour octet. */
    fun canonical(): String = listOf(
        "at=$at", "bundle=$bundle", "choice=$choice", "kind=${kind.wire}", "nonce=$nonce", "origin=${origin.wire}", "period=$period", "tv=$tv",
    ).joinToString("\n", prefix = "$FORMAT\n")

    /**
     * Code de secours dicté ou lu à 3 m : `<ALIAS>-<CHOIX>-<4 caractères Crockford>` (`CM2-12H-0PF8`). CHOIX = le choix en majuscules (`12H`, `7J`), `DEF` pour `defaut`.
     * Les 4 caractères sont les 20 premiers bits de SHA-256 de `castbridge-rent-request-v1|tv|bundle|choice|nonce`. Un mémo, jamais une preuve : celui qui le reçoit ne peut
     * pas le vérifier sans le nonce.
     */
    fun shortCode(alias: String): String {
        require(ALIAS.matches(alias)) { "alias illisible : « $alias »" }
        val d = MessageDigest.getInstance("SHA-256").digest("$FORMAT|$tv|$bundle|$choice|$nonce".toByteArray(Charsets.UTF_8))
        val bits = ((d[0].toInt() and 0xff) shl 12) or ((d[1].toInt() and 0xff) shl 4) or ((d[2].toInt() and 0xff) ushr 4)
        val tail = (15 downTo 0 step 5).map { CROCKFORD[(bits ushr it) and 31] }.joinToString("")
        return "$alias-${if (choice == DEFAULT) "DEF" else choice.uppercase()}-$tail"
    }

    /** Le libellé exact de W16 § 1.3, sans aucune conversion jours / heures. */
    fun choiceLabel(): String = when {
        choice == DEFAULT -> "Sans durée précise : ${RentalDurations.DEFAULT_DAYS} jours"
        unit == Unit.HOURS -> amount().let { if (it == 1) "1 heure d'utilisation" else "$it heures d'utilisation" }
        else -> amount().let { if (it == 1) "1 jour" else "$it jours" }
    }

    /** L'unité du choix : `defaut` et `Nj` en jours, `Nh` en heures d'utilisation. */
    val unit: Unit get() = if (choice.endsWith("h")) Unit.HOURS else Unit.DAYS

    private fun amount() = choice.dropLast(1).toInt()

    companion object {
        const val FORMAT = "castbridge-rent-request-v1"
        const val DEFAULT = "defaut"

        /** Longueur maximale d'une demande à l'analyse (la forme canonique la plus longue fait environ 220 caractères). */
        const val MAX_TEXT = 1024

        /** Valeurs du sélecteur W16 (§ 1.1) ; `PilotRules.Choice` (w16-04) n'est pas fusionné : validation locale, à reprendre d'une ligne. */
        val PICKER_DAYS = setOf(1, 3, 7, 14)
        val PICKER_HOURS = setOf(1, 3, 6, 12, 24, 48, 96)

        private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private val ALIAS = Regex("^[A-Z0-9]{1,6}$")
        private val TV = Regex("^[0-9a-f]{16}$")
        private val BUNDLE = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
        private val CHOICE = Regex("^(defaut|[1-9][0-9]{0,2}[jh])$")
        private val NONCE = Regex("^[0-9a-f]{8}$")
        private val NUMBER = Regex("^[0-9]{1,15}$")
        private val KEYS = setOf("at", "bundle", "choice", "kind", "nonce", "origin", "period", "tv")

        fun validTv(v: String) = TV.matches(v)
        fun validBundle(v: String) = BUNDLE.matches(v)
        fun validChoice(v: String) = CHOICE.matches(v)
        fun validNonce(v: String) = NONCE.matches(v)

        /** Vrai si [choice] (grammaire correcte) est une valeur du sélecteur : `defaut`, 1/3/7/14 jours, 1/3/6/12/24/48/96 heures. */
        fun inPicker(choice: String): Boolean = choice == DEFAULT || (validChoice(choice) && choice.dropLast(1).toInt().let { if (choice.endsWith("h")) it in PICKER_HOURS else it in PICKER_DAYS })

        private fun bad(why: String) = Parsed.Bad(Refusal.MALFORMED, "Demande de location illisible ($why) : elle est ignorée.")

        /**
         * Analyse STRICTE : en-tête exact, les huit champs et eux seuls, chacun à sa grammaire, `period` = 0 pour `new` et > 0 pour `extend`, puis relecture canonique :
         * toute autre graphie (ordre, espace, `\r`, retour final, zéro en tête) est refusée. Ne lève jamais d'exception.
         */
        fun parse(text: String): Parsed {
            if (text.length > MAX_TEXT) return bad("trop long")
            val lines = text.split("\n")
            if (lines.first() != FORMAT) return bad("en-tête")
            val kv = LinkedHashMap<String, String>()
            for (line in lines.drop(1)) {
                val i = line.indexOf('=')
                if (i <= 0) return bad("ligne sans « = »")
                if (kv.put(line.substring(0, i), line.substring(i + 1)) != null) return bad("champ en double")
            }
            if (kv.keys != KEYS) return bad("champs manquants ou inconnus")
            val kind = Kind.entries.firstOrNull { it.wire == kv["kind"] } ?: return bad("genre")
            val origin = Origin.entries.firstOrNull { it.wire == kv["origin"] } ?: return bad("origine")
            if (!NUMBER.matches(kv.getValue("at")) || !NUMBER.matches(kv.getValue("period"))) return bad("nombre")
            val r = RentRequest(kv.getValue("tv"), kv.getValue("bundle"), kv.getValue("choice"), kind, kv.getValue("period").toLong(), kv.getValue("nonce"), kv.getValue("at").toLong(), origin)
            if (!validTv(r.tv)) return bad("téléviseur")
            if (!validBundle(r.bundle)) return bad("bouquet")
            if (!validChoice(r.choice)) return bad("choix")
            if (!validNonce(r.nonce)) return bad("nonce")
            if ((kind == Kind.NEW) != (r.period == 0L)) return bad("période")
            if (r.canonical() != text) return bad("graphie non canonique")
            return Parsed.Ok(r)
        }
    }
}
