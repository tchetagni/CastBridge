package castbridge.core.xfer

/**
 * R-16 : « la vidéo AVI s'est figée, mais le son continue fluidement ». PUR : horloge injectée par l'appelant, aucune E/S.
 *
 * Pourquoi un détecteur de plus : [PlaybackHealth] compare la tête de lecture à l'horloge, et libVLC la fait avancer avec le SON. Une image
 * figée par un décodeur logiciel en retard (AVI MPEG-4 ASP sur 4 petits coeurs, que la copie concurrence) laisse donc la tête de lecture au
 * rythme : aveugle. Ici, on lit ce que libVLC compte réellement (`Media.getStats()` : images affichées et images perdues) et on le
 * compare à l'horloge audio :
 *  - [Level.FROZEN] : aucune nouvelle image affichée depuis [FREEZE_MS] alors que le son a avancé d'au moins [AUDIO_ADVANCE_MS], lecture en
 *    cours, hors [GRACE_MS] de démarrage (ou de reprise, ou de saut) ;
 *  - [Level.DROPPING] : plus de [DROP_RATIO] des images des [WINDOW_MS] dernières millisecondes sont perdues, ou la détresse vient de cesser
 *    (elle « traîne » [LINGER_MS] pour que la copie ne reparte pas plein débit tout de suite : sinon elle ferait osciller l'image) ;
 *  - [sustained] : la détresse a cumulé [SUSTAINED_S] secondes (elle se résorbe à la même vitesse) : on peut alléger le décodeur (PlayerTuning).
 * Pas de statistiques fraîches (libVLC n'en donne plus depuis [STALE_MS]), pas de piste vidéo, lecteur en pause, son arrêté : `OK` (inconnu
 * n'est pas figé). Ce que ce code ne prouve pas : que libVLC de la vraie TV tienne ses compteurs à jour (docs/test-plans P-52).
 */
class VideoStallDetector {
    enum class Level { OK, DROPPING, FROZEN }

    private var playing = false
    private var hasVideo = true
    private var graceUntil = 0L
    private var lastDisplayed = -1
    private var lastChangeAt = 0L
    private var audioAtChange = 0L
    private var audioPos = -1L
    private var lastAudioAt = 0L
    private var anchorNow = 0L
    private var anchorPos = -1L
    private var lastStatsAt = Long.MIN_VALUE / 2
    private var lingerUntil = Long.MIN_VALUE / 2
    private var accS = 0.0
    private var lastAccAt = -1L
    private var ratio = 0.0
    private class Sample(val at: Long, val displayed: Int, val lost: Int)
    private val window = ArrayDeque<Sample>()

    @Synchronized fun onPlaying(nowMs: Long) {
        playing = true; graceUntil = nowMs + GRACE_MS
        lastChangeAt = nowMs; audioAtChange = audioPos.coerceAtLeast(0)
        anchorNow = nowMs; anchorPos = -1; lastDisplayed = -1; window.clear(); lastAccAt = -1
    }

    @Synchronized fun onPaused() { playing = false; anchorPos = -1; lastAccAt = -1 }

    @Synchronized fun onAudioTime(nowMs: Long, posMs: Long) {
        if (!playing) { playing = true; graceUntil = nowMs + GRACE_MS; lastChangeAt = nowMs; audioAtChange = posMs }
        lastAudioAt = nowMs
        if (anchorPos >= 0) {
            val dt = (nowMs - anchorNow) / 1000.0; val dp = (posMs - anchorPos) / 1000.0
            if (dp < 0 || dp > dt * 3 + 1) {                          // a seek: nothing was frozen, give the picture time to come back
                graceUntil = nowMs + SEEK_GRACE_MS; lastChangeAt = nowMs; audioAtChange = posMs; window.clear()
            }
        }
        anchorNow = nowMs; anchorPos = posMs; audioPos = posMs
    }

    /** libVLC's counters (cumulative), about once a second. */
    @Synchronized fun onStats(nowMs: Long, displayed: Int, lost: Int, hasVideo: Boolean = true) {
        this.hasVideo = hasVideo
        if (displayed < lastDisplayed) { lastDisplayed = -1; window.clear() }         // new media: the counters restarted
        if (displayed > lastDisplayed) { lastChangeAt = nowMs; audioAtChange = audioPos.coerceAtLeast(0) }
        lastDisplayed = displayed; lastStatsAt = nowMs
        window.addLast(Sample(nowMs, displayed, lost))
        while (window.size > 1 && nowMs - window.first().at > WINDOW_MS) window.removeFirst()
        val a = window.first(); val b = window.last()
        val dShown = b.displayed - a.displayed; val dLost = b.lost - a.lost
        ratio = if (dShown + dLost >= MIN_PICTURES) dLost.toDouble() / (dShown + dLost) else 0.0
        val raw = raw(nowMs)
        if (lastAccAt >= 0) {
            val dt = ((nowMs - lastAccAt) / 1000.0).coerceIn(0.0, 5.0)
            accS = if (raw != Level.OK) minOf(ACC_CAP_S, accS + dt) else maxOf(0.0, accS - dt)
        }
        lastAccAt = nowMs
    }

    private fun raw(now: Long): Level {
        if (!playing || !hasVideo || now - lastStatsAt > STALE_MS) return Level.OK
        if (now >= graceUntil && now - lastChangeAt >= FREEZE_MS && now - lastAudioAt < AUDIO_FRESH_MS && audioPos - audioAtChange >= AUDIO_ADVANCE_MS) return Level.FROZEN
        return if (ratio > DROP_RATIO) Level.DROPPING else Level.OK
    }

    @Synchronized fun level(nowMs: Long): Level {
        val r = raw(nowMs)
        if (r != Level.OK) { lingerUntil = nowMs + LINGER_MS; return r }
        return if (playing && nowMs < lingerUntil) Level.DROPPING else Level.OK
    }

    /** The distress lasts: the decoder must be lightened, not only the copy slowed. */
    @Synchronized fun sustained(nowMs: Long): Boolean = playing && accS >= SUSTAINED_S

    /** Lost / (lost + displayed) over the last [WINDOW_MS] (0 until [MIN_PICTURES] pictures were counted). */
    @Synchronized fun droppedRatio(): Double = ratio

    companion object {
        const val FREEZE_MS = 1_500L
        const val AUDIO_ADVANCE_MS = 1_000L
        const val AUDIO_FRESH_MS = 2_000L
        const val GRACE_MS = 4_000L
        const val SEEK_GRACE_MS = 2_000L
        const val STALE_MS = 3_000L
        const val WINDOW_MS = 5_000L
        const val MIN_PICTURES = 10
        const val DROP_RATIO = 0.05
        const val LINGER_MS = 15_000L
        const val SUSTAINED_S = 10.0
        const val ACC_CAP_S = 15.0
    }
}
