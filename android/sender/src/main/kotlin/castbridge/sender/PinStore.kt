package castbridge.sender

import android.content.Context
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import castbridge.core.tv.Pin

/** Remembers the PIN of each TV (keyed by TV name or manual address). Private SharedPreferences. */
class PinStore(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("castbridge_pins", Context.MODE_PRIVATE)
    fun get(key: String?): String = key?.let { sp.getString(it, null) }.orEmpty()
    fun put(key: String, pin: String) = sp.edit().putString(key, pin).apply()
}

/** PIN entry for the selected TV; the value is remembered per TV as soon as it is well formed. */
@Composable
fun PinField(store: PinStore, key: String?, pin: String, onPin: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        pin, { v ->
            val d = v.filter { it.isDigit() }.take(Pin.LENGTH)
            onPin(d)
            if (key != null && Pin.isValidFormat(d)) store.put(key, d)
        }, modifier, singleLine = true, enabled = key != null,
        label = { Text("PIN de la TV (affiché sur son écran)") },
        isError = pin.isNotEmpty() && !Pin.isValidFormat(pin),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
}
