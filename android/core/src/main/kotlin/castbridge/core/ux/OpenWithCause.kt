package castbridge.core.ux

/**
 * « Ouvrir avec CastBridge » : pourquoi la TV n'est pas jointe, écrit DANS la fenêtre avec UNE action adaptée (jamais seulement un Toast, jamais « pas sur le même Wi-Fi » sans preuve).
 * Défaut du 2026-10-29 : la TV (192.168.0.121) répondait par son adresse alors que la fenêtre disait « Vérification… » sans fin puis « la TV n'est pas encore jointe en Wi-Fi » (faux) :
 * la TV n'était cherchée que par son NOM (mDNS). Ici : la cause, choisie sur des faits ; la recherche par l'adresse connue est faite par l'écran.
 */
object OpenWithCause {
    /** « Vérification de la liaison… » ne dure jamais plus : ensuite un verdict (ou l'envoi par le code). */
    const val CHECK_LIMIT_MS = 10_000L

    enum class Fix(val label: String) { NONE(""), RETRY("Réessayer"), CHANGE_WIFI("Changer de Wi-Fi") }

    data class Cause(val text: String, val fix: Fix)

    /**
     * @param tvName nom affiché de la TV · [tvHost] dernière adresse où elle a répondu · [tvWifi] nom de son Wi-Fi (dit par la TV) ·
     * [phoneIp]/[phonePrefix] adresse IPv4 Wi-Fi du téléphone (null = pas de Wi-Fi) · [phoneSsid] nom lu avec la permission de position (sinon null).
     */
    data class Facts(val tvName: String, val tvHost: String? = null, val tvWifi: String? = null, val phoneIp: String? = null, val phonePrefix: Int = 0, val phoneSsid: String? = null)

    /** 192.168.1.23 → 192.168.1.x (affichage, sans révéler plus que le réseau). */
    fun masked(ip: String?): String? = ip?.split('.')?.takeIf { it.size == 4 }?.let { it.take(3).joinToString(".") + ".x" }

    /** La TV ne répond pas (ou n'est pas trouvée) : la cause la plus sûre d'abord. */
    fun unreachable(f: Facts): Cause {
        val tvNet = NetworkNames.short(f.tvWifi)
        val me = NetworkNames.short(f.phoneSsid)
        if (f.phoneIp == null) return Cause("Ce téléphone n'est pas connecté au Wi-Fi : connectez-le" + (tvNet?.let { " à $it" } ?: "") + " pour joindre ${f.tvName}.", Fix.CHANGE_WIFI)
        val byName = if (me != null && tvNet != null) NetworkNames.cleanSsid(f.phoneSsid) == NetworkNames.cleanSsid(f.tvWifi) else null
        if (byName == false) return Cause("Ce téléphone est sur $me, la TV sur $tvNet", Fix.CHANGE_WIFI)
        if (byName == null && NetworkNames.sameSubnet(f.phoneIp, f.phonePrefix, f.tvHost) == false)
            return Cause("Ce téléphone n'est pas sur le réseau de la TV (téléphone en ${masked(f.phoneIp)}, TV en ${masked(f.tvHost)})", Fix.CHANGE_WIFI)
        // same network, or nothing proves otherwise: the TV itself does not answer
        return Cause(f.tvHost?.let { "${f.tvName} ne répond pas à $it : allumez la TV et ouvrez CastBridge-TV." }
            ?: "${f.tvName} est introuvable sur le réseau : allumez la TV et ouvrez CastBridge-TV.", Fix.RETRY)
    }

    /** Le départ échoue : si la TV a donné ses raisons (place, profil enfant…), ses mots ; sinon la cause réseau. */
    fun startFailure(f: Facts, tvReason: String?): Cause =
        tvReason?.takeIf { it.isNotBlank() }?.let { Cause("Impossible de démarrer : $it", Fix.RETRY) } ?: unreachable(f)
}
