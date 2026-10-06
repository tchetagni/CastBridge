package castbridge.core.owner

import castbridge.core.lots.TvTransport
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.str
import castbridge.core.ssh.Lan
import castbridge.core.trust.TvCredential
import castbridge.core.tv.Pin
import java.io.IOException

/**
 * How the phone sends an activation key to the TV: over Wi-Fi (POST /api/activation/install, which the TV opens to the connection code ONLY, never to a trusted-phone token) or over Bluetooth.
 * Pure logic, nothing Android.
 */
object ActivationSend {
    enum class Channel { LAN_WITH_PIN, LAN_ASKS_PIN, BLUETOOTH }

    data class Choice(val channel: Channel, val pin: String?, val explanation: String?)

    /** [lanBase]: the TV's Wi-Fi address if reachable; [credential]: what the phone holds for it (PIN or token or null); [typedPin]: the code the customer typed for this call (or null/blank). */
    fun choose(lanBase: String?, credential: String?, typedPin: String?): Choice {
        if (lanBase == null) return Choice(Channel.BLUETOOTH, null, "La TV n'est pas joignable par le Wi-Fi : envoi par Bluetooth.")
        if (Pin.isValidFormat(credential)) return Choice(Channel.LAN_WITH_PIN, credential, null)
        val t = typedPin?.trim().orEmpty()
        if (Pin.isValidFormat(t)) return Choice(Channel.LAN_WITH_PIN, t, null)
        val why = if (t.isNotEmpty()) "Le code de connexion doit comporter ${Pin.LENGTH} chiffres." else null
        return Choice(Channel.LAN_ASKS_PIN, null, why ?: ASK_PIN_TEXT)
    }

    const val ASK_PIN_TEXT = "Pour envoyer la clé par le Wi-Fi, saisissez le code de connexion de la TV (6 chiffres, affiché sur l'écran d'activation de CastBridge-TV, ou dans « Connexion & réglages »). Il n'est utilisé que pour cet envoi et n'est pas enregistré. Sinon, l'envoi par Bluetooth reste possible."
    const val WRONG_PIN_TEXT = "Code de connexion refusé par la TV. Vérifiez-le sur l'écran d'activation de CastBridge-TV (ou « Connexion & réglages »), ou envoyez par Bluetooth."

    /** A CastBridge-TV announced on the Wi-Fi (mDNS); [locked] = it announces `locked=1` (waiting for its key). */
    data class Found(val name: String, val base: String, val locked: Boolean)

    /**
     * Where to send the key over the Wi-Fi: the TV the phone is linked to ([linked] = base and credential), otherwise the TV found on the Wi-Fi whose base the customer
     * TOUCHED ([chosenBase]); never an automatic choice (audit M2: a fake « TV à activer » announced on the network must not receive the key and the code by default),
     * never an address outside the local network ([Lan.isLocal]), never the phone's own Bluetooth gateway (loopback). No code is known for a found TV: it is asked.
     */
    fun lanTarget(linked: Pair<String, String?>?, found: List<Found>, chosenBase: String?): Pair<String, String?>? {
        if (linked != null) return linked.takeIf { hostOf(it.first)?.let(Lan::isLocal) == true }
        val pick = found.firstOrNull { it.base == chosenBase && isLanTv(it.base) } ?: return null   // only what the customer touched, no fallback
        return pick.base to null
    }

    /** A TV found on the Wi-Fi: a private address literal, not loopback (the phone's own Bluetooth gateway). */
    fun isLanTv(base: String): Boolean {
        val h = hostOf(base) ?: return false
        if (h == "localhost" || h.startsWith("127.") || h == "::1" || h == "0:0:0:0:0:0:0:1") return false
        return Lan.isLocal(h)
    }

    /** The host of an `http://host:port` base (IPv6 with or without brackets); null for anything else. */
    fun hostOf(base: String): String? {
        if (!base.startsWith("http://")) return null
        val rest = base.removePrefix("http://").substringBefore('/')
        if (rest.startsWith("[")) return rest.substring(1).substringBefore(']').takeIf { "]" in rest && it.isNotEmpty() }
        return when (rest.count { it == ':' }) {
            0 -> rest
            1 -> rest.substringBefore(':')
            else -> rest.substringBeforeLast(':')
        }.takeIf { it.isNotEmpty() }
    }

    /** Shown next to the code field (audit M2): the customer sees WHICH address will receive the key and the code, « TV 192.168.1.21 ». */
    fun targetLabel(base: String?): String? = base?.let(::hostOf)?.let { "TV $it" }

    /**
     * A new mDNS announce [incoming] merged into [current] (audit M2): a TV re-announced from the SAME address is updated; an announce with the same name from ANOTHER
     * address never replaces the TV silently: the oldest address is kept and the other one is attached with [flag] ([other] reads it back) so that the screen can say it.
     */
    fun <T> mergeAnnounce(current: List<T>, incoming: T, name: (T) -> String, address: (T) -> String, other: (T) -> String?, flag: (T, String) -> T): List<T> {
        val i = current.indexOfFirst { name(it) == name(incoming) }
        if (i < 0) return current + incoming
        val old = current[i]
        val kept = if (address(old) == address(incoming)) other(old)?.let { flag(incoming, it) } ?: incoming else flag(old, address(incoming))
        return current.toMutableList().also { it[i] = kept }
    }

    /** Result of the LAN call: [pinRefused] true when the TV did not accept the connection code (the screen asks again / offers Bluetooth). */
    data class Result(val ok: Boolean, val message: String, val pinRefused: Boolean = false, val linkDown: Boolean = false)

    fun sendLan(tv: TvTransport, key: String): Result {
        val r = try { tv.call("POST", "/api/activation/install", emptyMap(), key.trim().toByteArray(Charsets.UTF_8)) }
        catch (e: TvCredential.Missing) { return Result(false, WRONG_PIN_TEXT, pinRefused = true) }
        catch (e: IOException) { return Result(false, "La TV n'a pas répondu par le Wi-Fi.", linkDown = true) }
        val j = runCatching { JsonLite.obj(r.json) }.getOrNull()
        return when {
            r.status == 401 -> Result(false, WRONG_PIN_TEXT, pinRefused = true)
            r.status in 200..299 -> Result(true, j?.str("label")?.takeIf { it.isNotBlank() }?.let { "Clé : $it." }.orEmpty())
            r.status == 404 -> Result(false, "Cette version de CastBridge-TV ne reçoit pas les clés par le Wi-Fi : utilisez le Bluetooth.", linkDown = true)
            else -> Result(false, j?.str("error") ?: j?.str("message") ?: "HTTP ${r.status}")
        }
    }
}
