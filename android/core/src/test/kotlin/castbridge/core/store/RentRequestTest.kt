package castbridge.core.store

import castbridge.core.store.RentRequest.Kind
import castbridge.core.store.RentRequest.Origin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Format `castbridge-rent-request-v1` (w17-03) : forme canonique, analyse stricte, code court, libellés W16 § 1.3. */
class RentRequestTest {
    private val base = RentRequest("0123456789abcdef", "classe-cm2", "12h", Kind.NEW, 0, "a1b2c3d4", 1_800_000_000_000L, Origin.TV)
    private val baseText = "castbridge-rent-request-v1\nat=1800000000000\nbundle=classe-cm2\nchoice=12h\nkind=new\nnonce=a1b2c3d4\norigin=tv\nperiod=0\ntv=0123456789abcdef"

    private fun bad(text: String): RentRequest.Parsed.Bad = RentRequest.parse(text).let { assertIs<RentRequest.Parsed.Bad>(it, "devait être refusé : ${text.replace("\n", "\\n")}") }
    private fun with(from: String, to: String) = baseText.replace(from, to)

    @Test fun canonicalFormIsExact() {
        assertEquals(baseText, base.canonical())
    }

    @Test fun canonicalLinesAreSortedWithoutSpaceOrTrailingNewline() {
        val lines = base.canonical().split("\n")
        assertEquals(RentRequest.FORMAT, lines[0])
        val keys = lines.drop(1).map { it.substringBefore('=') }
        assertEquals(keys.sorted(), keys)
        assertEquals(8, keys.size)
        assertTrue(base.canonical().none { it == ' ' || it == '\r' || it == '\t' })
        assertTrue(!base.canonical().endsWith("\n"))
    }

    @Test fun parseRoundTrips() {
        assertEquals(base, assertIs<RentRequest.Parsed.Ok>(RentRequest.parse(baseText)).request)
        val ext = base.copy(kind = Kind.EXTEND, period = 1_799_000_000_000L, choice = "7j", origin = Origin.PHONE)
        assertEquals(ext, assertIs<RentRequest.Parsed.Ok>(RentRequest.parse(ext.canonical())).request)
        val def = base.copy(choice = "defaut")
        assertEquals(def, assertIs<RentRequest.Parsed.Ok>(RentRequest.parse(def.canonical())).request)
    }

    @Test fun malformedIsRefusedWithAFrenchSentence() {
        val b = bad("n'importe quoi")
        assertEquals(Refusal.MALFORMED, b.refusal)
        assertTrue(b.message.isNotBlank() && b.message.contains("illisible"), b.message)
    }

    @Test fun wrongHeaderOrEmptyIsRefused() {
        bad(""); bad(with("castbridge-rent-request-v1", "castbridge-rent-request-v2")); bad(baseText.substringAfter("\n"))
    }

    @Test fun everyFieldIsMandatory() {
        for (key in listOf("at", "bundle", "choice", "kind", "nonce", "origin", "period", "tv")) {
            val text = baseText.split("\n").filterNot { it.startsWith("$key=") }.joinToString("\n")
            assertEquals(Refusal.MALFORMED, bad(text).refusal, "sans $key")
        }
    }

    @Test fun unknownOrDuplicateFieldIsRefused() {
        bad("$baseText\nzzz=1")
        bad(with("period=0\n", "period=0\nperiod=0\n"))
    }

    @Test fun tvMustBeSixteenLowercaseHex() {
        for (tv in listOf("0123456789abcde", "0123456789abcdef0", "0123456789ABCDEF", "0123456789abcdeg", ""))
            bad(with("tv=0123456789abcdef", "tv=$tv"))
    }

    @Test fun bundleMustBeAnId() {
        for (b in listOf("Classe-CM2", "-cm2", "classe cm2", "a".repeat(65), "", "classe_cm2")) bad(with("bundle=classe-cm2", "bundle=$b"))
        assertTrue(RentRequest.parse(with("bundle=classe-cm2", "bundle=" + "a".repeat(64))) is RentRequest.Parsed.Ok)
    }

    @Test fun choiceGrammar() {
        for (c in listOf("0j", "0h", "007j", "12", "12x", "1000j", "DEFAUT", "-1h", "", "12H", "defaut2")) bad(with("choice=12h", "choice=$c"))
        for (c in listOf("defaut", "1j", "14j", "96h", "999h")) assertTrue(RentRequest.parse(with("choice=12h", "choice=$c")) is RentRequest.Parsed.Ok, c)
    }

    @Test fun kindAndPeriodMustAgree() {
        bad(with("kind=new", "kind=renew"))
        bad(with("period=0", "period=5"))                       // new avec period
        bad(with("kind=new", "kind=extend"))                    // extend sans period
        val ext = with("kind=new", "kind=extend").replace("period=0", "period=1799000000000")
        assertTrue(RentRequest.parse(ext) is RentRequest.Parsed.Ok)
        bad(ext.replace("period=1799000000000", "period=-1"))
    }

    @Test fun nonceIsEightLowercaseHex() {
        for (n in listOf("a1b2c3d", "a1b2c3d4e", "A1B2C3D4", "a1b2c3dz", "")) bad(with("nonce=a1b2c3d4", "nonce=$n"))
    }

    @Test fun originAndClockAreChecked() {
        bad(with("origin=tv", "origin=server"))
        for (at in listOf("-1", "abc", "", "1.5", "1" + "0".repeat(15))) bad(with("at=1800000000000", "at=$at"))
    }

    @Test fun onlyTheCanonicalSpellingIsAccepted() {
        val lines = baseText.split("\n")
        bad(lines.take(1).plus(lines.drop(1).reversed()).joinToString("\n"))      // lignes non triées
        bad("$baseText\n")                                                        // retour final
        bad(baseText.replace("\n", "\r\n"))
        bad(with("choice=12h", "choice= 12h"))
        bad(with("tv=", "tv ="))
    }

    @Test fun shortCodeIsDeterministicAndPinned() {
        assertEquals("CM2-12H-0PF8", base.shortCode("CM2"))
        assertEquals("CM2-12H-0PF8", base.copy().shortCode("CM2"))
        assertEquals("CM2-7J-8ASG", base.copy(choice = "7j").shortCode("CM2"))
    }

    @Test fun shortCodeOfTheDefaultChoiceSaysDEF() {
        assertEquals("CM2-DEF-1TK8", base.copy(choice = "defaut", nonce = "0000000a").shortCode("CM2"))
    }

    @Test fun shortCodeDependsOnTheNonceAndTheBundle() {
        val other = base.copy(nonce = "a1b2c3d5").shortCode("CM2")
        assertNotEquals(base.shortCode("CM2"), other)
        assertTrue(Regex("^CM2-12H-[0-9A-HJKMNP-TV-Z]{4}$").matches(other), other)
    }

    @Test fun shortCodeRefusesAnAliasThatCannotBeDictated() {
        for (a in listOf("", "cm2", "CM 2", "ABCDEFG", "É")) assertFailsWith<IllegalArgumentException>(a) { base.shortCode(a) }
    }

    @Test fun choiceLabelsAreTheExactW16Texts() {
        assertEquals("12 heures d'utilisation", base.choiceLabel())
        assertEquals("1 heure d'utilisation", base.copy(choice = "1h").choiceLabel())
        assertEquals("7 jours", base.copy(choice = "7j").choiceLabel())
        assertEquals("1 jour", base.copy(choice = "1j").choiceLabel())
        assertEquals("Sans durée précise : 30 jours", base.copy(choice = "defaut").choiceLabel())
    }

    @Test fun unitFollowsTheChoiceWithoutConversion() {
        assertEquals(RentRequest.Unit.HOURS, base.unit)
        assertEquals(RentRequest.Unit.DAYS, base.copy(choice = "7j").unit)
        assertEquals(RentRequest.Unit.DAYS, base.copy(choice = "defaut").unit)
    }

    @Test fun anOversizedTextIsRefusedBeforeBeingSplit() {
        val b = bad(baseText + "\n" + "x=".padEnd(5000, 'y'))
        assertTrue(b.message.contains("trop long"), b.message)
    }

    @Test fun boundaryValuesAreAccepted() {
        assertTrue(RentRequest.parse(base.copy(bundle = "a" + "b".repeat(63), choice = "999h").canonical()) is RentRequest.Parsed.Ok)
        assertTrue(RentRequest.parse(base.copy(bundle = "0", choice = "1j", at = 0).canonical()) is RentRequest.Parsed.Ok)
    }
}
