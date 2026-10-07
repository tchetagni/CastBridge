@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package castbridge.sender

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import castbridge.core.owner.ActivationRoutePlan
import castbridge.core.owner.ActivationRoutePlan.Cause
import castbridge.core.owner.ActivationRoutePlan.LineState
import castbridge.core.owner.ActivationRoutePlan.Phase
import castbridge.core.owner.ActivationRoutePlan.Route
import castbridge.core.owner.ActivationScreenState
import castbridge.core.owner.KeyAcquisition
import castbridge.core.owner.LockedRequestRoute
import castbridge.core.owner.ShareRequestPlan
import castbridge.core.quiz.QrCode
import castbridge.core.tv.TvClient
import castbridge.core.tv.WdCode
import castbridge.owner.SuperAdmin
import castbridge.owner.TvBluetooth

/** Garde la recherche de la TV et l'installation de la clé quand l'écran tourne ; rend tout (réseau demandé, fils) quand l'écran est quitté pour de bon. */
class ActivationViewModel(app: Application) : AndroidViewModel(app) {
    val discovery = TvDiscovery(app)
    val driver = ActivationDriver(app, discovery, SuperAdmin.enabled)
    override fun onCleared() { driver.release(); discovery.stop() }
}

/**
 * « Activer la TV » (docs/coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md) : UN champ, « Code affiché sur la TV » (6 chiffres). Une fois saisi, le téléphone cherche la TV tout seul, dans l'ordre :
 * réseau local, groupe Wi-Fi Direct dérivé du code (sans quitter l'app ni perdre les données mobiles), Bluetooth ; il lit la demande d'appareil de la TV, aide à obtenir la clé (console
 * propriétaire, clé collée ou trouvée dans le presse-papiers, fichier) puis l'installe, avec le même résultat que sur la TV. Les décisions sont dans `core` ([ActivationRoutePlan],
 * [KeyAcquisition]), l'exécution dans [ActivationDriver] ; cet écran dessine et relaie les gestes. Rien de secret ici : la clé est signée et liée à UNE TV, la TV la vérifie elle-même.
 */
class ActivateTvActivity : ComponentActivity() {
    private val vm: ActivationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CastTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Screen(vm) } } }
    }

    override fun onStart() { super.onStart(); vm.discovery.start() }
    override fun onStop() { super.onStop(); vm.discovery.stop() }

    /** À la reprise de l'écran (retour de WhatsApp avec la clé copiée) : le presse-papiers n'est lu que lorsque la TV est jointe et qu'Android donne le focus à l'écran. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboard()
    }

    private fun checkClipboard() {
        val ui = vm.driver.ui.value
        if (ui.run?.phase !is Phase.Connected || ui.acq.phase is KeyAcquisition.Phase.Installed) return
        // an unreadable clipboard (no window focus yet) says nothing: it never erases a proposal
        val text = readClipboard() ?: return
        vm.driver.clipboard(text)
    }

    private fun readClipboard(): String? = runCatching {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val d = cm.primaryClipDescription ?: return null
        if (!d.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) && !d.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) return null
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
    }.getOrNull()

    private fun copyRequest(text: String) {
        val clip = ClipData.newPlainText("Demande d'activation CastBridge-TV", text)
        if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    }

    /** « Partager la demande » : le TEXTE seul, ce que lisent les outils de l'agent (R-50 : WhatsApp envoie l'image et abandonne le texte d'un partage image + texte) ; ce que porte l'intention est décidé par [ShareRequestPlan]. */
    private fun shareRequest(plan: ShareRequestPlan.Send) = startActivity(ShareRequestIntents.chooser(plan))

    /** « Partager le code QR » : l'image du QR, SEULE (bouton à part, présent seulement quand le QR tient), avec l'autorisation de lecture sur l'intention et sur le sélecteur ; si l'image ne s'écrit pas, le dit et ne partage rien. */
    private fun shareQr(plan: ShareRequestPlan.Send) {
        val png = plan.image?.let { runCatching { writeQrPng(it) }.getOrNull() }
        if (png == null) { android.widget.Toast.makeText(this, ShareRequestPlan.QR_FAILED, android.widget.Toast.LENGTH_LONG).show(); return }
        startActivity(ShareRequestIntents.chooser(plan, png))
    }

    /** Le QR en PNG (noir sur blanc, marge de 4 modules, 10 pixels par module) dans le cache privé, rendu par le fournisseur de fichiers de l'application ; supprimé à la fermeture de l'écran. */
    private fun writeQrPng(q: QrCode): Uri {
        val quiet = 4; val px = 10; val n = q.size + 2 * quiet
        val bmp = android.graphics.Bitmap.createBitmap(n * px, n * px, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; style = android.graphics.Paint.Style.FILL }
        for (y in 0 until q.size) for (x in 0 until q.size) if (q[x, y]) canvas.drawRect(((x + quiet) * px).toFloat(), ((y + quiet) * px).toFloat(), ((x + quiet + 1) * px).toFloat(), ((y + quiet + 1) * px).toFloat(), paint)
        val dir = java.io.File(cacheDir, "activation_requests").apply { mkdirs() }
        val f = java.io.File(dir, "demande-appareil-castbridge-tv.png")
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return androidx.core.content.FileProvider.getUriForFile(this, "$packageName.activationshare", f)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { java.io.File(cacheDir, "activation_requests").deleteRecursively() }
    }

    /** Un fichier texte choisi dans le sélecteur du système : 256 Kio au plus, lu une fois, jamais gardé. */
    private fun readFile(uri: Uri, done: (String?, String?) -> Unit) {
        Thread {
            val r = runCatching {
                contentResolver.openInputStream(uri)?.use { s ->
                    val buf = java.io.ByteArrayOutputStream(); val chunk = ByteArray(8192); var total = 0
                    while (true) { val n = s.read(chunk); if (n < 0) break; total += n; if (total > KeyAcquisition.MAX_FILE_BYTES) return@runCatching null to KeyAcquisition.FILE_TOO_BIG; buf.write(chunk, 0, n) }
                    String(buf.toByteArray(), Charsets.UTF_8) to null
                }
            }.getOrNull()
            runOnUiThread { done(r?.first, r?.second) }
        }.start()
    }

    private fun openAddTv() {
        startActivity(Intent(this, MainActivity::class.java).putExtra(TvHomeRequest.EXTRA, TvHomeRequest.ADD_TV).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    private fun open(i: Intent) { runCatching { startActivity(i) } }

    @Composable private fun Screen(vm: ActivationViewModel) {
        val driver = vm.driver
        val cs = MaterialTheme.colorScheme
        val ui by driver.ui.collectAsState()
        val run = ui.run; val acq = ui.acq
        var codeText by rememberSaveable { mutableStateOf("") }
        var keyText by rememberSaveable { mutableStateOf("") }
        var fileNote by remember { mutableStateOf<String?>(null) }
        var askedNearby by remember { mutableStateOf(false) }
        var permissionTick by remember { mutableStateOf(0) }
        var pendingCode by remember { mutableStateOf<String?>(null) }
        val focus = remember { FocusRequester() }
        val found = (run?.phase as? Phase.Connected)?.found

        // permissions: « Appareils à proximité » (Bluetooth: finding the TV without pairing, Android 12+; the location before; and the Wi-Fi network of the TV, Android 13+) is asked ONCE when the sixth digit
        // is typed, with its sentence on screen above the system box ([ActivationRoutePlan.BLE_PERMISSION_WHY]); a button asks again after a refusal; nothing new in the manifest
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionTick++; pendingCode?.let { c -> pendingCode = null; driver.start(c) } }
        fun missingPermissions(): Array<String> {
            val bt = TvBluetooth.missingPermissions(this@ActivateTvActivity).toList()
            val nearby = if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES) != android.content.pm.PackageManager.PERMISSION_GRANTED) listOf(android.Manifest.permission.NEARBY_WIFI_DEVICES) else emptyList()
            return (bt + nearby).toTypedArray()
        }
        fun begin(code: String) {
            val missing = missingPermissions()
            if (missing.isNotEmpty() && !askedNearby) { askedNearby = true; pendingCode = code; ask.launch(missing) } else driver.start(code)
        }
        val permissionWhy = remember(permissionTick) {
            if (TvBluetooth.missingPermissions(this@ActivateTvActivity).isEmpty()) null
            else ActivationRoutePlan.BLE_PERMISSION_WHY + (if (Build.VERSION.SDK_INT < 31) ". " + ActivationRoutePlan.BLE_PERMISSION_WHY_LOCATION else "")
        }

        val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            fileNote = null
            if (uri == null) driver.fileText(null) else readFile(uri) { text, note -> if (note != null) fileNote = note else driver.fileText(text) }
        }
        val console = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            r.data?.getStringExtra(SuperAdmin.RESULT_KEY)?.let { driver.consoleKey(it) }
        }

        // what the linked TV itself says about its activation (read through the link the phone already has with it): an activation done any way is noticed
        val link by TvLinkManager.state.collectAsState()
        var tvView by remember { mutableStateOf<ActivationScreenState.View?>(null) }
        LaunchedEffect(link) {
            val s = (link as? LinkUi.Connected)?.session
            val base = s?.base
            if (base == null) { tvView = null; return@LaunchedEffect }
            while (true) {
                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { org.json.JSONObject(TvClient(base, s.credential).raw("GET", "/api/activation")) } }
                r.onSuccess { j ->
                    val info = ActivationScreenState.Info(j.optBoolean("required"), j.optBoolean("locked"), j.optBoolean("trial"), j.optString("label"), if (j.isNull("usageEndsAt")) null else j.optLong("usageEndsAt"))
                    tvView = ActivationScreenState.view(info, null, System.currentTimeMillis())
                }.onFailure { tvView = null }
                kotlinx.coroutines.delay(4_000)
            }
        }
        // the TV was just joined: a key may already wait in the clipboard (proposed, never installed without a touch)
        LaunchedEffect(found?.route, found?.name) { if (found != null) checkClipboard() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

        Scaffold(containerColor = cs.background, topBar = {
            TopAppBar(title = { Text(castbridge.core.ux.UxLabels.ACTIVATE_TV, maxLines = 1) },
                navigationIcon = { IconButton({ finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Fermer") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface))
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tvView?.let { v ->
                    Text(v.headline, color = if (v.good) cs.primary else cs.onSurface, style = MaterialTheme.typography.titleMedium)
                    v.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }

                // ---- 1. le code
                OutlinedTextField(
                    codeText,
                    { v ->
                        val d = v.filter(Char::isDigit).take(WdCode.DIGITS)
                        if (d != codeText) { codeText = d; if (d.length == WdCode.DIGITS) begin(d) else driver.cancel() }
                    },
                    label = { Text("Code affiché sur la TV") }, singleLine = true,
                    textStyle = TextStyle(fontSize = 34.sp, fontFamily = FontFamily.Monospace, letterSpacing = 8.sp, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    supportingText = { Text("6 chiffres, affichés en gros sur l'écran d'activation de CastBridge-TV.") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
                // act-bt: the sentence that goes with the permission box, shown once on screen as long as the permission is missing
                permissionWhy?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }

                if (run != null && run.phase !is Phase.Idle) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(run.headline().takeIf { run.phase !is Phase.Failed } ?: "La TV n'a pas été jointe.", style = MaterialTheme.typography.titleSmall,
                            color = if (run.phase is Phase.Failed) cs.error else cs.onSurface)
                        // an ended search says everything in its one message (a line per route with its cause); the lines are for a search that is going on or succeeded
                        if (run.phase !is Phase.Failed) run.lines().filter { it.state != LineState.UNUSED }.forEach { RouteLine(it) }
                    }
                    when (val ph = run.phase) {
                        is Phase.Trying -> {
                            // le réseau de la TV est introuvable : « Réessayer » tout de suite (OK sur la TV, puis ce geste : un nouveau plan, jamais une boucle), sans attendre la fin du Bluetooth
                            if (run.retryOffered()) Button({ begin(codeText) }, enabled = codeText.length == WdCode.DIGITS, modifier = Modifier.fillMaxWidth()) { Text("Réessayer") }
                            OutlinedButton({ driver.cancel(); codeText = "" }, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
                        }
                        is Phase.ManualJoin -> {
                            Text("Sur Android 9 et moins, l'application ne peut pas rejoindre ce réseau seule : ouvrez les réglages Wi-Fi, rejoignez « ${ph.ssid} » avec le mot de passe ci-dessus, puis revenez ici.", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton({ open(Intent(Settings.ACTION_WIFI_SETTINGS)) }) { Text("Réglages Wi-Fi") }
                                Button({ driver.userJoined() }) { Text("C'est fait") }
                            }
                            TextButton({ driver.skipManual() }) { Text("Passer au Bluetooth") }
                        }
                        is Phase.Failed -> {
                            Text(ph.message, style = MaterialTheme.typography.bodyMedium, color = cs.error)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button({ begin(codeText) }, enabled = codeText.length == WdCode.DIGITS) { Text("Réessayer") }
                                val causes = run.causes.values.map { it.kind }
                                if (Cause.Kind.BT_PERMISSION in causes || Cause.Kind.GROUP_PERMISSION in causes || Cause.Kind.BLE_PERMISSION in causes)
                                    OutlinedButton({ askedNearby = false; pendingCode = codeText.takeIf { it.length == WdCode.DIGITS }; ask.launch(missingPermissions()) }) { Text("Autoriser") }
                                if (Cause.Kind.WIFI_OFF in causes) OutlinedButton({ open(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS)) }) { Text("Allumer le Wi-Fi") }
                                if (Cause.Kind.BT_OFF in causes) OutlinedButton({ open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Allumer le Bluetooth") }
                            }
                        }
                        else -> {}
                    }
                }

                // ---- 2. la TV trouvée et sa demande d'appareil
                if (found != null) {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val req = found.request
                            Text("TV trouvée : ${found.name}" + (req?.let { " · code d'appareil ${it.code}" } ?: ""), style = MaterialTheme.typography.titleMedium)
                            // l'adresse de la TV qui a répondu (réseau local) : à comparer avec « TV 192.168… » sur l'écran d'activation de la TV (audit I-4)
                            Text("Jointe par : ${found.route.label}" + (found.address()?.let { " · TV $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            if (req != null) {
                                Text("Vérifiez que ce code d'appareil est bien celui de l'écran d'activation de la TV.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                val share = LockedRequestRoute.shareText(req)
                                val qr = remember(share) { LockedRequestRoute.qr(req) }
                                // R-50 : le texte part seul ; le code QR part par un bouton à part, qui n'existe que quand le QR tient (ShareRequestPlan)
                                val textShare = remember(share) { ShareRequestPlan.request(LockedRequestRoute.SHARE_SUBJECT, share) }
                                val qrShare = remember(share, qr) { ShareRequestPlan.qr(LockedRequestRoute.SHARE_SUBJECT, share, qr) }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button({ shareRequest(textShare) }) { Text(ShareRequestPlan.REQUEST_BUTTON) }
                                    qrShare?.let { q -> OutlinedButton({ shareQr(q) }) { Text(ShareRequestPlan.QR_BUTTON) } }
                                    OutlinedButton({ copyRequest(share) }) { Text(ShareRequestPlan.COPY_BUTTON) }
                                }
                                Text("À envoyer à l'agent (WhatsApp, e-mail) pour recevoir la clé de cette TV.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                if (qr != null) {
                                    QrView(qr, Modifier.fillMaxWidth(0.7f).align(Alignment.CenterHorizontally))
                                    Text("Ou montrez ce QR à l'agent : il contient la même demande.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                            } else {
                                Text("La demande d'appareil de cette TV n'a pas pu être lue.", style = MaterialTheme.typography.bodyMedium)
                                if (found.route != Route.BLUETOOTH) OutlinedButton({ driver.readRequestByBluetooth() }, enabled = !ui.reading) { Text(if (ui.reading) "Lecture…" else "Lire la demande par Bluetooth") }
                            }
                            run?.hint()?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.error) }
                            ui.btNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error) }
                        }
                    }

                    // ---- 3. la clé
                    when (val ph = acq.phase) {
                        is KeyAcquisition.Phase.Installed -> ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (ph.staged) "Clé reçue" else KeyAcquisition.NOTICE_TITLE, style = MaterialTheme.typography.titleMedium, color = cs.primary)
                                Text(ph.text, style = MaterialTheme.typography.bodyMedium)
                                if (acq.offerAddTv) {
                                    Text("Pour retrouver cette TV sans code (copier, lire, télécommande), ajoutez-la à CastBridge.", style = MaterialTheme.typography.bodySmall)
                                    Button({ openAddTv() }, modifier = Modifier.fillMaxWidth()) { Text("Ajouter ma TV") }
                                }
                                OutlinedButton({ finish() }, modifier = Modifier.fillMaxWidth()) { Text("Terminer") }
                            }
                        }
                        else -> ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Clé d'activation", style = MaterialTheme.typography.titleMedium)
                                if (driver.consolePresent) {
                                    Button({ console.launch(SuperAdmin.intentForActivation(this@ActivateTvActivity, req(found))) }, modifier = Modifier.fillMaxWidth()) { Text("Émettre et installer") }
                                    Text("La console propriétaire de ce téléphone émet la clé pour cette TV, puis l'installe.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                                acq.clipboard?.let { c ->
                                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer)) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Une clé d'activation est dans le presse-papiers.", style = MaterialTheme.typography.titleSmall)
                                            Text(KeyAcquisition.describe(c.check, found.request), style = MaterialTheme.typography.bodySmall)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button({ driver.acceptClipboard() }) { Text("Installer cette clé") }
                                                TextButton({ driver.dismissClipboard() }) { Text("Ignorer") }
                                            }
                                        }
                                    }
                                }
                                OutlinedTextField(keyText, { keyText = it; fileNote = null; driver.paste(it) }, label = { Text("Coller la clé reçue") },
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp), minLines = 3, modifier = Modifier.fillMaxWidth())
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedButton({
                                        val t = readClipboard().orEmpty()
                                        if (t.isBlank()) fileNote = "Le presse-papiers est vide : copiez d'abord la clé." else { keyText = t; fileNote = null; driver.paste(t) }
                                    }) { Text("Coller") }
                                    OutlinedButton({ fileNote = null; pickFile.launch(arrayOf("*/*")) }) { Text("Choisir un fichier") }
                                }
                                (fileNote ?: acq.message)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (fileNote != null || acq.pick?.installable == false) cs.error else cs.onSurface) }
                                when (ph) {
                                    is KeyAcquisition.Phase.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Installation de la clé sur la TV…")
                                    }
                                    is KeyAcquisition.Phase.Failed -> {
                                        Text(ph.text, style = MaterialTheme.typography.bodyMedium, color = cs.error)
                                        if (ph.codeRefused) OutlinedButton({ codeText = ""; driver.cancel(); runCatching { focus.requestFocus() } }) { Text("Changer le code") }
                                    }
                                    else -> {}
                                }
                                Button({ driver.install() }, enabled = acq.canInstall(), modifier = Modifier.fillMaxWidth()) { Text("Installer la clé") }
                                if (acq.serverEnabled) {
                                    OutlinedButton({ driver.serverRequest() }, enabled = acq.server is KeyAcquisition.Server.Idle || acq.server is KeyAcquisition.Server.Failed, modifier = Modifier.fillMaxWidth()) { Text(KeyAcquisition.SERVER_BUTTON) }
                                    Text(KeyAcquisition.serverLine(acq.server), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // a key already chosen waits for the TV (the link dropped, or the screen was reopened): it is installed as soon as the TV is joined again
                if (found == null && acq.pick != null && acq.confirmed)
                    Text("Une clé est prête : elle sera installée dès que la TV sera de nouveau jointe (saisissez son code).", style = MaterialTheme.typography.bodyMedium, color = cs.primary)

                Text(castbridge.core.tunnel.TunnelTerms.TITLE + " (" + castbridge.core.tunnel.TunnelTerms.VERSION + ")", style = MaterialTheme.typography.titleSmall)
                Text(castbridge.core.tunnel.TunnelTerms.PHONE_NOTE, style = MaterialTheme.typography.bodySmall)
                Text(castbridge.core.tunnel.TunnelTerms.TEXT, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    /** La demande à pré-remplir dans la console propriétaire : complète (avec la clé d'installation si la TV l'a donnée), sinon rien. */
    private fun req(f: ActivationRoutePlan.Found): String? = f.request?.fullText()

    @Composable private fun RouteLine(l: ActivationRoutePlan.Line) {
        val cs = MaterialTheme.colorScheme
        val (glyph, color) = when (l.state) {
            LineState.DONE -> "✓" to cs.primary
            LineState.FAILED -> "✗" to cs.error
            LineState.SKIPPED -> "–" to cs.onSurfaceVariant
            LineState.ACTIVE -> "…" to cs.onSurface
            LineState.ASKING -> "▸" to cs.onSurface
            LineState.WAITING, LineState.UNUSED -> "·" to cs.onSurfaceVariant
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(glyph, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
            Text(l.text, color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }

    /** Le QR, noir sur blanc avec sa marge de 4 modules, lisible dans le thème sombre aussi. */
    @Composable private fun QrView(q: QrCode, modifier: Modifier) {
        Canvas(modifier.aspectRatio(1f).background(Color.White).semantics { contentDescription = "QR de la demande d'appareil de la TV" }) {
            val quiet = 4; val n = q.size + 2 * quiet; val cell = size.width / n
            for (y in 0 until q.size) for (x in 0 until q.size) if (q[x, y]) drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }

    companion object { fun open(ctx: Context) = ctx.startActivity(Intent(ctx, ActivateTvActivity::class.java)) }
}
