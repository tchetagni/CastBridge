package castbridge.sender

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** « Appareils à proximité » (Android 12+): connecting and scanning. Before Android 12 Bluetooth needs no runtime permission here. */
val BT_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN) else emptyArray()

private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

/**
 * Bluetooth permission state that stays right: re-checked every time the app comes back to the front (after the user
 * changed it in the settings), and a way out when Android no longer shows the request (refused twice).
 * Returns (granted, UI to show when not granted).
 */
@Composable
fun rememberBtPermission(): Pair<Boolean, @Composable () -> Unit> {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasBtPermission(ctx)) }
    var blocked by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = hasBtPermission(ctx) }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = hasBtPermission(ctx)
        // Refused and Android will not ask again: only the app settings can change it now.
        val act = ctx.activity()
        blocked = !granted && act != null && BT_PERMISSIONS.none { act.shouldShowRequestPermissionRationale(it) } && r.isNotEmpty()
    }
    val ui: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (blocked) "Le Bluetooth est bloqué pour CastBridge. Ouvrez les réglages de l'app › Autorisations › « Appareils à proximité » › Autoriser."
                else "CastBridge a besoin de l'autorisation « Appareils à proximité » pour parler à la TV en Bluetooth.",
                style = MaterialTheme.typography.bodyMedium, color = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            // R-14: the automatic Wi-Fi Direct's permission (Android 13+) belongs to the same « Appareils à proximité » group: asked with it, one answer
            if (!blocked) Button(onClick = { ask.launch(BT_PERMISSIONS + listOfNotNull(castbridge.core.link.WdJoin.permission(Build.VERSION.SDK_INT))) }) { Text("Autoriser le Bluetooth") }
            OutlinedButton(onClick = {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text("Ouvrir les réglages de l'app") }
        }
    }
    return (granted || BT_PERMISSIONS.isEmpty()) to ui
}
