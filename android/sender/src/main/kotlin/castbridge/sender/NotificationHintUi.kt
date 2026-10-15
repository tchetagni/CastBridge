package castbridge.sender

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import castbridge.core.tv.NotificationAccessPolicy
import castbridge.core.ux.NotificationHint

/**
 * R-52 (« les copies ne signalent plus en notification ») : quand Android jette les notifications de CastBridge (permission POST_NOTIFICATIONS absente, ou un canal de copie coupé), la
 * carte d'envoi et la file d'attente de l'accueil le DISENT, en rouge, avec un bouton « Autoriser » ; rien n'est affiché quand tout va bien. Le texte, le bouton et la décision
 * (dialogue d'Android ou réglage de l'app) viennent de la règle pure [NotificationHint] ; ici on lit l'état d'Android et on dessine. L'état est relu à chaque retour au premier plan
 * (l'usager revient du réglage) et après la réponse du dialogue.
 */
@Composable
fun NotificationHintRow() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (!granted) PhoneNotificationState.noteDenied(ctx); tick++ }
    val state = remember(tick) { PhoneNotificationState.read(ctx) }
    val text = NotificationHint.text(state.enabled, state.blockedChannels) ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = {
            if (PhoneNotificationState.action(ctx, state.enabled) == NotificationAccessPolicy.Action.ASK) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            else PhoneNotificationState.openSettings(ctx)
        }) { Text(NotificationHint.BUTTON) }
    }
}

/** Ce que Android dit des notifications de CastBridge, et ce que fait « Autoriser ». Les décisions sont celles de [NotificationHint] / [NotificationAccessPolicy] (purs, testés en JVM). */
internal object PhoneNotificationState {
    class State(val enabled: Boolean, val blockedChannels: List<String>)

    private const val PREFS = "castbridge_notifications"
    /** Android a déjà répondu « refusé » à POST_NOTIFICATIONS : sans cette mémoire on ne distingue pas « jamais demandé » de « refus définitif » (même `shouldShowRequestPermissionRationale` faux). */
    private const val DENIED = "denied"

    /** L'app est-elle autorisée à notifier, et quels canaux de copie l'usager a-t-il coupés un à un (leurs noms lisibles) ? */
    fun read(ctx: Context): State {
        val nm = NotificationManagerCompat.from(ctx)
        val enabled = runCatching { nm.areNotificationsEnabled() }.getOrDefault(true)          // illisible : on ne crie pas à tort
        val blocked = if (!enabled) emptyList() else NotificationHint.COPY_CHANNELS.mapNotNull { (id, name) ->
            name.takeIf { runCatching { nm.getNotificationChannel(id)?.importance == NotificationManager.IMPORTANCE_NONE }.getOrDefault(false) }
        }
        return State(enabled, blocked)
    }

    /** À appeler quand le dialogue d'Android a été refusé (le sien, ou celui de l'onglet « TV DLNA » dans [MainActivity]). */
    fun noteDenied(ctx: Context) { runCatching { ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(DENIED, true).apply() } }

    private fun deniedBefore(ctx: Context): Boolean =
        runCatching { ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(DENIED, false) }.getOrDefault(false)

    private fun Context.findActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.findActivity(); else -> null }

    /** Demander l'autorisation (dialogue d'Android), ou ouvrir le réglage des notifications de l'app. [appEnabled] faux = l'app entière est coupée ; vrai = seuls des canaux le sont. */
    fun action(ctx: Context, appEnabled: Boolean): NotificationAccessPolicy.Action {
        val sdk = Build.VERSION.SDK_INT
        val rationale = sdk >= 33 && ctx.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
        return NotificationHint.buttonAction(sdk, appEnabled, rationale, deniedBefore(ctx))
    }

    /** Le réglage des notifications de CastBridge ; à défaut (aucun écran de ce nom) les détails de l'app. Jamais d'exception. */
    fun openSettings(ctx: Context) {
        val pkg = ctx.packageName
        val tries = listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")),
        )
        val host = ctx.findActivity()
        tries.any { i -> runCatching { if (host != null) host.startActivity(i) else ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
    }
}
