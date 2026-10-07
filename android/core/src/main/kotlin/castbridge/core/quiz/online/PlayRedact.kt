package castbridge.core.quiz.online

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Journaux du service de jeu sans secret (DESIGN-W20 § 3.5, T-18). PUR. Même esprit que `Redact.scrub` de la TV (DESIGN-W19 § 3.1 règle 5) : ce qui sort dans un
 * journal ne permet ni d'entrer dans une salle, ni de se faire passer pour quelqu'un, ni de retrouver une personne.
 *
 *  - jeton de joueur ou d'hôte (128 bits = 32 caractères hexadécimaux), secret de session de repli : retirés ([REDACTED]) ;
 *  - ticket de partie (`v1.xxx.yyy`), activation (`cbx1…`), en-tête `Authorization` / `X-Play-Ticket`, cookie `__Host-cbp-…`, `token=…` : retirés ;
 *  - code de salle : ses 4 premiers caractères seulement (`ABCD-****`) ; l'identifiant de salle (`roomId`) sert à corréler ;
 *  - adresse : IPv4 `a.b.x.x`, IPv6 ses deux premiers groupes (`2001:db8::`) ;
 *  - pseudonyme : jamais en clair, 8 caractères hexadécimaux d'une empreinte (corrèle sans identifier) ;
 *  - appareil : 8 caractères hexadécimaux de son empreinte.
 */
object PlayRedact {
    const val REDACTED = "[retiré]"

    private val hex32 = Regex("(?i)(?<![0-9a-f])[0-9a-f]{32,}(?![0-9a-f])")
    private val ticket = Regex("\\bv1\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}")
    private val activation = Regex("\\b(?:cbx1|cbp1|cbe1|cbr1|cbw1)[A-Za-z0-9._~+/=-]*", RegexOption.IGNORE_CASE)   // activation, ticket, et les pièces signées du portefeuille (blocage, résultat, instantané : identité et montants)
    private val header = Regex("(?i)\\b(authorization|x-play-ticket|x-cb-play-token|cookie|set-cookie)\\s*[:=]\\s*[^\\r\\n]*")
    private val cookie = Regex("__Host-cbp-[A-Za-z0-9_-]*\\s*=\\s*[^;\\s\"]*")
    private val param = Regex("(?i)\\b(token|ticket|secret|activation|jti)\\s*[=:]\\s*[\"']?[^\\s\"'&,;}]+")
    private val longOpaque = Regex("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{40,}(?![A-Za-z0-9_-])")
    private val ipv4 = Regex("\\b(\\d{1,3})\\.(\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}\\b")
    private val ipv6 = Regex("(?i)\\b([0-9a-f]{1,4}):([0-9a-f]{1,4})(?::[0-9a-f]{0,4}){2,6}\\b")
    private val codeParam = Regex("(?i)\\bcode\\s*[=:]\\s*[\"']?([0-9A-Z]{4})-?[0-9A-Z]{4}\\b")

    /** Texte libre (`message`, `detail`) : tout secret reconnaissable est retiré, adresses et codes tronqués. */
    fun scrub(s: String): String {
        var t = s
        t = header.replace(t) { it.groupValues[1] + ": " + REDACTED }
        t = cookie.replace(t, REDACTED)
        t = ticket.replace(t, REDACTED)
        t = activation.replace(t, REDACTED)
        t = param.replace(t) { it.groupValues[1] + "=" + REDACTED }
        t = codeParam.replace(t) { "code=" + it.groupValues[1].uppercase() + "-****" }
        t = hex32.replace(t, REDACTED)
        t = longOpaque.replace(t, REDACTED)
        t = ipv4.replace(t) { "${it.groupValues[1]}.${it.groupValues[2]}.x.x" }
        t = ipv6.replace(t) { "${it.groupValues[1]}:${it.groupValues[2]}::" }
        return t
    }

    /** Adresse (ou clé d'adresse `v6:…::/64` du service) tronquée. */
    fun ip(ip: String?): String {
        if (ip.isNullOrEmpty()) return "?"
        if (ip.startsWith("v6:")) return ip.removePrefix("v6:").split(':').take(2).joinToString(":") + "::"
        if (ip.contains(':')) return ip.split(':').take(2).joinToString(":") + "::"
        val p = ip.split('.')
        return if (p.size == 4) "${p[0]}.${p[1]}.x.x" else "?"
    }

    /** Code de salle tronqué : `ABCD-****` (la salle se corrèle par son `roomId`). */
    fun code(code: String?): String {
        val n = RoomCode.normalize(code) ?: return if (code.isNullOrEmpty()) "?" else "****"
        return n.substring(0, 4) + "-****"
    }

    /**
     * Huit caractères hexadécimaux d'une empreinte CLÉE : pseudonyme ou appareil. HMAC-SHA-256 avec une clé tirée au sort au démarrage du processus ET renouvelée chaque jour
     * (numéro de jour UTC passé par l'appelant, qui a l'horloge : `LogRedactor` ; le cœur n'en lit aucune ; sans jour, 0, pour les `toString` jamais journalisés) : un journal volé ne se « rejoue » pas contre un dictionnaire de pseudonymes (un SHA-256 sans clé de 4 octets se casse en quelques secondes), et d'un jour à
     * l'autre les empreintes ne se corrèlent plus (on peut suivre un abus pendant la journée, pas un joueur pendant des semaines).
     */
    fun pseudo(name: String?, day: Long = 0L): String = if (name == null) "?" else hash8(name.trim().lowercase(), day)
    fun device(deviceHash: String?, day: Long = 0L): String = if (deviceHash == null) "?" else hash8(deviceHash, day)

    private val processSecret: ByteArray = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }

    private fun hash8(s: String, day: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(processSecret + day.toString().toByteArray()), "HmacSHA256"))
        return mac.doFinal(s.toByteArray(Charsets.UTF_8)).take(4).joinToString("") { "%02x".format(it) }
    }

    /** Reste-t-il quelque chose de reconnaissable comme secret dans ce texte ? (sert aux tests et à la garde finale du service). */
    fun leaks(s: String): Boolean = hex32.containsMatchIn(s) || ticket.containsMatchIn(s) || activation.containsMatchIn(s) || cookie.containsMatchIn(s)
}
