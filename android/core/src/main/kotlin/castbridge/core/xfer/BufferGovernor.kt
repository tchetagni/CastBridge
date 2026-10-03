package castbridge.core.xfer

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * R-15, régulation en boucle fermée du débit de copie par le tampon du lecteur. PUR : horloge injectée, aucune E/S.
 *
 * Mesure : secondes de confort du lecteur ([PlaybackHealth], ou l'avance réelle d'un fichier qui grandit), lissées (descente rapide,
 * montée lente) avec une tendance qui anticipe une chute. Sortie : débit autorisé = plancher + niveau × (plafond − plancher), `niveau` ∈ [0,1] :
 *  - tampon > 12 s : plein débit (le plafond de la politique statique, [PlaybackAwareCopyPolicy]) ; sortie de la bande sous 10,5 s ;
 *  - 6 à 12 s : proportionnel ; descente multiplicative (×0,5 par échantillon), montée additive (+0,25 niveau/s) ;
 *  - < 6 s : plancher [PlaybackAwareCopyPolicy.FLOOR_BPS] (sortie de la bande au-dessus de 7 s) ;
 *  - < 2 s : pause de tranche, jusqu'à > 8 s ; JAMAIS plus de [PlaybackAwareCopyPolicy.MAX_HOLD_MS] sans qu'un octet passe (un octet « de
 *    cœur » est laissé passer : le téléphone ne voit pas un blocage, la copie finit) ;
 *  - sans mesure (null, ou plus rien depuis [STALE_MS]) : boucle OUVERTE, la politique statique seule décide.
 */
class BufferGovernor(private val clock: () -> Long = System::currentTimeMillis) {
    enum class Band { OPEN, FULL, PROPORTIONAL, LOW, HOLD }

    private var inner = Band.PROPORTIONAL
    private var hasSample = false
    private var lastSampleAt = 0L
    private var smoothed = 0.0
    private var trend = 0.0
    private var effective = 0.0
    private var level = 1.0
    private var lastPassAt = 0L
    private var count = 0

    private fun fresh() = hasSample && clock() - lastSampleAt <= STALE_MS

    @get:Synchronized val band: Band get() = if (fresh()) inner else Band.OPEN
    /** Number of band changes since the start (stability checks). */
    @get:Synchronized val transitions: Int get() = count
    /** Smoothed comfort in seconds, null in open loop (read-only state for /api/info). */
    @Synchronized fun smoothedSec(): Double? = if (fresh()) smoothed else null

    /** One measurement ([bufferSec], null = the player gives none) every 250-500 ms. */
    @Synchronized fun sample(bufferSec: Double?) {
        val t = clock()
        if (bufferSec == null) { hasSample = false; return }
        val b = bufferSec.coerceIn(0.0, 600.0)
        val reseed = !fresh()
        val dt = if (reseed) 0.5 else ((t - lastSampleAt) / 1000.0).coerceIn(0.05, 5.0)
        if (reseed) { smoothed = b; trend = 0.0; inner = Band.PROPORTIONAL; level = 1.0 }
        else {
            val prev = smoothed
            smoothed += (1 - exp(-dt / (if (b < smoothed) 0.4 else 1.0))) * (b - smoothed)
            trend = max(-2.0, trend + (1 - exp(-dt / 2.0)) * ((smoothed - prev) / dt - trend))
        }
        effective = smoothed + min(0.0, trend) * LOOKAHEAD_S
        val before = inner
        inner = when {
            smoothed < HOLD_SEC -> Band.HOLD
            inner == Band.HOLD && smoothed <= HOLD_EXIT -> Band.HOLD
            effective < LOW_SEC -> Band.LOW
            inner == Band.LOW && effective <= LOW_EXIT -> Band.LOW
            effective > FULL_SEC -> Band.FULL
            inner == Band.FULL && effective >= FULL_EXIT -> Band.FULL
            else -> Band.PROPORTIONAL
        }
        if (inner != before) count++
        when (inner) {
            Band.FULL -> level = min(1.0, level + ADD_LEVEL_PER_S * dt)
            Band.PROPORTIONAL -> {
                val target = ((effective - LOW_SEC) / (FULL_SEC - LOW_SEC)).coerceIn(0.0, 1.0)
                level = if (target < level) max(target, level * DOWN_FACTOR) else min(target, level + ADD_LEVEL_PER_S * dt)
            }
            else -> level = 0.0
        }
        hasSample = true; lastSampleAt = t
    }

    /** Receive rate allowed now (bytes/s, 0 = unlimited) under the static [ceilingBps] of the policy (0 = none). Never below the floor. */
    @Synchronized fun allowedBps(ceilingBps: Long): Long {
        if (!fresh()) return ceilingBps
        if (level >= 1.0) return ceilingBps
        val ref = if (ceilingBps > 0) ceilingBps else REF_CEILING_BPS
        val floor = PlaybackAwareCopyPolicy.FLOOR_BPS
        val a = floor + (level * (max(ref, floor) - floor)).toLong()
        return max(floor, if (ceilingBps > 0) min(a, ceilingBps) else a)
    }

    /**
     * How long a writer must wait before its next bytes (0 = go). In HOLD only, and at most until [PlaybackAwareCopyPolicy.MAX_HOLD_MS] after
     * the last byte that passed: then one slice is let through ([passed] restarts the count).
     */
    @Synchronized fun holdMs(): Long {
        if (!fresh() || inner != Band.HOLD) return 0
        val waited = clock() - lastPassAt
        return if (waited >= PlaybackAwareCopyPolicy.MAX_HOLD_MS) 0 else PlaybackAwareCopyPolicy.MAX_HOLD_MS - waited
    }

    /** Bytes were let through now. */
    @Synchronized fun passed() { lastPassAt = clock() }

    /** Playback stopped: the next playback starts from a clean state. */
    @Synchronized fun reset() { hasSample = false; inner = Band.PROPORTIONAL; level = 1.0; smoothed = 0.0; trend = 0.0 }

    companion object {
        const val FULL_SEC = 12.0
        const val FULL_EXIT = 10.5
        const val LOW_SEC = 6.0
        const val LOW_EXIT = 7.0
        const val HOLD_SEC = 2.0
        const val HOLD_EXIT = 8.0
        const val LOOKAHEAD_S = 1.5
        const val ADD_LEVEL_PER_S = 0.25
        const val DOWN_FACTOR = 0.5
        const val STALE_MS = 3_000L
        /** Scale of the proportional band when the static policy sets no ceiling. */
        const val REF_CEILING_BPS = 8_000_000L
    }
}

/**
 * What the player gives us about its comfort, as seconds. libVLC (the TV player) does not expose its read-ahead level: this estimates it
 * from what libVLC does tell: the playhead (`TimeChanged`) against the wall clock and the `Buffering` events. NOT a measure of the player's
 * real buffer, a stutter detector expressed in the same unit: smooth playback earns 1 s of comfort per second (up to [CAP_SEC]), a playhead
 * that slips behind the wall clock costs [SLIP_K] s per second of slip, a `Buffering` < 100 % drops it to 1 s, silence from a playing
 * player decays it. Not verified against the real TV (docs/test-plans P-51). R-16 adds the picture: frozen video / lost frames ([VideoStallDetector], P-52).
 */
class PlaybackHealth {
    private var margin = START_SEC
    private var playing = false
    private var seenTime = false
    private var anchorNow = 0L
    private var anchorPos = -1L
    private var lastEventAt = 0L
    private val video = VideoStallDetector()

    @Synchronized fun onPlaying(nowMs: Long) {
        video.onPlaying(nowMs)
        if (!playing) { margin = START_SEC; seenTime = false }
        playing = true; anchorNow = nowMs; anchorPos = -1; lastEventAt = nowMs
    }

    @Synchronized fun onTime(nowMs: Long, posMs: Long) {
        if (!playing) { playing = true; margin = START_SEC }
        seenTime = true; lastEventAt = nowMs
        video.onAudioTime(nowMs, posMs)
        if (anchorPos < 0) { anchorNow = nowMs; anchorPos = posMs; return }
        val dt = (nowMs - anchorNow) / 1000.0
        if (dt < 0.2) return
        val dp = (posMs - anchorPos) / 1000.0
        anchorNow = nowMs; anchorPos = posMs
        if (dp < 0 || dp > dt * 3 + 1) return                 // a seek: not a stutter
        val lag = dt - dp
        margin = if (lag > SLIP_TOLERANCE * dt) max(0.0, margin - lag * SLIP_K) else min(CAP_SEC, margin + dt)
    }

    @Synchronized fun onBuffering(nowMs: Long, percent: Float) {
        if (percent < 100f) { margin = min(margin, 1.0); lastEventAt = nowMs }
    }

    @Synchronized fun onStopped() { playing = false; seenTime = false; anchorPos = -1; video.onPaused() }

    /**
     * R-16: libVLC's picture counters (`Media.getStats()`), about once a second. The playhead follows the AUDIO: a frozen picture with a
     * smooth sound leaves it on time, which is why this second measure exists ([VideoStallDetector]).
     */
    fun onVideoStats(nowMs: Long, displayed: Int, lost: Int, hasVideo: Boolean = true) = video.onStats(nowMs, displayed, lost, hasVideo)

    /** 0 = the picture is fine, 1 = it froze / drops frames / just did, 2 = it has been so for a while (R-16). */
    fun videoDistress(nowMs: Long): Int = when {
        video.level(nowMs) == VideoStallDetector.Level.OK -> 0
        video.sustained(nowMs) -> 2
        else -> 1
    }

    @Synchronized fun bufferSec(nowMs: Long): Double? {
        if (!playing || !seenTime) return null
        val silent = nowMs - lastEventAt - SILENCE_MS
        val base = if (silent > 0) max(0.0, margin - silent / 1000.0 * SILENCE_DECAY) else margin
        // R-16: a frozen picture is a buffer that ran dry whatever the playhead says (the copy pauses, bounded by MAX_HOLD_MS); lost frames or a
        // distress that has just ended keep the copy at its floor (never above DISTRESS_FLOOR_SEC, which is inside the LOW band)
        return when (video.level(nowMs)) {
            VideoStallDetector.Level.FROZEN -> min(base, FROZEN_SEC)
            VideoStallDetector.Level.DROPPING -> min(base, DISTRESS_FLOOR_SEC)
            VideoStallDetector.Level.OK -> base
        }
    }

    companion object {
        const val START_SEC = 10.0
        const val CAP_SEC = 20.0
        const val SLIP_K = 6.0
        const val SLIP_TOLERANCE = 0.05
        const val SILENCE_MS = 1_500L
        const val SILENCE_DECAY = 3.0
        /** R-16: comfort reported for a frozen picture (inside the HOLD band, < [BufferGovernor.HOLD_SEC]). */
        const val FROZEN_SEC = 1.0
        /** R-16: comfort reported for lost frames (inside the LOW band: floor rate, no hold). */
        const val DISTRESS_FLOOR_SEC = 4.0
    }
}
