package castbridge.receiver

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import castbridge.core.learn.ExerciseTier
import castbridge.core.learn.LearnCatalog
import castbridge.core.learn.LearnProgress
import castbridge.core.learn.MockExamSession
import castbridge.core.learn.Pack
import castbridge.core.learn.PackInstaller
import castbridge.core.learn.EmbeddedLessonSource
import castbridge.core.learn.LearnFormat
import castbridge.core.learn.LearnLotCatalog
import castbridge.core.learn.PackRef
import castbridge.core.learn.Profile
import castbridge.core.learn.ReviewStatus
import castbridge.core.quiz.Json
import castbridge.core.tv.TransferRule
import java.time.LocalDate
import java.util.Locale

/**
 * « Apprendre » on the TV (docs/LEARN.md): students' profiles → home (prepare my exam, resume, reviews, rewards,
 * browse the curriculum) → subject packs → lessons, exercises, mock exams. Plain Views laid out on a 1280 × 720 grid
 * scaled to the screen ([LearnStyle]); only LinearLayouts, so no text can be drawn over another. The phone can drive
 * every screen (/api/learn/cmd), which is also the « mode classe » (teacher shows the lesson in big on the TV).
 */
class LearnActivity : Activity() {
    lateinit var st: LearnStyle
    lateinit var content: FrameLayout
    val main = Handler(Looper.getMainLooper())
    private val stack = ArrayList<Screen>()
    var profile: Profile? = null
    private var tts: TextToSpeech? = null
    @Volatile var ttsReady = false; private set
    /** Pages of nursery / primary lessons are read aloud when shown. */
    var autoRead = true
    var teacher = false; private set

    abstract class Screen(val a: LearnActivity) {
        abstract val name: String
        abstract fun build(): View
        open fun shown() {}
        open fun key(code: Int, e: KeyEvent): Boolean = false
        /** true = BACK handled here. */
        open fun back(): Boolean = false
        open fun leave() {}
        open fun state(): Map<String, Any?> = emptyMap()
        open fun remote(action: String, p: Map<String, String>): String? = when (action) {
            "ok" -> { a.currentFocus?.performClick(); null }
            "next" -> { a.moveFocus(View.FOCUS_RIGHT); null }
            "prev" -> { a.moveFocus(View.FOCUS_LEFT); null }
            else -> "action « $action » impossible sur cet écran"
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        LearnHub.init(this)
        st = LearnStyle(this)
        content = FrameLayout(this).apply { setBackgroundColor(LearnStyle.BG) }
        setContentView(content)
        tts = runCatching { TextToSpeech(this) { s -> ttsReady = s == TextToSpeech.SUCCESS } }.getOrNull()
        val wanted = intent?.getStringExtra("profile")
        profile = LearnHub.progress().profile(wanted)
        push(if (profile != null) LearnHomeScreen(this) else ProfilesScreen(this))
    }

    private val meter = Handler(Looper.getMainLooper())
    private val meterTick = object : Runnable { override fun run() { Thread { RentalHub.meterOneMinute(applicationContext) }.start(); meter.postDelayed(this, 60_000L) } }
    override fun onResume() { super.onResume(); LearnHub.screen = this; meter.postDelayed(meterTick, 60_000L) }
    override fun onPause() { super.onPause(); meter.removeCallbacks(meterTick); if (LearnHub.screen === this) LearnHub.screen = null; LearnHub.save() }
    override fun onDestroy() { stack.lastOrNull()?.leave(); runCatching { tts?.shutdown() }; super.onDestroy() }

    fun top(): Screen? = stack.lastOrNull()
    fun push(s: Screen) { top()?.leave(); stack += s; show(s) }
    fun replace(s: Screen) { top()?.leave(); stack.removeLastOrNull(); stack += s; show(s) }
    fun pop() { top()?.leave(); stack.removeLastOrNull(); top()?.let { show(it) } ?: finish() }
    private fun show(s: Screen) { rebuild(); s.shown() }

    /** Rebuilds the current screen (after a page change, an answer…). */
    fun rebuild(focus: Boolean = true) {
        val s = top() ?: return
        content.removeAllViews()
        content.addView(s.build(), FrameLayout.LayoutParams(-1, -1))
        if (focus) content.post { val f = currentFocus; if (f == null || !f.isShown || !f.isClickable) firstFocusable(content)?.requestFocus() }
    }

    private fun firstFocusable(v: View): View? {
        if (v.isFocusable && v.isShown && v.visibility == View.VISIBLE && v !is ScrollView) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) firstFocusable(v.getChildAt(i))?.let { return it }
        return null
    }

    fun moveFocus(dir: Int) { currentFocus?.focusSearch(dir)?.requestFocus() }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN && e.keyCode != KeyEvent.KEYCODE_BACK && top()?.key(e.keyCode, e) == true) return true
        return super.dispatchKeyEvent(e)
    }

    @Deprecated("TV remote BACK")
    override fun onBackPressed() {
        if (top()?.back() == true) return
        stopSpeaking()
        if (stack.size > 1) pop() else finish()
    }

    // ------------------------------------------------------------------ helpers shared by the screens

    /** Page frame: title + subtitle, profile chip, a progress bar, the body (takes the rest), a hint line at the bottom. */
    fun frame(title: String, sub: String?, body: View, hint: String?, progress: Float = -1f, pageLabel: String? = null): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(st.px(48), st.px(24), st.px(48), st.px(16)); clipChildren = true }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        head.addView(TvStyle.logo(this, R.drawable.logo_apprendre, 52).apply { contentDescription = "Apprendre"; (layoutParams as LinearLayout.LayoutParams).rightMargin = st.px(16) })
        titles.addView(st.text(title, 30f, Color.WHITE, true, 1))
        sub?.let { titles.addView(st.text(it, 19f, LearnStyle.MUTED, false, 1)) }
        head.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        pageLabel?.takeIf { it.isNotEmpty() }?.let { head.addView(st.text(it, 22f, LearnStyle.MUTED), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = st.px(18) }) }
        if (teacher) head.addView(st.text("● " + "Mode classe", 18f, LearnStyle.GOLD, true), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = st.px(14) })
        profile?.let { p -> head.addView(avatarChip(p)) }
        root.addView(head)
        if (progress >= 0) root.addView(BarView(this, LearnStyle.ACCENT).apply { fraction = progress }, LinearLayout.LayoutParams(-1, st.px(5)).apply { topMargin = st.px(10) })
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = st.px(16); bottomMargin = st.px(8) })
        val hv = st.text(hint ?: "", 18f, LearnStyle.MUTED, false, 1)
        hintView = hv; hintText = hint
        root.addView(hv)
        return root
    }

    fun avatarChip(p: Profile): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(AvatarView(this@LearnActivity, p), LinearLayout.LayoutParams(st.px(40), st.px(40)).apply { rightMargin = st.px(10) })
        addView(st.text(p.name, 22f, Color.WHITE, true, 1))
    }

    /** Short message shown in place of the hint line (a system toast would be drawn over the page's text). */
    fun toast(m: String) {
        val h = hintView ?: return Toast.makeText(this, m, Toast.LENGTH_LONG).show()
        val before = hintText
        h.text = m; h.setTextColor(LearnStyle.GOLD); h.maxLines = 3
        main.removeCallbacks(restoreHint); restoreHint = Runnable { if (hintView === h) { h.text = before; h.setTextColor(LearnStyle.MUTED); h.maxLines = 1 } }
        main.postDelayed(restoreHint, 6000)
    }
    private var hintView: TextView? = null
    private var hintText: String? = null
    private var restoreHint = Runnable {}

    fun confirm(title: String, msg: String, yes: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("Oui") { _, _ -> yes() }.setNegativeButton("Non", null).show()
    }

    fun speak(text: String, lang: String) {
        val t = tts ?: return
        if (!ttsReady || text.isBlank()) return
        runCatching {
            t.language = if (lang.startsWith("en")) Locale.UK else Locale.FRANCE
            t.setSpeechRate(0.92f)
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "learn")
        }
    }

    fun stopSpeaking() { runCatching { tts?.stop() } }

    fun setTeacher(on: Boolean) { teacher = on; st.scale = if (on) 1.15f else 1f; rebuild() }

    // ------------------------------------------------------------------ phone remote (LearnHub → /api/learn/cmd), main thread

    fun remote(action: String, p: Map<String, String>): String? = when (action) {
        "back" -> { onBackPressed(); null }
        "teacher" -> { setTeacher(p["value"] != "off"); null }
        "lesson" -> {
            val pack = p["pack"]?.let { LearnHub.library().pack(it) } ?: return "pack introuvable"
            val l = p["lesson"]?.let { pack.lesson(it) } ?: pack.lessons.firstOrNull() ?: return "fiche introuvable"
            val r = ReaderScreen(this, pack, l, p["page"]?.toIntOrNull() ?: 0)
            if (top() is ReaderScreen) replace(r) else push(r); null   // the teacher jumps from fiche to fiche: BACK still leads to the menu
        }
        else -> top().let { if (it == null) "aucun écran" else it.remote(action, p) }
    }

    fun stateJson(): String = Json.write(linkedMapOf("screen" to top()?.name, "profile" to profile?.let { linkedMapOf("id" to it.id, "name" to it.name) },
        "teacher" to teacher, "state" to (top()?.state() ?: emptyMap<String, Any?>())))

    // ------------------------------------------------------------------ data helpers

    fun packs(): List<PackRef> = LearnHub.library().packs()
    fun openPack(ref: PackRef): Pack? = LearnHub.library().pack(ref.id) ?: run { toast("Pack « ${ref.manifest.title} » illisible : ${LearnHub.library().problems.values.lastOrNull() ?: "?"}"); null }
    fun subjectColor(key: String) = LearnCatalog.subject(key)?.color ?: LearnStyle.ACCENT
    fun starsOf(pack: Pack): Int = profile?.let { p -> LearnHub.progress().state.of(p.id).lessons.values.filter { it.pack == pack.id }.sumOf { it.stars } } ?: 0
}

/** Round avatar: a colour and the initial (emoji fonts are missing on some TVs). */
class AvatarView(a: Activity, private val p: Profile) : View(a) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val r = minOf(width, height) / 2f
        paint.color = COLORS[p.avatar % COLORS.size]; c.drawCircle(width / 2f, height / 2f, r, paint)
        paint.color = Color.WHITE; paint.textAlign = Paint.Align.CENTER; paint.textSize = r * 1.1f; paint.isFakeBoldText = true
        c.drawText(p.name.take(1).uppercase(), width / 2f, height / 2f + r * 0.38f, paint)
    }
    companion object {
        val COLORS = intArrayOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFB8C00.toInt(), LearnStyle.ACCENT, 0xFF00ACC1.toInt(),
            0xFFD81B60.toInt(), 0xFF6D4C41.toInt(), 0xFF3949AB.toInt(), 0xFF7CB342.toInt(), 0xFFF4511E.toInt(), 0xFF546E7A.toInt())
    }
}

// ====================================================================== menus

/** Who is learning? Up to 6 students (first name + avatar + class), or the whole class (« mode classe »). */
class ProfilesScreen(a: LearnActivity) : LearnActivity.Screen(a) {
    override val name = "profiles"
    override fun build(): View {
        a.profile = null
        val pr = LearnHub.progress()
        val tiles = ArrayList<View>()
        for (p in pr.profiles) {
            val lv = p.level?.let { LearnCatalog.level(it)?.label } ?: "—"
            tiles += LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(a.st.px(14), a.st.px(18), a.st.px(14), a.st.px(14))
                addView(AvatarView(a, p), LinearLayout.LayoutParams(a.st.px(96), a.st.px(96)))
                addView(a.st.text(p.name, 26f, Color.WHITE, true, 1).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.st.px(10) })
                addView(a.st.text(lv, 19f, LearnStyle.MUTED, false, 1).apply { gravity = Gravity.CENTER })
                a.st.focusable(this)
                setOnClickListener { a.profile = p; a.push(LearnHomeScreen(a)) }
                setOnLongClickListener { manage(p); true }
            }
        }
        if (pr.profiles.size < LearnProgress.MAX_PROFILES) tiles += a.st.tile("Ajouter un élève", "prénom, avatar, classe", LearnStyle.GOOD, "+") { a.push(NewProfileScreen(a)) }
        tiles += a.st.tile("Mode classe", "leçon en grand, pilotée depuis le téléphone", LearnStyle.GOLD, "▣") { a.setTeacher(!a.teacher); a.push(BrowseScreen(a)) }
        tiles += a.st.tile("Contenus", "mes classes, clé USB, envoi du téléphone", LearnStyle.ACCENT, "⇩") { a.push(ContentsScreen(a)) }
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(a.st.text("Qui apprend aujourd'hui ?", 36f, Color.WHITE, true))
        col.addView(a.st.text("Les progrès restent sur cette TV : juste un prénom, pas de données personnelles. Appui long sur un profil : modifier ou supprimer.", 20f, LearnStyle.MUTED), col.lp(bottom = a.st.px(12)))
        col.addView(a.st.grid(tiles, 4, 20, 230))
        return a.frame("Apprendre", "De la maternelle à la licence · préparer le CEP, le FSLC, le BEPC, le GCE, le Probatoire, le Bac", ScrollView(a).apply { clipChildren = true; addView(col) },
            "Flèches : choisir   ·   OK : ouvrir   ·   RETOUR : quitter")
    }

    private fun manage(p: Profile) {
        AlertDialog.Builder(a).setTitle(p.name).setItems(arrayOf("Changer la classe…", "Supprimer ce profil et ses progrès")) { _, i ->
            if (i == 0) a.push(NewProfileScreen(a, p))
            else a.confirm("Supprimer ${p.name} ?", "Ses étoiles, badges et progrès seront effacés de la TV.") { LearnHub.progress().removeProfile(p.id); LearnHub.save(); a.rebuild() }
        }.setNegativeButton("Fermer", null).show()
    }
}

/** New student (or change of class): first name with the TV keyboard, an avatar colour, the class. */
class NewProfileScreen(a: LearnActivity, private val editing: Profile? = null) : LearnActivity.Screen(a) {
    override val name = "new-profile"
    private var avatar = editing?.avatar ?: 0
    private var level: String? = editing?.level
    private var nameText = editing?.name ?: ""
    override fun build(): View {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(a.st.text("Prénom (ou surnom)", 22f, LearnStyle.MUTED))
        val edit = EditText(a).apply {
            setText(nameText); setTextColor(Color.WHITE); a.st.size(this, 28f); isSingleLine = true; hint = "ex. Awa"; setHintTextColor(TvStyle.TEXT3)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS; isEnabled = editing == null
            background = a.st.rounded(LearnStyle.BG, 10f, LearnStyle.ACCENT, 2f); setPadding(a.st.px(16), a.st.px(8), a.st.px(16), a.st.px(8))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { nameText = s?.toString() ?: "" }
                override fun beforeTextChanged(s: CharSequence?, st0: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st0: Int, b: Int, c: Int) {}
            })
        }
        col.addView(edit, col.lp(-1, -2, bottom = a.st.px(14)))
        col.addView(a.st.text("Couleur de l'avatar", 22f, LearnStyle.MUTED))
        // selections are shown in place (a green frame): no rebuild, so the focus and the scroll stay where they are
        val avs = ArrayList<LinearLayout>()
        fun markAvatars() = avs.forEachIndexed { i, v -> a.st.focusable(v, if (i == avatar) LearnStyle.GOOD else LearnStyle.CARD) }
        AvatarView.COLORS.indices.forEach { i ->
            avs += LinearLayout(a).apply {
                gravity = Gravity.CENTER; setPadding(a.st.px(6), a.st.px(6), a.st.px(6), a.st.px(6))
                addView(AvatarView(a, Profile("x", nameText.ifBlank { "?" }, i, null)), LinearLayout.LayoutParams(a.st.px(56), a.st.px(56)))
                setOnClickListener { avatar = i; markAvatars() }
            }
        }
        markAvatars()
        col.addView(a.st.grid(avs, 12, 8), col.lp(bottom = a.st.px(14)))
        val classLabel = a.st.text("", 22f, LearnStyle.MUTED)
        col.addView(classLabel)
        val lvButtons = ArrayList<Pair<String, LinearLayout>>()
        fun markLevels() {
            classLabel.text = "Classe" + (level?.let { " : " + (LearnCatalog.level(it)?.label ?: it) } ?: " (choisis-la ci-dessous)")
            lvButtons.forEach { (k, b) -> a.st.focusable(b, if (k == level) LearnStyle.GOOD else LearnStyle.CARD) }
        }
        for (c in LearnCatalog.cursus) {
            col.addView(a.st.text(c.label, 20f, Color.WHITE, true), col.lp(top = a.st.px(8)))
            val lv = LearnCatalog.levelsOf(c.key).map { l -> a.st.button(l.label, size = 20f) { level = l.key; markLevels() }.also { lvButtons += l.key to it } }
            col.addView(a.st.grid(lv, 7, 8))
        }
        markLevels()
        col.addView(a.st.button(if (editing == null) "Créer le profil" else "Enregistrer", fill = LearnStyle.GOOD) { save() }, col.lp(-2, -2, top = a.st.px(16)))
        return a.frame(if (editing == null) "Nouvel élève" else "Modifier ${editing.name}", "6 profils au maximum sur la TV", ScrollView(a).apply { clipChildren = true; addView(col) }, "OK sur le champ : clavier de la TV   ·   RETOUR : annuler")
    }

    private fun save() {
        val pr = LearnHub.progress()
        if (editing != null) { pr.updateProfile(editing.copy(avatar = avatar, level = level)); LearnHub.save(); a.pop(); return }
        val (p, err) = pr.addProfile(nameText, avatar, level, System.currentTimeMillis())
        if (p == null) { a.toast(err ?: "impossible"); return }
        LearnHub.save(); a.profile = p; a.replace(LearnHomeScreen(a))
    }
}

/** A student's home: prepare my exam (with countdown), resume, reviews, rewards, then the whole curriculum. */
class LearnHomeScreen(a: LearnActivity) : LearnActivity.Screen(a) {
    override val name = "home"
    override fun build(): View {
        val p = a.profile ?: return ProfilesScreen(a).build()
        val pr = LearnHub.progress(); val sp = pr.state.of(p.id); val now = System.currentTimeMillis()
        val tiles = ArrayList<View>()
        val exam = p.exam?.let { LearnCatalog.exam(it) } ?: LearnCatalog.examsFor(p.level).firstOrNull()
        val days = pr.daysToExam(p, now)
        val nursery = LearnCatalog.cursusOfLevel(p.level)?.stage == LearnCatalog.Stage.NURSERY
        if (!nursery) tiles += a.st.tile("Préparer mon examen", exam?.let { e -> e.label + (days?.let { d -> if (d >= 0) " · J-$d" else " · date passée" } ?: "") } ?: "CEP, FSLC, BEPC, GCE, Probatoire, Bac",
            LearnStyle.GOLD, days?.takeIf { it >= 0 }?.let { "J-$it" } ?: "◎") { a.push(if (exam != null) ExamScreen(a, exam.key) else ExamPickScreen(a)) }
        sp.resume?.let { r ->
            val pack = runCatching { LearnHub.library().pack(r.pack) }.getOrNull(); val l = pack?.lesson(r.lesson)
            if (pack != null && l != null) tiles += a.st.tile("Reprendre", l.title, LearnStyle.ACCENT, "▶") { a.push(ReaderScreen(a, pack, l, r.page)) }
        }
        val due = pr.reviewCount(p.id, now)
        tiles += a.st.tile("Révisions", if (due > 0) "$due exercice(s) à revoir" else "rien à revoir aujourd'hui", LearnStyle.ACCENT, "$due") { reviews() }
        tiles += a.st.tile("Mes récompenses", "${sp.lessons.values.sumOf { it.stars }} ★ · ${sp.badges.size} badge(s) · série ${sp.streak} j", LearnStyle.GOLD, "★") { a.push(RewardsScreen(a)) }
        val lvl = p.level?.let { LearnCatalog.level(it) }
        val mine = a.packs().filter { it.manifest.level == p.level }
        val subjTiles = mine.map { r -> a.st.tile(LearnCatalog.subject(r.manifest.subject)?.label(r.manifest.lang) ?: r.manifest.title, "${r.manifest.lessons} fiches · ${r.manifest.exercises} exercices", a.subjectColor(r.manifest.subject)) {
            a.openPack(r)?.let { a.push(PackScreen(a, it)) } } }
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(a.st.grid(tiles, 4, 18, 200))
        col.addView(a.st.text(lvl?.let { "Ma classe : ${it.label}" } ?: "Ma classe", 24f, Color.WHITE, true), col.lp(top = a.st.px(14)))
        if (subjTiles.isEmpty()) col.addView(a.st.text("Aucun contenu installé pour cette classe. Voir « Tout le programme » ou « Contenus ».", 20f, LearnStyle.MUTED))
        else col.addView(a.st.grid(subjTiles, 4, 18, 170))
        col.addView(a.st.grid(listOf(
            a.st.button("Tout le programme", "maternelle → licence, francophone et anglophone") { a.push(BrowseScreen(a)) },
            a.st.button("Changer d'élève", "profils de la TV") { a.replace(ProfilesScreen(a)) },
            a.st.button("Contenus", "installer, mettre à jour, supprimer des packs") { a.push(ContentsScreen(a)) },
        ), 3, 18), col.lp(top = a.st.px(10)))
        return a.frame("Bonjour ${p.name} !", "Apprendre · ${lvl?.label ?: ""}", ScrollView(a).apply { clipChildren = true; addView(col) }, "OK : ouvrir   ·   RETOUR : changer d'élève")
    }

    private fun reviews() {
        val p = a.profile ?: return
        val due = LearnHub.progress().dueReviews(p.id, System.currentTimeMillis(), 10)
        if (due.isEmpty()) { a.toast("Rien à réviser aujourd'hui : les exercices ratés reviennent un jour ou plus après."); return }
        val lib = LearnHub.library()
        val items = due.mapNotNull { d -> lib.pack(d.pack)?.exercise(d.exercise)?.let { it to d.pack } }
        if (items.isEmpty()) { a.toast("Les packs de ces exercices ne sont plus installés."); return }
        val byId = items.associate { it.first.id to it.second }
        a.push(SeriesScreen(a, null, items.map { it.first }, "Révisions", review = true) { x -> byId[x.id]?.let { lib.pack(it) } })
    }
}

class ExamPickScreen(a: LearnActivity) : LearnActivity.Screen(a) {
    override val name = "exams"
    override fun build(): View {
        val packs = a.packs()
        val tiles = LearnCatalog.exams.map { e ->
            val n = packs.count { it.manifest.exam == e.key }
            a.st.tile(e.label, "${e.description}\n" + if (n > 0) "$n matière(s) disponible(s)" else "contenu à installer", if (n > 0) LearnStyle.GOLD else TvStyle.OUTLINE) {
                a.profile?.let { p -> LearnHub.progress().updateProfile(p.copy(exam = e.key)); a.profile = LearnHub.progress().profile(p.id); LearnHub.save() }
                a.replace(ExamScreen(a, e.key))
            }
        }
        return a.frame("Préparer mon examen", "Choisis ton examen", ScrollView(a).apply { clipChildren = true; addView(a.st.grid(tiles, 4, 18, 220)) }, "OK : choisir")
    }
}

/** One exam: countdown, its subjects (packs), missing ones to download. */
class ExamScreen(a: LearnActivity, private val exam: String) : LearnActivity.Screen(a) {
    override val name = "exam"
    override fun build(): View {
        val e = LearnCatalog.exam(exam)!!
        val packs = a.packs().filter { it.manifest.exam == exam || (exam == "BAC" && it.manifest.exam == "PROBATOIRE") || (exam == "PROBATOIRE" && it.manifest.exam == "BAC") }
        val p = a.profile
        val days = p?.let { LearnHub.progress().daysToExam(it, System.currentTimeMillis()) }
        val tiles = ArrayList<View>()
        tiles += a.st.tile("Date de mon examen", p?.examDate?.let { "le $it" + (days?.let { d -> if (d >= 0) " · J-$d" else "" } ?: "") } ?: "compte à rebours (facultatif)", LearnStyle.GOLD,
            days?.takeIf { it >= 0 }?.let { "J-$it" } ?: "▦") { a.push(ExamDateScreen(a, exam)) }
        for (r in packs) tiles += a.st.tile(LearnCatalog.subject(r.manifest.subject)?.label(r.manifest.lang) ?: r.manifest.title,
            "${r.manifest.lessons} fiches · ${r.manifest.exercises} exercices" + (if (r.manifest.mockExams > 0) " · épreuve blanche" else "") + (if (r.manifest.exam != exam) " · ${LearnCatalog.exam(r.manifest.exam)?.label}" else ""),
            a.subjectColor(r.manifest.subject)) { a.openPack(r)?.let { a.push(PackScreen(a, it)) } }
        val missing = EXAM_SUBJECTS[exam].orEmpty().filter { s -> packs.none { it.manifest.subject == s } }
        for (s in missing) tiles += a.st.tile(LearnCatalog.subject(s)?.label(e.lang) ?: s, "Pas encore sur la TV : à envoyer depuis le téléphone", TvStyle.OUTLINE, "⇩") {
            a.push(ContentsScreen(a, "Le pack « ${LearnCatalog.subject(s)?.fr ?: s} — ${e.label} » n'est pas installé."))
        }
        tiles += a.st.tile("Changer d'examen", null, TvStyle.OUTLINE) { a.replace(ExamPickScreen(a)) }
        return a.frame("${e.label}" + (days?.takeIf { it >= 0 }?.let { " · J-$it" } ?: ""), "${e.description} · ${e.organizer}", ScrollView(a).apply { clipChildren = true; addView(a.st.grid(tiles, 4, 18, 210)) },
            "Fiches « l'essentiel », exercices type examen corrigés, épreuve blanche chronométrée")
    }

    companion object {
        val EXAM_SUBJECTS = mapOf(
            "CEP" to listOf("maths", "francais", "sciences", "histoire-geo"), "FSLC" to listOf("maths", "english", "sciences"),
            "BEPC" to listOf("maths", "francais", "pct", "svt", "english", "histoire-geo"), "GCE-OL" to listOf("maths", "english", "biology", "physics", "chemistry"),
            "PROBATOIRE" to listOf("maths", "francais", "physique-chimie"), "BAC" to listOf("maths", "physique-chimie", "svt", "philosophie"),
            "GCE-AL" to listOf("maths", "physics", "chemistry", "biology"),
        )
    }
}

/** Exam date for the countdown: day / month / year changed with ▲ ▼. */
class ExamDateScreen(a: LearnActivity, private val exam: String) : LearnActivity.Screen(a) {
    override val name = "exam-date"
    private var date: LocalDate = a.profile?.examDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now().plusMonths(3)
    override fun build(): View {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(a.st.text("Sélectionne une case puis ▲ ▼ pour changer", 22f, LearnStyle.MUTED))
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun spin(label: String, value: String, change: (Int) -> Unit) = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(a.st.px(26), a.st.px(10), a.st.px(26), a.st.px(10))
            addView(a.st.text("▲", 22f, LearnStyle.MUTED).apply { gravity = Gravity.CENTER })
            addView(a.st.text(value, 48f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
            addView(a.st.text("▼", 22f, LearnStyle.MUTED).apply { gravity = Gravity.CENTER })
            addView(a.st.text(label, 18f, LearnStyle.MUTED).apply { gravity = Gravity.CENTER })
            a.st.focusable(this)
            setOnKeyListener { v, code, e ->
                if (e.action != KeyEvent.ACTION_DOWN) false
                else when (code) { KeyEvent.KEYCODE_DPAD_UP -> { change(1); a.rebuild(); refocus(label); true }; KeyEvent.KEYCODE_DPAD_DOWN -> { change(-1); a.rebuild(); refocus(label); true }; else -> false }
            }
            tag = label
        }
        row.addView(spin("jour", "%02d".format(date.dayOfMonth)) { date = date.plusDays(it.toLong()) })
        row.addView(spin("mois", "%02d".format(date.monthValue)) { date = date.plusMonths(it.toLong()) })
        row.addView(spin("année", "${date.year}") { date = date.plusYears(it.toLong()) })
        col.addView(row, col.lp(-2, -2, top = a.st.px(10), bottom = a.st.px(16)))
        val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date)
        col.addView(a.st.text(if (days >= 0) "Il reste $days jour(s)" else "Cette date est passée", 28f, LearnStyle.GOLD, true))
        col.addView(a.st.grid(listOf(
            a.st.button("Enregistrer") { save(date.toString()) },
            a.st.button("Pas de date") { save(null) },
        ), 2, 18), col.lp(-1, -2, top = a.st.px(16)))
        return a.frame("Date de l'examen", LearnCatalog.exam(exam)?.label, col, "La date officielle est publiée par les autorités : saisis-la toi-même.")
    }

    private fun refocus(tag: String) = a.content.post { a.content.findViewWithTag<View>(tag)?.requestFocus() }

    private fun save(d: String?) {
        val p = a.profile ?: run { a.pop(); return }
        LearnHub.progress().updateProfile(p.copy(exam = exam, examDate = d)); a.profile = LearnHub.progress().profile(p.id); LearnHub.save(); a.pop()
    }
}

/** A subject pack: its chapters and fiches (with stars), exam-style series, mock exams. */
class PackScreen(a: LearnActivity, private val pack: Pack) : LearnActivity.Screen(a) {
    override val name = "pack"
    override fun build(): View {
        val p = a.profile
        val sp = p?.let { LearnHub.progress().state.of(it.id) }
        val en = pack.lang == "en"
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val actions = ArrayList<View>()
        pack.mockExams.forEach { m -> actions += a.st.tile(if (en) "Mock exam" else "Épreuve blanche", "${m.minutes} min · /20", LearnStyle.BAD, "⏱") { a.push(MockExamScreen(a, pack, m)) } }
        if (pack.mockExams.isEmpty() && pack.exam != null) MockExamSession.auto(pack, System.currentTimeMillis() / 86_400_000L)?.let { m ->
            actions += a.st.tile(if (en) "Mock exam" else "Épreuve blanche", if (en) "assembled from the exercises" else "assemblée à partir des exercices", LearnStyle.BAD, "⏱") { a.push(MockExamScreen(a, pack, m)) }
        }
        val examSet = pack.exercises.filter { (it.tier == ExerciseTier.EXAM || it.tier == ExerciseTier.DEEPER) && !it.review && pack.mockExams.none { m -> it.id in m.exerciseIds } }
        if (examSet.isNotEmpty()) actions += a.st.tile(if (en) "Exam-style exercises" else "Exercices type examen", "${examSet.size} " + (if (en) "corrected" else "corrigés"), LearnStyle.GOLD, "✎") {
            a.push(SeriesScreen(a, pack, examSet, (if (en) "Exam-style — " else "Type examen — ") + pack.title))
        }
        val selfs = pack.exercises.filter { it.tier == ExerciseTier.SELFCHECK }
        if (selfs.isNotEmpty()) actions += a.st.tile(if (en) "Quick quiz" else "Quiz express", "${minOf(10, selfs.size)} " + (if (en) "questions" else "questions"), 0xFF00ACC1.toInt(), "?") {
            a.push(SeriesScreen(a, pack, selfs.shuffled().take(10), (if (en) "Quick quiz — " else "Quiz express — ") + pack.title))
        }
        if (actions.isNotEmpty()) col.addView(a.st.grid(actions, 4, 18, 200))
        fun ficheTile(l: castbridge.core.learn.Lesson): View {
            val s = sp?.lessons?.get(l.id)
            val stars = s?.stars ?: 0
            return a.st.tile(l.title, "★".repeat(stars) + "☆".repeat(3 - stars) + "  ·  ${l.minutes} min" + (if (s?.completed == true) (if (en) " · done" else " · terminée") else if (s?.seen == true) (if (en) " · started" else " · commencée") else ""),
                a.subjectColor(pack.subject)) { a.push(ReaderScreen(a, pack, l)) }
        }
        if (pack.chapters.all { pack.lessonsOf(it.id).size <= 1 }) {
            // one fiche per chapter: a single grid (no chapter title repeating the fiche title)
            col.addView(a.st.text(if (en) "Lessons" else "Fiches", 24f, Color.WHITE, true), col.lp(top = a.st.px(12)))
            col.addView(a.st.grid(pack.chapters.flatMap { pack.lessonsOf(it.id) }.map { ficheTile(it) }, 3, 18, 170))
        } else for (c in pack.chapters) {
            val ls = pack.lessonsOf(c.id); if (ls.isEmpty()) continue
            col.addView(a.st.text(c.title, 24f, Color.WHITE, true), col.lp(top = a.st.px(12)))
            col.addView(a.st.grid(ls.map { ficheTile(it) }, 3, 18, 170))
        }
        val status = if (pack.status == ReviewStatus.VALIDATED) "Contenu certifié" else "Brouillon à relire par un enseignant"
        return a.frame(pack.title, "${pack.level} · ${status} · ${a.starsOf(pack)} ★", ScrollView(a).apply { clipChildren = true; addView(col) }, if (en) "OK: open" else "OK : ouvrir")
    }
}

/** The whole curriculum: sub-system and stage → level → subjects available. */
class BrowseScreen(a: LearnActivity, private val cursus: String? = null, private val level: String? = null) : LearnActivity.Screen(a) {
    override val name = "browse"
    override fun build(): View {
        val packs = a.packs()
        val tiles: List<View> = when {
            cursus == null -> LearnCatalog.cursus.map { c ->
                val n = packs.count { LearnCatalog.level(it.manifest.level)?.cursus == c.key }
                a.st.tile(c.label, if (n > 0) "$n pack(s)" else "à venir", if (n > 0) LearnStyle.ACCENT else TvStyle.OUTLINE) { a.push(BrowseScreen(a, c.key)) }
            }
            level == null -> LearnCatalog.levelsOf(cursus).map { l ->
                val n = packs.count { it.manifest.level == l.key }
                a.st.tile(l.label, if (n > 0) "$n matière(s)" else "à venir", if (n > 0) LearnStyle.ACCENT else TvStyle.OUTLINE) { a.push(BrowseScreen(a, cursus, l.key)) }
            }
            else -> packs.filter { it.manifest.level == level }.map { r ->
                a.st.tile(LearnCatalog.subject(r.manifest.subject)?.label(r.manifest.lang) ?: r.manifest.title, "${r.manifest.lessons} fiches · ${r.manifest.exercises} exercices", a.subjectColor(r.manifest.subject)) {
                    a.openPack(r)?.let { a.push(PackScreen(a, it)) }
                }
            }.ifEmpty { listOf(a.st.tile("Rien d'installé ici", "Voir « Contenus »", TvStyle.OUTLINE) { a.push(ContentsScreen(a)) }) }
        }
        val title = when { cursus == null -> "Tout le programme"; level == null -> LearnCatalog.cursus(cursus)?.label ?: ""; else -> LearnCatalog.level(level)?.label ?: level }
        return a.frame(title, if (a.teacher) "Mode classe : la leçon s'affiche en grand, le téléphone peut la piloter" else "Apprendre", ScrollView(a).apply { clipChildren = true; addView(a.st.grid(tiles, 4, 18, 180)) }, "OK : ouvrir   ·   RETOUR : revenir")
    }
}

class RewardsScreen(a: LearnActivity) : LearnActivity.Screen(a) {
    override val name = "rewards"
    override fun build(): View {
        val p = a.profile ?: return View(a)
        val pr = LearnHub.progress(); val sp = pr.state.of(p.id)
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(a.st.text("${sp.lessons.values.sumOf { it.stars }} ★   ·   ${sp.lessons.values.count { it.completed }} fiche(s) terminée(s)   ·   série de ${sp.streak} jour(s)", 30f, LearnStyle.GOLD, true))
        col.addView(a.st.text("Badges", 26f, Color.WHITE, true), col.lp(top = a.st.px(14)))
        col.addView(a.st.grid(LearnProgress.BADGES.map { (k, label) ->
            val on = k in sp.badges
            a.st.tile(label, if (on) "gagné !" else "à gagner", if (on) LearnStyle.GOLD else TvStyle.OUTLINE, if (on) "✪" else "·") {}
        }, 3, 16, 150))
        if (sp.mocks.isNotEmpty()) {
            col.addView(a.st.text("Épreuves blanches", 26f, Color.WHITE, true), col.lp(top = a.st.px(14)))
            sp.mocks.takeLast(8).reversed().forEach { m -> col.addView(a.st.text("${LearnCatalog.subject(m.subject)?.fr ?: m.subject} : ${castbridge.core.learn.Scene.fmt(m.score)} / 20", 22f)) }
        }
        return a.frame("Mes récompenses", p.name, ScrollView(a).apply { clipChildren = true; addView(col) }, "RETOUR : revenir")
    }
}

/** « Contenus »: packs found (where, size, version), packs refused, import from the phone / USB drive, delete. */
class ContentsScreen(a: LearnActivity, private val note: String? = null) : LearnActivity.Screen(a) {
    override val name = "contents"
    override fun build(): View {
        val lib = LearnHub.library()
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        note?.let { col.addView(a.st.text(it, 24f, LearnStyle.GOLD, true), col.lp(bottom = a.st.px(8))) }
        col.addView(a.st.text("Installer un pack (fichier .learn.zip) : copiez-le dans le dossier CastBridge/Packs d'une clé USB " +
            "(ou Android/data/castbridge.receiver/files/CastBridge/Packs si la TV ne lit pas la racine), ou envoyez-le depuis l'app du téléphone. " +
            "Les mises à jour des classes arrivent par le téléphone dès qu'il peut parler à la TV : la TV n'a pas besoin d'Internet. Les vidéos ne sont jamais dans les packs.", 19f, LearnStyle.MUTED), col.lp(bottom = a.st.px(10)))
        // « Mes classes » : the lots held by this TV and how fresh their data is (the starter packs of the APK are listed below)
        col.addView(a.st.text("Mes classes sur cette TV", 24f, Color.WHITE, true), col.lp(bottom = a.st.px(6)))
        val classes = LearnLotCatalog(LearnHub.lots(), EmbeddedLessonSource()).classes()
        for (c in classes) col.addView(a.st.text("${c.title} · ${c.lessons} fiches · " + (c.meta?.let { "v${it.version} · ${LearnFormat.size(it.bytes)} · ${LearnFormat.dataDate(c.date)}" } ?: "contenu de démarrage de l'app"), 19f, LearnStyle.MUTED), col.lp(bottom = a.st.px(4)))
        val received = LearnHub.libraryPackFiles()
        val acts = ArrayList<View>()
        acts += a.st.button("Rechercher à nouveau", "clé USB, mémoire de la TV") { lib.forget(); a.rebuild() }
        if (received.isNotEmpty()) acts += a.st.button("Installer les packs reçus (${received.size})", "envoyés depuis le téléphone") {
            val msgs = received.map { f -> when (val r = LearnHub.importFile(f.name)) { is PackInstaller.Result.Installed -> "✓ ${r.manifest.title} → ${r.where}"; is PackInstaller.Result.Refused -> "✗ ${f.name} : ${r.reason}" } }
            a.toast(msgs.joinToString("\n")); a.rebuild()
        }
        val onUsb = lib.all().filter { it.removable }
        if (onUsb.isNotEmpty()) acts += a.st.button("Copier les packs de la clé sur la TV (${onUsb.size})", "pour s'en servir sans la clé") {
            val msgs = onUsb.map { r -> runCatching { r.bytes().use { it.readBytes() } }.getOrNull()?.let { b ->
                val targets = LearnHub.installTargets().filter { t -> !t.label.contains("(racine)") && LearnHub.packDirs().none { d -> d.second == t.dir && d.third } }
                when (val res = PackInstaller().install(b, targets)) { is PackInstaller.Result.Installed -> "✓ ${res.manifest.title}"; is PackInstaller.Result.Refused -> "✗ ${r.manifest.title} : ${res.reason}" }
            } ?: "✗ ${r.manifest.title} : illisible" }
            lib.forget(); a.toast(msgs.joinToString("\n")); a.rebuild()
        }
        col.addView(a.st.grid(acts, 3, 14))
        col.addView(a.st.text("Packs disponibles", 24f, Color.WHITE, true), col.lp(top = a.st.px(12)))
        val best = lib.packs().map { it.toString() }.toSet()
        for (r in lib.all().sortedWith(compareBy({ it.manifest.level }, { it.manifest.subject }))) {
            val used = r.toString() in best
            val line = "${r.manifest.title}  ·  v${r.version}  ·  ${TransferRule.size(r.manifest.size, up = true)}  ·  ${r.origin}" + if (!used) "  ·  (autre version utilisée)" else ""
            col.addView(a.st.button(line, "${r.manifest.lessons} fiches · ${r.manifest.exercises} exercices" + if (r.file != null) " · OK : supprimer" else " · embarqué dans l'app", size = 21f) {
                if (r.file == null) return@button
                a.confirm("Supprimer ${r.manifest.title} ?", "Le fichier ${r.file?.name} sera effacé (${r.origin}). Les progrès des élèves sont gardés.") {
                    LearnHub.remove(r)?.let { a.toast(it) }; a.rebuild()
                }
            }, col.lp(bottom = a.st.px(6)))
        }
        if (lib.problems.isNotEmpty()) {
            col.addView(a.st.text("Packs refusés (altérés ou invalides)", 24f, LearnStyle.BAD, true), col.lp(top = a.st.px(12)))
            lib.problems.forEach { (k, v) -> col.addView(a.st.text("$k : $v", 19f, LearnStyle.MUTED)) }
        }
        return a.frame("Contenus", "Socle embarqué + packs par examen et matière", ScrollView(a).apply { clipChildren = true; addView(col) }, "OK : choisir   ·   RETOUR : revenir")
    }
}
