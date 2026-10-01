package castbridge.sender

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

/**
 * « Contrôle parental » on the phone: a screen of its own (docs/PARENTAL.md), opened from the shield in the app bar. Same rules and API
 * as on the TV. Hidden from screenshots and the recent-apps preview (the parental PIN is typed here); the PIN is forgotten when the
 * screen closes.
 */
class ParentalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent { CastTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { ParentalScreen(onClose = ::finish) } } }
    }

    companion object {
        fun open(ctx: Context) = ctx.startActivity(Intent(ctx, ParentalActivity::class.java))
    }
}
