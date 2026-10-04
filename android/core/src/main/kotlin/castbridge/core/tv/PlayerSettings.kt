package castbridge.core.tv

import castbridge.core.ux.DisplayTexts

/**
 * Écran « Réglages du lecteur » (wtv-01 n°10) : modèle PUR (rubriques, lignes, valeur courante, état « par défaut », changement par GAUCHE/DROITE, navigation).
 * Les vues Android n'en font qu'un affichage ; les lignes ACTION (minuteur, boucle, marque-pages, réinitialisations) sont exécutées par l'activité.
 */
object PlayerSettings {
    enum class Section(val label: String) { PLAYBACK("Lecture"), PICTURE("Image"), SOUND("Son"), SUBTITLES("Sous-titres"), DISPLAY("Affichage") }
    enum class Kind { VALUE, ACTION }

    /** [value] : ce qui est affiché à droite du libellé ; [isDefault] : vrai quand la valeur est celle d'origine (le texte « par défaut » s'affiche alors). */
    data class Row(val id: String, val section: Section, val label: String, val value: String, val isDefault: Boolean, val kind: Kind = Kind.VALUE) {
        fun text() = if (kind == Kind.ACTION) label else "$label : $value" + if (isDefault) " (par défaut)" else ""
    }

    /** Tout ce que l'écran montre : les réglages de CE fichier, les réglages par défaut, et l'état du moment. */
    data class State(
        val file: PlayerPrefs = PlayerPrefs(),
        val defaults: PlayerPrefs = PlayerPrefs(),
        val skipStepSec: Int = PlayerSeek.DEFAULT_STEP_S,
        val autoNext: Boolean = true,
        val sleepText: String? = null,
        val loop: LoopAB = LoopAB(),
        val fitDefaultKey: String = VideoFit.DEFAULT.key,
    ) {
        val eff: PlayerPrefs get() = file.resolved(defaults)
        val picture: PictureTuning get() = eff.picture ?: PictureTuning()
        val audio: AudioTuning get() = eff.audioTuning ?: AudioTuning()
        val sub: SubtitleStyle get() = eff.subStyle ?: SubtitleStyle()
    }

    private fun pct(v: Int) = "$v %"
    private fun yn(b: Boolean) = if (b) "oui" else "non"
    private fun fitLabel(key: String) = DisplayTexts.label(VideoFit.parse(key) ?: VideoFit.DEFAULT)

    fun rows(s: State): List<Row> {
        val pic = s.picture; val au = s.audio; val st = s.sub
        val out = ArrayList<Row>()
        fun v(id: String, sec: Section, label: String, value: String, def: Boolean) { out += Row(id, sec, label, value, def) }
        fun a(id: String, sec: Section, label: String) { out += Row(id, sec, label, "", true, Kind.ACTION) }
        val P = Section.PLAYBACK
        v("skip", P, "Saut des flèches", "${PlayerSeek.step(s.skipStepSec)} s (appui long : ${PlayerSeek.bigMs(s.skipStepSec) / 1000} s)", PlayerSeek.step(s.skipStepSec) == PlayerSeek.DEFAULT_STEP_S)
        v("autonext", P, "Épisode suivant automatique", yn(s.autoNext), s.autoNext)
        a("sleep", P, "Minuteur d'arrêt : " + (s.sleepText ?: "non"))
        a("loop", P, s.loop.label() + if (s.loop.active) " (OK : effacer)" else " (OK : poser ici)")
        a("mark_add", P, "Marque-page : ajouter ici (${s.eff.bookmarks.marksMs.size}/${Bookmarks.MAX})")
        a("next", P, "Vidéo suivante de la liste"); a("prev", P, "Vidéo précédente de la liste")
        a("mark_go", P, "Marque-pages : aller à… (${s.eff.bookmarks.marksMs.size})")
        val I = Section.PICTURE
        for (f in PictureTuning.Field.values()) {
            val x = PictureTuning.get(pic, f)
            v("pic_${f.key}", I, f.label, if (f == PictureTuning.Field.PAN_X || f == PictureTuning.Field.PAN_Y) "$x" else pct(x), x == f.neutral)
        }
        v("pic_d", I, "Désentrelacement", if (pic.deinterlace) "forcé" else "réglage d'origine", !pic.deinterlace)
        v("pic_r", I, "Rotation", "${pic.rotation}°", pic.rotation == 0)
        a("pic_reset", I, "Réinitialiser l'image")
        a("pic_default", I, "Image : comme le réglage par défaut"); a("pic_save", I, "Image : enregistrer comme réglage par défaut")
        val S = Section.SOUND
        v("au_n", S, "Mode nuit (voix plus claires, explosions réduites)", yn(au.night), !au.night)
        v("au_g", S, "Amplification", pct(au.gainPercent) + (au.warning?.let { " : $it" } ?: ""), au.gainPercent == 100)
        v("au_k", S, "Vitesse sans changer la voix", yn(au.keepPitch), !au.keepPitch)
        a("au_default", S, "Son : comme le réglage par défaut"); a("au_save", S, "Son : enregistrer comme réglage par défaut")
        val T = Section.SUBTITLES
        v("st_c", T, "Couleur", st.color.label, st.color == SubtitleStyle.Color.WHITE)
        v("st_o", T, "Contour", st.outline.label, st.outline == SubtitleStyle.Outline.NORMAL)
        v("st_p", T, "Position (distance au bas)", pct(st.bottomPercent), st.bottomPercent == 0)
        v("st_e", T, "Encodage des caractères", SubtitleStyle.ENCODINGS.firstOrNull { it.key == st.encoding }?.label ?: st.encoding, st.encoding == "auto")
        v("st_f", T, "Police", SubtitleStyle.FONTS.firstOrNull { it.first == st.font }?.second ?: st.font, st.font == "default")
        a("st_preview", T, SubtitleStyle.PREVIEW)
        a("st_default", T, "Sous-titres : comme le réglage par défaut"); a("st_save", T, "Sous-titres : enregistrer comme réglage par défaut")
        val fit = s.file.fit
        v("fit", Section.DISPLAY, "Affichage de cette vidéo", if (fit == null) "comme le réglage par défaut (${fitLabel(s.fitDefaultKey)})" else fitLabel(fit), fit == null)
        return out
    }

    private fun <T> cycle(list: List<T>, cur: T, dir: Int): T = list[((list.indexOf(cur).takeIf { it >= 0 } ?: 0) + dir + list.size * 2) % list.size]

    /** GAUCHE (-1) / DROITE (+1) sur une ligne de valeur : le nouvel état. Les valeurs numériques s'arrêtent à leurs bornes ; les choix tournent. Lignes ACTION : inchangé. */
    fun change(s: State, id: String, dir: Int): State {
        val d = if (dir < 0) -1 else 1
        fun pic(f: (PictureTuning) -> PictureTuning) = s.copy(file = s.file.copy(picture = f(s.picture)))
        fun au(f: (AudioTuning) -> AudioTuning) = s.copy(file = s.file.copy(audioTuning = f(s.audio)))
        fun sub(f: (SubtitleStyle) -> SubtitleStyle) = s.copy(file = s.file.copy(subStyle = f(s.sub)))
        PictureTuning.Field.values().firstOrNull { "pic_${it.key}" == id }?.let { f -> return pic { PictureTuning.set(it, f, PictureTuning.get(it, f) + d * f.step) } }
        return when (id) {
            "skip" -> s.copy(skipStepSec = cycle(PlayerSeek.STEPS_S, PlayerSeek.step(s.skipStepSec), d))
            "autonext" -> s.copy(autoNext = !s.autoNext)
            "pic_d" -> pic { it.copy(deinterlace = !it.deinterlace) }
            "pic_r" -> pic { PictureTuning.withRotation(it, it.rotation + d * 90) }
            "au_n" -> au { it.copy(night = !it.night) }
            "au_g" -> au { it.copy(gainPercent = AudioTuning.clampGain(it.gainPercent + d * AudioTuning.GAIN_STEP)) }
            "au_k" -> au { it.copy(keepPitch = !it.keepPitch) }
            "st_c" -> sub { it.copy(color = cycle(SubtitleStyle.Color.values().toList(), it.color, d)) }
            "st_o" -> sub { it.copy(outline = cycle(SubtitleStyle.Outline.values().toList(), it.outline, d)) }
            "st_p" -> sub { it.copy(bottomPercent = SubtitleStyle.clampBottom(it.bottomPercent + d * 5)) }
            "st_e" -> sub { it.copy(encoding = cycle(SubtitleStyle.ENCODINGS.map { e -> e.key }, it.encoding, d)) }
            "st_f" -> sub { it.copy(font = cycle(SubtitleStyle.FONTS.map { f -> f.first }, it.font, d)) }
            "fit" -> {
                val keys = listOf<String?>(null) + VideoFit.Mode.values().map { it.key }
                s.copy(file = s.file.copy(fit = cycle(keys, s.file.fit, d)))
            }
            else -> s
        }
    }

    /** La commande à appliquer au lecteur après un changement de la ligne [id] (état [s] DÉJÀ changé) ; null : ligne sans effet direct sur le lecteur. */
    fun commandFor(id: String, s: State): PlayerCommand? {
        PictureTuning.Field.values().firstOrNull { "pic_${it.key}" == id }?.let { f -> return PlayerCommand.PictureField(f, PictureTuning.get(s.picture, f)) }
        return when (id) {
            "pic_d" -> PlayerCommand.PictureDeinterlace(s.picture.deinterlace)
            "pic_r" -> PlayerCommand.PictureRotation(s.picture.rotation)
            "au_n" -> PlayerCommand.AudioNight(s.audio.night)
            "au_g" -> PlayerCommand.AudioGain(s.audio.gainPercent)
            "au_k" -> PlayerCommand.AudioKeepPitch(s.audio.keepPitch)
            "st_c", "st_o", "st_p", "st_e", "st_f" -> PlayerCommand.SubStyleSet(s.sub)
            "skip" -> PlayerCommand.SkipStep(PlayerSeek.step(s.skipStepSec))
            "autonext" -> PlayerCommand.AutoNext(s.autoNext)
            "fit" -> PlayerCommand.Fit(s.file.fit)
            else -> null
        }
    }

    /** Action « Réinitialiser » / « Comme le réglage par défaut » : le groupe revient à « comme le défaut » (null) ou à la valeur neutre. */
    fun reset(s: State, id: String): State = when (id) {
        "pic_reset" -> s.copy(file = s.file.copy(picture = PictureTuning()))
        "pic_default" -> s.copy(file = s.file.copy(picture = null))
        "au_default" -> s.copy(file = s.file.copy(audioTuning = null))
        "st_default" -> s.copy(file = s.file.copy(subStyle = null))
        else -> s
    }

    /** Le groupe de réglages enregistré comme défaut (action « enregistrer comme réglage par défaut »). */
    fun saveDefault(s: State, id: String): State = when (id) {
        "pic_save" -> s.copy(defaults = s.defaults.copy(picture = s.picture))
        "au_save" -> s.copy(defaults = s.defaults.copy(audioTuning = s.audio))
        "st_save" -> s.copy(defaults = s.defaults.copy(subStyle = s.sub))
        else -> s
    }

    /** Navigation HAUT/BAS dans la liste : tourne aux extrémités. */
    fun move(index: Int, dir: Int, size: Int): Int = if (size <= 0) 0 else ((index + dir) % size + size) % size

    /** Les rubriques présentes, dans l'ordre d'affichage (pour les titres de section). */
    fun sections(rows: List<Row>): List<Section> = rows.map { it.section }.distinct()
}
