package castbridge.receiver

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.VideoView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.langues.*

/**
 * « Langues » (docs/LANGUES.md): the smallest honest viewer. Languages and levels come from the installed `langues` lots; a pack opens
 * on its units (vocabulary, dialogues, grammar notes, stories as text), then the exercises of the unit, marked with [LangMarking].
 * D-pad only: every line is a focusable button. Illustrations (word images, with the synthetic mention), sounds and short silent videos (starter copy when no media lot is installed; full-screen player, OK = pause, BACK = back to the same button) and commented corrections are shown. Not done yet: the
 * « match » and « strokes » exercises, spaced repetition cards, placement test, progress saving, animations.
 */
class LanguesActivity : Activity() {
    private lateinit var dx: Dx
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private var player: MediaPlayer? = null
    private lateinit var root: FrameLayout
    /** The full-screen video layer (null = none) and the button that opened it, which gets the focus back. */
    private var videoLayer: FrameLayout? = null; private var videoView: VideoView? = null; private var videoOpener: android.view.View? = null

    /** Navigation stack: each entry redraws one screen. */
    private val stack = ArrayList<Runnable>()
    private val packs get() = LanguesHub.packs(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        dx = Dx(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dx.px(96), dx.px(56), dx.px(96), dx.px(56)) }
        scroll = ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(GamesColors.BG_TOP, GamesColors.BG)); isFillViewport = true; addView(body)
        }
        root = FrameLayout(this).apply { addView(scroll, FrameLayout.LayoutParams(-1, -1)) }
        setContentView(root)
        go { home() }
    }

    override fun onResume() { super.onResume(); LanguesHub.screen = this }
    override fun onPause() { if (LanguesHub.screen === this) LanguesHub.screen = null; stopAudio(); closeVideo(); super.onPause() }
    /** The screen is no longer visible (Home, another app): the download stops (a lot already installed stays; the next press resumes). */
    override fun onStop() { if (updating) cancelUpdate = true; super.onStop() }

    /** A lot arrived or was removed: back to the first screen, with fresh data. */
    fun reload() {
        if (updating) { reloadLater = true; return }       // a lot just installed by the update in progress: the update screen stays, the list is refreshed at the end
        stack.clear(); go { home() }
    }
    @Volatile private var updating = false
    private val cancelFlag = java.util.concurrent.atomic.AtomicBoolean(false)
    private var cancelUpdate: Boolean get() = cancelFlag.get(); set(v) = cancelFlag.set(v)
    private var reloadLater = false

    private fun go(screen: () -> Unit) { val r = Runnable { screen() }; stack.add(r); r.run() }
    /** While a video plays: OK / play-pause toggle the pause, BACK closes it, every other key is swallowed (the lesson underneath keeps its focus untouched). */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (videoLayer == null) return super.dispatchKeyEvent(e)
        if (e.keyCode == KeyEvent.KEYCODE_BACK) { if (e.action == KeyEvent.ACTION_UP) closeVideo(); return true }
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 && e.keyCode in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE)) {
            videoView?.let { v -> if (v.isPlaying) v.pause() else v.start() }
        }
        return true
    }
    @Deprecated("Deprecated in Java") override fun onBackPressed() {
        if (videoLayer != null) { closeVideo(); return }
        stopAudio()
        if (updating) cancelUpdate = true
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            stack[stack.lastIndex].run()
        } else {
            @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    // ---------------------------------------------------------------- building blocks
    private fun clear(title: String, sub: String? = null) {
        body.removeAllViews(); voiceNoticed = false; missingNoticed = false
        body.addView(dx.text(TextView(this).apply { text = title }, 64, GamesColors.TEXT_HIGH, true))
        if (sub != null) body.addView(dx.text(TextView(this).apply { text = sub; setLineSpacing(0f, 1.2f) }, 30, GamesColors.TEXT_MEDIUM), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dx.px(8) })
        scroll.scrollTo(0, 0)
    }
    private fun label(t: String, size: Int = 34, color: Int = GamesColors.TEXT_HIGH, bold: Boolean = false, top: Int = 14): TextView =
        dx.text(TextView(this).apply { text = t; setLineSpacing(0f, 1.2f) }, size, color, bold).also { body.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dx.px(top) }) }
    private fun heading(t: String) = label(t, 40, GamesColors.PRIMARY, true, 36)
    private fun button(t: String, primary: Boolean = false, onClick: () -> Unit): Button = Button(this).apply {
        text = t; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL; isFocusable = true; isFocusableInTouchMode = false
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dx.px(34).toFloat()); setTextColor(if (primary) GamesColors.ON_PRIMARY else GamesColors.TEXT_HIGH)
        background = dx.focusable(if (primary) GamesColors.PRIMARY else GamesColors.SURFACE, GamesColors.SURFACE_HIGH, 20)
        setPadding(dx.px(32), dx.px(18), dx.px(32), dx.px(18)); setOnClickListener { onClick() }
    }.also { body.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dx.px(14) }) }

    private fun focusFirst() { (0 until body.childCount).map { body.getChildAt(it) }.firstOrNull { it is Button || it is EditText }?.requestFocus() }

    // ---------------------------------------------------------------- screens
    private fun home() {
        val all = packs
        clear("Langues", "Leçons et exercices reçus du téléphone, sans Internet.")
        if (all.isEmpty()) label(LangCatalog.EMPTY_MESSAGE, 38, GamesColors.TEXT_MEDIUM, top = 48)
        for (l in LangCatalog.languages(all)) {
            val n = all.count { it.parts.target == l }
            button("${l.fr}   ·   $n lot(s)") { go { levels(l) } }
        }
        // only when the system says this TV has Internet, and only when pressed: the TV never fetches by itself (docs/LOTS.md)
        if (LanguesHub.canUpdate(this)) button("Mettre à jour les lots Langues", primary = all.isEmpty()) { go { update() } }
        focusFirst()
    }

    /** « Mettre à jour les lots Langues »: server -> TV, free Langues lots only, with progress and a plain result. Runs only while this screen is shown. */
    private fun update() {
        clear("Mise à jour des lots Langues", "Téléchargement depuis le serveur CastBridge, parce que vous l'avez demandé. Rien d'autre n'est envoyé.")
        if (castbridge.receiver.ActivationCenter.trial()) { label(castbridge.core.owner.TrialPolicy.MESSAGE, 34, GamesColors.ERROR, top = 40); button("Retour") { onBackPressed() }; focusFirst(); return }
        val fetcher = try { LanguesHub.fetcher(applicationContext) } catch (e: IllegalArgumentException) {
            label("L'adresse du serveur n'est pas sécurisée (HTTPS obligatoire) : utilisez le téléphone pour envoyer les leçons.", 34, GamesColors.ERROR, top = 40)
            button("Retour") { onBackPressed() }; focusFirst(); return
        }
        val status = label(if (LanguesHub.hasInternet(this)) "Connexion au serveur…" else "Demande d'Internet au téléphone…", 36, GamesColors.TEXT_HIGH, top = 40)   // relay-R1 : sans Internet, un téléphone synchronisé est sollicité
        val cancel = button("Annuler") { cancelUpdate = true; status.text = "Interruption…" }
        focusFirst()
        updating = true; cancelUpdate = false; reloadLater = false
        // the worker never holds the Activity (it may be destroyed meanwhile): weak references only, and the fetcher was built on the application context
        UpdateTask(java.lang.ref.WeakReference(this), java.lang.ref.WeakReference(status), java.lang.ref.WeakReference(cancel), fetcher, cancelFlag).start()
    }

    private class UpdateTask(
        private val act: java.lang.ref.WeakReference<LanguesActivity>,
        private val status: java.lang.ref.WeakReference<android.widget.TextView>,
        private val cancel: java.lang.ref.WeakReference<android.view.View>,
        private val fetcher: castbridge.core.lots.TvLotFetcher,
        private val flag: java.util.concurrent.atomic.AtomicBoolean,
    ) : Thread() {
        override fun run() {
            val report = runCatching { fetcher.run(progress = { p ->
                val pct = if (p.total > 0) p.done * 100 / p.total else 0
                act.get()?.runOnUiThread { if (act.get()?.updating == true) status.get()?.text = "Lot ${p.index} sur ${p.count} : ${p.id.scope}   ($pct %)" }
            }, cancelled = { flag.get() }) }.getOrNull()
            val a = act.get() ?: return
            a.runOnUiThread { a.finishUpdate(report, status.get(), cancel.get()) }
        }
    }

    private fun finishUpdate(report: castbridge.core.lots.TvLotFetcher.Report?, status: android.widget.TextView?, cancel: android.view.View?) {
        updating = false
        if (status == null || isFinishing || isDestroyed) return
        if (body.indexOfChild(status) < 0) { if (reloadLater) reload(); return }   // the user went back meanwhile: only refresh the list
        if (cancel != null) body.removeView(cancel)
        val ok = report?.ok == true
        status.text = report?.message() ?: "La mise à jour a échoué : réessayez plus tard."
        status.setTextColor(if (ok) GamesColors.SUCCESS else GamesColors.ERROR)
        report?.results?.filter { it.status == castbridge.core.lots.TvLotFetcher.Status.INSTALLED || it.status == castbridge.core.lots.TvLotFetcher.Status.UPDATED }
            ?.take(12)?.forEach { label("✓ ${it.id.scope}", 28, GamesColors.TEXT_MEDIUM, top = 4) }
        button("Retour aux langues", primary = true) { onBackPressed() }
        focusFirst()
    }

    private fun levels(l: Lang) {
        clear(l.fr, "Choisissez un niveau")
        val all = packs
        for (lv in LangCatalog.levels(all, l)) button("${lv.name}  (${lv.cefr})   ·   ${LangCatalog.packsFor(all, l, lv).size} thème(s)") { go { themes(l, lv) } }
        focusFirst()
    }

    private fun themes(l: Lang, lv: LangLevel) {
        clear("${l.fr} — ${lv.name}", "Choisissez un thème")
        for (p in LangCatalog.packsFor(packs, l, lv)) button("${p.title}   ·   ${p.units.size} unité(s)") { go { units(p) } }
        focusFirst()
    }

    private fun units(p: LangPack) {
        clear(p.title, "Départ : ${p.parts.source.fr}")
        for (u in p.units) button("${u.title}   ·   ${u.minutes} min") { go { unit(p, u) } }
        focusFirst()
    }

    private fun unit(p: LangPack, u: LangUnit) {
        clear(u.title, "${u.minutes} min")
        if (u.vocab.isNotEmpty()) {
            heading("Vocabulaire")
            for (v in u.vocab) {
                label("${v.term}" + (v.reading?.let { "   $it" } ?: "") + "   —   ${v.gloss}", 36); audioButton(p, v.audio, "Écouter : ${v.term}"); illustration(p, u, v)
                LangVisuals.videosAfter(p, u, v.id).forEach { videoButton(p, it) }
            }
            LangVisuals.videosAtEnd(p, u).forEach { videoButton(p, it) }
        }
        for (d in u.dialogues) {
            heading("Dialogue : ${d.title}")
            for (x in d.lines) {
                label((if (x.who.isNotBlank()) "${x.who} : " else "") + x.text + (x.reading?.let { "   ($it)" } ?: ""), 34)
                label(x.translation, 28, GamesColors.TEXT_MEDIUM, top = 2)
                audioButton(p, x.audio, "Écouter cette phrase")
            }
            audioButton(p, d.audio, "Écouter le dialogue")
        }
        for (g in u.grammar) {
            heading("Grammaire : ${g.title}"); label(plain(g.md), 32)
            for ((t, tr) in g.examples) label("$t   —   $tr", 32, GamesColors.TEXT_MEDIUM, top = 6)
        }
        for (s in u.stories) { heading("Histoire : ${s.title}"); for (x in s.paragraphs) { label(x.text, 34); label(x.translation, 28, GamesColors.TEXT_MEDIUM, top = 2) } }
        if (u.exercises.isNotEmpty()) { heading("À vous"); button("Faire les exercices (${u.exercises.size})", primary = true) { go { Run(p, u).show() } } }
        else label("Pas d'exercice dans cette unité.", 30, GamesColors.TEXT_MEDIUM, top = 30)
        button("Retour") { onBackPressed() }
        focusFirst()
    }

    /** The illustration of a word, when its image is available (installed media lot, else bundled with the starter): shown under the word, never focusable, with the synthetic mention. */
    private fun illustration(p: LangPack, u: LangUnit, v: VocabItem) {
        val m = LangVisuals.image(p, u, v) ?: return
        val bmp = LanguesHub.illustration(this, p, m) ?: return
        body.addView(android.widget.ImageView(this).apply {
            setImageBitmap(bmp); scaleType = android.widget.ImageView.ScaleType.FIT_START; adjustViewBounds = true; isFocusable = false
            contentDescription = m.alt ?: v.gloss
        }, LinearLayout.LayoutParams(dx.px(720), dx.px(480)).apply { topMargin = dx.px(10) })
        LangVisuals.notice(m)?.let { label(it, 24, GamesColors.TEXT_MEDIUM, top = 4) }
    }

    /** Restricted Markdown of the notes → plain text (the TV shows no formatting). */
    private fun plain(md: String) = md.replace("**", "").replace("*", "").replace("`", "")

    // ---------------------------------------------------------------- audio (installed media lot, else the copy bundled with the starter)
    private var voiceNoticed = false; private var missingNoticed = false
    private fun audioButton(p: LangPack, ref: String?, text: String) {
        if (ref == null) return
        val e = LangVisuals.audio(p, ref) ?: return
        val f = LanguesHub.audioFile(this, p, e)
        if (f == null) { if (!missingNoticed) { missingNoticed = true; label("Audio non disponible sur la TV.", 26, GamesColors.TEXT_MEDIUM, top = 6) } }
        else {
            button(text) { play(f) }
            // one discreet mention per screen, under the first sound
            if (!voiceNoticed) LangVisuals.voiceNotice(e)?.let { voiceNoticed = true; label(it, 24, GamesColors.TEXT_MEDIUM, top = 4) }
        }
    }
    private fun play(f: java.io.File) {
        stopAudio()
        player = runCatching { MediaPlayer().apply { setDataSource(f.path); prepare(); start(); setOnCompletionListener { stopAudio() } } }
            .onFailure { android.util.Log.w("Langues", "lecture audio impossible : ${f.name}", it) }.getOrNull()
    }
    private fun stopAudio() { runCatching { player?.release() }; player = null }

    // ---------------------------------------------------------------- video (installed media lot, else the copy bundled with the starter; played full screen, stops at the end)
    /** « Voir la vidéo (muette) » + the synthetic mention; a video the TV cannot read gives one line « Vidéo non disponible » (also shown if the player fails). */
    private fun videoButton(p: LangPack, c: LangVisuals.UnitClip) {
        val f = LanguesHub.videoFile(this, p, c.media)
        if (f == null) { label("Vidéo non disponible.", 26, GamesColors.TEXT_MEDIUM, top = 6); return }
        lateinit var b: Button; lateinit var unavailable: TextView
        b = button(LangVisuals.videoButton(c)) { playVideo(f, c.media, b) { unavailable.visibility = android.view.View.VISIBLE } }
        LangVisuals.videoNotice(c.media)?.let { label(it, 24, GamesColors.TEXT_MEDIUM, top = 4) }
        unavailable = label("Vidéo non disponible.", 26, GamesColors.TEXT_MEDIUM, top = 4).also { it.visibility = android.view.View.GONE }
    }
    private var videoFailed: (() -> Unit)? = null

    /** Opens the full-screen layer and starts [f]; [onFail] runs (after the layer is closed) when the player cannot read it. */
    private fun playVideo(f: java.io.File, m: MediaEntry, opener: android.view.View, onFail: () -> Unit) {
        closeVideo(); stopAudio()
        runCatching {
            val v = VideoView(this)
            val note = dx.text(TextView(this).apply { text = (LangVisuals.videoNotice(m) ?: "") + "   ·   OK : pause / reprise   ·   RETOUR : revenir à la leçon" }, 26, GamesColors.Dark.TEXT_HIGH)   // sur la vidéo : texte clair, fond noir, quel que soit le thème
            val layer = FrameLayout(this).apply {
                setBackgroundColor(android.graphics.Color.BLACK); isClickable = true
                addView(v, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
                addView(note, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dx.px(24) })
            }
            v.setOnCompletionListener { closeVideo() }                       // the video stops at its end and the lesson comes back
            v.setOnErrorListener { _, w, x -> android.util.Log.w("Langues", "lecture vidéo impossible : ${f.name} ($w, $x)"); closeVideo(failed = true); true }
            v.setVideoPath(f.path)
            videoLayer = layer; videoView = v; videoOpener = opener; videoFailed = onFail
            root.addView(layer, FrameLayout.LayoutParams(-1, -1))
            v.start()
        }.onFailure { android.util.Log.w("Langues", "lecture vidéo impossible : ${f.name}", it); if (videoLayer == null) onFail() else closeVideo(failed = true) }
    }

    private fun closeVideo(failed: Boolean = false) {
        val layer = videoLayer ?: return
        runCatching { videoView?.stopPlayback() }
        root.removeView(layer); videoLayer = null; videoView = null
        val opener = videoOpener; videoOpener = null; val onFail = videoFailed; videoFailed = null
        opener?.requestFocus()                                                // the same button as before: the lesson underneath was never redrawn
        if (failed) onFail?.invoke()
    }

    // ---------------------------------------------------------------- exercises
    private inner class Run(val p: LangPack, val u: LangUnit) {
        var i = 0; var right = 0; var graded = 0

        fun show() {
            if (i >= u.exercises.size) { done(); return }
            val x = u.exercises[i]
            clear(u.title, "Exercice ${i + 1} / ${u.exercises.size}   ·   ${LangCatalog.kindLabel(x.kind)}")
            label(x.prompt, 40, top = 30)
            audioButton(p, x.audio, "Écouter")
            when (x.kind) {
                LangExerciseKind.MCQ -> x.choices.forEachIndexed { k, c -> button(c) { feedback(x, k == x.correct) } }
                LangExerciseKind.TRUEFALSE -> {
                    val labels = if (x.choices.size == 2) x.choices else listOf("Faux", "Vrai")
                    labels.forEachIndexed { k, c -> button(c) { feedback(x, k == x.correct) } }
                }
                LangExerciseKind.SPEAK, LangExerciseKind.WRITE -> {
                    lateinit var b: Button
                    b = button("Voir la réponse modèle") { body.removeView(b); label("Modèle : ${x.model}", 34, GamesColors.SUCCESS, top = 24); explain(x); selfAssess(x) }
                }
                LangExerciseKind.MATCH -> {
                    label("Associez, dans la tête, chaque mot à son équivalent, puis vérifiez.", 28, GamesColors.TEXT_MEDIUM)
                    lateinit var b: Button
                    b = button("Voir les paires") { body.removeView(b); x.pairs.forEach { (l, r) -> label("$l   =   $r", 34, GamesColors.SUCCESS) }; explain(x); selfAssess(x) }
                }
                else -> typed(x)
            }
            focusFirst()
        }

        private fun typed(x: LangExercise) {
            if (x.kind == LangExerciseKind.ORDER) label("Pièces : " + x.words.joinToString("  /  "), 34, GamesColors.TEXT_MEDIUM)
            val input = EditText(this@LanguesActivity).apply {
                setSingleLine(); imeOptions = EditorInfo.IME_ACTION_DONE; inputType = InputType.TYPE_CLASS_TEXT
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dx.px(38).toFloat()); setTextColor(GamesColors.TEXT_HIGH); setHintTextColor(GamesColors.TEXT_MEDIUM); hint = "Votre réponse"
                background = dx.focusable(GamesColors.SURFACE, GamesColors.SURFACE_HIGH, 16); setPadding(dx.px(28), dx.px(16), dx.px(28), dx.px(16))
            }
            body.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dx.px(24) })
            val check = { feedback(x, LangMarking.matches(input.text.toString(), x), input.text.toString()) }
            input.setOnEditorActionListener { _, a, ev -> if (a == EditorInfo.IME_ACTION_DONE || (ev?.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)) { check(); true } else false }
            button("Vérifier", primary = true) { check() }
        }

        /** The commented correction, when the exercise has one (nothing is drawn otherwise). */
        private fun explain(x: LangExercise) { LangVisuals.explanation(x)?.let { label("Explication : $it", 30, GamesColors.TEXT_MEDIUM, top = 22) } }

        private fun selfAssess(x: LangExercise) {
            label("Votre réponse était-elle juste ?", 30, GamesColors.TEXT_MEDIUM, top = 24)
            button("Oui") { graded++; right++; i++; show() }
            button("Pas encore") { graded++; i++; show() }
        }

        private fun feedback(x: LangExercise, ok: Boolean, typed: String = "") {
            graded++; if (ok) right++
            clear(u.title, "Exercice ${i + 1} / ${u.exercises.size}")
            label(if (ok) "Bonne réponse" else "Pas tout à fait", 52, if (ok) GamesColors.SUCCESS else GamesColors.ERROR, true, 30)
            if (!ok) {
                val expected = when (x.kind) { LangExerciseKind.MCQ -> x.choices.getOrNull(x.correct ?: -1); LangExerciseKind.TRUEFALSE -> (if (x.choices.size == 2) x.choices else listOf("Faux", "Vrai")).getOrNull(x.correct ?: -1); else -> x.answers.firstOrNull() }
                if (expected != null) label("Réponse attendue : $expected", 36)
                if (typed.isNotBlank() && x.answers.any { LangMarking.sameIgnoringTones(it, typed) }) label("Presque : les tons (accents) comptent.", 30, GamesColors.TEXT_MEDIUM)
            }
            explain(x)
            button("Suivant", primary = true) { i++; show() }
            focusFirst()
        }

        private fun done() {
            clear(u.title, "Unité terminée")
            label(if (graded == 0) "Terminé." else "$right bonne(s) réponse(s) sur $graded", 48, GamesColors.TEXT_HIGH, true, 30)
            label("Cette TV ne garde pas encore la progression des langues.", 28, GamesColors.TEXT_MEDIUM)
            button("Refaire", primary = true) { i = 0; right = 0; graded = 0; show() }
            button("Retour à l'unité") { onBackPressed() }
            focusFirst()
        }
    }
}
