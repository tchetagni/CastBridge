package castbridge.core.brand

/**
 * Marque de l'app en cours (marque blanche, docs/WHITE-LABEL.md). Rien à voir avec [BrandTokens] (couleurs) : ici les NOMS.
 * Les apps l'initialisent au démarrage depuis leur BuildConfig ([init]) ; avant cela, ou sans appel, c'est CastBridge.
 *
 * Les textes de :core nomment « CastBridge » et « CastBridge-TV » en dur ; [t] les remplace à l'affichage par la marque.
 * Pour CastBridge ([t] rend le texte tel quel) rien ne change : c'est la garantie « build par défaut identique ».
 */
object Brand {
    const val DEFAULT_NAME = "CastBridge"
    const val DEFAULT_TV_NAME = "CastBridge-TV"

    @Volatile var id = "castbridge"; private set
    @Volatile var name = DEFAULT_NAME; private set
    @Volatile var tvName = DEFAULT_TV_NAME; private set
    @Volatile var owner = DEFAULT_NAME; private set
    @Volatile var tagline = ""; private set
    @Volatile var supportPhone = ""; private set
    /** Thème par défaut du téléphone pour cette marque : DARK (CastBridge), LIGHT ou SYSTEM. Le choix de l'utilisateur prime. */
    @Volatile var phoneTheme = "DARK"; private set

    /** Vrai si la marque n'est pas CastBridge : le seul cas où [t] fait quelque chose. */
    val isCustom: Boolean get() = name != DEFAULT_NAME || tvName != DEFAULT_TV_NAME

    fun init(id: String, name: String, tvName: String, owner: String, tagline: String, supportPhone: String, palette: String = "", phoneTheme: String = "DARK") {
        BrandPalette.init(palette)
        this.phoneTheme = phoneTheme.uppercase().takeIf { it in setOf("DARK", "LIGHT", "SYSTEM") } ?: "DARK"
        this.id = id; this.name = name; this.tvName = tvName; this.owner = owner; this.tagline = tagline; this.supportPhone = supportPhone
    }

    // « CastBridge-TV » et « CastBridge TV » d'abord (sinon « CastBridge » seul les couperait). Un chemin (« Download/CastBridge ») n'est pas une marque : il reste.
    private val tvForm = Regex("(?<![/\\w])CastBridge[- ]TV")
    private val phoneForm = Regex("(?<![/\\w])CastBridge(?![\\w-])")

    /** Texte vu par l'utilisateur, avec la marque courante à la place de CastBridge / CastBridge-TV. */
    fun t(text: String): String {
        if (!isCustom || !text.contains("CastBridge")) return text
        return text.replace(tvForm, Regex.escapeReplacement(tvName)).replace(phoneForm, Regex.escapeReplacement(name))
    }

    fun t(text: CharSequence): CharSequence = if (text is String) t(text) else text   // texte stylé : laissé tel quel (voir docs/WHITE-LABEL.md)
}
