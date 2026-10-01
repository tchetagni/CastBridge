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
 * « Contrôle parental » on the phone, opened from the padlock in the app bar: the same content as the « Parental » tab ([ParentalTab], session
 * and PIN shared with it). Hidden from screenshots and the recent-apps preview (the parental PIN is typed here). The TV's rules editor
 * ([ParentalScreen]) stays available from Réglages.
 */
class ParentalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent { CastTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { ParentalTab(onClose = ::finish) } } }
    }

    override fun onStart() { super.onStart(); ParentalSession.onForeground() }
    override fun onStop() { super.onStop(); ParentalSession.onBackground() }

    companion object {
        fun open(ctx: Context) = ctx.startActivity(Intent(ctx, ParentalActivity::class.java))
    }
}
