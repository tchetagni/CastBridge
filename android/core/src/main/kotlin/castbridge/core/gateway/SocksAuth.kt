package castbridge.core.gateway

import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Le SOCKS local de la TV (127.0.0.1:1080) est réservé au processus CastBridge-TV (relay-R1 § 4, inventaire I-5 : « tout processus de la TV peut l'utiliser »).
 * Chaque session du tuyau reçoit un jeton aléatoire ([newToken]), gardé en mémoire dans [BtGatewayHost]. Le serveur SOCKS n'accepte que la méthode « nom d'utilisateur
 * et mot de passe » (RFC 1929) dont le mot de passe est ce jeton ; le client SOCKS du JDK (HttpURLConnection, Socket(Proxy)) le présente par
 * [SocksTokenAuthenticator], installé une fois dans le processus et qui ne répond que pour CE proxy. Un autre processus de la TV qui trouverait le port n'a pas le jeton :
 * « aucune méthode acceptable ». Sans jeton configuré (tests, TV d'avant) : aucune authentification, comme avant.
 */
object SocksAuth {
    const val USER = "cb"
    private val random = SecureRandom()

    /** 128 bits aléatoires, en hexadécimal : un par session du tuyau. */
    fun newToken(): String = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    /** Comparaison sans arrêt au premier octet différent. */
    fun sameToken(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    /**
     * La négociation de méthode d'une connexion SOCKS5 dont la version (5) est déjà lue : nombre de méthodes, méthodes, réponse du serveur, et si besoin la sous-négociation
     * RFC 1929. Vrai = la connexion peut continuer. Toute erreur de lecture est un refus (l'appelant ferme la connexion).
     */
    fun negotiate(i: DataInputStream, o: OutputStream, token: String?): Boolean = try {
        val n = i.readUnsignedByte()
        val methods = ByteArray(n).also { i.readFully(it) }.map { it.toInt() and 255 }
        when {
            token == null -> { o.write(byteArrayOf(5, 0)); o.flush(); true }
            2 !in methods -> { o.write(byteArrayOf(5, 0xFF.toByte())); o.flush(); false }
            else -> {
                o.write(byteArrayOf(5, 2)); o.flush()
                val ver = i.readUnsignedByte()
                val user = ByteArray(i.readUnsignedByte()).also { i.readFully(it) }
                val pass = String(ByteArray(i.readUnsignedByte()).also { i.readFully(it) }, Charsets.ISO_8859_1)
                val ok = ver == 1 && user.isNotEmpty() && token.isNotEmpty() && sameToken(pass, token)
                o.write(byteArrayOf(1, if (ok) 0 else 1)); o.flush()
                ok
            }
        }
    } catch (_: IOException) { false }
}

/**
 * Présente le jeton de session au proxy SOCKS de la TV, et seulement à lui : protocole `SOCKS5`, adresse de bouclage, port du proxy. Toute autre demande
 * d'authentification du processus (un vrai proxy HTTP, un autre serveur SOCKS) reçoit « pas d'identifiants » comme avant. Installé par [install] (global au processus).
 */
class SocksTokenAuthenticator(private val port: Int, private val token: () -> String?) : Authenticator() {
    override fun getPasswordAuthentication(): PasswordAuthentication? {
        if (requestingProtocol != "SOCKS5" || requestingPort != port) return null
        val loopback = requestingSite?.isLoopbackAddress == true
        if (!loopback) return null
        val t = token()?.takeIf { it.isNotEmpty() } ?: return null
        return PasswordAuthentication(SocksAuth.USER, t.toCharArray())
    }

    companion object {
        /** Une fois par processus et par port (idempotent) ; remplace l'authentificateur par défaut, qui n'était utilisé par rien d'autre. */
        @Synchronized fun install(port: Int, token: () -> String?) { setDefault(SocksTokenAuthenticator(port, token)) }
    }
}
