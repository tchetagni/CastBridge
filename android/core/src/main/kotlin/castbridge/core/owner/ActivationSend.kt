package castbridge.core.owner

import castbridge.core.lots.TvTransport
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.str
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
     * Where to send the key over the Wi-Fi: the TV the phone is linked to ([linked] = base and credential), otherwise the TV found on the Wi-Fi named [chosenName], otherwise the
     * first locked one found (the one waiting for a key), otherwise the first found; never the phone's own Bluetooth gateway (loopback). No code is known for a found TV: it is asked.
     */
    fun lanTarget(linked: Pair<String, String?>?, found: List<Found>, chosenName: String?): Pair<String, String?>? {
        if (linked != null) return linked
        val lan = found.filter { f -> listOf("127.", "localhost", "[::1]").none { f.base.removePrefix("http://").startsWith(it) } }
        val pick = lan.firstOrNull { it.name == chosenName } ?: lan.firstOrNull { it.locked } ?: lan.firstOrNull() ?: return null
        return pick.base to null
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
