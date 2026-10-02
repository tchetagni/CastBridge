package castbridge.core.owner

import castbridge.core.crypto.MemoryWrapper
import castbridge.core.lots.Access
import castbridge.core.lots.Right
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.test.*

/** TvProof (build on the TV, verify on the phone), InstallSigner and the two owner frames. TEST KEYS only, derived from public strings. */
class TvProofTest {
    private val t0 = 1_800_000_000_000L
    private val day = 24L * 3600 * 1000
    private val hour = 3_600_000L
    private val nonce = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    private fun seed(n: String) = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-key|$n".toByteArray())
    private val desk = Ed25519Signer(seed("desk"))
    private val support = Ed25519Signer(seed("support"))
    private val rogue = Ed25519Signer(seed("rogue"))
    private val ring = KeyRing(listOf(desk.trusted(), support.trusted(setOf(KeyScope.COMMAND_SUPPORT))))
    private val install = InstallSigner(seed("tv-install-1"))
    private val install2 = InstallSigner(seed("tv-install-2"))
    private val pin = TrustedKey(install.keyId, install.publicKeyBase64)

    private val soldered = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/mmc1:0001/net/wlan0"
    private val devA = DeviceIdentity.fingerprints(RawFactors("FLASHSERIAL-A1", "cid-a1", "AA:BB:CC:00:11:01", "10:20:30:40:50:01", soldered, "SYSA0001", "11:22:33:44:55:01"))
    private val devB = DeviceIdentity.fingerprints(RawFactors("FLASHSERIAL-B2", "cid-b2", "AA:BB:CC:00:11:02", "10:20:30:40:50:02", soldered, "SYSB0002", "11:22:33:44:55:02"))

    private fun act(signer: Ed25519Signer = desk, fp: Fingerprints = devA, kind: ActivationKind = ActivationKind.PRODUCTION, subject: Subject = Subject.TV, rights: List<Right> = emptyList(),
                    issued: Long = t0, seq: Long = 1): String {
        val seat = SeatIds.of("lic-0001", fp); val k = DeviceIdentity.kFor(fp.n)
        val p = Activation.payload(kind, subject, signer.keyId, seq, "00112233445566778899aabbccddeeff", issued, issued - hour, issued + 47 * hour, "lic-0001", seat, k, fp.byKind, rights)
        return Activation(kind, subject, signer.keyId, seq, "00112233445566778899aabbccddeeff", issued, issued - hour, issued + 47 * hour, "lic-0001", seat, k, fp.byKind, rights,
            Base64.getEncoder().encodeToString(signer.sign(p.toByteArray()))).encode()
    }

    private val full = TvAccess(true, Access.TRIAL_ONLY, null, "Version complète")
    private fun proof(token: String? = act(), access: TvAccess = full, fp: Fingerprints = devA, signer: InstallSigner = install, n: String = nonce, seq: Long = 5, now: Long = t0, name: String = "Salon", grace: Boolean = false) =
        TvProof.build(signer, fp, n, seq, now, access, token, name, grace, uptimeMs = 12_345)

    private fun verify(token: String, pinned: TrustedKey? = pin, now: Long = t0 + 60_000, consumed: Set<String> = emptySet(), lastSeq: Long? = null, ringOf: KeyRing = ring, n: String = nonce, rev: RevocationState = RevocationState()) =
        TvProof.verify(token, n, pinned, ringOf, now, consumed, lastSeq, rev)

    private fun rejected(r: ProofResult, why: ProofRejection): ProofResult.Rejected { assertIs<ProofResult.Rejected>(r, r.toString()); assertEquals(why, r.reason, r.message); return r }

    // ---- nominal ----
    @Test fun aProductionProofIsAccepted() {
        val r = assertIs<ProofResult.Accepted>(verify(proof(name = "Salon")))
        assertEquals(DeviceCode.of(devA), r.proof.tvCode); assertEquals("Salon", r.proof.tvName); assertEquals(install.keyId, r.proof.installKeyId)
        assertNull(r.proof.endsAt); assertFalse(r.proof.superUnlimited); assertEquals(5, r.proof.seq); assertEquals(t0 + 60_000, r.proof.verifiedAt)
    }

    @Test fun theProofCarriesTheActivationTokenForTheServerHeader() {
        val a = act()
        assertEquals(a, assertIs<ProofResult.Accepted>(verify(proof(a))).proof.activationToken)
    }

    @Test fun anActivationOutsideItsInstallWindowStillCountsMonthsLater() {
        // the 48 h window is for INSTALLING; a proof months later must not depend on it
        assertIs<ProofResult.Accepted>(verify(proof(now = t0 + 100 * day), now = t0 + 100 * day))
    }

    @Test fun aSuperUnlimitedActivationGivesSuperAndNoEnd() {
        val a = act(rights = listOf(Right.Super("super", t0)))
        val r = assertIs<ProofResult.Accepted>(verify(proof(a, access = full.copy(superUnlimited = true))))
        assertTrue(r.proof.superUnlimited); assertNull(r.proof.endsAt)
    }

    @Test fun theUsageCeilingBecomesEndsAt() {
        val a = act(rights = listOf(Right.Usage(t0 - hour, t0 + 90 * day)))
        assertEquals(t0 + 90 * day, assertIs<ProofResult.Accepted>(verify(proof(a))).proof.endsAt)
    }

    @Test fun sameInputsGiveTheSameBytes() { assertEquals(proof(), proof()) }

    @Test fun theNameIsCutAtFortyCharactersAndHasNoLineBreak() {
        val r = assertIs<ProofResult.Accepted>(verify(proof(name = "Salon\nde la maison avec un nom vraiment trop long pour tenir")))
        assertTrue(r.proof.tvName.length <= TvProof.NAME_MAX); assertFalse('\n' in r.proof.tvName)
    }

    // ---- pinning ----
    @Test fun firstLinkAsksForThePinWithTheFingerprint() {
        val r = assertIs<ProofResult.NeedsPin>(verify(proof(), pinned = null))
        assertEquals(install.keyId, r.keyId); assertEquals(install.publicKeyBase64, r.publicKeyBase64); assertEquals(install.fingerprintText(), r.fingerprint)
        assertTrue(Regex("^([0-9a-f]{4}-){7}[0-9a-f]{4}$").matches(r.fingerprint), r.fingerprint)
        assertEquals("Salon", r.tvName)
    }

    @Test fun aReinstalledTvIsAnIdentityChangeNotAnAcceptance() {
        val r = assertIs<ProofResult.IdentityChanged>(verify(proof(signer = install2)))
        assertEquals(install2.fingerprintText(), r.fingerprint); assertEquals(install2.keyId, r.keyId)
        assertNotEquals(install.fingerprintText(), r.fingerprint)
    }

    @Test fun aForgedIdentityChangeIsJustABadSignature() {
        // a proof that claims install2's key but is signed by install: refused before any prompt
        val e = Envelope.decode(proof(signer = install2))!!
        val forged = Envelope(e.type, e.keyId, e.seq, e.nonce, e.issuedAt, e.notBefore, e.expiresAt, e.target, e.body, install.sign(e.canonicalPayload())).encode()
        rejected(verify(forged), ProofRejection.BAD_SIGNATURE)
    }

    // ---- refusals, in the order of the design ----
    @Test fun garbageAndOtherTypesAreRefused() {
        rejected(verify("nimporte quoi"), ProofRejection.MALFORMED)
        rejected(verify(act()), ProofRejection.WRONG_TYPE)
    }

    @Test fun aDifferentNonceIsRefused() { rejected(verify(proof(), n = "ff".repeat(32)), ProofRejection.NONCE_MISMATCH) }

    @Test fun aConsumedNonceIsAReplay() { rejected(verify(proof(), consumed = setOf(nonce)), ProofRejection.REPLAY) }

    @Test fun aTamperedBodyFailsTheSignature() {
        val e = Envelope.decode(proof(access = full.copy(trial = true)))!!
        val body = e.body.map { if (it.startsWith("state=")) "state=production" else it }
        rejected(verify(Envelope(e.type, e.keyId, e.seq, e.nonce, e.issuedAt, e.notBefore, e.expiresAt, e.target, body, e.signature).encode()), ProofRejection.BAD_SIGNATURE)
    }

    @Test fun aSwappedPublicKeyIsRefused() {
        val e = Envelope.decode(proof())!!
        val body = e.body.map { if (it.startsWith("installKey=")) "installKey=${install2.publicKeyBase64}" else it }
        rejected(verify(Envelope(e.type, e.keyId, e.seq, e.nonce, e.issuedAt, e.notBefore, e.expiresAt, e.target, body, e.signature).encode()), ProofRejection.MALFORMED)
    }

    @Test fun anOlderSequenceThanTheLastSeenIsAReplay() {
        rejected(verify(proof(seq = 4), lastSeq = 5), ProofRejection.REPLAY)
        assertIs<ProofResult.Accepted>(verify(proof(seq = 5), lastSeq = 5))
    }

    @Test fun theWindowClosesButToleratesClockSkew() {
        val token = proof(now = t0)
        assertIs<ProofResult.Accepted>(verify(token, now = t0 + 10 * hour))
        rejected(verify(token, now = t0 + 3 * day), ProofRejection.WINDOW_CLOSED)
        rejected(verify(token, now = t0 - 3 * day), ProofRejection.WINDOW_CLOSED)
    }

    @Test fun everyNonProductionStateIsRefusedWithTheTvIdentity() {
        val cases = listOf(
            Triple(full.copy(trial = true), false, ProofRejection.TRIAL), Triple(full.copy(suspended = true), false, ProofRejection.SUSPENDED),
            Triple(TvAccess(false, Access.TRIAL_ONLY, null, "Aucune clé installée"), false, ProofRejection.LOCKED), Triple(TvAccess(false, Access.TRIAL_ONLY, null, "x"), true, ProofRejection.GRACE),
        )
        for ((access, grace, why) in cases) {
            val r = rejected(verify(proof(access = access, grace = grace)), why)
            assertEquals(DeviceCode.of(devA), r.tvCode); assertEquals("Salon", r.tvName)
        }
    }

    @Test fun degradedIsRefusedWhenTheTvSaysSo() {
        val e = Envelope.decode(proof())!!
        val body = e.body.map { if (it.startsWith("state=")) "state=degraded" else it }
        val payload = Envelope.payload(e.type, e.keyId, e.seq, e.nonce, e.issuedAt, e.notBefore, e.expiresAt, e.target, body)
        val r = rejected(verify(Envelope(e.type, e.keyId, e.seq, e.nonce, e.issuedAt, e.notBefore, e.expiresAt, e.target, body, install.sign(payload)).encode()), ProofRejection.DEGRADED)
        assertEquals("Salon", r.tvName)
    }

    @Test fun superWithoutAnyTokenStillNeedsAnActivationToBeProven() {
        rejected(verify(proof(token = null, access = full.copy(superUnlimited = true))), ProofRejection.LOCKED)
    }

    @Test fun aTvWithoutActivationIsLocked() { rejected(verify(proof(token = null, access = full)), ProofRejection.LOCKED) }

    @Test fun aTrialActivationIsNotAProof() {
        rejected(verify(proof(act(kind = ActivationKind.TRIAL, rights = listOf(Right.Usage(t0 - hour, t0 + 30 * day))))), ProofRejection.TRIAL)
    }

    @Test fun theActivationKeyMustBeKnownAllowedAndNotRevoked() {
        rejected(verify(proof(act(signer = rogue))), ProofRejection.ACTIVATION_INVALID)
        rejected(verify(proof(act(signer = support))), ProofRejection.ACTIVATION_INVALID)
        rejected(verify(proof(), ringOf = KeyRing(listOf(desk.trusted()), setOf(desk.keyId))), ProofRejection.ACTIVATION_INVALID)
        rejected(verify(proof(), rev = RevocationState(keys = setOf(desk.keyId))), ProofRejection.ACTIVATION_INVALID)
    }

    @Test fun aSuperRightNeedsTheSuperScope() {
        val noSuper = Ed25519Signer(seed("phone"))
        val r = KeyRing(listOf(noSuper.trusted(KeyScope.ALL - KeyScope.SUPER_UNLIMITED)))
        rejected(verify(proof(act(signer = noSuper, rights = listOf(Right.Super("super", t0))), access = full.copy(superUnlimited = true)), ringOf = r), ProofRejection.ACTIVATION_INVALID)
    }

    @Test fun aPhoneActivationOrARevokedSeatIsRefused() {
        rejected(verify(proof(act(subject = Subject.PHONE))), ProofRejection.ACTIVATION_INVALID)
        val a = Activation.decode(act())!!
        rejected(verify(proof(), rev = RevocationState(seats = mapOf("${a.license}|${a.seat}" to t0 + day))), ProofRejection.ACTIVATION_INVALID)
    }

    @Test fun anActivationOfAnotherTvDoesNotMatchTheCode() { rejected(verify(proof(act(fp = devB), fp = devA)), ProofRejection.CODE_MISMATCH) }

    @Test fun aPassedUsageCeilingIsRefused() {
        val a = act(rights = listOf(Right.Usage(t0 - 40 * day, t0 - day)))
        rejected(verify(proof(a)), ProofRejection.USAGE_EXCEEDED)
    }

    @Test fun theUsageCeilingIsJudgedAtTheLaterOfPhoneClockAndIssueDate() {
        // phone clock behind (within the window tolerance): the TV's signed issue date proves time reached the ceiling
        val a = act(rights = listOf(Right.Usage(t0 - 40 * day, t0 + hour)))
        rejected(verify(proof(a, now = t0 + 2 * hour), now = t0 - 8 * hour), ProofRejection.USAGE_EXCEEDED)
    }

    @Test fun aSteppedActivationInconsistencyIsRefused() {
        val e = Envelope.decode(proof())!!
        val body = e.body.map { if (it.startsWith("super=")) "super=1" else it }
        val payload = Envelope.payload(e.type, e.keyId, e.seq, e.nonce, e.issuedAt, e.notBefore, e.expiresAt, e.target, body)
        rejected(verify(Envelope(e.type, e.keyId, e.seq, e.nonce, e.issuedAt, e.notBefore, e.expiresAt, e.target, body, install.sign(payload)).encode()), ProofRejection.MALFORMED)
    }

    // ---- frames ----
    @Test fun theTwoFramesAreFreeTypesAndTheProofFitsAFrame() {
        assertEquals(9, OwnerFrames.PROOF_REQUEST); assertEquals(10, OwnerFrames.PROOF)
        val all = listOf(OwnerFrames.CHALLENGE_REQUEST, OwnerFrames.CHALLENGE, OwnerFrames.COMMAND, OwnerFrames.RESULT, OwnerFrames.DEVICE_INFO_REQUEST, OwnerFrames.DEVICE_INFO, OwnerFrames.PAIR, OwnerFrames.ACTIVATION, OwnerFrames.PROOF_REQUEST, OwnerFrames.PROOF)
        assertEquals(all.size, all.toSet().size)
        val token = proof(act(rights = listOf(Right.Purchase("p-classe-cm2", listOf("classe-cm2"), t0), Right.Usage(t0 - hour, t0 + 900 * day))))
        val bytes = OwnerFrames.encode(OwnerFrames.PROOF, token)
        val f = OwnerFrames.read(bytes.inputStream())!!
        assertEquals(OwnerFrames.PROOF, f.type); assertEquals(token, f.text)
        assertTrue(token.length < OwnerFrames.MAX_PAYLOAD, "${token.length}")
        assertEquals(nonce, OwnerFrames.read(OwnerFrames.encode(OwnerFrames.PROOF_REQUEST, nonce).inputStream())!!.text)
    }

    // ---- InstallSigner ----
    @Test fun theInstallKeyIdIsTheKeyRingIdAndSignsVerifiably() {
        assertEquals(KeyRing.idOf(install.publicKeyBase64), install.keyId)
        assertTrue(TrustedKey(install.keyId, install.publicKeyBase64).verify("abc", install.sign("abc")))
        assertFalse(TrustedKey(install.keyId, install.publicKeyBase64).verify("abd", install.sign("abc")))
        assertNotEquals(install.keyId, install2.keyId)
    }

    @Test fun theSealedSeedSurvivesAReloadAndABadWrapperRegenerates() {
        val store = object : InstallSignerStore { var blob: ByteArray? = null; override fun load() = blob; override fun save(blob: ByteArray) { this.blob = blob } }
        val w = MemoryWrapper(ByteArray(32) { 7 })
        val first = InstallSigner.loadOrCreate(store, w, SecureRandom())
        assertFalse(first.regenerated)
        assertFalse(Base64.getEncoder().encodeToString(store.blob!!).contains(first.signer.publicKeyBase64.take(10)))
        val again = InstallSigner.loadOrCreate(store, w)
        assertFalse(again.regenerated); assertEquals(first.signer.keyId, again.signer.keyId)
        val other = InstallSigner.loadOrCreate(store, MemoryWrapper(ByteArray(32) { 9 }))
        assertTrue(other.regenerated); assertNotEquals(first.signer.keyId, other.signer.keyId)
    }

    @Test fun aFailedWriteThrowsAndNoUnsavedIdentityIsReturned() {
        val store = object : InstallSignerStore { override fun load(): ByteArray? = null; override fun save(blob: ByteArray) { throw java.io.IOException("disque plein") } }
        assertFailsWith<java.io.IOException> { InstallSigner.loadOrCreate(store) }
    }

    @Test fun theFileStoreKeepsTheSameKeyWithThePlainFallback() {
        val dir = kotlin.io.path.createTempDirectory("install").toFile()
        try {
            val f = File(dir, "install.key")
            val a = InstallSigner.loadOrCreate(FileInstallSignerStore(f))
            val b = InstallSigner.loadOrCreate(FileInstallSignerStore(f))
            assertEquals(a.signer.keyId, b.signer.keyId); assertFalse(b.regenerated)
        } finally { dir.deleteRecursively() }
    }
}
