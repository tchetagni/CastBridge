package castbridge.sender.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import castbridge.core.tv.TvClient
import castbridge.sender.CastTheme

/** Debug builds only: the library assistant opened on a TV given by intent extras (base = http://host:port, pin). */
class AssistantDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val base = intent.getStringExtra("base")
        val pin = intent.getStringExtra("pin")
        setContent {
            CastTheme { LibraryAssistantDialog(base?.let { TvClient(it, pin) }) { finish() } }
        }
    }
}
