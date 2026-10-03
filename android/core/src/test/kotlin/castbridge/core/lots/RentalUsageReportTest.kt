package castbridge.core.lots

import java.io.File
import java.time.ZoneOffset
import kotlin.test.*

/** W16 (w16-03): the usage statement on the phone: parsing, monotone merge, one file per installation, words for the screen, no personal data. */
class RentalUsageReportTest {
    private val inst = "0123456789abcdef"
    private val other = "fedcba9876543210"
    private fun line(contract: String = "loc-cm2@1", unit: String = "hours", used: Long = 90, max: Long = 720, state: String = "ACTIVE", reason: String = "-", endsAt: Long = 1_794_700_800_000L, at: Long = 10) =
        "contract=$contract|unit=$unit|used=$used|max=$max|state=$state|reason=$reason|endsAt=$endsAt|at=$at"
    private fun text(vararg lines: String, install: String = inst) = (listOf("castbridge-rental-usage-v1", "install=$install") + lines).joinToString("\n", postfix = "\n")

    @Test fun parsesEveryLineOfAStatement() {
        val r = RentalUsageReport.parse(text(line(), line("loc-cm1@1", "days", 5, 0, endsAt = 7)))!!
        assertEquals(inst, r.install); assertEquals(0, r.ignored)
        assertEquals(listOf("loc-cm1@1", "loc-cm2@1"), r.lines.map { it.contract }, "sorted by contract")
        val h = r.lines.last(); assertEquals("hours", h.unit); assertEquals(90L, h.used); assertEquals(720L, h.max); assertEquals("ACTIVE", h.state); assertNull(h.reason); assertEquals(10L, h.at)
    }

    @Test fun aMalformedLineIsIgnoredAndCountedNeverFatal() {
        val bad = listOf(line(used = -1), "contract=x|unit=hours", line(unit = "weeks"), line(state = "FUTURE"), line(reason = "CHAOS"), line(contract = "../etc|x"), "n'importe quoi", line().replace("used=90", "used=abc"))
        val r = RentalUsageReport.parse(text(line(), *bad.toTypedArray(), "", "sig=ignored-hook"))!!
        assertEquals(1, r.lines.size); assertEquals(bad.size, r.ignored, "blank lines and the future sig= line are not counted")
    }

    @Test fun anythingButAStatementIsRefusedAndAnEmptyOneIsValid() {
        assertNull(RentalUsageReport.parse("")); assertNull(RentalUsageReport.parse("castbridge-rental-usage-v2\ninstall=$inst\n"))
        assertNull(RentalUsageReport.parse("castbridge-rental-usage-v1\n" + line())); assertNull(RentalUsageReport.parse(text(install = "123e4567-e89b-12d3-a456-426614174000")))
        assertNull(RentalUsageReport.parse(text(install = "../../../x")), "the installation id names a file: only 16 hex")
        val empty = RentalUsageReport.parse(text())!!; assertTrue(empty.lines.isEmpty()); assertEquals(0, empty.ignored)
        assertEquals(listOf("Aucune location sur cette TV."), RentalUsageReport.format(empty, ZoneOffset.UTC))
    }

    @Test fun mergeIsMonotoneUsedNeverGoesBackAndTheFurthestStateWins() {
        val old = RentalUsageReport.parse(text(line(used = 300, state = "EXPIRED", reason = "USAGE", at = 50), line("loc-cm1@1", used = 10, at = 5), line("loc-old@1", used = 7)))!!
        val new = RentalUsageReport.parse(text(line(used = 120, state = "ACTIVE", at = 60), line("loc-cm1@1", used = 40, state = "GRACE", at = 70)))!!
        val m = RentalUsageReport.merge(old, new).lines.associateBy { it.contract }
        assertEquals(300L, m["loc-cm2@1"]!!.used, "an older, smaller reading never lowers the count"); assertEquals("EXPIRED", m["loc-cm2@1"]!!.state); assertEquals("USAGE", m["loc-cm2@1"]!!.reason)
        assertEquals(60L, m["loc-cm2@1"]!!.at, "the latest time of statement is kept")
        assertEquals(40L, m["loc-cm1@1"]!!.used); assertEquals("GRACE", m["loc-cm1@1"]!!.state)
        assertEquals(7L, m["loc-old@1"]!!.used, "a contract absent from the new statement is kept")
        assertEquals(RentalUsageReport.merge(old, new), RentalUsageReport.merge(RentalUsageReport.merge(old, new), new), "idempotent")
        assertFailsWith<IllegalArgumentException> { RentalUsageReport.merge(old, RentalUsageReport.parse(text(line(), install = other))!!) }
    }

    @Test fun theStoreKeepsOneFilePerInstallationMergedAndBackedUp() {
        val dir = Kit.tmp(); val store = RentalReportStore(dir)
        store.put(RentalUsageReport.parse(text(line(used = 100)))!!)
        store.put(RentalUsageReport.parse(text(line(used = 60, at = 99)))!!)               // older reading: monotone
        store.put(RentalUsageReport.parse(text(line("loc-cm1@1", used = 5), install = other))!!)
        assertEquals(2, dir.listFiles()!!.count { it.isFile && !it.name.endsWith(".bak") && !it.name.endsWith(".tmp") }, "one file per installation")
        val all = RentalReportStore(dir).all()                                              // a new store reads the folder again
        assertEquals(setOf(inst, other), all.map { it.install }.toSet())
        assertEquals(100L, all.single { it.install == inst }.lines.single().used)
        assertTrue(dir.listFiles()!!.any { it.name.endsWith(".bak") }, "SafeFile keeps the last good copy")
        // a corrupt main file falls back to the .bak
        val main = dir.listFiles()!!.first { it.name.contains(inst) && !it.name.endsWith(".bak") }; main.writeText("corrompu")
        assertEquals(100L, RentalReportStore(dir).all().single { it.install == inst }.lines.single().used)
        val ex = store.export(); assertEquals(2, ex.split("castbridge-rental-usage-v1").size - 1, "both statements are in the shared text"); assertTrue(ex.indexOf("install=$inst") >= 0)
        assertTrue(RentalReportStore(Kit.tmp()).all().isEmpty()); assertEquals("", RentalReportStore(Kit.tmp()).export())
    }

    @Test fun theScreenLinesSayTheNatureOfEachRentalInFrench() {
        fun f(l: String) = RentalUsageReport.format(RentalUsageReport.parse(text(l))!!, ZoneOffset.UTC).single()
        assertEquals("CM2 : 5 h 20 d'utilisation restante(s) sur 12 h · à utiliser avant le 15/11", f(line(used = 400)).replace("loc-cm2", "CM2"))
        assertEquals("loc-cm2 : vos 12 h d'utilisation sont épuisées", f(line(used = 720, state = "EXPIRED", reason = "USAGE")))
        assertEquals("loc-cm2 : vos heures non utilisées ont expiré le 15/11 (fin du test gratuit)", f(line(used = 100, state = "EXPIRED", reason = "DATE")))
        assertEquals("loc-cm1 : location en jours, jusqu'au 15/11 · 1 h 30 d'utilisation mesurée", f(line("loc-cm1@1", "days", 90, 0)))
        assertEquals("loc-cm1 : location en jours terminée le 15/11", f(line("loc-cm1@1", "days", 90, 0, state = "EXPIRED", reason = "DATE")))
        assertEquals("loc-cm2 : activation retirée · 45 min d'utilisation comptée(s)", f(line(unit = "unknown", used = 45, max = 0, state = "ORPHAN", endsAt = 0)))
        assertTrue("pas encore commencée" in f(line(state = "NOT_STARTED")))
        assertFalse("sender" in f(line()) || "receiver" in f(line()), "CastBridge words only")
    }

    @Test fun aStatementNeverCarriesAnIdentifierOfLicenceSeatOrPerson() {
        val r = RentalUsageReport.parse(text(line(), line("loc-cm1@1", "days", 3, 0)))!!
        val shown = RentalUsageReport.toText(r) + RentalUsageReport.format(r, ZoneOffset.UTC).joinToString("\n")
        for (w in listOf("lic", "seat", "box", "licence", "profil", "prénom", "@gmail", "uuid")) assertFalse(w in shown.lowercase(), "« $w » must not appear")
        assertTrue(Regex("(?m)^(castbridge-rental-usage-v1|install=[0-9a-f]{16}|contract=[^|]+\\|unit=\\w+\\|used=\\d+\\|max=\\d+\\|state=\\w+\\|reason=[\\w-]+\\|endsAt=\\d+\\|at=\\d+)$").findAll(RentalUsageReport.toText(r).trim()).count() == 4)
        assertEquals(r, RentalUsageReport.parse(RentalUsageReport.toText(r)), "text round trip")
    }
}
