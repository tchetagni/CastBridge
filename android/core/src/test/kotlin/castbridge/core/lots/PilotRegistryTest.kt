package castbridge.core.lots

import java.time.LocalDate
import kotlin.test.*

class PilotRegistryTest {
    private fun e(date: String = "2026-10-14", lic: String = "LIC-1", bundle: String = "classe-cm2", period: Long = 1000L, unit: String = "heures", qty: Int = 12, days: Int = 30, type: String = "nouvelle") =
        PilotRegistry.Entry(date, lic, "AB…89", bundle, period, unit, qty, days, type)

    @Test fun formatAndParseRoundTrip() {
        val r = PilotRegistry().append(e()).append(e(date = "2026-10-15", bundle = "classe-cp", period = 2000L, unit = "defaut", qty = 7, days = 7))
        val csv = r.format()
        assertEquals("date,licence,code,bouquet,period,unite,quantite,jours,type,install", csv.lines().first())
        assertEquals("2026-10-14,LIC-1,AB…89,classe-cm2,1000,heures,12,30,nouvelle,-", csv.lines()[1], "no installation fingerprint: a dash")
        val withFp = PilotRegistry().append(PilotRegistry.Entry("2026-10-14", "L", "AB…89", "b", 1, "heures", 1, 1, "nouvelle", PilotRegistry.fingerprint("pub-A")))
        assertEquals(withFp.entries, PilotRegistry.parse(withFp.format()).entries)
        assertEquals(16, withFp.entries.single().installFp.length); assertTrue(withFp.entries.single().installFp.matches(Regex("[0-9a-f]{16}")), "a fingerprint, never the key")
        assertNotEquals(PilotRegistry.fingerprint("pub-A"), PilotRegistry.fingerprint("pub-B")); assertEquals(PilotRegistry.fingerprint("pub-A"), PilotRegistry.fingerprint("pub-A"))
        assertFalse(withFp.format().contains("pub-A"))
        assertEquals(1, PilotRegistry.parse("date,licence,code,bouquet,period,unite,quantite,jours,type\n2026-10-14,L,AB…89,b,1,heures,1,1,nouvelle\n").entries.size, "a register written before the install column still reads")
        assertFailsWith<IllegalArgumentException> { PilotRegistry.Entry("2026-10-14", "L", "AB…89", "b", 1, "heures", 1, 1, "nouvelle", "not-a-fingerprint") }
        assertEquals(r.entries, PilotRegistry.parse(csv).entries)
        assertEquals(r.entries, PilotRegistry.parse(csv.replace("\n", "\r\n") + "\r\n\r\n").entries, "CRLF and blank lines tolerated")
        assertEquals(emptyList(), PilotRegistry.parse("").entries)
        assertEquals(emptyList(), PilotRegistry.parse(PilotRegistry().format()).entries)
    }

    @Test fun parseRefusesMalformedLinesInFrench() {
        val head = "date,licence,code,bouquet,period,unite,quantite,jours,type\n"
        for (l in listOf("2026-10-14,L,c,b,1,heures,12,30", "2026-13-14,L,c,b,1,heures,12,30,nouvelle", "2026-10-14,L,c,b,x,heures,12,30,nouvelle", "2026-10-14,L,c,b,1,minutes,12,30,nouvelle",
            "2026-10-14,L,c,b,1,heures,0,30,nouvelle", "2026-10-14,L,c,b,1,heures,12,30,autre", "2026-10-14,L,c,b,1,heures,12,30,nouvelle,extra", "2026-10-14,L,c,b,1,heures,12,0,nouvelle")) {
            val m = assertFailsWith<IllegalArgumentException>(l) { PilotRegistry.parse(head + l) }.message!!
            assertTrue(m.contains("ligne 2"), m)
        }
    }

    @Test fun fieldsCannotBreakTheCsv() {
        assertFailsWith<IllegalArgumentException> { PilotRegistry().append(e(lic = "A,B")) }
        assertFailsWith<IllegalArgumentException> { PilotRegistry().append(e(bundle = "x\ny")) }
        assertFailsWith<IllegalArgumentException> { PilotRegistry().append(e(lic = "")) }
        assertFailsWith<IllegalArgumentException> { PilotRegistry().append(e(unit = "heures,")) }
        assertFailsWith<IllegalArgumentException> { e(date = "14/10/2026") }
    }

    @Test fun sameLicenseBundleChoiceAndDayIsADuplicate() {
        val r = PilotRegistry().append(e())
        assertTrue(r.isDuplicate(e(period = 5000L)))
        assertFailsWith<IllegalArgumentException> { r.append(e(period = 5000L)) }.also { assertTrue(it.message!!.contains("déjà")) }
        assertEquals(2, r.append(e(period = 5000L), allowDuplicate = true).entries.size, "--encore")
        assertFalse(r.isDuplicate(e(date = "2026-10-15")), "another day")
        assertFalse(r.isDuplicate(e(qty = 6))); assertFalse(r.isDuplicate(e(unit = "jours"))); assertFalse(r.isDuplicate(e(bundle = "classe-cp"))); assertFalse(r.isDuplicate(e(lic = "LIC-2")))
    }

    @Test fun extensionAndReissueNeedThePeriodInProgress() {
        val r = PilotRegistry().append(e(period = 1000L))
        assertEquals(2, r.append(e(type = "prolongation", period = 1000L, qty = 6, date = "2026-10-16")).entries.size)
        assertEquals(2, r.append(e(type = "reemission", period = 1000L, qty = 8, date = "2026-10-16")).entries.size)
        assertFailsWith<IllegalArgumentException> { r.append(e(type = "prolongation", period = 999L, qty = 6, date = "2026-10-16")) }.also { assertTrue(it.message!!.contains("period"), it.message) }
        assertFailsWith<IllegalArgumentException> { PilotRegistry().append(e(type = "prolongation")) }
        assertFailsWith<IllegalArgumentException> { r.append(e(type = "prolongation", bundle = "classe-cp", period = 1000L, qty = 6, date = "2026-10-16")) }
        assertFailsWith<IllegalArgumentException> { r.append(e(type = "prolongation", lic = "LIC-2", period = 1000L, qty = 6, date = "2026-10-16")) }
        val relet = r.append(e(period = 3000L, date = "2026-10-20", qty = 3))
        assertEquals(2, relet.entries.size, "a new rental after the end has a new period")
        assertFailsWith<IllegalArgumentException>("the period of an old rental is not the one in progress any more") { relet.append(e(type = "prolongation", period = 1000L, qty = 6, date = "2026-10-21")) }
        assertFailsWith<IllegalArgumentException>("a new rental cannot reuse a period") { r.append(e(date = "2026-10-18", qty = 3)) }
    }

    @Test fun findAndHoursOverSevenDays() {
        val r = PilotRegistry().append(e(date = "2026-10-08", qty = 96, period = 1L)).append(e(date = "2026-10-10", qty = 12, period = 2L, bundle = "classe-cp"))
            .append(e(type = "prolongation", date = "2026-10-12", qty = 6, period = 2L, bundle = "classe-cp")).append(e(type = "reemission", date = "2026-10-13", qty = 8, period = 2L, bundle = "classe-cp"))
            .append(e(date = "2026-10-13", unit = "jours", qty = 7, days = 7, period = 3L, bundle = "classe-ce1")).append(e(lic = "LIC-2", date = "2026-10-13", qty = 50, period = 4L))
        assertEquals(5, r.find("LIC-1").size); assertEquals(1, r.find("LIC-2").size); assertEquals(emptyList(), r.find("LIC-3"))
        fun at(d: String, h: Int, mi: Int = 0) = LocalDate.parse(d).atTime(h, mi).toInstant(java.time.ZoneOffset.ofHours(1)).toEpochMilli()      // Africa/Douala = UTC+1, explicit
        // sliding 168 h: 96 + 12 + 6 + 8 (a reissue counts: it gives the hours again); the days entry is not hours
        assertEquals(122, r.hoursIssuedLast168h("LIC-1", at("2026-10-14", 12)))
        assertEquals(26, r.hoursIssuedLast168h("LIC-1", at("2026-10-16", 12)), "the 8th (a date alone counts until the end of its day) has fallen out")
        assertEquals(0, r.hoursIssuedLast168h("LIC-1", at("2026-10-30", 12)))
        assertEquals(50, r.hoursIssuedLast168h("LIC-2", at("2026-10-14", 12)))
        assertEquals(122, r.hoursIssuedLast168h("LIC-1", at("2026-10-14", 0)), "a date alone is conservative: counted from the day before even at midnight")
        val t = r.append(e(lic = "LIC-3", date = "2026-10-11T09:30", qty = 4, period = 9L))
        assertEquals(4, t.hoursIssuedLast168h("LIC-3", at("2026-10-18", 9, 29)), "inside by one minute")
        assertEquals(0, t.hoursIssuedLast168h("LIC-3", at("2026-10-18", 9, 30)), "exactly 168 h later: out (the window is open at its start)")
        assertEquals(4, t.hoursIssuedLast168h("LIC-3", at("2026-10-11", 9, 0)), "an entry a little in the future (another issuer's clock ahead) counts, up to now + 168 h")
        assertEquals(4, t.hoursIssuedLast168h("LIC-3", at("2026-10-04", 9, 30)), "exactly 168 h ahead: still counted")
        assertEquals(0, t.hoursIssuedLast168h("LIC-3", at("2026-10-04", 9, 29)), "a minute further: not counted")
        assertEquals(122, r.hoursIssuedLast168h("LIC-1", at("2026-10-14", 12)), "date-only entries unchanged")
        assertEquals(122, r.hoursIssuedLast168h("LIC-1", at("2026-10-10", 12)), "date-only entries up to 168 h ahead count as well")
        assertEquals(t.entries, PilotRegistry.parse(t.format()).entries)
    }

    @Test fun everyQuantityIsAtLeastOne() {
        assertFailsWith<IllegalArgumentException> { e(qty = 0) }
        assertFailsWith<IllegalArgumentException> { e(qty = 0, type = "prolongation") }
        assertFailsWith<IllegalArgumentException>("a reissue of less than an hour is recorded rounded UP: 1 at least, so it counts in the quota") { e(qty = 0, type = "reemission") }
    }

    @Test fun theTypeIsPartOfTheDuplicate() {
        val r = PilotRegistry().append(e(qty = 3))
        assertFalse(r.isDuplicate(e(qty = 3, type = "prolongation")), "a new rental of 3 h and an extension of 3 h on the same day are two orders")
        assertEquals(2, r.append(e(qty = 3, type = "prolongation", period = 1000L)).entries.size)
        assertTrue(r.isDuplicate(e(qty = 3, period = 55L)))
    }

    @Test fun summaryFollowsTheEnginesRule() {
        val t0 = 1_800_000_000_000L; val D = 24L * 3600 * 1000
        val fp = PilotRegistry.fingerprint("pub-A")
        fun ent(date: String, bundle: String, period: Long, unit: String, q: Int, days: Int, type: String) = PilotRegistry.Entry(date, "LIC", "AB…89", bundle, period, unit, q, days, type, fp)
        val r = PilotRegistry().append(ent("2026-10-12", "b1", t0, "heures", 12, 30, "nouvelle")).append(ent("2026-10-14", "b1", t0, "heures", 6, 1, "prolongation"))
            .append(ent("2026-10-15", "b1", t0, "heures", 2, 1, "reemission"))
            .append(ent("2026-10-12", "b2", t0, "jours", 7, 7, "nouvelle")).append(ent("2026-10-16", "b2", t0, "jours", 14, 14, "prolongation"))
            .append(ent("2026-10-12", "b3", t0, "defaut", 30, 30, "nouvelle"))
        val h = r.summary("LIC", "b1", t0 + 3 * D)!!
        assertEquals(ContractSummary("loc-b1", t0, RentalUnit.HOURS, 20 * 60, t0 + 32 * D, reissues = 1, endedAt = null, installPub = fp), h)
        val d = r.summary("LIC", "b2", t0)!!
        assertEquals(RentalUnit.DAYS, d.unit); assertEquals(0, d.maxUsageMinutes); assertEquals(t0 + 21 * D, d.endsAt)
        assertEquals(RentalUnit.DAYS, r.summary("LIC", "b3", t0)!!.unit); assertEquals(t0 + 30 * D, r.summary("LIC", "b3", t0)!!.endsAt)
        assertNull(r.summary("LIC", "b4", t0)); assertNull(r.summary("OTHER", "b1", t0))
        val big = PilotRegistry().append(ent("2026-10-12", "b1", t0, "heures", 90, 30, "nouvelle")).append(ent("2026-10-13", "b1", t0, "heures", 30, 1, "prolongation"))
        assertEquals(96 * 60, big.summary("LIC", "b1", t0)!!.maxUsageMinutes, "clamped at 96 h like the engine")
        val second = r.append(ent("2026-10-20", "b1", t0 + 8 * D, "heures", 3, 20, "nouvelle"))
        assertEquals(t0 + 8 * D, second.summary("LIC", "b1", t0 + 9 * D)!!.period, "the rental in progress is the latest period")
    }

    @Test fun aReadFromTheBackupIsRefusedFailClosed() {
        val dir = java.nio.file.Files.createTempDirectory("pilot-registry-bak").toFile()
        try {
            val f = java.io.File(dir, "r.csv")
            PilotRegistry.update(f) { it.append(e(lic = "A1")) }
            PilotRegistry.update(f) { it.append(e(lic = "A2", period = 2000L)) }      // main = 2 entries, .bak = 1 entry (the stale copy)
            f.writeText("corrompu\n")
            assertTrue(castbridge.core.owner.SafeFile.read(f) { runCatching { PilotRegistry.parse(it) }.isSuccess }!!.fromBackup, "it is the backup that answers")
            val m = assertFailsWith<IllegalStateException> { PilotRegistry.load(f) }.message!!
            assertTrue(m.contains("à vérifier"), m)
            val before = f.readText()
            assertFailsWith<IllegalStateException> { PilotRegistry.update(f) { it.append(e(lic = "A3", period = 3000L)) } }
            assertEquals(before, f.readText(), "nothing is written from a stale copy")
            assertEquals(1, java.io.File(dir, "r.csv.bak").readText().lines().count { it.startsWith("2026") }, "the .bak keeps its content (1 entry)")
        } finally { dir.deleteRecursively() }
    }

    @Test fun theTvCodeMustBeMasked() {
        assertEquals("AB…89", PilotRegistry.mask("ABCDEFGH89"))
        assertEquals("AB…89", PilotRegistry.mask("AB…89"), "idempotent")
        assertFailsWith<IllegalArgumentException> { PilotRegistry.Entry("2026-10-14", "L", "ABCDEFGH89", "b", 1, "heures", 1, 1, "nouvelle") }
        assertFailsWith<IllegalArgumentException> { PilotRegistry.Entry("2026-10-14", "L", "ABCD…WXYZ", "b", 1, "heures", 1, 1, "nouvelle") }
        assertFailsWith<IllegalArgumentException> { PilotRegistry.mask("AB") }
        assertFailsWith<IllegalArgumentException> { PilotRegistry.parse("2026-10-14,L,ABCDEFGH89,b,1,heures,1,1,nouvelle") }
    }

    @Test fun updatesAreAtomicAndTwoIssuersCannotLoseEachOther() {
        val dir = java.nio.file.Files.createTempDirectory("pilot-registry").toFile()
        try {
            val f = java.io.File(dir, "pilot-rentals.csv")
            assertEquals(emptyList(), PilotRegistry.load(f).entries, "no file yet")
            PilotRegistry.update(f) { it.append(e()) }
            assertEquals(1, PilotRegistry.load(f).entries.size)
            assertTrue(f.readText().startsWith(PilotRegistry.HEADER))
            assertFailsWith<IllegalArgumentException>("a refusal inside leaves the file untouched") { PilotRegistry.update(f) { it.append(e(period = 7L)) } }
            assertEquals(1, PilotRegistry.load(f).entries.size)
            val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
            try {
                (1..24).map { i -> pool.submit { PilotRegistry.update(f) { r -> r.append(e(lic = "CONC-$i", period = 100L + i)) } } }.forEach { it.get() }
            } finally { pool.shutdown() }
            assertEquals(25, PilotRegistry.load(f).entries.size, "24 simultaneous issuings plus the first: none lost")
            assertTrue(java.io.File(dir, "pilot-rentals.csv.bak").isFile, "the last good copy is kept (SafeFile)")
        } finally { dir.deleteRecursively() }
    }
}
