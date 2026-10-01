package castbridge.receiver

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.connect.ConsentText
import castbridge.core.connect.ServerLink
import castbridge.core.connect.ServerUrl
import castbridge.core.telemetry.Consent
import castbridge.core.update.UpdateSchedule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The TV's link with the CastBridge server, with the remote control (docs/TELEMETRY.md, docs/API-SERVER.md):
 * - « consent »: information screen at the first launch (two levels, the choice is required to continue);
 * - « updates »: real state of the automatic updates (last check, version available, progress), « Vérifier maintenant »,
 *   « Installer », and the quiz questions (« Mettre à jour les questions »);
 * - « privacy »: usage statistics on/off, « Mes données », « Effacer mes données »;
 * - « connection »: short id of the TV (to find it in /admin), server, last contact; the server address is a hidden
 *   setting (press the id line 7 times), also reachable with the PIN API (POST /api/server/url) or the phone.
 * - « mandatory »: blocking screen of a mandatory update (only way out: install it, or leave the app).
 */
class ServerActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var leftCol: LinearLayout
    private lateinit var rightCol: LinearLayout
    private var mode = MODE_UPDATES
    private var hiddenTaps = 0
    private var showAdvanced = false
    private val refresh: () -> Unit = { if (!isFinishing) render() }
    private val dp = { v: Int -> TvStyle.dp(this, v) }

    fun screenId(): String = when (mode) {
        MODE_CONSENT -> "onboarding"
        MODE_PRIVACY -> "privacy"
        MODE_CONNECTION -> "settings"
        else -> "updates"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TvConnect.init(this)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_UPDATES
        leftCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rightCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(30), dp(40), 0, 0) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(TvStyle.BG); setPadding(dp(56), dp(36), dp(56), dp(28))
            addView(ScrollView(this@ServerActivity).apply { addView(leftCol); isFocusable = false }, LinearLayout.LayoutParams(0, -1, 1.5f))
            addView(ScrollView(this@ServerActivity).apply { addView(rightCol) }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        setContentView(root)
        render(focusFirst = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        mode = intent.getStringExtra(EXTRA_MODE) ?: mode
        render(focusFirst = true)
    }

    override fun onStart() { super.onStart(); TvConnect.addListener(refresh); render() }
    override fun onStop() { TvConnect.removeListener(refresh); super.onStop() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val link = TvConnect.link
            when {
                mode == MODE_CONSENT && link?.state?.needsConsent == true -> { toast("Choisissez l'une des deux options pour continuer"); return true }
                mode == MODE_MANDATORY && mandatoryPending() -> { moveTaskToBack(true); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun mandatoryPending(): Boolean {
        val u = TvConnect.link?.update ?: return false
        return u.mandatory && u.manifest != null && u.phase != ServerLink.Phase.UP_TO_DATE
    }

    // ------------------------------------------------------------------ rendering

    private fun render(focusFirst: Boolean = false) {
        val link = TvConnect.link ?: return
        val focused = (0 until rightCol.childCount).firstOrNull { rightCol.getChildAt(it).hasFocus() }
        TvConnect.screens.enter(screenId())
        leftCol.removeAllViews(); rightCol.removeAllViews()
        when (mode) {
            MODE_CONSENT -> consent(link)
            MODE_PRIVACY -> privacy(link)
            MODE_CONNECTION -> connection(link)
            MODE_MANDATORY -> mandatory(link)
            else -> updates(link)
        }
        val target = if (!focusFirst && focused != null) rightCol.getChildAt(minOf(focused, rightCol.childCount - 1)) else rightCol.getChildAt(0)
        target?.requestFocus()
    }

    private fun title(s: String) {
        if (leftCol.childCount == 0) leftCol.addView(TvStyle.logo(this, R.drawable.logo_castbridge_tv_horizontal, 44).apply {
            contentDescription = "CastBridge TV"; (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8) })
        leftCol.addView(TextView(this).apply { text = s; textSize = 28f; typeface = TvFonts.bold; setTextColor(Color.WHITE) })
    }
    private fun para(s: String, head: String? = null) {
        if (head != null) leftCol.addView(TextView(this).apply { text = head; textSize = TvStyle.Type.BODY; typeface = TvFonts.bold; setTextColor(TvStyle.ACCENT); setPadding(0, dp(14), 0, 0) })
        leftCol.addView(TextView(this).apply { text = s; textSize = 16f; setTextColor(TvStyle.TEXT2); setPadding(0, dp(if (head == null) 12 else 4), 0, 0); setLineSpacing(0f, 1.1f) })
    }
    private fun fact(k: String, v: String, onClick: (() -> Unit)? = null) {
        leftCol.addView(TextView(this).apply { text = k; textSize = TvStyle.Type.CAPTION; setTextColor(TvStyle.MUTED); setPadding(0, dp(12), 0, 0) })
        leftCol.addView(TextView(this).apply {
            text = v; textSize = 19f; setTextColor(Color.WHITE)
            if (onClick != null) { isFocusable = true; isClickable = true; setOnClickListener { onClick() }; background = focusBg() }
        })
    }
    private fun action(label: String, f: () -> Unit) {
        rightCol.addView(TextView(this).apply {
            text = label; textSize = TvStyle.Type.BODY; setTextColor(Color.WHITE); isFocusable = true; isClickable = true
            setPadding(dp(18), dp(12), dp(18), dp(12)); background = focusBg(); setOnClickListener { f() }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
    }
    private fun focusBg() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), TvStyle.rounded(this@ServerActivity, TvStyle.CARD_FOCUS, TvStyle.R_MD, TvStyle.RING, 3))
        addState(intArrayOf(), TvStyle.rounded(this@ServerActivity, 0x00000000, TvStyle.R_MD))
    }
    private fun progress(done: Long, total: Long) {
        leftCol.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; this.progress = if (total > 0) (done * 1000 / total).toInt() else 0
        }, LinearLayout.LayoutParams(-1, dp(14)).apply { topMargin = dp(10) })
    }

    private fun consent(link: ServerLink) {
        title(ConsentText.TITLE)
        for ((head, text) in ConsentText.paragraphs) para(text, head)
        action(ConsentText.ACCEPT) { choose(link, true) }
        action(ConsentText.ESSENTIAL_ONLY) { choose(link, false) }
    }

    private fun choose(link: ServerLink, usage: Boolean) {
        TvConnect.post { setConsent(usage); tick() }       // registration at once, on the link's thread
        toast(if (usage) "Merci : statistiques d'usage acceptées (modifiable dans Réglages)" else "Seulement l'essentiel (modifiable dans Réglages)")
        if (intent.getBooleanExtra(EXTRA_THEN_FINISH, true)) finish() else { mode = MODE_PRIVACY; render(true) }
    }

    private fun updates(link: ServerLink) {
        val u = link.update
        val s = link.state
        title("Mises à jour")
        fact("Version installée", "${link.installed.versionName ?: "?"} (${link.installed.versionCode})")
        fact("Mises à jour automatiques", "vérification au démarrage puis toutes les 12 heures, installation dès qu'une version est prête" +
            " (jamais pendant un film)")
        val last = s.updateSchedule.lastCheckAt
        fact("Dernière vérification", if (last > 0) date(last) else if (s.needsConsent) "pas encore (écran d'information à valider)" else "pas encore")
        fact("État", u.message.ifBlank { s.lastUpdateMessage ?: "—" })
        u.manifest?.let { m ->
            fact("Version disponible", "${m.versionName} (${m.versionCode})" + (if (u.mandatory) " — obligatoire" else "") + " · ${m.size / (1 shl 20)} Mo")
            if (m.notes.isNotBlank()) para(m.notes, "Nouveautés")
        }
        if (u.phase == ServerLink.Phase.DOWNLOADING) progress(u.done, u.total)
        val quiz = QuizHub.cachedSource(this)
        fact("Questions du quiz", "${quiz.serverCount()} reçues du serveur" + (s.quizSyncedAt.takeIf { it > 0 }?.let { " · mises à jour le ${date(it)}" } ?: "") +
            (s.quizMessage?.let { "\n$it" } ?: ""))
        fact("Données hors ligne (leçons, questions)", LotsHub.budgetText(this) + "\n" + LotsHub.ageText(this) + "\nElles arrivent du téléphone, sans que la TV ait besoin d'Internet.")
        fact("Sans Internet", "depuis le téléphone : CastBridge › CastBridge TV › Avancé › Installer des APK (ou par la clé USB)")
        if (s.needsConsent) action("Lire l'écran d'information…") { open(this, MODE_CONSENT, thenFinish = false) }
        action("Vérifier maintenant") {
            TvConnect.feature("updates", "menu")
            toast("Recherche d'une mise à jour…")
            TvConnect.post { checkUpdate(UpdateSchedule.Trigger.USER) }
        }
        if (u.phase == ServerLink.Phase.READY || (u.phase == ServerLink.Phase.INSTALLING && u.file != null) ||
            (u.phase == ServerLink.Phase.FAILED && u.file?.isFile == true)) action("Installer la version ${u.manifest?.versionName ?: ""}") {
            TvConnect.post { offerInstall(userAsked = true) }
        }
        action("Mettre à jour les questions du quiz") { toast("Mise à jour des questions…"); TvConnect.post { syncQuiz() } }
        action("Confidentialité (mes données)") { mode = MODE_PRIVACY; render(true) }
        action("Connexion au serveur") { mode = MODE_CONNECTION; render(true) }
        action("Fermer") { finish() }
    }

    private fun mandatory(link: ServerLink) {
        val u = link.update
        title("Mise à jour obligatoire")
        para("Cette version de CastBridge TV n'est plus prise en charge. Installez la nouvelle version pour continuer à utiliser l'application.")
        u.manifest?.let { m ->
            fact("Nouvelle version", "${m.versionName} (${m.versionCode}) · ${m.size / (1 shl 20)} Mo")
            if (m.notes.isNotBlank()) para(m.notes, "Nouveautés")
        }
        fact("État", u.message)
        if (u.phase == ServerLink.Phase.DOWNLOADING) progress(u.done, u.total)
        if (!mandatoryPending()) { finish(); return }
        when (u.phase) {
            ServerLink.Phase.READY, ServerLink.Phase.INSTALLING, ServerLink.Phase.FAILED ->
                if (u.file?.isFile == true) action("Installer maintenant") { TvConnect.post { offerInstall(userAsked = true) } }
                else action("Réessayer") { TvConnect.post { checkUpdate(UpdateSchedule.Trigger.USER) } }
            ServerLink.Phase.DOWNLOADING, ServerLink.Phase.CHECKING -> {}
            else -> action("Réessayer") { TvConnect.post { checkUpdate(UpdateSchedule.Trigger.USER) } }
        }
        action("Quitter CastBridge TV") { moveTaskToBack(true) }
    }

    private fun privacy(link: ServerLink) {
        val s = link.state
        title("Confidentialité")
        fact("Votre choix", when {
            s.needsConsent -> "pas encore fait"
            s.consent == Consent.USAGE -> "Essentiel + statistiques d'usage (acceptées le ${date(s.consentAt)})"
            else -> "Seulement l'essentiel (depuis le ${date(s.consentAt)})"
        })
        para(ConsentText.ESSENTIAL, ConsentText.ESSENTIAL_TITLE)
        para(ConsentText.USAGE, ConsentText.USAGE_TITLE)
        para(ConsentText.RIGHTS)
        if (s.needsConsent) action("Lire l'écran d'information…") { open(this, MODE_CONSENT, thenFinish = false) }
        else if (s.consent == Consent.USAGE) action("Retirer mon accord aux statistiques d'usage") { TvConnect.post { setConsent(false); tick() }; toast("Statistiques d'usage désactivées") }
        else action("Accepter les statistiques d'usage") { TvConnect.post { setConsent(true); tick() }; toast("Merci : statistiques d'usage acceptées") }
        action("Mes données (ce que le serveur connaît de cette TV)") { myData() }
        action("Effacer mes données") {
            AlertDialog.Builder(this).setTitle("Effacer mes données ?")
                .setMessage("Le serveur supprime la fiche de cette TV, son historique et ses statistiques. L'écran d'information sera affiché à nouveau.")
                .setPositiveButton("Effacer") { _, _ -> erase() }.setNegativeButton("Annuler", null).show()
        }
        action("Relire l'information complète") { open(this, MODE_CONSENT, thenFinish = false) }
        action("Mises à jour") { mode = MODE_UPDATES; render(true) }
        action("Fermer") { finish() }
    }

    private fun connection(link: ServerLink) {
        val s = link.state
        title("Connexion au serveur")
        fact("Identifiant de la TV (à chercher dans l'administration)", s.shortId ?: "pas encore enregistrée") {
            if (++hiddenTaps >= 7 && !showAdvanced) { showAdvanced = true; toast("Réglage avancé affiché"); render() }
        }
        if (s.customServer) fact("Serveur personnalisé", s.baseUrl)
        fact("Dernier contact", if (s.lastContactAt > 0) date(s.lastContactAt) + (s.lastContactMessage?.let { " — $it" } ?: "")
            else s.lastContactMessage ?: if (s.needsConsent) "aucun (écran d'information à valider)" else "aucun pour l'instant")
        fact("Internet", TvService.running?.netSummary() ?: "—")
        if (s.blocked) fact("État", "Appareil bloqué par l'administrateur : pas de mise à jour ni de quiz en ligne")
        if (s.channel == "beta") fact("Canal", "bêta (versions de test)")
        action("Contacter le serveur maintenant") { toast("Contact du serveur…"); TvConnect.post { if (contact()) flush() } }
        if (showAdvanced || s.customServer) {
            action("Adresse du serveur (avancé)…") { editUrl(link) }
            if (s.customServer) action("Revenir au serveur par défaut") { TvConnect.post { state.baseUrl = ServerUrl.DEFAULT; tick() }; toast("Serveur par défaut rétabli") }
        }
        action("Confidentialité (mes données)") { mode = MODE_PRIVACY; render(true) }
        action("Fermer") { finish() }
    }

    private fun editUrl(link: ServerLink) {
        val input = EditText(this).apply { setText(if (link.state.customServer) link.state.baseUrl else ""); inputType = InputType.TYPE_TEXT_VARIATION_URI; setSelectAllOnFocus(true) }
        AlertDialog.Builder(this).setTitle("Adresse du serveur CastBridge").setMessage("HTTPS obligatoire. Laissez vide pour le serveur par défaut.")
            .setView(input)
            .setPositiveButton("Enregistrer") { _, _ ->
                val v = input.text.toString().trim().ifEmpty { ServerUrl.DEFAULT }
                val problem = ServerUrl.problem(v)
                if (problem != null) toast(problem)
                else { TvConnect.post { state.baseUrl = v; tick() }; toast(if (v == ServerUrl.DEFAULT) "Serveur par défaut rétabli" else "Serveur : ${ServerUrl.normalize(v)}") }
            }.setNegativeButton("Annuler", null).show()
    }

    private fun myData() {
        toast("Demande au serveur…")
        TvConnect.post {
            val text = runCatching { myData() }.fold({ pretty(it) }, { "Impossible d'obtenir vos données : ${it.message}" })
            main.post {
                if (isFinishing) return@post
                val tv = TextView(this@ServerActivity).apply { this.text = text; typeface = Typeface.MONOSPACE; textSize = TvStyle.Type.CAPTION; setTextColor(TvStyle.TEXT); setPadding(dp(20), dp(16), dp(20), dp(16)) }
                AlertDialog.Builder(this@ServerActivity).setTitle("Mes données").setView(ScrollView(this@ServerActivity).apply { addView(tv); setBackgroundColor(TvStyle.BG_ELEVATED) })
                    .setPositiveButton("Fermer", null).show()
            }
        }
    }

    private fun erase() {
        toast("Effacement en cours…")
        TvConnect.post {
            val r = runCatching { eraseMyData() }
            main.post {
                if (r.isSuccess) { toast("Données effacées sur le serveur et sur la TV"); mode = MODE_CONSENT; intent.putExtra(EXTRA_THEN_FINISH, true); render(true) }
                else toast("Effacement impossible : ${r.exceptionOrNull()?.message}")
            }
        }
    }

    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_THEN_FINISH = "thenFinish"
        const val MODE_CONSENT = "consent"
        const val MODE_UPDATES = "updates"
        const val MODE_PRIVACY = "privacy"
        const val MODE_CONNECTION = "connection"
        const val MODE_MANDATORY = "mandatory"

        fun open(ctx: Context, mode: String, thenFinish: Boolean = true) {
            runCatching {
                ctx.startActivity(Intent(ctx, ServerActivity::class.java).putExtra(EXTRA_MODE, mode).putExtra(EXTRA_THEN_FINISH, thenFinish)
                    .addFlags(if (ctx is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        private val fmt = SimpleDateFormat("d MMM yyyy 'à' HH:mm", Locale.FRANCE)
        fun date(ms: Long): String = synchronized(fmt) { fmt.format(Date(ms)) }

        /** Indents the JSON of GET /devices/me for reading on the TV. */
        fun pretty(json: String): String = runCatching { pretty(castbridge.core.net.JsonLite.parse(json), "") }.getOrDefault(json)
        private fun pretty(v: Any?, ind: String): String = when (v) {
            is Map<*, *> -> if (v.isEmpty()) "{}" else v.entries.joinToString(",\n", "{\n", "\n$ind}") { "$ind  ${it.key}: ${pretty(it.value, "$ind  ")}" }
            is List<*> -> if (v.isEmpty()) "[]" else v.joinToString(",\n", "[\n", "\n$ind]") { "$ind  ${pretty(it, "$ind  ")}" }
            is String -> "\"$v\""
            else -> v.toString()
        }
    }
}
