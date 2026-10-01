package castbridge.owner

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.SuperAdminGate
import castbridge.ownerlib.BuildConfig

/** What the phone apps need to know: is the hidden entry part of this build, and how to open it. */
object SuperAdmin {
    /** False when the build carries no valid bcrypt hash: the gesture must not even react (nothing is revealed). */
    val enabled: Boolean get() = SuperAdminGate(BuildConfig.SUPERADMIN_BCRYPT, castbridge.core.owner.UnlockGuard({ 0L })).enabled
    fun open(ctx: Context) = ctx.startActivity(Intent(ctx, SuperAdminActivity::class.java))
}

/**
 * The « Super administration » screen of the phone apps (opened by the hidden gesture). One password: checked against the build's bcrypt hash (local, no server),
 * then used to open this phone's vault (created on first use). The vault key is what gives the power; this door only gates the screen.
 */
class SuperAdminActivity : ConsoleActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        if (!SuperAdmin.enabled) { super.onCreate(savedInstanceState); finish(); return }   // a build without hash has no such screen
        super.onCreate(savedInstanceState)
    }

    @Composable override fun Entry(hasVault: Boolean, onSigner: (Ed25519Signer) -> Unit) {
        val gate = remember { SuperAdminGate(BuildConfig.SUPERADMIN_BCRYPT, store.guard) }
        var code by remember { mutableStateOf("") }; var msg by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Super administration", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(code, { code = it }, label = { Text("Mot de passe") }, visualTransformation = PasswordVisualTransformation(), singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button({
                val c = code.toCharArray(); code = ""; busy = true; msg = null
                Thread {
                    val r = gate.attempt(c)
                    var signer: Ed25519Signer? = null; var note: String? = null
                    when (r) {
                        is SuperAdminGate.Result.Open -> {
                            // the same password opens (or, the first time, creates) this phone's own key: the vault is the real gate to the powers
                            signer = try { if (store.hasVault()) store.unlock(c) else store.create(c) } catch (e: OwnerStore.Locked) { null }
                            if (signer == null) note = "Le coffre de ce téléphone est protégé par un autre code."
                        }
                        is SuperAdminGate.Result.Locked -> note = "Trop d'essais : réessayez dans ${(r.waitMs + 999) / 1000} s."
                        is SuperAdminGate.Result.Wrong -> note = "Mot de passe incorrect."
                        is SuperAdminGate.Result.Disabled -> note = "Indisponible."
                    }
                    c.fill('0')
                    runOnUiThread { busy = false; if (signer != null) { lastUse = System.currentTimeMillis(); onSigner(signer) } else msg = note }
                }.start()
            }, enabled = !busy && code.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Vérification…" else "Ouvrir") }
        }
    }
}
