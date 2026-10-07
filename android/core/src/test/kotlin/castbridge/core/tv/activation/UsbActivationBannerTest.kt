package castbridge.core.tv.activation

import castbridge.core.owner.TrialPolicy
import castbridge.core.tv.activation.UsbActivationBanner as B
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The USB banner of the activation screen (F5, docs/coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md): at plug-in, when the screen opens and on the « Chercher » button the TV looks for
 * the activation file and says EXACTLY what it found: « activation trouvée pour cette TV › Activer » (a focusable button installs it) or the precise reason. Pure: built from the facts of
 * the lookup (paths, counts, file states), never from a key.
 */
class UsbActivationBannerTest {
    private val vol = listOf(VolumeFact("A379-E209", false))
    private val own = "/storage/A379-E209/Android/data/castbridge.receiver/files"

    private fun facts(vararg p: Probe, volumes: List<VolumeFact> = vol, access: StorageAccess = StorageAccess.GRANTED, place: Place = Place.OWN_DIR) =
        LookupFacts(volumes, p.map { ProbeFact(place, "A379-E209", it) }, emptyList(), access, listOf(own))

    private fun state(vararg p: Probe, volumes: List<VolumeFact> = vol, access: StorageAccess = StorageAccess.GRANTED) = B.from(facts(*p, volumes = volumes, access = access)).state

    @Test fun `a key that verifies for this TV is announced with its button`() {
        val v = B.from(facts(Probe.ACCEPTED))
        assertEquals(B.State.FOUND, v.state)
        assertTrue(v.canActivate, "the banner is the « Activer » button")
        assertEquals(LineTone.GOOD, v.tone)
        assertEquals("Clé USB : activation trouvée pour cette TV › Activer", v.text)
        assertTrue(v.keyPresent)
    }

    @Test fun `every other outcome names its exact reason and offers no button`() {
        val cases: List<Triple<List<Probe>, B.State, List<String>>> = listOf(
            Triple(listOf(Probe.WRONG_DEVICE), B.State.WRONG_TV, listOf("clé d'une autre TV")),
            Triple(listOf(Probe.EXPIRED), B.State.EXPIRED, listOf("périmée", "48 h")),
            Triple(listOf(Probe.NOT_VALID), B.State.NOT_VALID, listOf("pas une clé valable")),
            Triple(listOf(Probe.TOO_BIG), B.State.TOO_BIG, listOf("trop gros", "16 ko")),
            Triple(listOf(Probe.EMPTY), B.State.EMPTY, listOf("vide")),
            Triple(listOf(Probe.UNREADABLE), B.State.UNREADABLE, listOf("refuse de le lire", "Android/data/castbridge.receiver/files/")),
            Triple(listOf(Probe.ABSENT, Probe.ABSENT), B.State.NOT_FOUND, listOf("aucun fichier « activation »", "Android/data/castbridge.receiver/files/")),
        )
        for ((probes, expected, words) in cases) {
            val v = B.from(facts(*probes.toTypedArray()))
            assertEquals(expected, v.state, probes.toString())
            for (w in words) assertTrue(w in v.text, "« $w » missing from « ${v.text} »")
            assertTrue(v.text.startsWith("Clé USB : "), v.text)
            assertFalse(v.canActivate, probes.toString())
            assertEquals(LineTone.WARN, v.tone, probes.toString())
        }
    }

    @Test fun `Android 11 hiding Download is said as such, with the folder to use`() {
        val v = B.from(facts(Probe.ABSENT, Probe.UNREADABLE, access = StorageAccess.MISSING, place = Place.DOWNLOAD))
        assertEquals(B.State.HIDDEN, v.state)
        assertEquals("Clé USB : dossier Download invisible : déposez le fichier dans Android/data/castbridge.receiver/files/", v.text)
        assertFalse(v.canActivate)
        // nothing at all read, permission missing, only « absent » answers: still the cause, never « aucune clé »
        assertEquals(B.State.HIDDEN, state(Probe.ABSENT, access = StorageAccess.MISSING))
        // the permission is there: the same answers mean the file is simply not on the key
        assertEquals(B.State.NOT_FOUND, state(Probe.ABSENT, access = StorageAccess.GRANTED))
        // the permission is missing but something WAS read in the app's own folder: that verdict wins, Download is not blamed
        assertEquals(B.State.WRONG_TV, state(Probe.ABSENT, Probe.WRONG_DEVICE, access = StorageAccess.MISSING))
        assertEquals(B.State.FOUND, state(Probe.ABSENT, Probe.ACCEPTED, access = StorageAccess.MISSING))
        assertEquals(B.State.EMPTY, state(Probe.EMPTY, access = StorageAccess.MISSING), "a file that WAS read, even empty, is not a hidden folder")
    }

    @Test fun `with several files the most useful verdict wins, like the report lines`() {
        assertEquals(B.State.FOUND, state(Probe.WRONG_DEVICE, Probe.EXPIRED, Probe.ACCEPTED, Probe.NOT_VALID), "a bad file never hides a good one")
        assertEquals(B.State.WRONG_TV, state(Probe.EXPIRED, Probe.NOT_VALID, Probe.WRONG_DEVICE, Probe.UNREADABLE))
        assertEquals(B.State.EXPIRED, state(Probe.NOT_VALID, Probe.EXPIRED, Probe.EMPTY, Probe.ABSENT))
        assertEquals(B.State.NOT_VALID, state(Probe.UNREADABLE, Probe.NOT_VALID, Probe.TOO_BIG))
        assertEquals(B.State.UNREADABLE, state(Probe.ABSENT, Probe.UNREADABLE, Probe.TOO_BIG, Probe.EMPTY))
        assertEquals(B.State.TOO_BIG, state(Probe.EMPTY, Probe.TOO_BIG, Probe.ABSENT))
        assertEquals(B.State.EMPTY, state(Probe.ABSENT, Probe.EMPTY))
    }

    @Test fun `no key plugged is a calm line, whatever else was probed`() {
        val none = B.from(facts(Probe.ABSENT, volumes = emptyList()))
        assertEquals(B.State.NO_KEY, none.state)
        assertFalse(none.keyPresent)
        assertEquals(LineTone.INFO, none.tone)
        assertTrue("aucune clé" in none.text && "branchez" in none.text, none.text)
        assertEquals(B.State.NO_KEY, state(Probe.ABSENT, Probe.UNREADABLE, volumes = emptyList(), access = StorageAccess.MISSING), "no volume: not a hidden-folder problem")
        assertEquals(B.State.NO_KEY, B.from(LookupFacts(emptyList(), emptyList(), emptyList())).state)
        // a verdict is still a verdict (a file found in the internal storage's folders)
        assertEquals(B.State.FOUND, state(Probe.ACCEPTED, volumes = emptyList()))
    }

    @Test fun `only the found state can be activated, and only it is green`() {
        for (s in B.State.values()) {
            val v = viewOf(s)
            assertEquals(s == B.State.FOUND, v.canActivate, "$s")
            assertEquals(s == B.State.FOUND, v.tone == LineTone.GOOD, "$s")
        }
    }

    /** One view per state, through the public constructors only. */
    private fun viewOf(s: B.State): B.View = when (s) {
        B.State.IDLE -> B.idle(); B.State.TERMS_PENDING -> B.termsPending(); B.State.SEARCHING -> B.searching()
        B.State.NO_KEY -> B.from(facts(volumes = emptyList())); B.State.FOUND -> B.from(facts(Probe.ACCEPTED))
        B.State.WRONG_TV -> B.from(facts(Probe.WRONG_DEVICE)); B.State.EXPIRED -> B.from(facts(Probe.EXPIRED))
        B.State.NOT_VALID -> B.from(facts(Probe.NOT_VALID)); B.State.TOO_BIG -> B.from(facts(Probe.TOO_BIG))
        B.State.EMPTY -> B.from(facts(Probe.EMPTY)); B.State.UNREADABLE -> B.from(facts(Probe.UNREADABLE))
        B.State.HIDDEN -> B.from(facts(Probe.ABSENT, access = StorageAccess.MISSING)); B.State.NOT_FOUND -> B.from(facts(Probe.ABSENT))
        B.State.INSTALLED -> B.from(facts(Probe.INSTALLED))
    }

    @Test fun `the waiting states say so`() {
        assertEquals(B.State.IDLE, B.idle().state); assertEquals("", B.idle().text)
        assertTrue("recherche" in B.searching().text, B.searching().text)
        val t = B.termsPending()
        assertTrue("conditions d'usage" in t.text, t.text); assertFalse(t.canActivate)
        for (s in B.State.values()) assertEquals(s, viewOf(s).state, "every state is reachable")
    }

    @Test fun `the lane line tells the key's presence`() {
        assertEquals("Clé détectée : A379-E209", B.presence(facts(Probe.ABSENT)))
        assertEquals("Clé détectée : A379-E209 (lecture seule)", B.presence(facts(Probe.ABSENT, volumes = listOf(VolumeFact("A379-E209", true)))))
        val none = "Aucune clé USB détectée : branchez-la, la TV la lit aussitôt"
        assertEquals(none, B.presence(facts(volumes = emptyList())), "the same words as the report would say « sur la TV »: one wording on screen")
        assertEquals(none, B.presence(null), "before any search")
        assertEquals(none, B.idle().presence)
        assertEquals("Clé détectée : A379-E209", B.from(facts(Probe.ABSENT)).presence)
        assertTrue("conditions d'usage" in B.termsPending().presence, "the key is not read before the terms: the line says why it does not know yet")
        assertFalse("Aucune clé" in B.termsPending().presence, "a key may well be plugged in: never claim none")
    }

    @Test fun `the home only speaks when a file named activation was really seen`() {
        val tile = TrialPolicy.UPGRADE_LABEL
        val found = B.homeLine(B.from(facts(Probe.ACCEPTED)))!!
        assertTrue("activation trouvée pour cette TV" in found && tile in found, found)
        for (s in listOf(B.State.WRONG_TV, B.State.EXPIRED, B.State.NOT_VALID, B.State.TOO_BIG, B.State.EMPTY, B.State.UNREADABLE)) {
            assertEquals(viewOf(s).text, B.homeLine(viewOf(s)), "$s")
        }
        // a key plugged to watch videos must not nag the home
        for (s in listOf(B.State.IDLE, B.State.TERMS_PENDING, B.State.SEARCHING, B.State.NO_KEY, B.State.HIDDEN, B.State.NOT_FOUND, B.State.INSTALLED)) assertNull(B.homeLine(viewOf(s)), "$s")
    }

    @Test fun `the search at plug-in is retried briefly while the volume settles, never in a loop`() {
        for (s in listOf(B.State.NO_KEY, B.State.NOT_FOUND, B.State.HIDDEN)) {
            assertEquals(1_500L, B.nextRetryDelayMs(s, 0), "$s")
            assertEquals(2_500L, B.nextRetryDelayMs(s, 1), "$s")
            assertNull(B.nextRetryDelayMs(s, 2), "$s: bounded")
            assertNull(B.nextRetryDelayMs(s, 7), "$s: bounded")
        }
        for (s in B.State.values().filter { it !in listOf(B.State.NO_KEY, B.State.NOT_FOUND, B.State.HIDDEN) }) assertNull(B.nextRetryDelayMs(s, 0), "$s is decisive or waiting: no retry")
        assertTrue(B.nextRetryDelayMs(B.State.NO_KEY, 0)!! + B.nextRetryDelayMs(B.State.NO_KEY, 1)!! <= 5_000L, "the banner is there within 5 s of the mount (ACT-F6)")
    }

    @Test fun `a key that is already installed is not announced, whatever the number of copies`() {
        // R-44 (I-16) : l'outil de bureau écrit trois copies, la clé reste branchée : « trouvée » puis « périmée » à chaque branchement. Maintenant : rien à annoncer.
        for (copies in 1..4) {
            val v = B.from(facts(*Array(copies) { Probe.INSTALLED }))
            assertEquals(B.State.INSTALLED, v.state, "$copies copie(s)")
            assertFalse(v.canActivate, "il n'y a rien à activer")
            assertEquals(LineTone.INFO, v.tone, "ni vert (« trouvée ») ni orange (« périmée »)")
            assertTrue(v.keyPresent)
            assertEquals("Clé USB : cette activation est déjà installée sur cette TV", v.text)
        }
        assertNull(B.homeLine(B.from(facts(Probe.INSTALLED))), "l'accueil ne dit rien")
    }

    @Test fun `an installed key hides nothing that is worth saying about another file, and nothing hides it but a real verdict`() {
        assertEquals(B.State.FOUND, state(Probe.INSTALLED, Probe.ACCEPTED), "une AUTRE clé bonne reste annoncée")
        assertEquals(B.State.WRONG_TV, state(Probe.INSTALLED, Probe.WRONG_DEVICE), "la clé d'une autre TV, déposée à côté, reste signalée")
        assertEquals(B.State.EXPIRED, state(Probe.INSTALLED, Probe.EXPIRED))
        assertEquals(B.State.NOT_VALID, state(Probe.INSTALLED, Probe.NOT_VALID))
        // les fichiers absents, illisibles, vides ou trop gros ne sont pas des verdicts : la clé installée parle
        assertEquals(B.State.INSTALLED, state(Probe.ABSENT, Probe.INSTALLED, Probe.UNREADABLE, Probe.EMPTY, Probe.TOO_BIG))
        // Android 11 cache Download mais un fichier a été LU dans le dossier de l'application : ce n'est pas un dossier invisible
        assertEquals(B.State.INSTALLED, state(Probe.ABSENT, Probe.INSTALLED, access = StorageAccess.MISSING))
        assertEquals(B.State.INSTALLED, state(Probe.INSTALLED, volumes = emptyList()), "un verdict reste un verdict, même sans volume vu")
    }

    @Test fun `no key text can be in a banner, only paths and states`() {
        for (s in B.State.values()) {
            val t = viewOf(s).text
            assertFalse("cbx1" in t || "ed25519" in t, t)
        }
    }
}
