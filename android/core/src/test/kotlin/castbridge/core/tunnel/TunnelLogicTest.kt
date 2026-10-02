package castbridge.core.tunnel

import castbridge.core.owner.Activation
import castbridge.core.owner.ActivationKind
import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.KeyScope
import castbridge.core.lots.Right
import castbridge.core.owner.Subject
import java.io.File
import java.util.Random
import kotlin.test.*

class TunnelLogicTest {
    private val dir = kotlin.io.path.createTempDirectory("tunl").toFile()
    @AfterTest fun clean() { dir.deleteRecursively() }

    // ---- terms ----
    @Test fun termsAcceptanceIsVersionedDatedAndPerInstallation() {
        var now = 1_800_000_000_000L; var inst = "AAAA-BBBB-CCCC-DDDD"
        val s = TermsStore(File(dir, "terms.json"), { inst }, { now })
        assertFalse(s.accepted())
        val a = s.accept(); assertEquals(TunnelTerms.VERSION, a.version); assertEquals(now, a.acceptedAt)
        assertTrue(s.accepted())
        now += 5000; assertEquals(a, s.accept(), "idempotent: the first date is kept")
        assertTrue(TermsStore(File(dir, "terms.json"), { inst }, { now }).accepted(), "survives a restart")
        inst = "ZZZZ-BBBB-CCCC-DDDD"; assertFalse(s.accepted(), "a file copied to another TV means nothing")
        inst = "AAAA-BBBB-CCCC-DDDD"; assertTrue(s.accepted())
        assertFalse(TermsStore(File(dir, "terms.json"), { inst }, { now }, version = "Conditions d'usage v2").accepted(), "a new version asks again")
        s.withdraw(); assertFalse(s.accepted()); assertFalse(TermsStore(File(dir, "terms.json"), { inst }).accepted())
    }

    @Test fun termsTextCarriesArticlesXYZ() {
        assertTrue(TunnelTerms.TEXT.contains("Article X") && TunnelTerms.TEXT.contains("Article Y") && TunnelTerms.TEXT.contains("Article Z"))
        assertTrue("CastBridge du téléphone" in TunnelTerms.ARTICLE_Y && "journal" in TunnelTerms.ARTICLE_Y)
        assertEquals("Conditions d'usage v1", TunnelTerms.VERSION)
    }

    @Test fun corruptTermsFileMeansNotAccepted() {
        val f = File(dir, "t.json"); f.writeText("{garbage")
        assertFalse(TermsStore(f, { "X" }).accepted())
    }

    // ---- enroll ----
    @Test fun enrollRequestWireFormat() {
        assertEquals("""{"activation":"cbx1.a.b","sshPublicKey":"ssh-ed25519 AAAA tv","deviceCode":"ABCD-EFGH-JKLM-NPQR"}""", EnrollRequest("cbx1.a.b", "ssh-ed25519 AAAA tv", "ABCD-EFGH-JKLM-NPQR").toJson())
    }

    private val fp = "SHA256:" + "A".repeat(43)
    @Test fun enrollReplyParsing() {
        val ok = TunnelEnroll.outcome(200, """{"host":"bridge.sti-cm.com","sshPort":2200,"user":"cbtunnel","port":22100,"hostKeyFingerprint":"$fp"}""", "id", "k") as EnrollOutcome.Ok
        assertEquals(22100, ok.enrollment.port); assertEquals(fp, ok.enrollment.hostKeyFingerprint); assertEquals("cbtunnel", ok.enrollment.user)
        val noFp = TunnelEnroll.outcome(200, """{"host":"bridge.sti-cm.com","sshPort":2200,"user":"cbtunnel","port":22101}""", "id", "k") as EnrollOutcome.Ok
        assertNull(noFp.enrollment.hostKeyFingerprint)
        for (bad in listOf("""{"host":"a b","sshPort":2200,"user":"cbtunnel","port":22100}""", """{"host":"h.example","sshPort":0,"user":"cbtunnel","port":22100}""",
            """{"host":"h.example","sshPort":2200,"user":"cb tunnel","port":22100}""", """{"host":"h.example","sshPort":2200,"user":"cbtunnel","port":80}""",
            """{"host":"h.example","sshPort":2200,"user":"cbtunnel","port":22100,"hostKeyFingerprint":"MD5:zz"}""", "not json", "{}"))
            assertTrue(TunnelEnroll.outcome(200, bad, "id", "k") is EnrollOutcome.Retry, bad)
    }

    @Test fun enrollErrorsMapToTheRightBehaviour() {
        assertTrue(TunnelEnroll.outcome(403, """{"message":"Tunnel révoqué"}""", "i", "k").let { it is EnrollOutcome.Revoked && it.message == "Tunnel révoqué" })
        assertEquals(TunnelEnroll.RATE_LIMITED_MIN_MS, (TunnelEnroll.outcome(429, "", "i", "k") as EnrollOutcome.Retry).minDelayMs)
        assertEquals(0L, (TunnelEnroll.outcome(503, "", "i", "k") as EnrollOutcome.Retry).minDelayMs)
        assertEquals(TunnelEnroll.REFUSED_MIN_MS, (TunnelEnroll.outcome(400, "", "i", "k") as EnrollOutcome.Retry).minDelayMs)
        assertEquals(TunnelEnroll.REFUSED_MIN_MS, (TunnelEnroll.outcome(404, "", "i", "k") as EnrollOutcome.Retry).minDelayMs)
    }

    @Test fun enrollmentStoreRoundTrip() {
        val st = EnrollmentStore(File(dir, "e.json"))
        assertEquals(null to 0L, st.load())
        val e = Enrollment("h.example", 2200, "cbtunnel", 22100, fp, "aid", "kid")
        st.save(e, 42L); assertEquals(e to 42L, st.load())
        st.save(null, 0L); assertEquals(null to 0L, st.load())
    }

    private fun act(kind: ActivationKind, issued: Long, seq: Long, usageEnd: Long? = null) = Activation(kind, Subject.TV, "kid", seq, "n$seq", issued, issued, issued + 1000, if (kind == ActivationKind.TRIAL) "trial" else "lic",
        "0a0a", 2, emptyMap(), listOfNotNull(usageEnd?.let { Right.Usage(issued, it) }), "sig$seq")

    @Test fun picksTheNewestCountingActivation() {
        val now = 1_800_000_000_000L; val day = 86_400_000L
        val trialOld = act(ActivationKind.TRIAL, now - 5 * day, 1, usageEnd = now + 10 * day)
        val prod = act(ActivationKind.PRODUCTION, now - 2 * day, 2)
        val prodEnded = act(ActivationKind.PRODUCTION, now - 1 * day, 3, usageEnd = now - 1000)
        assertEquals(prod, TunnelEnroll.pickActivation(listOf(trialOld, prod, prodEnded), now))
        assertEquals(trialOld, TunnelEnroll.pickActivation(listOf(trialOld, prodEnded), now))
        assertNull(TunnelEnroll.pickActivation(listOf(prodEnded), now)); assertNull(TunnelEnroll.pickActivation(emptyList(), now))
        val implicitSpent = act(ActivationKind.TRIAL, now - 400 * day, 4)
        assertNull(TunnelEnroll.pickActivation(listOf(implicitSpent), now), "an implicit trial ceiling that passed does not count")
    }

    // ---- backoff / pin / connectivity ----
    @Test fun backoffBoundsAndReset() {
        val b = TunnelBackoff(Random(7)); val seq = (1..30).map { b.next() }
        assertTrue(seq[0] in 2_500..6_300); assertTrue(seq.all { it <= 600_000 && it >= 2_500 }); assertTrue(seq.takeLast(10).all { it >= 450_000 })
        b.reset(); assertTrue(b.next() < 7_000)
        assertEquals(700_000L.coerceAtLeast(600_000), TunnelBackoff().next(700_000), "a server-imposed minimum wins")
    }

    @Test fun hostKeyPinning() {
        val other = "SHA256:" + "B".repeat(43)
        assertEquals(HostKeyPin.Decision.ACCEPT, HostKeyPin.decide(fp, fp, fp).decision)
        assertEquals(HostKeyPin.Decision.ACCEPT_AND_PIN, HostKeyPin.decide(fp, null, fp).decision)
        assertEquals(HostKeyPin.Decision.REJECT, HostKeyPin.decide(fp, fp, other).decision, "announced key wins")
        assertEquals(HostKeyPin.Decision.REJECT, HostKeyPin.decide(fp, other, other).decision, "a pinned key does not override the announced one")
        assertEquals(HostKeyPin.Decision.ACCEPT_AND_PIN, HostKeyPin.decide(null, null, fp).decision, "trust on first use")
        assertEquals(HostKeyPin.Decision.ACCEPT, HostKeyPin.decide(null, fp, fp).decision)
        assertEquals(HostKeyPin.Decision.REJECT, HostKeyPin.decide(null, fp, other).decision, "enforced afterwards")
        val pins = HostKeyPins(File(dir, "pins")); assertNull(pins.get("h", 2200)); pins.pin("h", 2200, fp); pins.pin("h2", 22, other)
        assertEquals(fp, HostKeyPins(File(dir, "pins")).get("h", 2200)); assertNull(pins.get("h", 22))
    }

    @Test fun connectivityGate() {
        assertEquals(TunnelPath.DIRECT, TunnelConnectivity.choose(true, true, true))
        assertEquals(TunnelPath.DIRECT, TunnelConnectivity.choose(true, false, false))
        assertEquals(TunnelPath.GATEWAY, TunnelConnectivity.choose(false, true, true))
        assertEquals(TunnelPath.OFFLINE, TunnelConnectivity.choose(false, true, false), "phone attached but no Internet behind it")
        assertEquals(TunnelPath.OFFLINE, TunnelConnectivity.choose(false, false, false))
    }

    // ---- journal ----
    @Test fun journalRotatesAndKeepsTheTail() {
        var t = 1_800_000_000_000L
        val j = TunnelJournal(File(dir, "j.log"), maxBytes = 600) { t }
        assertEquals(emptyList(), j.lines())
        repeat(30) { t += 1000; j.add("événement $it") }
        val l = j.lines(500)
        assertTrue(l.last().endsWith("événement 29")); assertTrue(l.size in 5..30); assertTrue(File(dir, "j.log.1").isFile)
        assertTrue(l.first().contains("UTC")); assertEquals(3, j.lines(3).size)
        assertTrue(File(dir, "j.log").length() <= 600 + 100)
    }

    // ---- experts ----
    private val signer = Ed25519Signer(ByteArray(32) { (it + 7).toByte() })
    private val trusted = listOf(signer.trusted(KeyScope.ALL))
    private val k1 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8g alice@laptop"
    private val k2 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj9A"

    @Test fun expertsSyncKeepsThePreviousValidListOnAnyRefusal() {
        var now = 1_800_000_000_000L
        val store = ExpertsStore(File(dir, "experts"), { trusted }); val sync = ExpertsSync(store, { trusted }, { now })
        assertTrue(store.keys(now).isEmpty())
        val v1 = ExpertsList.sign(signer, signer.keyId, listOf(ExpertsList.Expert("alice", k1), ExpertsList.Expert("bob", k2, now + 1000)), now).toJson()
        assertTrue(sync.apply(200 to v1).ok)
        assertEquals(listOf("alice", "bob"), store.keys(now).map { it.id })
        assertEquals(listOf("alice"), store.keys(now + 1000).map { it.id }, "an expert whose date passed is dropped at once")
        assertTrue(File(dir, "experts/authorized_keys").readText().lines().first().startsWith("restrict,pty ssh-ed25519 "))
        // older list (replay), bad signature, unsigned, 404, network failure: all refused, v1 stays
        val older = ExpertsList.sign(signer, signer.keyId, listOf(ExpertsList.Expert("mallory", k1)), now - 10).toJson()
        for (bad in listOf(200 to older, 200 to v1.replace("alice", "evil"), 200 to """{"generatedAt":1,"keyId":"x","experts":[],"signature":"UNSIGNED"}""", 404 to "", 200 to "garbage", null)) {
            val r = sync.apply(bad as Pair<Int, String>?); assertFalse(r.ok, r.message); assertTrue("conservée" in r.message)
            assertEquals(listOf("alice", "bob"), store.keys(now).map { it.id })
        }
        val v2 = ExpertsList.sign(signer, signer.keyId, listOf(ExpertsList.Expert("bob", k2)), now + 5).toJson()
        assertTrue(sync.apply(200 to v2).ok); assertEquals(listOf("bob"), store.keys(now).map { it.id })
        // a list signed by a key without REGISTRY is refused
        val noReg = listOf(signer.trusted(KeyScope.ALL - KeyScope.REGISTRY))
        assertFalse(ExpertsSync(ExpertsStore(File(dir, "e2"), { noReg }), { noReg }, { now }).apply(200 to v2).ok)
    }

    @Test fun tamperedStoredListGivesNoKeys() {
        val now = 1_800_000_000_000L
        val store = ExpertsStore(File(dir, "ex"), { trusted })
        ExpertsSync(store, { trusted }, { now }).apply(200 to ExpertsList.sign(signer, signer.keyId, listOf(ExpertsList.Expert("alice", k1)), now).toJson())
        assertEquals(1, store.keys(now).size)
        File(dir, "ex/experts.json").let { it.writeText(it.readText().replace("alice", "mallory")) }
        File(dir, "ex/experts.json.bak").delete()
        assertTrue(store.keys(now).isEmpty())
    }

    @Test fun statusTexts() {
        assertEquals("Assistance à distance : connectée", TunnelText.line(TunnelState.UP))
        assertEquals("Assistance à distance : hors ligne", TunnelText.line(TunnelState.BACKOFF))
        assertEquals("Assistance à distance : hors ligne", TunnelText.line(TunnelState.IDLE))
        assertEquals("Assistance à distance : en attente d'acceptation des conditions", TunnelText.line(TunnelState.NEEDS_TERMS))
    }
}
