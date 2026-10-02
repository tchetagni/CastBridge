package castbridge.core.owner

import castbridge.core.update.Ed25519
import java.security.MessageDigest
import java.util.Base64

/** Graduated powers of a console key (docs/TRIAL-EDITION.md § Console propriétaire). A key never exceeds its [Power]. */
enum class Power(val rank: Int, /** Longest validity the TV grants for this power, in days (the verifier clamps, the TV is the judge). */ val maxDays: Int) {
    /** Diagnostics, reset of the trial. No content unlocked. */
    SUPPORT(1, 0),
    /** Precise lots or bundles for a limited time. */
    UNLOCK(2, 30),
    /** « Tout ouvert » : everything the trial and beyond allow, at most 30 days per command, renewable by a new command. */
    OPEN_ALL(3, 30),
}

/**
 * What a key may sign (docs/ACTIVATION-FORMAT.md § Clés et portées). One key per tool (desk, owner phone, server), never a shared key: a compromised
 * tool is revoked on its own, and a key never exceeds its scopes (the server key has no [COMMAND_OPEN_ALL] and no [TRANSFER]).
 */
enum class KeyScope {
    ISSUE_TRIAL, ISSUE_PRODUCTION, COMMAND_SUPPORT, COMMAND_UNLOCK, COMMAND_OPEN_ALL,
    /** Move a licence seat to other hardware: desk and owner phone only, never the server. */
    TRANSFER,
    /** Sign revocation lists. */
    REVOKE,
    /** Sign licence registries (the synchronisation file of the three tools). */
    REGISTRY,
    /** Re-issue the activation of a seat that already exists (same hardware, no new seat): a key that may only reactivate cannot create seats. */
    REACTIVATE,
    /** Sign deferred orders (management policies, docs/agent-briefs/deferred-orders.md). The server key has it; no key gets it implicitly from another scope. */
    POLICY,
    /** SUPER_UNLIMITED: sign the `super` right (reads and unlocks everything, rentals included, for good). The super administrator's key only (phone superadmin, desk): never the server. Every code can still be installed during [ActivationPolicy.CODE_VALIDITY_HOURS] hours, this scope included. */
    SUPER_UNLIMITED;

    companion object {
        val ALL: Set<KeyScope> = values().toSet()
        /** Scopes of a key that may command up to [power] and issue activations (the old "maximum power" model). */
        fun upTo(power: Power): Set<KeyScope> = buildSet {
            add(COMMAND_SUPPORT)
            if (power.rank >= Power.UNLOCK.rank) { add(COMMAND_UNLOCK); add(ISSUE_TRIAL); add(ISSUE_PRODUCTION) }
            if (power.rank >= Power.OPEN_ALL.rank) { add(COMMAND_OPEN_ALL); add(TRANSFER); add(REVOKE); add(REGISTRY); add(REACTIVATE); add(POLICY); add(SUPER_UNLIMITED) }
        }
        fun of(p: Power) = when (p) { Power.SUPPORT -> COMMAND_SUPPORT; Power.UNLOCK -> COMMAND_UNLOCK; Power.OPEN_ALL -> COMMAND_OPEN_ALL }
    }
}

/** A public key the TV accepts, with its scopes. Several at once (rotation, spare key kept offline). */
data class TrustedKey(val keyId: String, val publicKeyBase64: String, val scopes: Set<KeyScope> = KeyScope.ALL) {
    fun allows(scope: KeyScope) = scope in scopes
    fun allows(power: Power) = KeyScope.of(power) in scopes

    fun verify(message: String, signatureBase64: String): Boolean = try {
        Ed25519.verify(Base64.getDecoder().decode(publicKeyBase64.trim()), message.toByteArray(Charsets.UTF_8), Base64.getDecoder().decode(signatureBase64))
    } catch (e: IllegalArgumentException) { false }
}

/** The keys the TV app embeds (public keys only: no secret in the distributed APK) plus the revocation list (updatable by a signed message). */
class KeyRing(keys: List<TrustedKey>, val revoked: Set<String> = emptySet()) {
    private val byId = keys.associateBy { it.keyId }
    fun find(keyId: String): TrustedKey? = byId[keyId]
    fun isRevoked(keyId: String) = keyId in revoked
    fun withRevoked(ids: Set<String>) = KeyRing(byId.values.toList(), revoked + ids)

    companion object {
        /** keyId = first 8 bytes (16 hex chars) of SHA-256 of the raw public key: stable, short, no secret. */
        fun idOf(publicKeyBase64: String): String = MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(publicKeyBase64.trim())).take(8).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Time on a TV that is offline for months and whose clock may be wrong (docs/TRIAL-EDITION.md § Horloge). Rules:
 * the time used for any validity decision is `max(clock, lastSeen, floor)` where [floor] is the highest `issuedAt` of anything signed that the TV
 * accepted (a signed message proves that time has at least reached it); a clock going BACK never shortens or extends anything
 * (the high-water mark stays); a clock jumping FORWARD by more than [AHEAD_MAX_MS] (45 days) is not believed (the time then advances with the monotonic time only, and
 * [TvGate] raises [castbridge.core.lots.ClockDoubt.AHEAD] and suspends the usage ceilings) until a signed message or the user ([confirmAhead]) confirms it.
 *
 * [uptimeMs] is the CUMULATIVE running time (sum of the monotonic time observed, persisted): an independent second ceiling for the usage rights, which a wall clock wound back
 * and a reboot cannot freeze. It does not count the time the TV is off, so it is a lower bound of the real time (never shorter than the truth for the customer).
 */
class TvClock(var lastSeen: Long = 0L, var floor: Long = 0L, /** Monotonic milliseconds that never go back during a boot (the receiver passes SystemClock.elapsedRealtime). */ var mono: () -> Long = DEFAULT_MONO,
              /** Cumulative running time up to the last observation (persisted in `clock.txt`, format v2). */ var uptimeMs: Long = 0L) {
    companion object {
        const val MAX_JUMP_MS = 400L * 24 * 3600 * 1000
        /** A forward jump of the wall clock beyond this (45 days) in one observation is doubted, not believed; same value as the rental engine's `aheadDoubtMs`. */
        const val AHEAD_MAX_MS = 45L * 24 * 3600 * 1000
        /** Below this gap the wall clock is simply believed against the high-water mark; beyond it the monotonic time takes over. */
        const val BEHIND_TOLERANCE_MS = 60_000L
        val DEFAULT_MONO: () -> Long = { System.nanoTime() / 1_000_000L }

        /** `clock.txt` v2 : `lastSeen floor uptimeMs`. The old format (2 fields, `lastSeen floor`) is still read with `uptimeMs = 0`. Null when the text is neither. */
        fun decode(text: String, mono: () -> Long = DEFAULT_MONO): TvClock? {
            val p = text.trim().split(' ').filter { it.isNotEmpty() }
            if (p.size != 2 && p.size != 3) return null
            val last = p[0].toLongOrNull() ?: return null; val fl = p[1].toLongOrNull() ?: return null
            val up = if (p.size == 3) p[2].toLongOrNull() ?: return null else 0L
            if (last < 0 || fl < 0 || up < 0) return null
            return TvClock(last, fl, mono, up)
        }
    }

    /**
     * Monotonic reading at the last observation of this boot (never persisted: a reboot restarts it, so a boot adds nothing by itself; the running time of the boot enters [uptimeMs]
     * at each [observe]). A reboot while the wall clock is rolled back loses at most the running time since the last persisted observation (the receiver saves every 5 minutes);
     * the persisted marks never go back, so the loss is bounded and never extends anything.
     */
    private var monoAtSeen: Long = mono()

    /** Time elapsed (monotonic) since the high-water mark was last refreshed in this boot. */
    private fun elapsed(): Long = maxOf(0L, mono() - monoAtSeen)

    /** Cumulative running time of the TV now: the persisted [uptimeMs] plus what ran since the last observation. */
    @Synchronized fun uptimeNow(): Long = uptimeMs + elapsed()

    /** The text of `clock.txt` (format v2). */
    @Synchronized fun encode(): String = "$lastSeen $floor $uptimeMs"

    /** The high-water mark advanced with the monotonic time of this boot: the TV time when the wall clock is BEHIND it (it keeps going forward, never freezes). */
    @Synchronized fun monotonicNow(): Long { val base = maxOf(lastSeen, floor); return if (base == 0L) 0L else base + elapsed() }

    /** True when [clock] is more than [AHEAD_MAX_MS] ahead of the high-water mark (no signed floor justifies it): the jump is not believed. */
    @Synchronized fun isAhead(clock: Long): Boolean { val base = maxOf(lastSeen, floor); return base != 0L && clock > base + AHEAD_MAX_MS }

    /** Time to use now for a decision. */
    @Synchronized fun now(clock: Long): Long {
        val base = maxOf(lastSeen, floor)
        if (base == 0L) return clock                      // first observation ever: nothing to compare with
        // wall clock behind the high-water mark (rollback): time keeps ADVANCING with the monotonic time instead of freezing; ahead beyond AHEAD_MAX_MS: not believed (AHEAD doubt)
        if (clock + BEHIND_TOLERANCE_MS >= base && clock <= base + AHEAD_MAX_MS) return maxOf(clock, base)   // normal running (a few seconds of jitter are not a rollback)
        if (clock > base + AHEAD_MAX_MS) return base + elapsed()   // AHEAD jump: time only advances with the monotonic time until a signed message or the user confirms it
        return maxOf(clock, base + elapsed())
    }

    /** Record what the clock says (call at each start and every 5 minutes of running) and what signed messages prove. */
    @Synchronized fun observe(clock: Long, signedIssuedAt: Long = 0L) {
        floor = maxOf(floor, signedIssuedAt)              // a signed message that proves the jump is taken into account BEFORE the jump is judged
        val n = now(clock)
        lastSeen = maxOf(lastSeen, n)
        val t = mono()
        uptimeMs += maxOf(0L, t - monoAtSeen)
        monoAtSeen = t
    }

    /** The user says « l'heure est juste » after an AHEAD suspension: accepted up to [MAX_JUMP_MS]; a clock BEHIND is never accepted (it would extend). */
    @Synchronized fun confirmAhead(clock: Long): Boolean {
        val base = maxOf(lastSeen, floor)
        if (clock < base || clock > base + MAX_JUMP_MS) return false
        lastSeen = clock; return true
    }

    /** True when the wall clock is clearly behind what the TV already saw (rollback or empty battery clock). */
    fun rolledBack(clock: Long, marginMs: Long = 24L * 3600 * 1000) = clock + marginMs < maxOf(lastSeen, floor)
}
