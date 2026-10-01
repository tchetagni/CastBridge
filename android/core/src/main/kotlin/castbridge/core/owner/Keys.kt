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
    /** Issue a PERMANENT usage licence (purchase of the bundle "tout"): superadmin phone / desk only, never the server. Every code can be installed during [ActivationPolicy.CODE_VALIDITY_HOURS] hours, this scope included. */
    ISSUE_UNLIMITED;

    companion object {
        val ALL: Set<KeyScope> = values().toSet()
        /** Scopes of a key that may command up to [power] and issue activations (the old "maximum power" model). */
        fun upTo(power: Power): Set<KeyScope> = buildSet {
            add(COMMAND_SUPPORT)
            if (power.rank >= Power.UNLOCK.rank) { add(COMMAND_UNLOCK); add(ISSUE_TRIAL); add(ISSUE_PRODUCTION) }
            if (power.rank >= Power.OPEN_ALL.rank) { add(COMMAND_OPEN_ALL); add(TRANSFER); add(REVOKE); add(REGISTRY); add(REACTIVATE); add(POLICY); add(ISSUE_UNLIMITED) }
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
 * (the high-water mark stays); a clock jumping FORWARD by more than [MAX_JUMP_MS] is not believed (it would expire everything for good
 * if it was a glitch) until a signed message confirms it.
 */
class TvClock(var lastSeen: Long = 0L, var floor: Long = 0L) {
    companion object { const val MAX_JUMP_MS = 400L * 24 * 3600 * 1000 }

    /** Time to use now for a decision. */
    fun now(clock: Long): Long {
        val base = maxOf(lastSeen, floor)
        if (base == 0L) return clock                      // first observation ever: nothing to compare with
        return if (clock > base + MAX_JUMP_MS) base else maxOf(clock, base)
    }

    /** Record what the clock says (call at each start and each hour of running) and what signed messages prove. */
    fun observe(clock: Long, signedIssuedAt: Long = 0L) {
        floor = maxOf(floor, signedIssuedAt)
        lastSeen = maxOf(lastSeen, now(clock))
    }

    /** True when the wall clock is clearly behind what the TV already saw (rollback or empty battery clock). */
    fun rolledBack(clock: Long, marginMs: Long = 24L * 3600 * 1000) = clock + marginMs < maxOf(lastSeen, floor)
}
