package castbridge.owner

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import castbridge.core.lots.Right
import castbridge.core.lots.RentalKeys
import castbridge.core.lots.RentalLines
import castbridge.core.lots.RentalDurations
import castbridge.core.lots.BundleCatalog
import castbridge.core.owner.*
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * « CastBridge Propriétaire » : generates the activations from what a TV shows (docs/OWNER-CONSOLE.md). Separate app, never published, no permission.
 * Hidden from screenshots and the recent-apps preview; locks itself after 2 minutes without use and when it leaves the screen.
 */
open class ConsoleActivity : ComponentActivity() {
    protected lateinit var store: OwnerStore
    protected val signer = mutableStateOf<Ed25519Signer?>(null)
    protected var lastUse = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        store = OwnerStore(this)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { Screen() } } }
    }

    override fun onStop() { super.onStop(); signer.value = null }     // leaving the screen locks the console
    override fun onUserInteraction() { super.onUserInteraction(); lastUse = System.currentTimeMillis() }

    @Composable protected fun Screen() {
        var hasVault by remember { mutableStateOf(store.hasVault()) }
        LaunchedEffect(signer.value) {                                      // auto-lock after 2 minutes of no use
            while (signer.value != null) { kotlinx.coroutines.delay(5_000); if (System.currentTimeMillis() - lastUse > AUTO_LOCK_MS) signer.value = null }
        }
        val s = signer.value
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("CastBridge Propriétaire", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (s != null) TextButton({ signer.value = null }) { Text("Verrouiller") }
            }
            Spacer(Modifier.height(8.dp))
            when {
                s != null -> Console(s)
                else -> Entry(hasVault) { signer.value = it; hasVault = true }
            }
        }
    }

    /** What stands in front of the console: by default the vault's own screens (create the key, unlock with its code). The hidden entry of the phone apps overrides it. */
    @Composable protected open fun Entry(hasVault: Boolean, onSigner: (Ed25519Signer) -> Unit) {
        if (!hasVault) Create(onSigner) else Unlock(onSigner)
    }

    @Composable protected fun Create(onDone: (Ed25519Signer) -> Unit) {
        var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }; var msg by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Première utilisation : créez la clé de signature de ce téléphone.", style = MaterialTheme.typography.titleMedium)
            Text("Cette clé est propre au téléphone (une clé par outil). Le code que vous choisissez la protège : il n'est jamais enregistré. " +
                "Choisissez un code NEUF et long (12 caractères ou plus). Si vous le perdez, la clé est perdue.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(a, { a = it }, label = { Text("Code de déverrouillage") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(b, { b = it }, label = { Text("Confirmez le code") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button({
                when {
                    a.length < 12 -> msg = "Code trop court : 12 caractères au moins."
                    a != b -> msg = "Les deux codes diffèrent."
                    else -> { busy = true; msg = null; Thread { val r = store.create(a.toCharArray()); runOnUiThread { busy = false; if (r != null) onDone(r) else msg = "Une clé existe déjà." } }.start() }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Création…" else "Créer la clé") }
        }
    }

    @Composable protected fun Unlock(onDone: (Ed25519Signer) -> Unit) {
        var code by remember { mutableStateOf("") }; var msg by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Console verrouillée", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(code, { code = it }, label = { Text("Code de déverrouillage") }, visualTransformation = PasswordVisualTransformation(),
                singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button({
                busy = true; msg = null
                val c = code.toCharArray(); code = ""
                Thread {
                    val r = try { store.unlock(c) } catch (e: OwnerStore.Locked) { runOnUiThread { busy = false; msg = "Trop d'essais : réessayez dans ${(e.waitMs + 999) / 1000} s." }; return@Thread }
                    c.fill('0')
                    runOnUiThread { busy = false; if (r != null) { lastUse = System.currentTimeMillis(); onDone(r) } else msg = "Code incorrect." }
                }.start()
            }, enabled = !busy && code.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Vérification…" else "Déverrouiller") }
        }
    }

    @Composable private fun Console(signer: Ed25519Signer) {
        var tab by remember { mutableIntStateOf(0) }
        TabRow(tab) { listOf("Activer", "Clé publique", "Journal").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
        Spacer(Modifier.height(8.dp))
        when (tab) { 0 -> Issue(signer); 1 -> PublicKey(); else -> Journal() }
    }

    @Composable private fun Issue(signer: Ed25519Signer) {
        var input by remember { mutableStateOf("") }
        var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
        if (field.text != input) field = androidx.compose.ui.text.input.TextFieldValue(input, androidx.compose.ui.text.TextRange(input.length))      // set from outside (Bluetooth read)
        var form by remember { mutableStateOf(ProductionForm()) }
        var catalogInfo by remember { mutableStateOf(store.catalog()) }
        var catalogMsg by remember { mutableStateOf<String?>(null) }
        val importCatalog = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) runCatching { store.saveCatalog(contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }) }
                .onSuccess { n -> catalogInfo = store.catalog(); form = form.copy(catalog = catalogInfo?.first, purchaseBundles = emptySet(), subscriptionBundles = emptySet(), rentalBundles = emptySet()); catalogMsg = "Catalogue importé : $n bouquets." }
                .onFailure { catalogMsg = it.message ?: "Import impossible" }
        }
        if (form.catalog == null && catalogInfo != null) form = form.copy(catalog = catalogInfo!!.first)
        var token by remember { mutableStateOf<String?>(null) }; var fileContent by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<String?>(null) }; var info by remember { mutableStateOf<String?>(null) }
        val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) runCatching { contentResolver.openOutputStream(uri)?.use { it.write((fileContent ?: "").toByteArray()) } }
                .onSuccess { info = "Fichier « activation » enregistré : copiez-le dans Download/CastBridge de la clé USB de la TV." }.onFailure { error = "Enregistrement impossible : ${it.message}" }
        }
        var tvs by remember { mutableStateOf(TvBluetooth.pairedTvs(this@ConsoleActivity)) }; var chosen by remember { mutableStateOf<TvBluetooth.Tv?>(null) }
        var btBusy by remember { mutableStateOf(false) }; var btMsg by remember { mutableStateOf<String?>(null) }
        val askBt = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tvs = TvBluetooth.pairedTvs(this@ConsoleActivity) }
        fun onBt(work: () -> String?) { btBusy = true; btMsg = null
            Thread { val r = runCatching(work).fold({ it }, { it.message ?: "Échec" }); runOnUiThread { btMsg = r; btBusy = false } }.start() }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TV en Bluetooth (sans rien saisir)", style = MaterialTheme.typography.titleSmall)
                if (!TvBluetooth.permitted(this@ConsoleActivity)) {
                    Text("Autorisez le Bluetooth pour voir vos TV appairées.", style = MaterialTheme.typography.bodySmall)
                    Button({ askBt.launch(TvBluetooth.missingPermissions(this@ConsoleActivity)) }) { Text("Autoriser") }
                } else if (tvs.isEmpty()) Text("Aucun appareil appairé : appairez d'abord la TV (réglages Bluetooth du téléphone).", style = MaterialTheme.typography.bodySmall)
                else {
                    tvs.forEach { tv -> FilterChip(chosen?.address == tv.address, { chosen = tv; btMsg = null }, { Text(tv.name) }) }
                    Button({ val tv = chosen ?: return@Button
                        onBt { TvBluetooth.with(this@ConsoleActivity, tv) { c -> c.deviceInfo() }?.let { info -> runOnUiThread { input = info; token = null; error = null }; "Demande lue : code de la TV rempli ci-dessous. Choisissez Essai ou Production, puis Générer." } ?: "La TV n'a pas donné son code" }
                    }, enabled = chosen != null && !btBusy, modifier = Modifier.fillMaxWidth()) { Text("1. Lire le code de la TV") }
                    Button({ val tv = chosen ?: return@Button; val t = token ?: return@Button
                        onBt { TvBluetooth.with(this@ConsoleActivity, tv) { c -> c.sendActivation(t) }.let { a -> if (a.ok) { store.journal("envoi-bt", tv.name, "-", "-", 0); "TV activée par Bluetooth." } else "Refusée par la TV : ${a.message}" } }
                    }, enabled = chosen != null && token != null && !btBusy, modifier = Modifier.fillMaxWidth()) { Text("3. Envoyer l'activation à la TV") }
                    Text("2. Entre les deux : « Générer » (plus bas).", style = MaterialTheme.typography.bodySmall)
                }
                btMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            } }
            OutlinedTextField(field, { v ->
                // the cursor stays where the user put it; only when a dash had to be added or the text changed shape does it go to the end
                val t = castbridge.core.owner.DeviceCode.typing(v.text)
                field = if (t == v.text) v else androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(t.length)); input = t; token = null; error = null }, label = { Text("Code d'appareil (XXXX-XXXX-XXXX-XXXX) ou demande d'appareil complète") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
            val production = form.production
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!production, { form = ProductionForm.withProduction(form, false); token = null }, { Text("Essai") }); FilterChip(production, { form = ProductionForm.withProduction(form, true); token = null }, { Text("Production") })
            }
            if (!(production && form.superUnlimited)) {
                Text(if (production) "Durée de la clé" else "Durée de la clé d'essai (toujours bornée)", style = MaterialTheme.typography.titleSmall)
                @OptIn(ExperimentalLayoutApi::class) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProductionForm.options(production).forEach { o -> FilterChip(form.durationChoice == o, { form = form.copy(durationChoice = o); token = null }, { Text(ProductionForm.label(o)) }) }
                }
                if (form.durationChoice == ProductionForm.OTHER) OutlinedTextField(form.otherDays, { form = form.copy(otherDays = it.filter(Char::isDigit).take(4)); token = null }, singleLine = true,
                    label = { Text("Nombre de jours (1 à ${if (production) ActivationPolicy.PRODUCTION_MAX_DAYS else ActivationPolicy.TRIAL_MAX_DAYS})") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                if (production && form.durationChoice == null) Text("Illimitée : la TV ne se reverrouille jamais.", style = MaterialTheme.typography.bodySmall)
            }
            Text("Le code est valable ${ActivationPolicy.CODE_VALIDITY_HOURS} h pour l'installer (pour tous les codes).", style = MaterialTheme.typography.bodySmall)
            if (production) Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (form.superUnlimited) "SUPER_UNLIMITED : lit et débloque tout, locations permanentes, sans durée" else "Droits selon les choix ci-dessous")
                Switch(form.superUnlimited, { form = form.copy(superUnlimited = it); token = null })
            }
            if (production) {
                OutlinedTextField(form.license, { form = form.copy(license = it) }, label = { Text("Identifiant de licence") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val ci = catalogInfo
                    Text(if (ci == null) "Aucun catalogue de bouquets." else "Catalogue du ${java.text.SimpleDateFormat("dd/MM/yyyy").format(java.util.Date(ci.second))} : ${ci.first.bundles.size} bouquets", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton({ importCatalog.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Importer le catalogue…") }
                    catalogMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                } }
                val cat = form.catalog
                if (cat == null) Text(ProductionForm.NO_CATALOG + " (achats, abonnements et locations). « Tout ouvert » et SUPER_UNLIMITED restent possibles.", style = MaterialTheme.typography.bodyMedium)
                else {
                    OutlinedTextField(form.product, { form = form.copy(product = it.trim()) }, label = { Text("Produit des achats et de l'abonnement") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    BundlePicker("Achats (permanents)", cat, false, form.purchaseBundles) { form = form.copy(purchaseBundles = it); token = null }
                    BundlePicker("Abonnement", cat, false, form.subscriptionBundles) { form = form.copy(subscriptionBundles = it); token = null }
                    if (form.subscriptionBundles.isNotEmpty()) OutlinedTextField(form.subscriptionEnd, { form = form.copy(subscriptionEnd = it.filter { c -> c.isDigit() || c == '-' }.take(10)); token = null },
                        label = { Text("Fin de l'abonnement (AAAA-MM-JJ)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    BundlePicker("Locations (durée fixée par le serveur)", cat, true, form.rentalBundles) { form = form.copy(rentalBundles = it); token = null }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(form.openProduct, { form = form.copy(openProduct = it) }, label = { Text("« Tout ouvert » : produit") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(form.openDays, { form = form.copy(openDays = it.filter(Char::isDigit).take(2)) }, label = { Text("Jours (≤ 30)") }, singleLine = true, modifier = Modifier.width(110.dp))
                }
            }
            Button({
                error = null; info = null; token = null; fileContent = null
                runCatching {
                                        val issuer = ActivationIssuer(signer); val now = System.currentTimeMillis(); val day = 24L * 3600 * 1000; val start = now / day * day
                    val full = OwnerFrames.parseDeviceInfo(input.trim().replace("\r", ""))
                    if (full != null) {
                        if (DeviceCode.of(full.third) != full.first) throw IssueException("Le code d'appareil ne correspond pas aux empreintes de la demande (texte altéré ?)")
                        val plan = form.build(now)
                        val rights = ArrayList<Right>(plan.rights)
                        if (production) {
                            plan.rentals.forEach { spec ->
                                form.catalog?.let { c -> RentalDurations.check(spec, c)?.let { throw IssueException(it) } }
                                rights += RentalIssuing.right(spec, now, form.license.trim(), SeatIds.of(form.license.trim(), full.third), full.third, RentalKeys.masterFrom(signer))
                            }
                        }
                        val kind = if (production) ActivationKind.PRODUCTION else ActivationKind.TRIAL
                        val lic = if (production) form.license.trim() else Activation.TRIAL_LICENSE
                        plan.usageDays?.let { rights += Right.Usage(now, now + it * day) }
                        // a TRIAL key also carries its one-time 12 h window of rented lots (the TV grants it once for the life of the application); it is never shown as a rental
                        if (!production) rights += RentalIssuing.right(RentalSpec(RentalLines.TRIAL_PRODUCT, listOf(Right.ALL_BUNDLE), RentalLines.TRIAL_DAYS, RentalLines.TRIAL_USAGE_MINUTES),
                            now, Activation.TRIAL_LICENSE, SeatIds.of(Activation.TRIAL_LICENSE, full.third), full.third, RentalKeys.masterFrom(signer))
                        val issued = issuer.issue(ActivationIssuer.Request(kind, full.first, full.third, issuedAt = now, rights = rights, license = lic))
                        token = issued.token; fileContent = issued.fileContent
                        store.journal(if (form.superUnlimited && production) "super" else "activation", full.first, kind.name, lic, ActivationPolicy.CODE_VALIDITY_HOURS)
                    } else {
                        val code = DeviceCode.parse(input.trim()) ?: throw IssueException("Code d'appareil mal formé (16 caractères, contrôle compris) et demande complète illisible")
                        val kind = if (production) ActivationKind.PRODUCTION else ActivationKind.TRIAL
                        val hourIdx = ((now - 1767225600000L) / ActivationPolicy.HOUR_MS).toInt()
                        if (form.superUnlimited && production) throw IssueException("SUPER_UNLIMITED exige la demande d'appareil complète (fichier ou Bluetooth) : une clé à saisir ne porte aucun droit")
                        token = issuer.issueCompact(kind, code, hourIdx)
                        store.journal("compact", code, kind.name, "-", ActivationPolicy.CODE_VALIDITY_HOURS)
                        info = "Clé compacte (à saisir) : liée à ce code d'appareil, sans droits ni clés de lots. Pour une activation complète, collez la demande d'appareil exportée par la TV."
                    }
                }.onFailure { error = (it as? IssueException)?.message ?: "Erreur : ${it.message}" }
            }, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Générer") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            token?.let { t ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Activation émise", style = MaterialTheme.typography.titleSmall)
                    SelectionContainer { Text(t, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ copy(t) }) { Text("Copier") }
                        OutlinedButton({ startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Envoyer")) }) { Text("Partager") }
                        if (fileContent != null) OutlinedButton({ save.launch("activation") }) { Text("Fichier") }
                    }
                } }
            }
        }
    }

    @Composable private fun BundlePicker(title: String, cat: BundleCatalog, forRental: Boolean, selected: Set<String>, onChange: (Set<String>) -> Unit) {
        var open by remember { mutableStateOf(false) }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("$title : ${selected.size} choisi(s)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton({ open = !open }) { Text(if (open) "Replier" else "Choisir") }
            }
            if (open) cat.bundles.sortedBy { it.id }.forEach { b ->
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(b.id in selected, { on -> onChange(if (on) selected + b.id else selected - b.id) })
                    Text(ProductionForm.bundleLine(b, forRental), style = MaterialTheme.typography.bodySmall)
                }
            }
        } }
    }

    @Composable private fun PublicKey() {
        val line = store.publicLine() ?: "—"
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Clé publique de ce téléphone : à faire accepter par les TV (liste des clés de confiance).", style = MaterialTheme.typography.bodyMedium)
            SelectionContainer { Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            OutlinedButton({ copy(line) }) { Text("Copier") }
        }
    }

    @Composable private fun Journal() {
        val rows = remember { store.journalLines() }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (rows.isEmpty()) Text("Aucune émission.")
            rows.forEach { r -> if (r.size >= 6) Text("${java.text.SimpleDateFormat("dd/MM HH:mm").format(java.util.Date(r[1].toLong()))} · ${r[2]} · ${r[3]} · ${r[4]} · ${r[5]}", style = MaterialTheme.typography.bodySmall) }
        }
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("activation", text)
        if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        cm.setPrimaryClip(clip)
    }

    companion object { private const val AUTO_LOCK_MS = 2 * 60 * 1000L }
}
