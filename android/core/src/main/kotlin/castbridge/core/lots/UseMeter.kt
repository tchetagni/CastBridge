package castbridge.core.lots

/** Thresholds of the use counter (milliseconds on the MONOTONE clock). */
data class UseMeterConfig(
    /** A pause (a film paused) keeps counting this long, then stops until [UseMeter.resume]. */
    val pauseStopMs: Long = 5 * 60_000L,
    /** Without any key press for this long the counter stops until [UseMeter.input] (a TV left on a lesson). Playback of a work extends it, see [UseMeter.startPlayback]. */
    val idleStopMs: Long = 30 * 60_000L,
    val minuteMs: Long = 60_000L,
    /** Length assumed for a work whose duration is unknown (fallback of [UseMeter.startPlayback]). */
    val unknownDurationMs: Long = 3 * 3_600_000L,
)

/** What a [UseMeter] call hands to the ledger: [minutes] whole minutes of use of [lot] (null lot = nothing open, always 0 minutes). */
data class Tick(val lot: LotId?, val minutes: Int)

/**
 * Counts the real use of rented content, in whole minutes, from a MONOTONE clock the caller passes as a parameter (`elapsedRealtime` on the TV): the wall clock is never read, so
 * changing the date changes nothing. Pure (no Android, no thread, no timer): the screen calls [tick] about every minute and on [close]. ONE meter per process (the ledger also
 * refuses more minutes than the monotone time that has elapsed, see `RentalLedger.recordTick`).
 *
 * What counts: a lot is open AND the screen is in the foreground (the caller opens on resume and closes on pause or screen off) AND not paused for more than
 * [UseMeterConfig.pauseStopMs] AND a key was pressed less than [UseMeterConfig.idleStopMs] ago. While a work PLAYS ([startPlayback], [tick] with `playing`) the playback counts as
 * activity for the length of the work plus [UseMeterConfig.idleStopMs] (fallback [UseMeterConfig.unknownDurationMs] when unknown); an automatic next work does not renew that window.
 *
 * The remainder below one minute is CARRIED PER LOT for the life of the process and never moves to another lot (so alternating a rented and a free lot cannot dodge the count, and
 * opening/closing every 59 s still adds up). The carry is never persisted: after a restart it is empty (loss under one minute per lot, accepted) and nothing can be counted twice.
 * Closing or switching lot returns the whole minutes of the lot that was open. A reading earlier than the previous one (reboot, rollback) counts zero; the reference moves back and
 * the key-press, pause and playback windows move back by the same amount (they neither freeze nor restart).
 */
class UseMeter(private val cfg: UseMeterConfig = UseMeterConfig()) {
    private var openLot: LotId? = null
    private var lastMono = 0L
    private val carryMs = HashMap<LotId, Long>()
    private var pausedSince: Long? = null
    private var lastInput = 0L
    private var playing = false
    private var playUntil = 0L

    /** Content of [lot] is on screen. Another lot already open is flushed first (its whole minutes are returned); the same lot again counts as a key press. */
    @Synchronized fun open(lot: LotId, nowMono: Long): Tick {
        val prev = openLot
        if (prev == null) {
            openLot = lot; lastMono = nowMono; pausedSince = null; lastInput = nowMono; playing = false; playUntil = 0
            return Tick(lot, 0)
        }
        accrue(nowMono)
        lastInput = nowMono; pausedSince = null
        if (prev == lot) return Tick(lot, 0)
        openLot = lot; playing = false; playUntil = 0
        return Tick(prev, takeMinutes(prev))
    }

    /** The content left the screen (pause of the activity, screen off): the whole minutes are returned; the remainder stays with the lot. Nothing stays open. */
    @Synchronized fun close(nowMono: Long): Tick {
        val lot = openLot ?: return Tick(null, 0)
        accrue(nowMono)
        val minutes = takeMinutes(lot)
        openLot = null; pausedSince = null; playing = false; playUntil = 0
        return Tick(lot, minutes)
    }

    /** A film was paused (the pause is itself a key press): it keeps counting for [UseMeterConfig.pauseStopMs], then stops. No effect without an open lot. */
    @Synchronized fun pause(nowMono: Long) {
        if (openLot == null) return
        accrue(nowMono)
        playing = false; lastInput = nowMono
        if (pausedSince == null) pausedSince = nowMono
    }

    /** The film plays again (a key press as well). The playback window moves by the real length of the pause, so the end of the work still counts. */
    @Synchronized fun resume(nowMono: Long) {
        if (openLot == null) return
        accrue(nowMono)
        pausedSince?.let { if (playUntil > 0) playUntil += maxOf(0L, nowMono - it) }
        pausedSince = null; lastInput = nowMono; playing = true
    }

    /**
     * Any key press: restarts the inactivity delay. It does NOT lift a pause: a pause exists only because the player declared it with [pause], and only [resume] (or a new
     * [startPlayback]) ends it; a D-pad key on the pause menu is not a resume.
     */
    @Synchronized fun input(nowMono: Long) {
        if (openLot == null) return
        accrue(nowMono)
        lastInput = nowMono
    }

    /**
     * A work starts playing. [userInitiated] (the viewer chose it: a key press) opens the playback window: playing counts as activity for [durationMs] (null = unknown, fallback
     * [UseMeterConfig.unknownDurationMs]) plus [UseMeterConfig.idleStopMs]. An automatic next work ([userInitiated] false) keeps the window as it is.
     */
    @Synchronized fun startPlayback(nowMono: Long, durationMs: Long?, userInitiated: Boolean = true) {
        if (openLot == null) return
        accrue(nowMono)
        playing = true; pausedSince = null
        if (userInitiated) { lastInput = nowMono; playUntil = nowMono + (durationMs?.takeIf { it > 0 } ?: cfg.unknownDurationMs) + cfg.idleStopMs }
    }

    /**
     * About every minute: the whole minutes of use since the last call (0 when nothing is open or counting is stopped). [playing] says whether a work plays NOW; it applies to the time
     * after this call (the time before it was judged with the state known at the previous call).
     */
    @Synchronized fun tick(nowMono: Long, playing: Boolean = this.playing): Tick {
        val lot = openLot ?: return Tick(null, 0)
        accrue(nowMono)
        this.playing = playing
        return Tick(lot, takeMinutes(lot))
    }

    /** Adds to the lot's carry the part of the time since the last call that really counts (stops after a long pause or a long silence), then moves the reference to [nowMono]. */
    private fun accrue(nowMono: Long) {
        val lot = openLot ?: return
        if (nowMono < lastMono) {                                      // reboot or rollback: nothing counted, every window moves back by the same amount
            val back = lastMono - nowMono
            lastMono = nowMono; lastInput -= back; pausedSince = pausedSince?.let { it - back }; playUntil -= back
            return
        }
        var limit = minOf(nowMono, maxOf(lastInput + cfg.idleStopMs, if (playing) playUntil else 0L))
        pausedSince?.let { limit = minOf(limit, it + cfg.pauseStopMs) }
        carryMs[lot] = (carryMs[lot] ?: 0L) + maxOf(0L, limit - lastMono)
        lastMono = nowMono
    }

    /** Whole minutes in [lot]'s carry (bounded to the ledger's 24 h per call); the remainder stays. */
    private fun takeMinutes(lot: LotId): Int {
        val c = carryMs[lot] ?: 0L
        val m = minOf(c / cfg.minuteMs, 24L * 60).toInt()
        carryMs[lot] = c - m.toLong() * cfg.minuteMs
        return m
    }
}
