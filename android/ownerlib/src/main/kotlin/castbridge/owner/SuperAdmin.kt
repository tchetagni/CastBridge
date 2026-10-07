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

    /** « Émettre et installer » (écran « Activer la TV ») : la demande d'appareil lue sur la TV, à pré-remplir dans l'écran « Activer » de la console. */
    const val EXTRA_DEVICE_REQUEST = "castbridge.owner.device_request"
    /** La console a été ouverte par l'écran « Activer la TV » : une fois la clé générée, « Installer sur la TV » la lui rend ([RESULT_KEY]) au lieu de la laisser sur place. */
    const val EXTRA_RETURN_KEY = "castbridge.owner.return_key"
    /** La clé (jeton `cbx1`) rendue à l'écran appelant, par `setResult` : le propriétaire a déjà touché « Installer sur la TV ». */
    const val RESULT_KEY = "castbridge.owner.key"

    /** L'intention de l'écran « Activer la TV » : la même console, ouverte pré-remplie ; le mot de passe reste demandé à chaque fois. */
    fun intentForActivation(ctx: Context, deviceRequest: String?): Intent =
        Intent(ctx, SuperAdminActivity::class.java).putExtra(EXTRA_RETURN_KEY, true).apply { if (deviceRequest != null) putExtra(EXTRA_DEVICE_REQUEST, deviceRequest) }
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
