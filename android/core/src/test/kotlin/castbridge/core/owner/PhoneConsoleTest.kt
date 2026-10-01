package castbridge.core.owner

import castbridge.core.lots.Right
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The owner phone's « Générer un jeton » core and the shared licence-aware flow (docs/ACTIVATION-TOOLS.md). */
class PhoneConsoleTest {
    private var t = 1_800_000_000_000L
    private val clock = { t.also { t += 1000 } }
    private val seed = ByteArray(32) { (it * 7 + 3).toByte() }
    private val signer = Ed25519Signer(seed)
    private val kdf = Pbkdf2Kdf(10)
    private val vault = OwnerVault.seal(seed, "mon code propriétaire".toCharArray(), kdf)
    private val own = signer.trusted(KeyScope.ALL - KeyScope.REGISTRY)
    private val registry = MemoryRegistry()
    private var g = 1_800_000_000_000L   // the guard's own (monotonic) clock, moved by hand
    private val guard = UnlockGuard({ g })
    private val console = PhoneConsole(vault, kdf, guard, KeyScope.ALL - KeyScope.REGISTRY, own, registry, clock = clock)

    private fun fp(n: Int) = DeviceIdentity.fingerprints(RawFactors("FLASH-$n", "cid-$n", "AA:BB:CC:00:00:%02x".format(n), "10:20:30:40:50:%02x".format(n), "/sys/devices/platform/soc/x/mmc_host/net/wlan0", "SYS$n", "11:22:33:44:55:%02x".format(n)))
    private fun info(n: Int) = fp(n).let { OwnerFrames.deviceInfo(DeviceCode.of(it), it) }
    private fun unlocked() = (console.unlock("mon code propriétaire".toCharArray()) as PhoneUnlock.Unlocked).session

    @Test fun trialIsIssuedWithEveryEncodingAndVerifiesOnTheTv() {
        val s = unlocked()
        val r = s.issue(info(1), IssueSpec(ActivationKind.TRIAL, windowDays = 90))
        assertTrue(r.token.startsWith(Envelope.PREFIX + ".") && r.fileContent == r.token + "\n")
        assertEquals(OwnerFrames.ACTIVATION, r.bluetoothFrame[0].toInt())
        val ring = KeyRing(listOf(own))
        assertIs<ActivationResult.Accepted>(ActivationVerifier(ring).verify(r.token, fp(1), t))
        assertIs<ActivationResult.Rejected>(ActivationVerifier(ring).verify(r.token, fp(2), t), "another TV does not accept it")
    }

    @Test fun seatsAreCountedByTheRegistryAndReactivationIsFree() {
        val s = unlocked()
        val rights = listOf(Right.Purchase("p-cm2", listOf("classe-cm2"), t))
        assertFailsWith<IssueException> { s.issue(info(1), IssueSpec(ActivationKind.PRODUCTION, rights = rights, license = "lic-0009")) }   // unknown licence
        s.createLicense("lic-0009", 1)
        val a = s.issue(info(1), IssueSpec(ActivationKind.PRODUCTION, rights = rights, license = "lic-0009"))
        assertEquals(0, a.delivered.seatsLeft); assertFalse(a.delivered.reused)
        val again = s.issue(info(1), IssueSpec(ActivationKind.PRODUCTION, rights = rights, license = "lic-0009"))
        assertTrue(again.delivered.reused); assertEquals(a.delivered.seat, again.delivered.seat)
        assertFailsWith<IssueException> { s.issue(info(2), IssueSpec(ActivationKind.PRODUCTION, rights = rights, license = "lic-0009")) }   // no seat left
    }

    @Test fun theRegistryOfThePhoneMergesWithAnotherToolsWithoutDuplicates() {
        val s = unlocked(); s.createLicense("lic-0010", 2)
        s.issue(info(3), IssueSpec(ActivationKind.PRODUCTION, rights = listOf(Right.Purchase("p", listOf("b"), t)), license = "lic-0010"))
        val events = registry.events()
        assertEquals(events.size, LicenseBook.merge(events, events).size)
        val state = LicenseBook.replay(events, KeyRing(listOf(own)))
        assertEquals(1, state.usedSeats("lic-0010")); assertEquals(emptyList(), state.rejected.toList())
    }

    @Test fun wrongCodeIsSlowedThenBlockedAndNeverReachesTheKey() {
        repeat(3) { assertIs<PhoneUnlock.Wrong>(console.unlock("faux code".toCharArray())) }
        val fourth = console.unlock("faux code".toCharArray()); assertIs<PhoneUnlock.Wrong>(fourth); assertTrue(fourth.waitMs > 0)
        assertIs<PhoneUnlock.Wait>(console.unlock("mon code propriétaire".toCharArray()), "even the right code waits while the guard counts down")
        g += 10 * 60 * 1000
        assertIs<PhoneUnlock.Unlocked>(console.unlock("mon code propriétaire".toCharArray()))
        assertTrue(console.audit.verify())
    }

    @Test fun lockingWipesTheAbilityToSign() {
        val s = unlocked(); s.lock(); assertFalse(s.unlocked)
        assertFailsWith<IssueException> { s.issue(info(1), IssueSpec(ActivationKind.TRIAL)) }
    }

    @Test fun auditNeverContainsSecrets() {
        val s = unlocked(); s.issue(info(1), IssueSpec(ActivationKind.TRIAL))
        val text = console.audit.entries.joinToString { "${it.action} ${it.target} ${it.outcome}" }
        assertFalse(text.contains("mon code") || text.contains(signer.publicKeyBase64))
        assertTrue(console.audit.verify() && console.audit.entries.any { it.action == "issue" })
    }

    @Test fun rightsSyntaxIsStrict() {
        val r = RightsSyntax.parseBox("# commentaire\nachat p-cm2=classe-cm2,classe-6e\nabonnement abo=tout:365:7:auto\ntout-ouvert ouvert:10\n", t)
        assertEquals(3, r.size)
        assertFailsWith<IssueException> { RightsSyntax.parseBox("achat sansbouquet", t) }
        assertFailsWith<IssueException> { RightsSyntax.parseBox("n'importe quoi", t) }
    }
}
