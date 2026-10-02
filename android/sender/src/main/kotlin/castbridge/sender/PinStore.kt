package castbridge.sender

import android.content.Context
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import castbridge.core.trust.PinKeys
import castbridge.core.trust.TvAuth
import castbridge.core.tv.Pin

/**
 * What the phone presents to a TV, keyed by TV name / "host:port" / "bt:<address>": for a TV this phone is trusted by, the
 * phone's own token (kept fresh by [TvLinkManager], no PIN involved); otherwise the PIN typed once (private SharedPreferences).
 * Screens call it "the PIN" for historical reasons; the value may be either ([TvAuth]).
 */
class PinStore(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("castbridge_pins", Context.MODE_PRIVATE)
    init { TvLinkManager.init(ctx) }
    /** Token of the saved TV [key] designates (any screen form, [PinKeys.resolve]); else the typed PIN stored under any key of that TV; else "". */
    fun get(key: String?): String = PinKeys.credential(TvLinkManager.credentialFor(key), key, key?.let { TvLinkManager.savedFor(it) }) { sp.getString(it, null) }
    /** The typed PIN only (never the token): found under [key] or under any other key of the same TV (tolerant read, old keys included). */
    fun pinOnly(key: String?): String = PinKeys.credential(null, key, key?.let { TvLinkManager.savedFor(it) }) { sp.getString(it, null) }
    /**
     * Writes [pin] under [key]; a TV with a token gets nothing under its IP keys (its token suffices), a PIN-only TV gets every key of the TV ([PinKeys.writeKeys]).
     * Still no erase on PIN_WRONG (w13-08).
     */
    fun put(key: String, pin: String) {
        val tv = TvLinkManager.savedFor(key)
        val keys = PinKeys.writeKeys(key, tv, hasToken = tv != null && TvLinkManager.credentialFor(key) != null)
        sp.edit().apply { keys.forEach { putString(it, pin) } }.apply()
    }
}

/** PIN entry for the selected TV; the value is remembered per TV as soon as it is well formed. A trusted phone does not need one. */
@Composable
fun PinField(store: PinStore, key: String?, pin: String, onPin: (String) -> Unit, modifier: Modifier = Modifier) {
    val trusted = TvAuth.isToken(pin)
    OutlinedTextField(
        if (trusted) "" else pin, { v ->
            val d = v.filter { it.isDigit() }.take(Pin.LENGTH)
            onPin(d)
            if (key != null && Pin.isValidFormat(d)) store.put(key, d)
        }, modifier, singleLine = true, enabled = key != null,
        label = { Text(if (trusted) "Ce téléphone est de confiance : aucun code nécessaire" else "PIN de la TV (affiché sur son écran)") },
        isError = !trusted && pin.isNotEmpty() && !Pin.isValidFormat(pin),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
}
