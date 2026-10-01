package castbridge.receiver

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.Intent
import castbridge.core.parental.AgeBand
import castbridge.core.parental.AppCategory
import castbridge.core.parental.AppRule
import castbridge.core.parental.AppRules
import castbridge.core.parental.AppSettings
import castbridge.core.parental.AppState
import castbridge.core.parental.NewAppDefault
import castbridge.core.parental.ProfileReportOptions
import castbridge.core.trust.TrustedPhone
import castbridge.core.parental.Category
import castbridge.core.parental.ChildProfile
import castbridge.core.parental.OverAge
import castbridge.core.parental.ParentalConfig
import castbridge.core.parental.ParentalRules
import castbridge.core.parental.Rating
import castbridge.core.parental.RatingRule
import castbridge.core.parental.RuleKind
import castbridge.core.parental.TimeWindow
import castbridge.core.parental.UseKind
import castbridge.core.tv.LibraryItem
import castbridge.core.tv.MediaType
import castbridge.core.tv.Pin

/**
 * « Contrôle parental » on the TV (docs/PARENTAL.md): create the parental PIN, child profiles, rules, video ratings, activity.
 * Classic views, D-pad only, every block stacked vertically (no text over text at 1280x720 or 1920x1080). Not exported.
 * The screen asks the parental PIN once when it opens; BACK goes up one level, then back to the home.
 */
class ParentalActivity : Activity() {
    private class Page(val title: String, val subtitle: String?, val build: LinearLayout.() -> Unit)

    private val stack = ArrayList<Page>()
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var statusView: TextView
    private var authed = false
    private val e get() = ParentalHub.engine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ParentalHub.init(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(TvStyle.BG)
            val p = TvStyle.dp(this@ParentalActivity, 32); setPadding(p, TvStyle.dp(this@ParentalActivity, 20), p, TvStyle.dp(this@ParentalActivity, 12))
        }
        titleView = ParentalUi.text(this, "", ParentalUi.TITLE_SP, TvStyle.ACCENT, true)
        subtitleView = ParentalUi.text(this, "", ParentalUi.SMALL_SP, TvStyle.MUTED)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply {
            isFillViewport = false; addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        statusView = ParentalUi.text(this, "", ParentalUi.SMALL_SP, 0xFF8BC34A.toInt(), true).apply { visibility = android.view.View.GONE }
        root.addView(titleView); root.addView(subtitleView); root.addView(scroll); root.addView(statusView)
        root.addView(ParentalUi.text(this, "OK : choisir   ·   HAUT / BAS : naviguer   ·   RETOUR : revenir", 16f, TvStyle.MUTED).apply { setPadding(0, TvStyle.dp(this@ParentalActivity, 8), 0, 0) })
        setContentView(root)
        push(entryPage())
    }

    // ------------------------------------------------------------------ navigation

    private fun push(p: Page) { stack += p; render() }
    private fun pop() { if (stack.size > 1) { stack.removeAt(stack.size - 1); render() } else finish() }

    private fun render() {
        val p = stack.last()
        titleView.text = p.title
        subtitleView.text = p.subtitle.orEmpty()
        subtitleView.visibility = if (p.subtitle.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        content.removeAllViews()
        p.build(content)
        content.post { firstFocusable()?.requestFocus() }
    }

    private fun firstFocusable() = (0 until content.childCount).map { content.getChildAt(it) }.firstOrNull { it.isFocusable }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { pop(); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun LinearLayout.row(title: String, value: String? = null, onClick: () -> Unit) { addView(ParentalUi.row(this@ParentalActivity, title, value, onClick)) }
    private fun LinearLayout.note(s: String) { addView(ParentalUi.text(this@ParentalActivity, s, ParentalUi.SMALL_SP, TvStyle.MUTED).apply { setPadding(TvStyle.dp(this@ParentalActivity, 6), TvStyle.dp(this@ParentalActivity, 8), 0, TvStyle.dp(this@ParentalActivity, 8)) }) }
    private fun LinearLayout.section(s: String) { addView(ParentalUi.text(this@ParentalActivity, s, 20f, TvStyle.ACCENT, true).apply { setPadding(0, TvStyle.dp(this@ParentalActivity, 14), 0, TvStyle.dp(this@ParentalActivity, 2)) }) }
    /** A message line under the list (never over it, so nothing overlaps). */
    private fun toast(s: String) {
        statusView.text = s; statusView.visibility = android.view.View.VISIBLE
        statusView.removeCallbacks(hideStatus); statusView.postDelayed(hideStatus, 7000)
    }
    private val hideStatus = Runnable { statusView.visibility = android.view.View.GONE }

    // ------------------------------------------------------------------ entry: create the PIN, or ask it

    private fun entryPage(): Page = when {
        !e.hasPin() -> introPage()
        authed || e.sessionActive() -> { authed = true; rootPage() }
        else -> gatePage()
    }

    private fun introPage() = Page("Contrôle parental", "Protégez les enfants : vidéos adaptées à leur âge, horaires, temps d'écran, écrans réservés aux parents.") {
        note("Tant que vous n'avez pas créé de code parental, rien n'est bloqué. Ce code est différent du code de connexion de la TV : choisissez 4 à 6 chiffres que les enfants ne connaissent pas.")
        row("Créer le code parental", "4 à 6 chiffres, à taper deux fois") {
            ParentalUi.createPin(this@ParentalActivity, "Créer le code parental", save = { pin -> e.createPin(pin) }) {
                authed = true; toast("Code parental créé. Créez maintenant un profil pour un enfant.")
                stack.clear(); push(rootPage())
            }
        }
        note("Important : si vous oubliez ce code, seul l'administrateur de la TV (code de connexion à 6 chiffres) peut l'effacer, ou l'effacement des données de l'application. Rien ne le récupère autrement.")
        row("Retour à l'accueil") { finish() }
    }

    private fun gatePage() = Page("Contrôle parental", "Cet écran est réservé aux parents.") {
        row("Saisir le PIN parental", "Le code que vous avez créé") {
            ParentalUi.pinDialog(this@ParentalActivity, "Code parental", "Saisissez votre code pour ouvrir les réglages.",
                check = { pin -> ParentalHub.pinError(e.verifyPin(pin)) }, onOk = { authed = true; stack.clear(); push(rootPage()) })
        }
        row("J'ai oublié le code parental", "Possible avec le code de connexion de la TV") { forgot() }
        row("Retour à l'accueil") { finish() }
    }

    private fun forgot() {
        AlertDialog.Builder(this).setTitle("Code parental oublié")
            .setMessage("Avec le code de connexion de la TV (celui à 6 chiffres, réservé à l'administrateur), le code parental sera EFFACÉ et le contrôle parental DÉSACTIVÉ. " +
                "Vos profils et vos règles sont gardés ; il faudra créer un nouveau code pour réactiver. Sans ce code, seul l'effacement des données de l'application aide.")
            .setPositiveButton("Continuer") { _, _ ->
                val tvPin = TvService.running?.pin
                if (tvPin.isNullOrEmpty()) { toast("Le service de la TV n'est pas prêt. Réessayez dans un instant."); return@setPositiveButton }
                ParentalUi.pinDialog(this, "Code de connexion de la TV", "Les 6 chiffres affichés dans « Connexion & réglages ».",
                    check = { pin -> ParentalHub.pinError(e.adminReset(pin) { Pin.matches(tvPin, it) }) },
                    onOk = { toast("Code parental effacé. Le contrôle est désactivé."); authed = false; stack.clear(); push(entryPage()) })
            }.setNegativeButton("Annuler", null).show()
    }

    // ------------------------------------------------------------------ main page

    private fun rootPage() = Page("Contrôle parental", "Réglages des parents") {
        val c = e.config()
        val active = c.active()
        section("État")
        row("Contrôle parental : " + if (c.enabled) "activé" else "désactivé",
            if (c.enabled) "Les règles s'appliquent à ${active?.name ?: "—"}. OK pour désactiver." else "OK pour activer (il faut un profil actif).") { toggleEnabled() }
        if (e.sessionActive()) row("Reverrouiller maintenant", "Le déverrouillage parent finit dans ${e.sessionLeftSec() / 60 + 1} min.") { e.endSession(); toast("Les règles sont de nouveau actives."); render() }
        section("Enfants")
        row("Profil actif : ${active?.name ?: "aucun"}", "Celui qui utilise la TV. Les règles de ce profil s'appliquent.") { chooseActive() }
        row("Profils des enfants", "${c.profiles.size} profil(s) : âge, horaires, durée, écrans bloqués") { push(profilesPage()) }
        section("Vidéos")
        row("Classer les vidéos", "${c.rules.size} règle(s) · non classées : ${c.unrated.label}") { push(ratingsPage()) }
        row("Vidéo trop adulte pour le profil : ${c.overAge.label.lowercase()}", "OK pour changer : masquer de la bibliothèque, ou laisser visible mais demander le code.") {
            e.edit { it.copy(overAge = if (it.overAge == OverAge.HIDE) OverAge.LOCK else OverAge.HIDE) }; render()
        }
        row("Vidéos non classées : ${c.unrated.label}", "Par défaut, une vidéo que vous n'avez pas classée est réservée aux adultes.") {
            e.edit { it.copy(unrated = if (it.unrated == Rating.ADULT) Rating.ALL else Rating.ADULT) }; render()
        }
        section("Toute la TV")
        val sup = e.supervision()
        row(sup.state.label, (sup.detail?.let { "$it. " } ?: "") + "YouTube, Netflix, navigateur, jeux… : temps compté et applications bloquables. OK pour régler.") {
            startActivity(Intent(this@ParentalActivity, SupervisionSetupActivity::class.java))
        }
        row("Applications de la TV", "Autorisée, bloquée, durée limitée ou code parental, application par application") {
            val first = c.active() ?: c.profiles.firstOrNull()
            if (first == null) ParentalUi.info(this@ParentalActivity, "Créez un profil", "Ajoutez d'abord un profil d'enfant : « Profils des enfants ».") else push(appsPage(first.id))
        }
        row("Rapports vers le téléphone du parent", "${ParentalHub.reports.recipients.active().size} téléphone(s) désigné(s) · résumés et alertes par Bluetooth") { push(reportsPage()) }
        section("Parents")
        row("Activité d'aujourd'hui", "Temps passé par profil et contenus bloqués") { showActivity() }
        row("Durée du déverrouillage parent : ${c.sessionMin} min", "Après le code sur un écran verrouillé, les règles sont suspendues ce temps-là.") {
            val opts = listOf(15, 30, 60, 120)
            ParentalUi.choose(this@ParentalActivity, "Durée du déverrouillage", opts.map { "$it minutes" }, opts.indexOf(c.sessionMin)) { i -> e.edit { it.copy(sessionMin = opts[i]) }; render() }
        }
        row("Changer le code parental", "Il faut d'abord saisir l'ancien") { changePin() }
        row("Quitter", "Retour à l'accueil") { finish() }
    }

    private fun toggleEnabled() {
        val c = e.config()
        if (!c.enabled && c.profiles.isEmpty()) { ParentalUi.info(this, "Créez un profil", "Ajoutez d'abord un profil d'enfant : « Profils des enfants », puis « Ajouter un profil »."); return }
        if (!c.enabled && c.active() == null) { ParentalUi.info(this, "Choisissez un profil actif", "Choisissez le profil de l'enfant qui utilise la TV : « Profil actif »."); return }
        e.edit { it.copy(enabled = !it.enabled) }; render()
    }

    private fun chooseActive() {
        val c = e.config()
        if (c.profiles.isEmpty()) { ParentalUi.info(this, "Aucun profil", "Ajoutez d'abord un profil : « Profils des enfants »."); return }
        val labels = c.profiles.map { "${it.name} (${it.age.label})" } + "Aucun (pas de règles)"
        ParentalUi.choose(this, "Qui utilise la TV ?", labels, c.profiles.indexOfFirst { it.id == c.activeProfile }) { i ->
            e.edit { it.copy(activeProfile = c.profiles.getOrNull(i)?.id) }; render()
        }
    }

    private fun changePin() {
        ParentalUi.pinDialog(this, "Ancien code parental", "Saisissez le code actuel.", check = { pin -> ParentalHub.pinError(e.verifyPin(pin)) }) { old ->
            ParentalUi.createPin(this, "Nouveau code parental", save = { new -> e.changePin(old, new) }) { toast("Code parental changé.") }
        }
    }

    private fun showActivity() {
        val r = e.report(2)
        @Suppress("UNCHECKED_CAST") val days = r["days"] as List<Map<String, Any?>>
        val today = days.firstOrNull()
        val sb = StringBuilder()
        @Suppress("UNCHECKED_CAST") val ps = (today?.get("profiles") as? List<Map<String, Any?>>).orEmpty()
        if (ps.isEmpty()) sb.append("Aucune activité enregistrée aujourd'hui.\n")
        for (p in ps) {
            sb.append("${p["name"]} : lecture ${p["play"]} min, jeux ${p["games"]} min, téléchargements ${p["downloads"]} min, autres applications ${p["apps"]} min\n")
            @Suppress("UNCHECKED_CAST") val top = (p["byApp"] as? List<Map<String, Any?>>).orEmpty().take(4)
            if (top.isNotEmpty()) sb.append("   " + top.joinToString(", ") { "${it["label"]} ${it["min"]} min" } + "\n")
        }
        @Suppress("UNCHECKED_CAST") val sv = r["supervision"] as? Map<String, Any?>
        sv?.let { sb.append("\n${it["label"]}\n") }
        @Suppress("UNCHECKED_CAST") val tamper = (r["tamper"] as? List<Map<String, Any?>>).orEmpty().take(3)
        for (t in tamper) sb.append("⚠ ${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.FRANCE).format(java.util.Date((t["ts"] as Number).toLong()))} ${t["what"]}\n")
        @Suppress("UNCHECKED_CAST") val news = (r["newApps"] as? List<Map<String, Any?>>).orEmpty()
        if (news.isNotEmpty()) sb.append("Nouvelles applications à valider : ${news.joinToString { it["label"].toString() }}\n")
        @Suppress("UNCHECKED_CAST") val blocked = (r["blocked"] as List<Map<String, Any?>>).take(8)
        if (blocked.isNotEmpty()) {
            sb.append("\nBloqué récemment :\n")
            val f = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.FRANCE)
            for (b in blocked) sb.append("• ${f.format(java.util.Date((b["ts"] as Number).toLong()))} ${b["what"]}\n")
        }
        sb.append("\nCes informations restent sur la TV et sur votre téléphone ; rien n'est envoyé au serveur.")
        AlertDialog.Builder(this).setTitle("Activité").setMessage(sb.toString()).setPositiveButton("Fermer", null)
            .setNeutralButton("Effacer l'historique") { _, _ -> e.clearHistory(); toast("Historique effacé.") }.show()
    }

    // ------------------------------------------------------------------ profiles

    private fun profilesPage() = Page("Profils des enfants", "Un profil par enfant : âge, horaires, durée, écrans bloqués.") {
        val c = e.config()
        for (p in c.profiles) row(p.name + if (p.id == c.activeProfile) "  (actif)" else "", describe(p)) { push(profilePage(p.id)) }
        if (c.profiles.size < ParentalConfig.MAX_PROFILES) row("+ Ajouter un profil", "Prénom de l'enfant") { addProfile() }
        val students = ParentalHub.learnStudents().filter { s -> c.profiles.none { it.learnId == s.first } }
        if (students.isNotEmpty() && c.profiles.size < ParentalConfig.MAX_PROFILES) row("Importer les élèves d'Apprendre", "${students.size} élève(s) : ${students.take(3).joinToString { it.second }}") { importStudents(students) }
        note("Les élèves d'Apprendre importés gardent leur prénom ; vérifiez ensuite la tranche d'âge de chacun.")
    }

    private fun describe(p: ChildProfile): String = buildList {
        add(p.age.label); add(if (p.kidMode) "mode enfant" else "accueil normal")
        add(p.window?.let { "de ${it.text()}" } ?: "toute la journée")
        add(if (p.dailyLimitMin > 0) "${p.dailyLimitMin} min par jour" else "sans limite de durée")
    }.joinToString(" · ")

    private fun askText(title: String, initial: String, max: Int, onText: (String) -> Unit) {
        val et = EditText(this).apply { setText(initial); inputType = InputType.TYPE_CLASS_TEXT; setSingleLine(); textSize = 24f; setSelection(text.length) }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> val t = et.text.toString().trim().take(max); if (t.isNotEmpty()) onText(t) }
            .setNegativeButton("Annuler", null).show()
    }

    private fun addProfile() = askText("Prénom de l'enfant", "", ParentalConfig.MAX_NAME) { name ->
        val c = e.config()
        val id = ParentalHub.newProfileId(c.profiles)
        e.edit { it.copy(profiles = it.profiles + ChildProfile(id, name), activeProfile = it.activeProfile ?: id) }
        render()
    }

    private fun importStudents(students: List<Triple<String, String, String?>>) {
        e.edit { c ->
            var profiles = c.profiles
            for ((lid, name, level) in students) {
                if (profiles.size >= ParentalConfig.MAX_PROFILES) break
                profiles = profiles + ChildProfile(ParentalHub.newProfileId(profiles), name.take(ParentalConfig.MAX_NAME), AgeBand.guessFromLevel(level), learnId = lid)
            }
            c.copy(profiles = profiles, activeProfile = c.activeProfile ?: profiles.firstOrNull()?.id)
        }
        render()
    }

    private fun profilePage(id: String) = Page(e.config().profile(id)?.name ?: "Profil", "Règles de ce profil") {
        val p = e.config().profile(id) ?: return@Page
        fun change(f: (ChildProfile) -> ChildProfile) { e.edit { c -> c.copy(profiles = c.profiles.map { if (it.id == id) f(it) else it }) }; render() }
        row("Prénom : ${p.name}") { askText("Prénom", p.name, ParentalConfig.MAX_NAME) { n -> change { it.copy(name = n) } } }
        row("Tranche d'âge : ${p.age.label}", "Décide des vidéos visibles : ${p.age.max.label.lowercase()} et moins.") {
            ParentalUi.choose(this@ParentalActivity, "Tranche d'âge", AgeBand.values().map { it.label }, p.age.ordinal) { i -> change { it.copy(age = AgeBand.values()[i]) } }
        }
        row("Mode enfant : " + if (p.kidMode) "oui" else "non", "Accueil simplifié : Apprendre, jeux, vidéos autorisées. Se quitte seulement avec le code.") { change { it.copy(kidMode = !it.kidMode) } }
        row("Heures autorisées : " + (p.window?.text() ?: "toute la journée"), "En dehors de ces heures, lecture, jeux et téléchargements sont bloqués.") { chooseWindow(p, ::change) }
        row("Durée par jour : " + if (p.dailyLimitMin > 0) "${p.dailyLimitMin} min" else "sans limite", "Un avertissement s'affiche 5 minutes avant la fin.") {
            val opts = ParentalConfig.LIMIT_CHOICES
            ParentalUi.choose(this@ParentalActivity, "Durée par jour", opts.map { if (it == 0) "Sans limite" else "$it minutes" }, opts.indexOf(p.dailyLimitMin)) { i -> change { it.copy(dailyLimitMin = opts[i]) } }
        }
        row("Ce qui compte : " + (p.kinds.joinToString { it.label.lowercase() }.ifEmpty { "rien" }), "Les heures et la durée s'appliquent à la lecture, aux jeux, aux téléchargements (à cocher).") {
            val all = UseKind.values()
            val on = BooleanArray(all.size) { all[it] in p.kinds }
            AlertDialog.Builder(this@ParentalActivity).setTitle("Ce qui compte dans le temps")
                .setMultiChoiceItems(all.map { it.label }.toTypedArray(), on) { _, i, b -> on[i] = b }
                .setPositiveButton("OK") { _, _ -> change { it.copy(kinds = all.filterIndexed { i, _ -> on[i] }.toSet()) } }
                .setNegativeButton("Annuler", null).show()
        }
        row("Écrans bloqués : " + (p.blocked.joinToString { it.label }.ifEmpty { "aucun" }), "Jeux, téléchargements, Internet, administration, SSH, réglages (à cocher).") {
            val all = Category.BLOCKABLE
            val on = BooleanArray(all.size) { all[it] in p.blocked }
            AlertDialog.Builder(this@ParentalActivity).setTitle("Écrans bloqués pour ${p.name}")
                .setMultiChoiceItems(all.map { it.label }.toTypedArray(), on) { _, i, b -> on[i] = b }
                .setPositiveButton("OK") { _, _ -> change { it.copy(blocked = all.filterIndexed { i, _ -> on[i] }.toSet()) } }
                .setNegativeButton("Annuler", null).show()
        }
        row("Utiliser ce profil maintenant", if (e.config().activeProfile == id) "C'est déjà le profil actif." else null) { e.edit { it.copy(activeProfile = id) }; toast("Profil actif : ${p.name}"); render() }
        row("Supprimer ce profil") {
            AlertDialog.Builder(this@ParentalActivity).setTitle("Supprimer le profil de ${p.name} ?").setMessage("Ses règles sont effacées. Son historique de temps reste dans le rapport.")
                .setPositiveButton("Supprimer") { _, _ ->
                    e.edit { c -> c.copy(profiles = c.profiles.filterNot { it.id == id }, activeProfile = if (c.activeProfile == id) null else c.activeProfile, enabled = c.enabled && c.activeProfile != id) }
                    pop()
                }.setNegativeButton("Annuler", null).show().getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
        }
    }

    private fun chooseWindow(p: ChildProfile, change: ((ChildProfile) -> ChildProfile) -> Unit) {
        ParentalUi.choose(this, "Heures autorisées", listOf("Toute la journée", "Choisir les heures…"), if (p.window == null) 0 else 1) { i ->
            if (i == 0) { change { it.copy(window = null) }; return@choose }
            val times = (0 until 48).map { it * 30 }
            val labels = times.map { TimeWindow.fmt(it) }
            ParentalUi.choose(this, "Autorisé à partir de", labels, times.indexOf(p.window?.fromMin ?: 420)) { a ->
                ParentalUi.choose(this, "…jusqu'à", labels, times.indexOf(p.window?.toMin ?: 1200)) { b ->
                    if (a == b) toast("L'heure de fin doit être différente du début.")
                    else change { it.copy(window = TimeWindow(times[a], times[b])) }
                }
            }
        }
    }

    // ------------------------------------------------------------------ ratings

    private fun ratingsPage() = Page("Classer les vidéos", "Tous publics · -12 ans · -16 ans · Adulte. Une vidéo non classée vaut : ${e.config().unrated.label}.") {
        val c = e.config()
        section("Règles par mot-clé ou par disque")
        for ((i, r) in c.rules.withIndex().filter { it.value.kind != RuleKind.FILE }) row("${r.kind.label} : ${r.match}", r.rating.label) { editRule(i, r) }
        row("+ Règle par mot-clé", "Toutes les vidéos dont le nom contient ce mot (exemple : horreur)") {
            askText("Mot-clé dans le nom", "", ParentalConfig.MAX_MATCH) { kw -> pickRating("Classement des vidéos « $kw »") { r -> addRule(RatingRule(RuleKind.KEYWORD, kw, r)) } }
        }
        val vols = TvService.running?.runCatching { registry.volumes().map { it.label } }?.getOrNull().orEmpty()
        if (vols.isNotEmpty()) row("+ Règle pour un dossier (TV ou clé USB)", "Toutes les vidéos d'un même stockage") {
            ParentalUi.choose(this@ParentalActivity, "Quel stockage ?", vols) { i -> pickRating("Classement de « ${vols[i]} »") { r -> addRule(RatingRule(RuleKind.VOLUME, vols[i], r)) } }
        }
        section("Vidéos de la TV")
        val vids = TvService.running?.server?.libraryItems().orEmpty().filter { it.type == MediaType.VIDEO }
        if (vids.isEmpty()) note("Aucune vidéo sur la TV pour le moment.")
        for (v in vids) row(v.title, "Classement : " + ratingText(c, v)) {
            pickRating("Classer « ${v.title} »") { r -> addRule(RatingRule(RuleKind.FILE, v.name, r), replaceFile = v.name) }
        }
    }

    private fun ratingText(c: ParentalConfig, v: LibraryItem): String {
        val r = ParentalRules.ratingOf(c, v.name, v.volumeLabel)
        val why = when {
            c.rules.any { it.kind == RuleKind.FILE && it.match.equals(v.name, true) } -> "vidéo"
            c.rules.any { (it.kind == RuleKind.KEYWORD && v.name.lowercase().contains(it.match.lowercase())) || (it.kind == RuleKind.VOLUME && it.match.equals(v.volumeLabel, true)) } -> "règle"
            else -> "non classée"
        }
        return "${r.label} ($why)"
    }

    private fun pickRating(title: String, onPick: (Rating) -> Unit) =
        ParentalUi.choose(this, title, Rating.values().map { it.label }) { i -> onPick(Rating.values()[i]) }

    private fun addRule(r: RatingRule, replaceFile: String? = null) {
        e.edit { c ->
            val rest = c.rules.filterNot { (replaceFile != null && it.kind == RuleKind.FILE && it.match.equals(replaceFile, true)) || (it.kind == r.kind && it.match.equals(r.match, true)) }
            c.copy(rules = (rest + r).takeLast(ParentalConfig.MAX_RULES))
        }
        render()
    }

    private fun editRule(index: Int, r: RatingRule) {
        ParentalUi.choose(this, "${r.kind.label} : ${r.match}", listOf("Changer le classement", "Supprimer cette règle")) { w ->
            if (w == 1) { e.edit { c -> c.copy(rules = c.rules.filterIndexed { i, _ -> i != index }) }; render() }
            else pickRating("Nouveau classement") { nr -> e.edit { c -> c.copy(rules = c.rules.mapIndexed { i, x -> if (i == index) x.copy(rating = nr) else x }) }; render() }
        }
    }

    // ------------------------------------------------------------------ apps of the whole TV

    private fun stateText(p: ChildProfile, s: AppSettings, a: castbridge.core.parental.InstalledApp): String {
        val r = s.rule(p.id, a.pkg)
        val cat = (r?.category ?: a.category).label
        val st = when {
            r != null -> r.state.label + if (r.state == AppState.LIMITED) " : ${r.limitMin} min par jour" else ""
            s.isNew(a.pkg) -> "Nouvelle application : " + s.newApp.label.lowercase()
            else -> "Autorisée (aucune règle)"
        }
        return "$st · $cat"
    }

    private fun appsPage(profileId: String): Page = Page("Applications de la TV", "Règles de ${e.config().profile(profileId)?.name ?: "—"} pour les autres applications") {
        val p = e.config().profile(profileId) ?: return@Page
        val apps = AppCatalog.launcherApps(this@ParentalActivity).sortedBy { it.label.lowercase() }
        e.syncInstalled(apps)                                   // the first time is the baseline: nothing is « nouvelle » on the setup day
        val s = e.appSettings()
        val env = AppCatalog.env(this@ParentalActivity)
        note("CastBridge-TV (Apprendre inclus), l'accueil de la TV et le système ne sont jamais bloquables : on peut toujours revenir à l'accueil. Les Réglages suivent la catégorie « Réglages » du profil.")
        if (e.config().profiles.size > 1) row("Profil : ${p.name}", "OK pour changer de profil") {
            val ps = e.config().profiles
            ParentalUi.choose(this@ParentalActivity, "Règles de quel profil ?", ps.map { it.name }, ps.indexOfFirst { it.id == profileId }) { i -> stack.removeAt(stack.size - 1); push(appsPage(ps[i].id)) }
        }
        row("Nouvelle application installée : ${s.newApp.label.lowercase()}", "Une application installée après la configuration s'affiche « Nouvelle » et suit ce choix jusqu'à votre validation. OK pour changer.") {
            e.editApps { it.copy(newApp = if (it.newApp == NewAppDefault.BLOCK) NewAppDefault.ALLOW else NewAppDefault.BLOCK) }; render()
        }
        if (s.news.isNotEmpty()) row("${s.news.size} nouvelle(s) application(s) à valider", s.news.joinToString { it.label }) {
            e.editApps { it.copy(known = it.known + it.news.map { n -> n.pkg }) }; toast("Applications validées."); render()
        }
        row("Régler toute une catégorie", "Navigateurs, jeux, vidéo et streaming… d'un seul coup") { bulk(p, apps, env) }
        section("Applications (${apps.size})")
        for (a in apps) {
            val never = AppRules.neverBlockable(a.pkg, env)
            val title = a.label + if (s.isNew(a.pkg)) "  · NOUVELLE" else ""
            if (never) row(title, "Toujours autorisée (CastBridge-TV, accueil ou système)") { ParentalUi.info(this@ParentalActivity, a.label, "Cette application ne peut pas être bloquée : sans elle, la TV n'aurait plus d'accueil.") }
            else row(title, stateText(p, s, a)) { editApp(p, a) }
        }
    }

    private fun askState(title: String, onPick: (AppState?, Int) -> Unit) {
        val labels = AppState.values().map { it.label } + "Retirer la règle"
        ParentalUi.choose(this, title, labels) { i ->
            if (i >= AppState.values().size) { onPick(null, 0); return@choose }
            val st = AppState.values()[i]
            if (st != AppState.LIMITED) { onPick(st, 0); return@choose }
            val opts = listOf(15, 30, 45, 60, 90, 120)
            ParentalUi.choose(this, "Durée par jour", opts.map { "$it minutes" }) { m -> onPick(st, opts[m]) }
        }
    }

    private fun applyRules(p: ChildProfile, pkgs: List<String>, st: AppState?, limit: Int, category: AppCategory? = null) {
        e.editApps { s ->
            val cur = s.rules[p.id].orEmpty()
            val keep = cur.filterNot { it.pkg in pkgs }
            val added = if (st == null) emptyList() else pkgs.map { pkg -> AppRule(pkg, st, limit, category ?: cur.firstOrNull { it.pkg == pkg }?.category) }
            s.copy(rules = s.rules + (p.id to (keep + added).takeLast(AppSettings.MAX_RULES_PER_PROFILE)), known = s.known + pkgs)
        }
        render()
    }

    private fun editApp(p: ChildProfile, a: castbridge.core.parental.InstalledApp) =
        askState("${a.label} pour ${p.name}") { st, limit -> applyRules(p, listOf(a.pkg), st, limit) }

    private fun bulk(p: ChildProfile, apps: List<castbridge.core.parental.InstalledApp>, env: castbridge.core.parental.AppEnv) {
        val cats = AppCategory.values()
        ParentalUi.choose(this, "Quelle catégorie ?", cats.map { c -> "${c.label} (${apps.count { it.category == c && !AppRules.neverBlockable(it.pkg, env) }})" }) { ci ->
            val pkgs = apps.filter { it.category == cats[ci] && !AppRules.neverBlockable(it.pkg, env) }.map { it.pkg }
            if (pkgs.isEmpty()) { toast("Aucune application dans cette catégorie."); return@choose }
            askState("${cats[ci].label} : ${pkgs.size} application(s)") { st, limit -> applyRules(p, pkgs, st, limit, cats[ci]) }
        }
    }

    // ------------------------------------------------------------------ reports to the parent's phone

    private val dayNames = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

    private fun reportsPage(): Page = Page("Rapports vers le téléphone du parent", "Envoyés par Bluetooth au téléphone désigné : jamais à un serveur.") {
        val rp = ParentalHub.reports
        val cfg = rp.config()
        section("Téléphones qui reçoivent les rapports")
        val phones = runCatching { TvService.running?.trust?.list() }.getOrNull().orEmpty()
        if (phones.isEmpty()) note("Aucun téléphone de confiance. Ajoutez d'abord le téléphone du parent avec « Ajouter un téléphone » (accueil de la TV).")
        for (ph in phones) {
            val on = rp.recipients.isRecipient(ph.address)
            row(ph.name + if (on) "  — reçoit les rapports" else "", if (on) "OK pour le retirer (code parental demandé)" else "OK pour le désigner (code parental demandé)") { designate(ph, on) }
        }
        note("Le téléphone d'un enfant ne reçoit rien tant que vous ne l'avez pas désigné ici. ${rp.outbox.count()} rapport(s) attendent la prochaine connexion du téléphone (conservés 14 jours au plus).")
        section("Quand")
        val times = (0 until 48).map { it * 30 }
        row("Résumé quotidien à ${TimeWindow.fmt(cfg.dailyAtMin)}", "L'heure à laquelle le résumé du jour part. OK pour changer.") {
            ParentalUi.choose(this@ParentalActivity, "Heure du résumé", times.map { TimeWindow.fmt(it) }, times.indexOf(cfg.dailyAtMin)) { i -> rp.saveConfig(cfg.copy(dailyAtMin = times[i]), null); render() }
        }
        row("Résumé hebdomadaire : ${dayNames[cfg.weeklyDow - 1].lowercase()}", "Jour d'envoi, à la même heure. OK pour changer.") {
            ParentalUi.choose(this@ParentalActivity, "Jour du résumé de la semaine", dayNames, cfg.weeklyDow - 1) { i -> rp.saveConfig(cfg.copy(weeklyDow = i + 1), null); render() }
        }
        section("Par enfant")
        for (p in e.config().profiles) row(p.name, optionsText(cfg.options(p.id))) { push(profileReportPage(p.id)) }
        row("Envoyer un rapport maintenant", "Le résumé du jour de chaque enfant, pour tester la liaison") {
            val n = rp.sendNow()
            toast(if (n == 0) "Aucun téléphone désigné : rien à envoyer." else "$n rapport(s) prêt(s) : le téléphone les recevra à sa prochaine connexion.")
            render()
        }
    }

    private fun optionsText(o: ProfileReportOptions) = buildList {
        add(if (o.daily) "résumé quotidien" else "pas de résumé quotidien"); add(if (o.weekly) "résumé hebdo" else "pas de résumé hebdo")
        val alerts = listOfNotNull("limite".takeIf { o.alertLimit }, "application bloquée".takeIf { o.alertBlocked }, "altération".takeIf { o.alertTamper }, "nouvelle application".takeIf { o.alertNewApp })
        add(if (alerts.isEmpty()) "aucune alerte" else "alertes : " + alerts.joinToString(", "))
    }.joinToString(" · ")

    private fun profileReportPage(id: String): Page = Page(e.config().profile(id)?.name ?: "Profil", "Ce qui est envoyé au téléphone du parent") {
        val rp = ParentalHub.reports
        val o = rp.config().options(id)
        fun change(f: (ProfileReportOptions) -> ProfileReportOptions) { val c = rp.config(); rp.saveConfig(c.copy(profiles = c.profiles + (id to f(c.options(id)))), null); render() }
        fun onOff(b: Boolean) = if (b) "oui" else "non"
        row("Résumé quotidien : ${onOff(o.daily)}", "Temps par application, contenus bloqués, état de la surveillance") { change { it.copy(daily = !it.daily) } }
        row("Résumé hebdomadaire : ${onOff(o.weekly)}", "Les sept derniers jours") { change { it.copy(weekly = !it.weekly) } }
        section("Alertes immédiates")
        row("Temps ou heures atteints : ${onOff(o.alertLimit)}") { change { it.copy(alertLimit = !it.alertLimit) } }
        row("Application bloquée essayée : ${onOff(o.alertBlocked)}") { change { it.copy(alertBlocked = !it.alertBlocked) } }
        row("Surveillance affaiblie (accès retiré…) : ${onOff(o.alertTamper)}", "Valable pour toute la TV : envoyée si un profil la demande") { change { it.copy(alertTamper = !it.alertTamper) } }
        row("Nouvelle application installée : ${onOff(o.alertNewApp)}", "Valable pour toute la TV : envoyée si un profil la demande") { change { it.copy(alertNewApp = !it.alertNewApp) } }
    }

    /** Designating or removing a recipient always asks the parental PIN, even during a parent session. */
    private fun designate(ph: TrustedPhone, on: Boolean) {
        ParentalUi.pinDialog(this, if (on) "Retirer ${ph.name} des rapports" else "Désigner ${ph.name}", "Code parental (4 à 6 chiffres)",
            check = { pin -> ParentalHub.pinError(e.verifyPin(pin)) }) {
            val rp = ParentalHub.reports
            val err = if (on) rp.recipients.remove(ph.address, pinVerified = true).also { rp.outbox.dropRecipient(ph.address) } else rp.recipients.designate(ph.address, pinVerified = true)
            toast(err ?: if (on) "${ph.name} ne reçoit plus les rapports." else "${ph.name} recevra les rapports à sa prochaine connexion Bluetooth.")
            render()
        }
    }
}
