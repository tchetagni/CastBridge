package castbridge.sender

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import castbridge.core.remote.KeyAction
import castbridge.core.remote.RemoteKey

/**
 * « Télécommande » of CastBridge TV on the phone (docs/REMOTE.md). A screen of its own so that the phone's volume buttons can
 * drive the TV while it is open, and so the Quick Settings tile can open it directly. The link to the TV lives while the
 * screen is visible (RemoteController); leaving it closes the link (no battery or Bluetooth kept busy in the background).
 */
class RemoteActivity : ComponentActivity() {
    private val prefs by lazy { RemotePrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CastTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { RemoteScreen(onClose = ::finish) } } }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) RemoteController.disconnect()
    }

    private fun volumeKey(code: Int): RemoteKey? = when (code) {
        KeyEvent.KEYCODE_VOLUME_UP -> RemoteKey.VOLUME_UP
        KeyEvent.KEYCODE_VOLUME_DOWN -> RemoteKey.VOLUME_DOWN
        else -> null
    }

    /** Phone volume buttons = TV volume while this screen is open (option, on by default). The system repeats a held button. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val k = volumeKey(keyCode)
        if (k != null && prefs.volumeKeys && RemoteController.connected) { RemoteController.key(k, KeyAction.PRESS); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (volumeKey(keyCode) != null && prefs.volumeKeys && RemoteController.connected) return true
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        fun open(ctx: Context) = ctx.startActivity(Intent(ctx, RemoteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
