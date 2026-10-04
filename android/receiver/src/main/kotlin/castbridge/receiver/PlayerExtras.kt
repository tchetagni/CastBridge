package castbridge.receiver

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import castbridge.core.tv.Chapter
import castbridge.core.tv.LibraryDb
import castbridge.core.tv.LibraryLogic
import castbridge.core.tv.PlayerCommand
import castbridge.core.tv.PlayerParams
import castbridge.core.tv.PlayerPrefs
import castbridge.core.tv.PlayerTracks
import castbridge.core.tv.SubtitleFinder
import castbridge.core.tv.Track
import castbridge.core.tv.VideoFit
import castbridge.core.tv.VideoInfo
import castbridge.core.ux.DisplayTexts
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.io.File

/**
 * The "as powerful as VLC" part of the TV player: per-file memory of the viewer's choices (audio/subtitle track, delays,
 * subtitle size, speed, picture format), external subtitles next to the video, hardware/software decoding, equalizer, and
 * reading everything back for the settings panels. Main thread only (libVLC calls).
 *
 * Memory: libVLC's subtitle engine stays off (--no-spu) unless this file needs it (saved subtitle choice, subtitle file next
 * to the video, or the viewer picks one), in which case the player is re-created with it at the same position.
 */
class PlayerExtras(private val db: () -> LibraryDb?, private val prefs: TvPrefs, private val save: (Runnable) -> Unit) {
    var p = PlayerPrefs(); private set
    private var key: String? = null
    private var name = ""; private var size = 0L
    var extSubs: List<File> = emptyList(); private set
    /** "off" after the hardware decoder failed on this file (automatic software retry). */
    var hwOverride: String? = null
    var eqPreset = -1; private set
    private var applied = false

    /** A new file starts (or the same one re-opens: its in-memory choices are kept). [dir] = its folder, for subtitle files. */
    fun load(fileName: String, fileSize: Long, dir: File?) {
        val k = "$fileSize:$fileName"
        if (k != key) {
            key = k; name = fileName; size = fileSize; hwOverride = null
            p = PlayerPrefs.decode(db()?.get(fileName, fileSize)?.prefs)
        }
        extSubs = dir?.list()?.let { SubtitleFinder.find(fileName, it.toList()).map { n -> File(dir, n) } }.orEmpty()
        applied = false
    }

    fun forget() { key = null; extSubs = emptyList() }

    fun hwMode(): String = hwOverride ?: prefs.getString("hw_mode", "auto") ?: "auto"
    /** (enabled, force) for Media.setHWDecoderEnabled. */
    fun hwFlags(): Pair<Boolean, Boolean> = when (hwMode()) { "on" -> true to true; "off" -> false to false; else -> true to false }

    /** Does this file need the subtitle engine? A subtitle file next to the video is shown automatically unless turned off. */
    fun wantsSpu(): Boolean = p.wantsSubtitles || (extSubs.isNotEmpty() && p.subtitle != -1)

    private fun persist() {
        val enc = p.encode(); val n = name; val s = size
        if (n.isNotEmpty()) save(Runnable { db()?.setPrefs(n, s, enc) })
    }

    fun update(f: (PlayerPrefs) -> PlayerPrefs) { p = f(p); persist() }

    /** Once per opening, when libVLC reports Playing (tracks are known then): re-apply what the viewer chose for this file. */
    fun onPlaying(mp: MediaPlayer, spu: Boolean) {
        if (applied) return
        applied = true
        // L'affichage d'abord et à part : un échec d'un autre réglage (piste audio, sous-titres...) ne doit jamais l'empêcher.
        if (p.aspect != "auto") runCatching { aspect(mp, p.aspect) } else applyFit(mp)
        runCatching {
            p.audio?.let { mp.setAudioTrack(it) }
            if (spu) {
                val file = p.subFile?.let { n -> extSubs.firstOrNull { it.name == n } } ?: if (p.subtitle == null) extSubs.firstOrNull() else null
                if (file != null) mp.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(file), true)
                else p.subtitle?.let { mp.setSpuTrack(it) }
            }
            if (p.subDelayMs != 0L) mp.setSpuDelay(p.subDelayMs * 1000)
            if (p.audioDelayMs != 0L) mp.setAudioDelay(p.audioDelayMs * 1000)
            if (p.rate != 1f) mp.setRate(p.rate)
            if (eqPreset >= 0) mp.setEqualizer(MediaPlayer.Equalizer.createFromPreset(eqPreset))
        }
    }

    // ---- Affichage (VideoFit): « Ajusté à l'écran » par défaut, réglage par fichier ou global ; calcul pur dans core, ici seulement les appels libVLC ----

    /** Taille réelle de la surface vidéo (donnée par l'activité ; (0, 0) = inconnue). */
    var panel: () -> Pair<Int, Int> = { 0 to 0 }
    var lastPlan: VideoFit.Plan? = null; private set
    private var lastVideo = 0 to 0
    private var lastSar = 0 to 0
    /** Vrai tant que l'affichage n'a pas pu être appliqué faute de piste vidéo connue (libVLC ne la donne parfois qu'après Vout/Playing) : l'activité réessaie. */
    var fitPending = false; private set

    /** « video_fit_set » : marque écrite seulement quand l'utilisateur choisit lui-même l'affichage par défaut (une ancienne valeur « fit » sans marque suit le nouveau défaut). */
    private fun globalChosen() = prefs.getString("video_fit_set") == "1"
    fun fitResolved(): VideoFit.Resolved = VideoFit.resolve(p.fit, prefs.getString("video_fit"), globalChosen())
    fun fitDefaultKey(): String = VideoFit.resolve(null, prefs.getString("video_fit"), globalChosen()).mode.key
    fun fitMode(): VideoFit.Mode = fitResolved().mode

    /**
     * Applique l'affichage choisi à la sortie vidéo qui tourne, sans rouvrir le flux. Rejoué à chaque événement Vout et à chaque changement de taille.
     * Un format forcé à la main (16:9, 4:3, rogner... du menu « Format d'image », ancien réglage) reste prioritaire : il n'est jamais écrasé en silence.
     */
    fun applyFit(mp: MediaPlayer) {
        runCatching {
            if (p.aspect != "auto") { aspect(mp, p.aspect); lastPlan = null; fitPending = false; return }
            val vt = mp.currentVideoTrack
            if (vt == null || vt.width <= 0 || vt.height <= 0) { fitPending = true; return }     // pas encore de piste : l'activité réessaie (TimeChanged, ESAdded)
            fitPending = false
            val (pw, ph) = panel()
            val plan = VideoFit.decide(vt.width, vt.height, vt.sarNum, vt.sarDen, VideoFit.rotationOf(vt.orientation), pw, ph, fitMode())
            lastPlan = plan; lastVideo = vt.width to vt.height; lastSar = vt.sarNum to vt.sarDen
            mp.aspectRatio = plan.aspectRatio                                   // null: libVLC reads the track's own pixel aspect ratio
            mp.videoScale = when (plan.scale) {
                VideoFit.Scale.BEST_FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
                VideoFit.Scale.FIT_SCREEN -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
                VideoFit.Scale.FILL -> MediaPlayer.ScaleType.SURFACE_FILL
                VideoFit.Scale.ORIGINAL -> MediaPlayer.ScaleType.SURFACE_ORIGINAL
            }
        }
    }

    /** « Affichage : Ajusté à l'écran 1920×1080 → 1280×720 » for the INFO screen; null before the picture is known. */
    fun fitInfo(): String {
        val (pw, ph) = panel()
        val line = VideoFit.diagnosticLine(fitResolved(), lastPlan, lastVideo.first, lastVideo.second, lastSar.first, lastSar.second, pw, ph)
        return if (p.aspect != "auto") "$line\nFormat d'image forcé à la main (menu « Format d'image ») : il passe avant l'Affichage" else line
    }

    /** La ligne courte du diagnostic sans touche (6 s en haut à gauche) : « Affichage : <mode> · source WxH · dalle WxH · image WxH ». */
    fun fitOverlay(): String {
        val (pw, ph) = panel()
        return VideoFit.overlayLine(fitResolved(), lastPlan, lastVideo.first, lastVideo.second, pw, ph)
    }

    fun aspect(mp: MediaPlayer, mode: String) {
        when (mode) {
            "16:9", "4:3" -> { mp.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT; mp.aspectRatio = mode }
            "fill" -> { mp.aspectRatio = null; mp.videoScale = MediaPlayer.ScaleType.SURFACE_FILL }
            "crop" -> { mp.aspectRatio = null; mp.videoScale = MediaPlayer.ScaleType.SURFACE_FIT_SCREEN }
            else -> { mp.aspectRatio = null; mp.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT }
        }
    }

    /** What changes needs the player re-created (subtitle engine, subtitle size, decoder); the caller re-opens at the same position. */
    sealed class Result { object Done : Result(); object Failed : Result(); object Reopen : Result() }

    fun apply(mp: MediaPlayer, spu: Boolean, c: PlayerCommand, playlistStep: (Int) -> Boolean): Result {
        fun ok(b: Boolean) = if (b) Result.Done else Result.Failed
        return when (c) {
            is PlayerCommand.Audio -> ok(mp.setAudioTrack(c.id)).also { if (it == Result.Done) update { it.copy(audio = c.id) } }
            is PlayerCommand.Subtitle -> when {
                c.id < 0 -> { if (spu) mp.setSpuTrack(-1); update { it.copy(subtitle = -1, subFile = null) }; Result.Done }
                !spu -> { update { it.copy(subtitle = c.id, subFile = null) }; Result.Reopen }
                else -> ok(mp.setSpuTrack(c.id)).also { if (it == Result.Done) update { p -> p.copy(subtitle = c.id, subFile = null) } }
            }
            is PlayerCommand.SubFile -> {
                val f = extSubs.firstOrNull { it.name == c.name } ?: return Result.Failed
                subtitleFile(mp, spu, f)
            }
            is PlayerCommand.SubDelay -> ok(mp.setSpuDelay(c.ms * 1000)).also { update { it.copy(subDelayMs = c.ms) } }
            is PlayerCommand.AudioDelay -> ok(mp.setAudioDelay(c.ms * 1000)).also { update { it.copy(audioDelayMs = c.ms) } }
            is PlayerCommand.SubScale -> { update { it.copy(subScale = c.percent) }; if (spu) Result.Reopen else Result.Done }
            is PlayerCommand.Rate -> { mp.setRate(c.rate); update { it.copy(rate = c.rate) }; Result.Done }
            is PlayerCommand.Aspect -> { aspect(mp, c.mode); update { it.copy(aspect = c.mode) }; if (c.mode == "auto") applyFit(mp); Result.Done }
            is PlayerCommand.Fit -> {
                val was = fitMode(); update { it.copy(aspect = "auto", fit = VideoFit.parse(c.mode)?.key) }; applyAfterFitChange(mp, was)
            }
            is PlayerCommand.FitDefault -> {
                val was = fitMode(); prefs.putString("video_fit", (VideoFit.parse(c.mode) ?: VideoFit.DEFAULT).key); prefs.putString("video_fit_set", "1"); applyAfterFitChange(mp, was)
            }
            is PlayerCommand.Chapter -> {
                val n = mp.getChapters(-1)?.size ?: 0
                if (c.index in 0 until n) { mp.chapter = c.index; Result.Done } else Result.Failed
            }
            is PlayerCommand.ChapterStep -> {
                val n = mp.getChapters(-1)?.size ?: 0
                when {
                    n > 1 && c.delta > 0 && mp.chapter < n - 1 -> { mp.nextChapter(); Result.Done }
                    n > 1 && c.delta < 0 && mp.chapter > 0 -> { mp.previousChapter(); Result.Done }
                    else -> ok(playlistStep(c.delta))          // no (more) chapters: the next file of the playlist
                }
            }
            is PlayerCommand.Title -> { val n = mp.titles?.size ?: 0; if (c.index in 0 until n) { mp.title = c.index; Result.Done } else Result.Failed }
            is PlayerCommand.Hw -> { prefs.putString("hw_mode", c.mode); hwOverride = null; Result.Reopen }
            is PlayerCommand.Eq -> {
                val count = runCatching { MediaPlayer.Equalizer.getPresetCount() }.getOrDefault(0)
                if (c.preset >= count) Result.Failed else {
                    eqPreset = c.preset
                    mp.setEqualizer(if (c.preset < 0) null else MediaPlayer.Equalizer.createFromPreset(c.preset)); Result.Done
                }
            }
        }
    }

    /** Instant (libVLC scale call) except when entering or leaving « Natif »: its « no filter at all » is a media option (PictureQuality), so the file re-opens at the same position. */
    private fun applyAfterFitChange(mp: MediaPlayer, was: VideoFit.Mode): Result {
        val now = fitMode()
        if ((was == VideoFit.Mode.NATIVE) != (now == VideoFit.Mode.NATIVE)) return Result.Reopen
        applyFit(mp); return Result.Done
    }

    fun subtitleFile(mp: MediaPlayer, spu: Boolean, f: File): Result {
        if (extSubs.none { it.absolutePath == f.absolutePath }) extSubs = extSubs + f
        update { it.copy(subFile = f.name, subtitle = null) }
        if (!spu) return Result.Reopen
        return if (mp.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(f), true)) Result.Done else Result.Failed
    }

    /** Everything the panels show. [spu] = the player was created with its subtitle engine. */
    fun read(mp: MediaPlayer, spu: Boolean, fileSize: Long, durMs: Long): PlayerTracks {
        val media = mp.media
        val mediaTracks = media?.let { m -> (0 until m.trackCount).mapNotNull { runCatching { m.getTrack(it) }.getOrNull() } }.orEmpty()
        val audio = mp.audioTracks?.map { Track(it.id, it.name ?: "Piste ${it.id}") }.orEmpty()
        val subs = if (spu) mp.spuTracks?.map { Track(it.id, if (it.id < 0) "Désactivés" else it.name ?: "Piste ${it.id}") }.orEmpty()
            else listOf(Track(-1, "Désactivés")) + mediaTracks.filter { it.type == IMedia.Track.Type.Text }
                .map { Track(it.id, listOfNotNull(it.language, it.description).joinToString(" ").ifEmpty { "Piste ${it.id}" }) }
        val chapters = runCatching { mp.getChapters(-1)?.map { Chapter(it.name ?: "", it.timeOffset) } }.getOrNull().orEmpty()
        val vt = runCatching { mp.currentVideoTrack }.getOrNull()
        val decoder = when (hwMode()) {
            "off" -> "logiciel (libavcodec)" + if (hwOverride == "off") " : le matériel a échoué sur ce fichier" else ""
            "on" -> "matériel forcé (MediaCodec)"
            else -> "matériel (MediaCodec) si le format le permet, sinon logiciel"
        }
        val video = vt?.let {
            VideoInfo(it.codec ?: "?", it.width, it.height, if (it.frameRateDen > 0) it.frameRateNum.toFloat() / it.frameRateDen else 0f,
                if (it.bitrate > 0) it.bitrate.toLong() else if (durMs > 0) fileSize * 8000 / durMs else 0, decoder)
        }
        val audioCodec = mediaTracks.firstOrNull { it.type == IMedia.Track.Type.Audio && it.id == mp.audioTrack }?.let { t ->
            (t.codec ?: "?") + ((t as? IMedia.AudioTrack)?.let { " ${it.channels} canaux ${it.rate / 1000} kHz" } ?: "")
        }
        val presets = runCatching { (0 until MediaPlayer.Equalizer.getPresetCount()).map { MediaPlayer.Equalizer.getPresetName(it) } }.getOrDefault(emptyList())
        return PlayerTracks(audio, mp.audioTrack, subs, if (spu) mp.spuTrack else -1, extSubs.map { it.name },
            mp.spuDelay / 1000, mp.audioDelay / 1000, p.subScale, mp.rate, p.aspect, chapters, runCatching { mp.chapter }.getOrDefault(-1),
            runCatching { mp.titles?.size ?: 0 }.getOrDefault(0), runCatching { mp.title }.getOrDefault(-1), hwMode(), video, audioCodec, eqPreset, presets,
            fit = fitMode().key, fitFile = p.fit, fitDefault = fitDefaultKey())
    }
}

/**
 * The TV's on-screen settings panel (MENU during playback): lists navigated with the D-pad, one dialog per setting, the
 * value shown in each line. Delays are adjusted by ±50 ms steps (the dialog re-opens on the new value).
 */
class PlayerPanel(private val act: Activity, private val api: Api) {
    interface Api {
        fun tracks(): PlayerTracks?
        fun command(c: PlayerCommand): Boolean
        fun general()
        fun repeat(mode: String): Boolean
        fun info(): String
        fun flash(msg: String)
    }

    fun show() {
        val t = api.tracks() ?: return
        val items = ArrayList<Pair<String, () -> Unit>>()
        val audioName = t.audio.firstOrNull { it.id == t.audioId }?.name ?: "—"
        if (t.audio.size > 1) items += "Piste audio : $audioName" to { audioPick(t) }
        val subName = t.subtitles.firstOrNull { it.id == t.subtitleId && it.id >= 0 }?.name ?: "Désactivés"
        items += "Sous-titres : $subName" to { subtitles(t) }
        items += "Décalage des sous-titres : ${PlayerParams.delayLabel(t.subDelayMs)}" to { delay("Décalage des sous-titres", t.subDelayMs) { PlayerCommand.SubDelay(it) } }
        items += "Décalage audio : ${PlayerParams.delayLabel(t.audioDelayMs)}" to { delay("Décalage audio", t.audioDelayMs) { PlayerCommand.AudioDelay(it) } }
        items += "Taille des sous-titres : ${t.subScale} %" to {
            pick("Taille des sous-titres", PlayerParams.SUB_SCALES.map { "$it %" }, PlayerParams.SUB_SCALES.indexOf(t.subScale)) { i -> api.command(PlayerCommand.SubScale(PlayerParams.SUB_SCALES[i])) }
        }
        items += "Vitesse : ${PlayerParams.rateLabel(t.rate)}" to {
            pick("Vitesse de lecture", PlayerParams.RATES.map { PlayerParams.rateLabel(it) }, PlayerParams.RATES.indexOfFirst { Math.abs(it - t.rate) < 0.01f }) { i -> api.command(PlayerCommand.Rate(PlayerParams.RATES[i])) }
        }
        items += "Format d'image : ${PlayerParams.aspectLabel(t.aspect)}" to {
            pick("Format d'image", PlayerParams.ASPECTS.map(PlayerParams::aspectLabel), PlayerParams.ASPECTS.indexOf(t.aspect)) { i -> api.command(PlayerCommand.Aspect(PlayerParams.ASPECTS[i])) }
        }
        val modes = VideoFit.Mode.values().toList()
        fun choice(m: VideoFit.Mode) = "${DisplayTexts.label(m)}${if (m == VideoFit.DEFAULT) " (par défaut)" else ""} : ${DisplayTexts.hint(m)}"
        val fitLine = DisplayTexts.label(VideoFit.parse(t.fit) ?: VideoFit.DEFAULT) + if (t.fitFile == null) " (réglage par défaut)" else ""
        items += "${DisplayTexts.ROW} : $fitLine" to { displayPick(t) }
        items += "${DisplayTexts.ROW_DEFAULT} : ${DisplayTexts.label(VideoFit.parse(t.fitDefault) ?: VideoFit.DEFAULT)}" to {
            pick(DisplayTexts.ROW_DEFAULT, modes.map(::choice), modes.indexOfFirst { it.key == t.fitDefault }) { i -> api.command(PlayerCommand.FitDefault(modes[i].key)) }
        }
        if (t.chapters.size > 1) items += "Chapitre : ${t.chapter + 1} / ${t.chapters.size}" to {
            pick("Chapitres", t.chapters.mapIndexed { i, c -> "${i + 1}. ${c.name.ifEmpty { "Chapitre ${i + 1}" }}  (${LibraryLogic.clock(c.timeMs)})" }, t.chapter) { i -> api.command(PlayerCommand.Chapter(i)) }
        }
        if (t.titles > 1) items += "Titre : ${t.title + 1} / ${t.titles}" to {
            pick("Titres", (1..t.titles).map { "Titre $it" }, t.title) { i -> api.command(PlayerCommand.Title(i)) }
        }
        if (t.queue.size > 1) items += "Liste de lecture : ${t.queueIndex + 1} / ${t.queue.size}, répétition ${repeatLabel(t.repeat)}" to {
            val modes = listOf("off", "all", "one")
            pick("Répétition", modes.map(::repeatLabel), modes.indexOf(t.repeat)) { i -> api.repeat(modes[i]) }
        }
        if (t.eqPresets.isNotEmpty()) items += "Égaliseur : ${t.eqPresets.getOrNull(t.eqPreset) ?: "désactivé"}" to {
            pick("Égaliseur", listOf("Désactivé") + t.eqPresets, t.eqPreset + 1) { i -> api.command(PlayerCommand.Eq(i - 1)) }
        }
        items += "Décodage : ${PlayerParams.hwLabel(t.hw)}" to {
            pick("Décodage vidéo", PlayerParams.HW_MODES.map(PlayerParams::hwLabel), PlayerParams.HW_MODES.indexOf(t.hw)) { i -> api.command(PlayerCommand.Hw(PlayerParams.HW_MODES[i])) }
        }
        items += "Informations techniques" to { AlertDialog.Builder(act).setTitle("Informations").setMessage(api.info()).setPositiveButton("Fermer", null).show() }
        items += "Options générales (USB, Bluetooth, SSH…)" to { api.general() }
        AlertDialog.Builder(act).setTitle("Réglages de lecture")
            .setItems(items.map { it.first }.toTypedArray()) { _, w -> items[w].second() }
            .setNegativeButton("Fermer", null).show()
    }

    // Entrées directes de la barre de commandes à l'écran (mêmes choix que le panneau, aucune logique en double).
    fun audio() { val t = api.tracks() ?: return; if (t.audio.size < 2) api.flash("Une seule piste audio") else audioPick(t) }
    fun subtitles() { val t = api.tracks() ?: return; subtitles(t) }
    fun display() { val t = api.tracks() ?: return; displayPick(t) }

    private fun audioPick(t: PlayerTracks) =
        pick("Piste audio", t.audio.map { it.name }, t.audio.indexOfFirst { it.id == t.audioId }) { i -> api.command(PlayerCommand.Audio(t.audio[i].id)) }

    private fun displayPick(t: PlayerTracks) {
        val modes = VideoFit.Mode.values().toList()
        fun choice(m: VideoFit.Mode) = "${DisplayTexts.label(m)}${if (m == VideoFit.DEFAULT) " (par défaut)" else ""} : ${DisplayTexts.hint(m)}"
        val def = DisplayTexts.label(VideoFit.parse(t.fitDefault) ?: VideoFit.DEFAULT)
        pick(DisplayTexts.ROW, listOf("${DisplayTexts.FOLLOW_DEFAULT} ($def)") + modes.map(::choice),
            if (t.fitFile == null) 0 else modes.indexOfFirst { it.key == t.fitFile } + 1) { i -> api.command(PlayerCommand.Fit(if (i == 0) null else modes[i - 1].key)) }
    }

    private fun repeatLabel(m: String) = when (m) { "all" -> "toute la liste"; "one" -> "ce fichier"; else -> "non" }

    private fun subtitles(t: PlayerTracks) {
        val labels = t.subtitles.map { it.name } + t.subtitleFiles.map { "Fichier : $it" }
        val cur = t.subtitles.indexOfFirst { it.id == t.subtitleId }.let { if (it < 0) 0 else it }
        pick("Sous-titres", labels, cur) { i ->
            if (i < t.subtitles.size) api.command(PlayerCommand.Subtitle(t.subtitles[i].id))
            else api.command(PlayerCommand.SubFile(t.subtitleFiles[i - t.subtitles.size]))
        }
    }

    private fun delay(title: String, cur: Long, cmd: (Long) -> PlayerCommand) {
        val steps = listOf(-500L, -50L, 50L, 500L)
        val labels = steps.map { (if (it > 0) "+" else "") + "$it ms" } + "Remettre à 0"
        AlertDialog.Builder(act).setTitle("$title : ${PlayerParams.delayLabel(cur)}")
            .setItems(labels.toTypedArray()) { _, i ->
                val v = if (i < steps.size) PlayerParams.clampDelay(cur + steps[i]) else 0L
                api.command(cmd(v))
                delay(title, v, cmd)                            // stay in the dialog: adjust again
            }.setNegativeButton("Fermer", null).show()
    }

    private fun pick(title: String, labels: List<String>, checked: Int, apply: (Int) -> Boolean) {
        AlertDialog.Builder(act).setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { d, i -> d.dismiss(); if (!apply(i)) api.flash("Impossible pour ce fichier") }
            .setNegativeButton("Fermer", null).show()
    }
}

/** Bottom bar with title, position and a progress line, shown a few seconds after a seek, a pause or INFO. */
class ProgressOverlay(private val act: Activity, parent: FrameLayout) {
    private val main = Handler(Looper.getMainLooper())
    private val title = TextView(act).apply { setTextColor(Color.WHITE); textSize = 24f; typeface = TvFonts.bold; maxLines = 1 }
    private val time = TextView(act).apply { setTextColor(TvStyle.TEXT2); textSize = TvStyle.Type.BODY }
    private val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000; progressTintList = android.content.res.ColorStateList.valueOf(TvStyle.ACCENT)
        secondaryProgressTintList = android.content.res.ColorStateList.valueOf(0x88FFFFFF.toInt())
    }
    private val box = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL; visibility = View.GONE
        // A soft shadow rising from the bottom, a thin line of progress: modern and out of the way.
        background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xE6000000.toInt(), 0x99000000.toInt(), 0x00000000))
        val pad = dp(24); val safe = castbridge.core.tv.PlayerIcons.safe(act.resources.displayMetrics.widthPixels, act.resources.displayMetrics.heightPixels)
        setPadding(maxOf(pad * 2, safe.horizontal), pad * 3, maxOf(pad * 2, safe.horizontal), safe.vertical + dp(8))       // marges de sécurité de 5 % (surbalayage)
        addView(title); addView(bar, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(10); bottomMargin = dp(8) }); addView(time)
    }
    private val hide = Runnable { box.fadeTo(false, 400); shown = false; onChange?.invoke() }

    /** R-16: true while the bar is on screen (the copy badge steps aside so nothing overlaps the seek bar); [onChange] tells the badge. */
    var shown = false; private set
    var onChange: (() -> Unit)? = null

    init { parent.addView(box, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)) }

    /** [reachableMs] > 0 while the file is still arriving: shown as the secondary (received) part of the bar. */
    fun show(name: String, posMs: Long, durMs: Long, extra: String = "", reachableMs: Long = -1) {
        title.text = LibraryLogic.title(name)
        bar.progress = if (durMs > 0) (posMs * 1000 / durMs).toInt() else 0
        bar.secondaryProgress = if (durMs > 0 && reachableMs >= 0) (reachableMs * 1000 / durMs).toInt() else 1000
        time.text = LibraryLogic.clock(posMs) + (if (durMs > 0) "  /  ${LibraryLogic.clock(durMs)}   (-${LibraryLogic.clock(durMs - posMs)})" else "") +
            (if (extra.isNotEmpty()) "   ·   $extra" else "")
        box.fadeTo(true)
        shown = true; onChange?.invoke()
        main.removeCallbacks(hide); main.postDelayed(hide, 4000)
    }

    fun hideNow() { main.removeCallbacks(hide); box.animate().cancel(); box.visibility = View.GONE; shown = false; onChange?.invoke() }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), act.resources.displayMetrics).toInt()
}
