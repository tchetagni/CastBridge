package castbridge.core.owner

/**
 * Ce que l'on tape ou colle dans le champ de la console du propriétaire (écran « Activer ») et dans l'outil de bureau (`emettre --appareil`, onglet « Émettre ») : jamais plus d'« erreur de format »
 * sans explication. Le lecteur strict ([OwnerFrames.parseDeviceInfo]) exige `code=`, `k=` puis des lignes `factor=` ; une activation COMPLÈTE ne peut pas partir du code seul (`ActivationIssuer.issue` exige
 * les empreintes des facteurs : le code en est le haché). [classify] dit ce que l'on a devant soi, [message] l'explique en français :
 *  - [Kind.Request] : une demande complète et lisible, lue comme avant ;
 *  - [Kind.CodeOnly] : un code d'appareil seul et valide : il identifie la TV mais ne suffit pas, [CODE_ONLY] dit où trouver la demande complète (le code seul ne sert qu'à la clé compacte à saisir, dernier recours) ;
 *  - [Kind.BadCode] : un code mal recopié : [BAD_CODE] donne le format ;
 *  - [Kind.Unreadable] : une demande dont une ligne manque ou est illisible : la ligne fautive est NOMMÉE (entre « », 40 caractères au plus, jamais le reste de la demande) ;
 *  - [Kind.Empty] : rien.
 * Pur : aucun Android, aucun journal. Le classeur ne contredit jamais le lecteur strict : il lit comme demande tout ce que celui-ci lit (avec au moins un facteur), et ne nomme que ce qu'il refuse.
 */
object DeviceRequestInput {
    sealed class Kind {
        object Empty : Kind() { override fun toString() = "Empty" }
        /** Une demande complète et lisible. */
        data class Request(val info: OwnerFrames.DeviceInfo) : Kind() { override fun toString() = "Request" }
        /** Un code d'appareil seul et valide ([code] normalisé XXXX-XXXX-XXXX-XXXX). */
        data class CodeOnly(val code: String) : Kind()
        /** Un texte d'une ligne, sans « = » (ou après « code= »), qui n'est pas un code d'appareil valide. */
        object BadCode : Kind() { override fun toString() = "BadCode" }
        /** Une demande dont une ligne manque ou est illisible : [problem] la nomme. */
        data class Unreadable(val problem: String) : Kind()
    }

    const val CODE_ONLY = "Ce code identifie la TV mais ne suffit pas : la clé est liée aux empreintes de la TV. Collez la demande d'appareil complète (lignes code=, k=, factor=…) : " +
        "sur le téléphone, CastBridge › Activer la TV la lit pour vous (par le code à 6 chiffres sur une TV à jour, sinon par Bluetooth) et ouvre cette console pré-remplie."
    const val BAD_CODE = "Code d'appareil mal recopié : 16 caractères XXXX-XXXX-XXXX-XXXX avec son caractère de contrôle"
    const val EMPTY = "Aucune demande d'appareil : collez le texte « code=…, k=…, factor=TYPE|empreinte » de la TV."
    private const val MISSING_FACTORS = "il manque les lignes « factor=TYPE|empreinte » (une par facteur d'identité)"
    private const val FALLBACK = "attendu : code=…, k=…, puis des lignes factor=TYPE|empreinte"
    private const val MAX_QUOTE = 40
    /** U+FEFF written as a code point: no invisible character in the source. */
    private val BOM = 0xFEFF.toChar().toString()

    fun classify(text: String): Kind {
        val lines = linesOf(text)
        if (lines.isEmpty()) return Kind.Empty
        OwnerFrames.parseDeviceInfo(lines.joinToString("\n"))?.let { info -> return if (info.fp.n >= 1) Kind.Request(info) else Kind.Unreadable(MISSING_FACTORS) }
        val one = lines.singleOrNull()?.removePrefix("code=")
        if (one != null && '=' !in one) return DeviceCode.parse(one)?.let { Kind.CodeOnly(it) } ?: Kind.BadCode
        return Kind.Unreadable(diagnose(lines))
    }

    /** La phrase pour l'écran ; null quand la demande est lisible. */
    fun message(kind: Kind): String? = when (kind) {
        is Kind.Request -> null
        Kind.Empty -> EMPTY
        is Kind.CodeOnly -> CODE_ONLY
        Kind.BadCode -> BAD_CODE
        is Kind.Unreadable -> "Demande d'appareil illisible : ${kind.problem}."
    }

    private fun linesOf(text: String): List<String> = text.removePrefix(BOM).replace("\r", "").lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** La ligne fautive, sur une ligne, sans caractère de contrôle, 40 caractères au plus. */
    private fun quote(line: String): String {
        val clean = line.map { if (it.isISOControl()) ' ' else it }.joinToString("").replace(Regex("\\s+"), " ").trim()
        return if (clean.length > MAX_QUOTE) clean.take(MAX_QUOTE) + "…" else clean
    }

    private fun hex64(s: String) = s.length == 64 && s.all { it in '0'..'9' || it in 'a'..'f' }

    /** Ce qui cloche dans [lines], dans l'ordre où le lecteur strict les lit : jamais plus d'une ligne citée. */
    private fun diagnose(lines: List<String>): String {
        val first = lines[0]
        if (!first.startsWith("code=")) return "la première ligne doit être « code=XXXX-XXXX-XXXX-XXXX » (trouvé : « ${quote(first)} »)"
        if (DeviceCode.parse(first.removePrefix("code=")) == null) return "la ligne « ${quote(first)} » n'est pas un code d'appareil valide (16 caractères XXXX-XXXX-XXXX-XXXX, caractère de contrôle compris)"
        if (lines.size < 2 || !lines[1].startsWith("k=")) {
            return if (lines.drop(1).any { it.startsWith("k=") }) "la ligne « k=… » doit suivre tout de suite « code=… » (elle est plus bas)" else "il manque la ligne « k=… » (juste après « code=… »)"
        }
        if (lines[1].removePrefix("k=").toIntOrNull() == null) return "la ligne « ${quote(lines[1])} » n'est pas « k= » suivi d'un nombre"
        var install = false; var signing: ByteArray? = null; var readable: String? = null
        for (l in lines.drop(2)) {
            val eq = l.indexOf('=')
            if (eq <= 0) return "la ligne « ${quote(l)} » n'est pas de la forme « clé=valeur »"
            val key = l.substring(0, eq); val value = l.substring(eq + 1)
            when {
                key == "factor" -> {
                    val p = value.split('|')
                    if (p.size != 2 || p[1].isEmpty() || FactorKind.values().none { it.name == p[0] })
                        return "la ligne « ${quote(l)} » n'est pas « factor=TYPE|empreinte » (TYPE : ${FactorKind.values().joinToString(", ") { it.name }})"
                }
                key == "install" && value.startsWith("x25519|") -> {
                    if (install || !hex64(value.removePrefix("x25519|"))) return "la ligne « ${quote(l)} » (clé d'installation de la TV) doit être « install=x25519| » suivi de 64 chiffres hexadécimaux en minuscules, une seule fois"
                    install = true
                }
                key == "install_sig" && value.startsWith("ed25519|") -> {
                    val h = value.removePrefix("ed25519|")
                    if (signing != null || !hex64(h)) return "la ligne « ${quote(l)} » (clé de signature de la TV) doit être « install_sig=ed25519| » suivi de 64 chiffres hexadécimaux en minuscules, une seule fois"
                    signing = h.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                }
                key == "install_fp" -> {
                    if (readable != null) return "la ligne « ${quote(l)} » est en double"
                    readable = ActivationBinding.normalizeFingerprint(value) ?: return "la ligne « ${quote(l)} » n'est pas une empreinte lisible (8 groupes de 4 chiffres hexadécimaux)"
                }
            }
        }
        val s = signing; val f = readable
        if (s != null && f != null && f != ActivationBinding.fingerprint(s).replace("-", "")) return "la ligne « install_fp=… » ne correspond pas à « install_sig=… » (demande modifiée en route ?)"
        return FALLBACK
    }
}
