package castbridge.core.trust

import castbridge.core.tv.BtProtocol

/**
 * Chaque refus de la TV (codes [BtProtocol] ERR_*) en français clair : la CAUSE et l'ACTION à faire. Pur, testé (`LinkRefusalTextsTest`).
 * Utilisé par la boite « Ouvrir avec CastBridge » (bandeau), la notification d'un envoi qui échoue et la file d'envoi.
 * Aucun texte ne contient un code PIN, un jeton ni un secret : seulement le numéro du refus.
 */
object LinkRefusalTexts {
    private class Row(val cause: String, val action: String, val pin: Boolean = false)

    private val rows: Map<Int, Row> = mapOf(
        BtProtocol.ERR_MAGIC to Row("CastBridge-TV n'est pas à la même version que ce téléphone", "mettez à jour CastBridge-TV sur la TV"),
        BtProtocol.ERR_PIN to Row("Le code PIN de la TV n'est pas le bon", "saisissez le code PIN affiché sur la TV", pin = true),
        BtProtocol.ERR_NAME to Row("La TV refuse le nom de ce fichier", "renommez le fichier puis réessayez"),
        BtProtocol.ERR_SPACE to Row("Il n'y a plus assez de place sur la TV", "supprimez des vidéos sur la TV puis réessayez"),
        BtProtocol.ERR_LOCKED to Row("La TV est verrouillée une minute après trop de codes faux", "attendez une minute puis saisissez le bon code PIN affiché sur la TV", pin = true),
        BtProtocol.ERR_IO to Row("La TV n'a pas pu enregistrer le fichier", "vérifiez le stockage de la TV puis réessayez"),
        BtProtocol.ERR_SIZE to Row("La TV refuse la taille de ce fichier", "vérifiez que le fichier n'est pas vide, puis réessayez"),
        BtProtocol.ERR_UNTRUSTED to Row("La TV ne reconnaît plus ce téléphone", "saisissez le code PIN affiché sur la TV pour le ré-associer", pin = true),
        BtProtocol.ERR_DENIED to Row("Le propriétaire a refusé ce téléphone sur la TV", "saisissez le code PIN affiché sur la TV, ou demandez d'autoriser le téléphone", pin = true),
        BtProtocol.ERR_TIMEOUT to Row("Personne n'a répondu sur la TV", "avec la télécommande, choisissez « Autoriser » sur la TV puis réessayez"),
        BtProtocol.ERR_NOT_OPEN to Row("La TV n'attend pas de nouveau téléphone", "sur la TV, ouvrez « Ajouter un téléphone » puis réessayez"),
        BtProtocol.ERR_BUSY to Row("La TV traite déjà une demande", "patientez quelques secondes puis réessayez"),
        BtProtocol.ERR_TRIAL to Row("La version d'essai de la TV ne reçoit pas de fichiers par Bluetooth", "envoyez par Wi-Fi, ou activez la TV"),
        BtProtocol.ERR_FULL to Row("La TV a déjà ${TrustRegistry.MAX_PHONES} téléphones", "sur la TV, choisissez le téléphone à retirer puis réessayez"),
        BtProtocol.ERR_FULL_CANCELED to Row("Le propriétaire de la TV n'a retiré aucun téléphone", "sur la TV, retirez-en un puis réessayez"),
        BtProtocol.ERR_FULL_TIMEOUT to Row("Personne n'a choisi de téléphone à retirer sur la TV", "réessayez quand vous êtes devant la TV"),
    )

    /** Les codes connus, dans l'ordre. */
    val KNOWN_CODES: List<Int> = rows.keys.sorted()

    private val unknown = Row("La TV a refusé la liaison", "réessayez ; si cela se répète, redémarrez CastBridge-TV sur la TV")
    private fun row(code: Int) = rows[code] ?: unknown

    fun cause(code: Int): String = row(code).cause
    fun action(code: Int): String = row(code).action
    /** Le refus se règle en saisissant le code PIN de la TV. */
    fun asksPin(code: Int): Boolean = row(code).pin

    /** Le bandeau de la boite « Ouvrir avec » : « La TV ne reconnaît plus ce téléphone (code 8) : saisissez le code PIN … ». */
    fun banner(code: Int): String = "${cause(code)} (code $code) : ${action(code)}"

    /** Le texte d'un envoi échoué (notification, fiche de la file) : « Échec : … ». Touchez = rouvre la boite sur le champ du code PIN. */
    fun ticket(code: Int): String {
        val c = cause(code).replaceFirstChar { it.lowercase() }
        return if (asksPin(code)) "Échec : $c. Touchez pour saisir le code PIN"
        else if (code in rows) "Échec : $c : ${action(code)}"
        else "Échec : $c (code $code) : ${action(code)}"
    }

    /** Le code de refus que dit cet état de la liaison de confiance, ou null (l'état n'est pas un refus de la TV). */
    fun codeOf(s: LinkState): Int? = when (s) {
        is LinkState.TvForgotMe -> BtProtocol.ERR_UNTRUSTED
        LinkState.Denied -> BtProtocol.ERR_DENIED
        LinkState.TvTooOld -> BtProtocol.ERR_MAGIC
        is LinkState.TvError -> s.code
        else -> null
    }

    /** La copie en attente de la liaison doit échouer tout de suite avec ce texte ([ticket]) ; null = la boucle réessaie seule, on attend. */
    fun failureFor(s: LinkState): String? = codeOf(s)?.let { ticket(it) }

    /** R-20 : l'état « code requis » de la file : une ligne, une action. Remplace « TV introuvable » tant que le code manque. */
    const val CODE_REQUIRED_LINE = "Code requis : la TV ne reconnaît plus ce téléphone. Touchez pour saisir le code affiché sur la TV"

    /**
     * Cet échec d'envoi se règle-t-il en saisissant le code de la TV ? (ticket d'un refus qui demande le code, jeton expiré ou refusé, 401/403.)
     * Alors la file est en « code requis », sans nouvel essai automatique, et la boite d'envoi demande le code elle-même.
     */
    fun isCodeRequired(reason: String?): Boolean {
        if (reason == null) return false
        if (KNOWN_CODES.any { asksPin(it) && reason == ticket(it) }) return true
        val r = reason.lowercase()
        if ("verrouillée" in r || "locked" in r) return false      // a locked TV (403 with `locked`/`trial`): activation, not the TV's code
        return "autorisation de la tv" in r || "code pin" in r || "pinrequired" in r || "(401)" in r || "(403)" in r || "jeton" in r && "refus" in r
    }
}
