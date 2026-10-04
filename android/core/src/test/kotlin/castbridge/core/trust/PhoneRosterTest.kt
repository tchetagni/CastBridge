package castbridge.core.trust

import java.time.ZoneOffset
import java.time.Instant
import kotlin.test.*

/** La vue de l'écran « Téléphones synchronisés » : ordre, étiquettes, compteur, suggestion. Horloge fausse, fuseau fixe. */
class PhoneRosterTest {
    private val now = Instant.parse("2026-10-04T12:00:00Z").toEpochMilli()
    private val min = 60_000L; private val hour = 60 * min; private val day = 24 * hour
    private fun addr(i: Int) = "AA:BB:CC:DD:EE:%02X".format(i)
    private fun phone(i: Int, seenAgo: Long, addedAgo: Long = 30 * day) = TrustedPhone(addr(i), "Tel $i", now - addedAgo, now - seenAgo)
    private fun build(phones: List<TrustedPhone>, active: Set<String> = emptySet()) = PhoneRoster.build(phones, active, now, ZoneOffset.UTC)

    @Test fun activeFirstThenMostRecentlySeen() {
        val v = build(listOf(phone(1, 3 * day), phone(2, 5 * min), phone(3, 10 * min), phone(4, 2 * hour)), active = setOf(addr(3), addr(4)))
        assertEquals(listOf(addr(3), addr(4), addr(2), addr(1)), v.rows.map { it.address })
        assertEquals(listOf(PhoneRoster.PhoneState.ACTIVE, PhoneRoster.PhoneState.ACTIVE, PhoneRoster.PhoneState.OUT_OF_RANGE, PhoneRoster.PhoneState.OUT_OF_RANGE), v.rows.map { it.state })
    }

    @Test fun labelsAreFrenchAndReadable() {
        val v = build(listOf(phone(1, 5 * min, addedAgo = 3 * day), phone(2, 3 * hour), phone(3, 2 * day), phone(4, 10_000)), active = setOf(addr(4)))
        val by = v.rows.associateBy { it.address }
        assertEquals("ajouté le 01/10/2026", by[addr(1)]!!.addedText)
        assertEquals("vu il y a 5 min", by[addr(1)]!!.seenText)
        assertEquals("vu il y a 3 h", by[addr(2)]!!.seenText)
        assertEquals("vu il y a 2 j", by[addr(3)]!!.seenText)
        assertEquals("actif", by[addr(4)]!!.seenText)
        assertEquals("hors de portée", by[addr(1)]!!.state.label); assertEquals("actif", by[addr(4)]!!.state.label)
    }

    @Test fun agoTable() {
        assertEquals("à l'instant", PhoneRoster.ago(0)); assertEquals("à l'instant", PhoneRoster.ago(59_999))
        assertEquals("il y a 1 min", PhoneRoster.ago(60_000)); assertEquals("il y a 59 min", PhoneRoster.ago(59 * min + 59_000))
        assertEquals("il y a 1 h", PhoneRoster.ago(hour)); assertEquals("il y a 23 h", PhoneRoster.ago(23 * hour + 59 * min))
        assertEquals("il y a 1 j", PhoneRoster.ago(day)); assertEquals("il y a 40 j", PhoneRoster.ago(40 * day))
        assertEquals("à l'instant", PhoneRoster.ago(-5_000), "a clock that went back is not a negative age")
    }

    @Test fun counterAndFullFlag() {
        val seven = build((1..7).map { phone(it, it * min) })
        assertEquals("7 / 8", seven.counter); assertFalse(seven.full)
        val eight = build((1..8).map { phone(it, it * min) })
        assertEquals("8 / 8", eight.counter); assertTrue(eight.full)
        assertEquals("0 / 8", build(emptyList()).counter)
    }

    @Test fun theSuggestionIsTheLeastRecentlySeenAndOnlyWhenFull() {
        val seven = build((1..7).map { phone(it, it * hour) })
        assertNull(seven.suggestedAddress); assertTrue(seven.rows.none { it.suggested })
        val eight = build((1..8).map { phone(it, it * hour) })
        assertEquals(addr(8), eight.suggestedAddress)
        assertEquals(1, eight.rows.count { it.suggested })
    }

    @Test fun anActivePhoneIsNotSuggestedWhileAnotherOneIsOutOfRange() {
        val v = build((1..8).map { phone(it, it * hour) }, active = setOf(addr(8)))
        assertEquals(addr(7), v.suggestedAddress, "the oldest one is active right now: the next oldest is proposed")
        val allActive = build((1..8).map { phone(it, it * hour) }, active = (1..8).map { addr(it) }.toSet())
        assertEquals(addr(8), allActive.suggestedAddress)
    }

    @Test fun emptyRoster() {
        val v = build(emptyList())
        assertTrue(v.rows.isEmpty()); assertNull(v.suggestedAddress); assertFalse(v.full)
    }

    @Test fun addressesAreMatchedWhateverTheirCase() {
        val v = build(listOf(phone(1, 5 * min)), active = setOf(addr(1).lowercase()))
        assertEquals(PhoneRoster.PhoneState.ACTIVE, v.rows.single().state)
    }

    @Test fun rowsHaveAScreenReaderDescriptionWithEverythingTheRowSays() {
        val v = build((1..8).map { phone(it, it * hour) })
        val d = v.rows.last().description
        assertTrue("Tel 8" in d && "vu il y a 8 h" in d && "hors de portée" in d && PhonesTexts.SUGGESTION in d, d)
    }

    @Test fun textsMentionTheNewPhoneAndTheCap() {
        assertEquals("Cette TV a déjà 8 téléphones : choisissez celui à retirer pour ajouter Galaxy de Paul", PhonesTexts.replaceTitle("Galaxy de Paul"))
        assertEquals("Téléphones synchronisés (7 / 8)…", PhonesTexts.menuEntry(7))
        assertTrue("code de la TV" in PhonesTexts.CONFIRM_REMOVE_TEXT && "approuvé de nouveau" in PhonesTexts.CONFIRM_REMOVE_TEXT)
    }
}
