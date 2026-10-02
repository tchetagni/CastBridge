package castbridge.core.owner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuperSessionTest {
    private val h = 3_600_000L
    private val day = 24 * h
    private val t0 = 1_800_000_000_000L
    private var mono = 0L
    private fun clock() = TvClock(lastSeen = t0, floor = 0L, mono = { mono })
    private fun session(audit: AuditChain? = null, c: TvClock = clock()) = SuperSession(c, audit, random = { ByteArray(16) { 0xab.toByte() } })

    @Test fun twelveHoursActiveAtElevenInactiveAtTwelve() {
        mono = 0L; val s = session(); s.open(SuperDuration.H12, t0)
        mono = 11 * h; assertTrue(s.active(t0 + 11 * h))
        assertEquals(h, s.remainingMs(t0 + 11 * h))
        mono = 12 * h; assertFalse(s.active(t0 + 12 * h))
        assertEquals(0L, s.remainingMs(t0 + 12 * h))
    }

    @Test fun wallClockRollbackDoesNotExtend() {
        mono = 0L; val c = clock(); val s = session(c = c); s.open(SuperDuration.H12, t0)
        mono = 11 * h; c.observe(t0 + 11 * h)            // l'horloge est observée en marche normale (le téléphone l'appelle régulièrement)
        mono = 12 * h
        assertFalse(s.active(t0 + 12 * h - 10 * h))   // l'horloge murale recule de 10 h, 12 h monotones se sont écoulées
    }

    @Test fun forwardJumpEndsSession() {
        mono = 0L; val s = session(); s.open(SuperDuration.H24, t0)
        mono = h
        assertFalse(s.active(t0 + 60 * day))
        assertEquals(0L, s.remainingMs(t0 + 60 * day))
    }

    @Test fun fiveFailuresCloseFourDoNot() {
        mono = 0L; val s = session(); s.open(SuperDuration.H12, t0)
        repeat(4) { assertFalse(s.noteFailure(t0)) }
        assertTrue(s.active(t0))
        assertTrue(s.noteFailure(t0))
        assertFalse(s.active(t0)); assertNull(s.state)
    }

    @Test fun successResetsFailureCounter() {
        mono = 0L; val s = session(); s.open(SuperDuration.H12, t0)
        repeat(4) { s.noteFailure(t0) }; s.noteSuccess()
        repeat(4) { s.noteFailure(t0) }
        assertTrue(s.active(t0))
    }

    @Test fun closeMakesInactive() {
        mono = 0L; val s = session(); s.open(SuperDuration.H4, t0)
        s.close("bouton", t0)
        assertFalse(s.active(t0)); assertEquals(0L, s.remainingMs(t0)); assertEquals("", s.encode())
    }

    @Test fun neverOpenedIsInactive() { mono = 0L; assertFalse(session().active(t0)) }

    @Test fun encodeDecodeRoundTrip() {
        mono = 0L; val s = session(); val st = s.open(SuperDuration.H12, t0)
        val txt = s.encode()
        assertEquals("v2 ${st.openedAt} ${st.untilMs} ${st.nonce} ${st.uptimeAtOpen} 0", txt)
        val d = SuperSession.decode(txt, clock(), t0)
        assertNotNull(d); assertEquals(st, d.state); assertTrue(d.active(t0 + h))
    }

    @Test fun alteredTextDecodesToNull() {
        mono = 0L; val s = session(); s.open(SuperDuration.H12, t0)
        val p = s.encode().split(' ')
        val u = p[4]
        listOf("", "v1 ${p[1]} ${p[2]} ${p[3]}", "v3 ${p[1]} ${p[2]} ${p[3]} $u 0", "v2 ${p[1]} ${p[2]} ${p[3]} $u", "v2 x ${p[2]} ${p[3]} $u 0", "v2 ${p[1]} y ${p[3]} $u 0",
            "v2 ${p[1]} ${p[2]} zz $u 0", "v2 ${p[1]} ${p[2]} ${p[3]} $u 0 extra", "v2 ${p[2]} ${p[1]} ${p[3]} $u 0", "v2 -5 ${p[2]} ${p[3]} $u 0",
            "v2 ${p[1]} ${p[1].toLong() + 25 * h} ${p[3]} $u 0", "v2 ${p[1]} ${p[2]} ${p[3]} q 0", "v2 ${p[1]} ${p[2]} ${p[3]} -1 0",
            "v2 ${p[1]} ${p[2]} ${p[3]} $u z", "v2 ${p[1]} ${p[2]} ${p[3]} $u -1", "v2 ${p[1]} ${p[2]} ${p[3]} $u 5").forEach { assertNull(SuperSession.decode(it, clock(), t0), it) }
    }

    @Test fun h24IsTheCeiling() {
        mono = 0L
        assertEquals(24 * h, SuperSession.MAX.ms)
        assertTrue(SuperDuration.values().all { it.ms <= SuperSession.MAX.ms })
        val s = session(); val st = s.open(SuperDuration.H24, t0)
        assertEquals(24 * h, st.untilMs - st.openedAt)
        assertNull(SuperSession.decode("v2 ${st.openedAt} ${st.openedAt + 24 * h + 1} ${st.nonce} 0 0", clock(), t0))   // un blob > 24 h est refusé
    }

    @Test fun defaultDurationIsTwelveHours() {
        mono = 0L; val s = session(); val st = s.open(nowWall = t0)
        assertEquals(12 * h, st.untilMs - st.openedAt)
    }

    @Test fun nonceIs32HexAndFreshPerOpen() {
        mono = 0L
        val na = SuperSession(clock()).open(SuperDuration.H1, t0).nonce; val nb = SuperSession(clock()).open(SuperDuration.H1, t0).nonce
        assertTrue(Regex("[0-9a-f]{32}").matches(na)); assertNotEquals(na, nb)
    }

    @Test fun auditChainRecordsOpenAndClose() {
        mono = 0L; val audit = AuditChain(); val s = session(audit)
        s.open(SuperDuration.H4, t0); s.close("bouton", t0 + h)
        assertEquals(listOf("super.open", "super.close"), audit.entries.map { it.action })
        assertEquals("H4", audit.entries[0].target)
        assertTrue(audit.verify())
    }

    @Test fun encodeHoldsNoSecret() {
        mono = 0L; val s = session(); s.open(SuperDuration.H12, t0)
        assertTrue(Regex("v2 \\d+ \\d+ [0-9a-f]{32} \\d+ \\d").matches(s.encode()))
    }

    // --- correctifs de l'audit ---

    @Test fun rollbackWithoutExternalObserveDoesNotExtend() {
        mono = 0L; val s = session(); s.open(SuperDuration.H12, t0)
        mono = 12 * h
        assertFalse(s.active(t0))   // recul total de l'horloge murale, personne n'a appelé observe
        assertNull(s.state)
    }

    @Test fun brandNewClockWorks() {
        mono = 0L; val c = TvClock(mono = { mono }); val s = session(c = c)
        s.open(SuperDuration.H1, t0)
        mono = 30 * 60_000L; assertTrue(s.active(t0 + 30 * 60_000L))
        mono = h; assertFalse(s.active(t0 - 5 * day))   // recul après coup : le temps monotone a épuisé l'heure
    }

    @Test fun clockFreezeAttackIsDefeated() {
        mono = 0L; val s = session(); s.open(SuperDuration.H1, t0)
        // l'attaquant remet l'horloge murale à t0 (reculée de moins de 60 s) à chaque minute : le temps TV avance à peine
        for (i in 1..59) { mono = i * 60_000L; assertTrue(s.active(t0 + i * 1_000L), "minute $i") }
        mono = h; assertFalse(s.active(t0 + 60 * 1_000L))   // 60 min de fonctionnement : fermée malgré l'horloge gelée
        assertNull(s.state)
    }

    @Test fun nonPersistedClockClosesDecodedSession() {
        mono = 0L; val c = clock(); c.uptimeMs = 5 * h
        val s = session(c = c); s.open(SuperDuration.H12, t0)
        val blob = s.encode()
        val audit = AuditChain()
        val d = SuperSession.decode(blob, clock(), t0, audit)   // horloge neuve : uptime 0 < uptimeAtOpen
        assertNotNull(d); assertNull(d.state); assertFalse(d.active(t0))
        assertEquals(listOf("horloge"), audit.entries.map { it.target })
    }

    @Test fun failureCounterSurvivesRestart() {
        mono = 0L; val s = session(); s.open(SuperDuration.H12, t0)
        repeat(3) { s.noteFailure(t0) }
        val d = SuperSession.decode(s.encode(), clock(), t0)
        assertNotNull(d); assertEquals(3, d.failures)
        assertFalse(d.noteFailure(t0)); assertTrue(d.noteFailure(t0))   // 5e échec cumulé : fermée
        assertEquals("", d.encode())
    }

    @Test fun activeStateClosesOnceOnExpiration() {
        mono = 0L; val audit = AuditChain(); val s = session(audit); s.open(SuperDuration.H1, t0)
        mono = 30 * 60_000L; assertNotNull(s.activeState(t0 + 30 * 60_000L))
        mono = h
        assertNull(s.activeState(t0 + h)); assertNull(s.activeState(t0 + h)); assertFalse(s.active(t0 + h))
        assertNull(s.state)
        assertEquals(listOf("super.open", "super.close"), audit.entries.map { it.action })
        assertEquals("expiration", audit.entries[1].target)
        assertTrue(audit.verify())
    }

    @Test fun aheadJumpClosesWithHorlogeReason() {
        mono = 0L; val audit = AuditChain(); val s = session(audit); s.open(SuperDuration.H24, t0)
        mono = h; assertFalse(s.active(t0 + 60 * day))
        assertNull(s.state); assertEquals("horloge", audit.entries.last().target)
    }

    @Test fun decodeRejectsOpenedInTheFuture() {
        mono = 0L; val s = session(); val st = s.open(SuperDuration.H12, t0)
        val far = "v2 ${t0 + 10 * day} ${t0 + 10 * day + 12 * h} ${st.nonce} 0 0"
        assertNull(SuperSession.decode(far, clock(), t0))
        val near = "v2 ${t0 + 30_000L} ${t0 + 30_000L + 12 * h} ${st.nonce} 0 0"
        assertNotNull(SuperSession.decode(near, clock(), t0))
        val over = "v2 ${t0 + 61_000L} ${t0 + 61_000L + 12 * h} ${st.nonce} 0 0"
        assertNull(SuperSession.decode(over, clock(), t0))
    }

    @Test fun decodeRaisesTheFloor() {
        mono = 0L; val c = clock(); val s = session(); val st = s.open(SuperDuration.H12, t0)
        assertNotNull(SuperSession.decode(s.encode(), c, t0))
        assertTrue(c.floor >= st.openedAt)
    }
}
