package castbridge.sender

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import castbridge.core.trust.CredentialDecision
import castbridge.core.trust.PinBook
import castbridge.core.trust.PinKeys
import castbridge.core.trust.PinKv
import castbridge.core.trust.TvAuth
import castbridge.core.tv.Pin

/** `castbridge_pins` as the code book's store: SYNCHRONOUS writes (`commit()`, R-10: a code typed just before the app is killed is not lost). */
class PrefsPinKv(private val sp: SharedPreferences) : PinKv {
    override fun get(key: String): String? = sp.getString(key, null)
    override fun write(put: Map<String, String>, remove: Collection<String>): Boolean {
        val e = sp.edit()
        remove.forEach { e.remove(it) }
        put.forEach { (k, v) -> e.putString(k, v) }
        return e.commit()
    }
}

/**
 * What the phone presents to a TV, for any screen key of that TV (name, mDNS name, "bt:<address>", "host:port", URL): for a TV this phone is trusted by, the
 * phone's own token (kept fresh by [TvLinkManager], no PIN involved); otherwise the PIN typed once, kept in ONE record per TV under a stable id ([PinBook], R-10:
 * an IP change, another screen, a new app session never ask it again). Screens call it "the PIN" for historical reasons; the value may be either ([TvAuth]).
 * Private SharedPreferences `castbridge_pins`, excluded from backups and device transfers; the code is never logged.
 */
class PinStore(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("castbridge_pins", Context.MODE_PRIVATE)
    private val book = PinBook(PrefsPinKv(sp))
    init { TvLinkManager.init(ctx) }
    /** Token of the saved TV [key] designates; else the code kept for that TV (any of its keys, old ones migrated); never a code the TV refused; else "". */
    fun get(key: String?): String = TvLinkManager.credentialFor(key) ?: book.read(key, TvLinkManager.pinScope())
    /** The typed PIN only (never the token). */
    fun pinOnly(key: String?): String = book.read(key, TvLinkManager.pinScope())
    /**
     * Keeps [pin] for the TV [key] designates (its record, plus the legacy keys of [PinKeys.writeKeys]: still nothing under the IPs of a TV with a token).
     * Never under the tunnel loopback with no gateway. Still no erase on PIN_WRONG (w13-08). False when it could not be kept.
     */
    fun put(key: String, pin: String): Boolean = book.write(key, pin, TvLinkManager.pinScope())
    /** The TV answered « bad pin » to [pin]: not presented again by itself (no lockout from a loop), not erased; the next typed code lifts it. */
    fun refused(key: String?, pin: String) = book.refused(key, pin, TvLinkManager.pinScope())
    /** [other] (the address the discovery saw, a new mDNS name) designates the TV of [known]: every screen key of it finds its code. */
    fun link(known: String, other: String) = book.link(known, other, TvLinkManager.pinScope())

    /** What a screen should do for [key] right now ([CredentialDecision]): token, kept code, wait (trusted link renewing, lockout), or ask the code with its cause. */
    fun decide(key: String?, lockedSec: Long? = null): CredentialDecision.Choice {
        val scope = TvLinkManager.pinScope()
        val lf = TvLinkManager.linkFacts(key)
        val relay = book.unknownRelay(key, scope)
        return CredentialDecision.decide(CredentialDecision.Facts(
            token = TvLinkManager.credentialFor(key), tokenRefused = lf?.tokenRefused == true, trustedTv = key != null && TvLinkManager.savedFor(key) != null,
            linkPending = lf?.pending == true, storedPin = book.read(key, scope), pinRefused = book.isRefused(key, scope),
            tvReset = lf?.tvReset == true || book.tvReset(key, scope), phoneRemoved = lf?.phoneRemoved == true, lockedSec = lockedSec, relayUnknown = relay))
    }
}

/** PIN entry for the selected TV; the value is remembered per TV as soon as it is well formed. A trusted phone does not need one. */
@Composable
fun PinField(store: PinStore, key: String?, pin: String, onPin: (String) -> Unit, modifier: Modifier = Modifier) {
    val trusted = TvAuth.isToken(pin)
    // R-10: while the trusted link renews its token (app just opened) or the TV locked the phone for a minute, say so instead of « type the code »
    val waiting = if (trusted || pin.isNotEmpty() || key == null) null else (store.decide(key) as? CredentialDecision.Choice.Wait)?.reason
    OutlinedTextField(
        if (trusted) "" else pin, { v ->
            val d = v.filter { it.isDigit() }.take(Pin.LENGTH)
            onPin(d)
            if (key != null && Pin.isValidFormat(d)) store.put(key, d)
        }, modifier, singleLine = true, enabled = key != null,
        label = { Text(if (trusted) "Ce téléphone est de confiance : aucun code nécessaire" else waiting ?: "PIN de la TV (affiché sur son écran)") },
        isError = !trusted && pin.isNotEmpty() && !Pin.isValidFormat(pin),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
}
