package castbridge.sender

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import castbridge.core.remote.OpenTvTexts
import kotlinx.coroutines.delay

/** Combien de temps la ligne du résultat reste affichée. */
private const val LINE_MS = 8_000L

/**
 * Le bouton « Ouvrir sur la TV » (icône TV) de l'onglet « CastBridge TV », avec la ligne du résultat dessous. CastBridge-TV déjà devant : [onAlready] (l'accueil ouvre la télécommande).
 */
@Composable
fun OpenTvButton(modifier: Modifier = Modifier, onAlready: () -> Unit = {}) {
    val ctx = LocalContext.current
    val st by OpenTv.state.collectAsState()
    val working = st is OpenTv.State.Working
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledTonalButton(onClick = { OpenTv.open(ctx) { o -> if (o.alreadyFront) onAlready() } }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
            if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.Tv, null)
            Spacer(Modifier.width(8.dp))
            Text(if (working) OpenTvTexts.WORKING else OpenTvTexts.BUTTON)
        }
        OpenTvLine()
    }
}

/** La touche « TV » de la rangée système de la télécommande (comme un bouton YouTube ou Netflix) : même ronde que les autres touches, un toucher. */
@Composable
fun OpenTvKey(size: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val cs = MaterialTheme.colorScheme
    val st by OpenTv.state.collectAsState()
    val working = st is OpenTv.State.Working
    Box(
        modifier.size(size.dp).clip(CircleShape).background(if (working) cs.primary.copy(alpha = 0.45f) else cs.primaryContainer)
            .clickable(enabled = !working, role = Role.Button) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); OpenTv.open(ctx) }
            .semantics { contentDescription = OpenTvTexts.KEY_DESCRIPTION },
        contentAlignment = Alignment.Center,
    ) {
        if (working) CircularProgressIndicator(Modifier.size((size * 0.45f).dp), strokeWidth = 2.dp)
        else Text(OpenTvTexts.KEY, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onPrimaryContainer, textAlign = TextAlign.Center)
    }
}

/** UNE ligne : « CastBridge-TV est à l'écran », « La TV ne répond pas : allumez-la… » ; rouge seulement pour un problème ; disparaît seule. Annoncée par TalkBack. */
@Composable
fun OpenTvLine(modifier: Modifier = Modifier) {
    val st by OpenTv.state.collectAsState()
    val done = st as? OpenTv.State.Done
    // an old result is never shown again when the screen is entered later (the line lives LINE_MS from the moment it was said)
    var visible by remember(done?.at) { mutableStateOf(done != null && System.currentTimeMillis() - done.at < LINE_MS) }
    LaunchedEffect(done?.at) {
        if (done != null) { val left = LINE_MS - (System.currentTimeMillis() - done.at); if (left > 0) delay(left); visible = false }
    }
    val cs = MaterialTheme.colorScheme
    if (done != null && visible) Text(done.outcome.line, modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = if (done.outcome.problem) cs.error else cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
}
