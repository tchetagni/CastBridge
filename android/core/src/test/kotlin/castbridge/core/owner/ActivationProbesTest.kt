package castbridge.core.owner

import castbridge.core.owner.ActivationRoutePlan.Candidate
import castbridge.core.owner.ActivationRoutePlan.Cause
import castbridge.core.owner.ActivationRoutePlan.Event
import castbridge.core.owner.ActivationRoutePlan.Found
import castbridge.core.owner.ActivationRoutePlan.Route
import castbridge.core.owner.LockedRequestRoute.Reply
import castbridge.core.trust.DeviceRequestParse
import castbridge.core.trust.TvDeviceRequest
import kotlin.test.*

/**
 * Sonder les TV : qui est interrogé (jamais une TV d'un voisin déjà activée), qui n'est plus réinterrogé (verrou de 60 s et plafond de codes faux de la TV), ce que chaque réponse
 * de la TV veut dire pour la recherche (réseau local, groupe Wi-Fi Direct). Pur : l'exécutant Android ne fait qu'appeler la TV et relayer ces événements.
 */
class ActivationProbesTest {
    private val fp = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9", FactorKind.SYSTEM_SERIAL to "aaaaaaaabbbbbbbbccccccccdddddddd"))
    private val request: TvDeviceRequest = (LockedRequestRoute.parse(OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, null, ByteArray(32) { it.toByte() })) as DeviceRequestParse.Ok).request
    private fun cand(i: Int, locked: Boolean = true, name: String = "CastBridge TV $i") = Candidate(name, "http://192.168.1.$i:8765", locked)

    // ------------------------------------------------------------------ qui interroger

    @Test fun lockedAnnouncedTvsComeFirstThenTheLinkedOneAndNoneIsLeftOutInAdvance() {
        // audit I-4 : la liste ne se coupe PLUS à trois d'avance (la 4e TV d'une boutique n'était jamais lue) ; le plafond de trois vaut par TOUR d'interrogation (LanProbes.due)
        val announced = listOf(cand(21), cand(22), cand(23), cand(24), cand(30, locked = false))
        val linked = cand(40, locked = false, name = "Ma TV")
        assertEquals(listOf(cand(21), cand(22), cand(23), cand(24), linked), ActivationRoutePlan.candidates(announced, linked))
        assertEquals(listOf(cand(21), cand(40, locked = false, name = "Ma TV")), ActivationRoutePlan.candidates(listOf(cand(21), cand(30, locked = false)), linked))
        assertEquals(listOf(linked), ActivationRoutePlan.candidates(emptyList(), linked))
        assertTrue(ActivationRoutePlan.candidates(emptyList(), null).isEmpty())
        assertEquals(3, ActivationRoutePlan.MAX_CANDIDATES)
    }

    // ------------------------------------------------------------------ audit I-4 : toutes les TV verrouillées annoncées sont interrogées à tour de rôle (avec le même plafond de trois par tour)

    @Test fun everyAnnouncedLockedTvIsAskedInTurnNotOnlyTheFirstThree() {
        val tvs = (21..25).map { cand(it) }                                  // une boutique d'agent : cinq TV verrouillées sur le même Wi-Fi ; la bonne est la 5e
        val all = ActivationRoutePlan.candidates(tvs, null)
        val p = ActivationRoutePlan.LanProbes()
        val first = p.due(all, 1_000)
        assertEquals(tvs.take(3), first, "premier tour : trois TV au plus")
        first.forEach { assertNotNull(p.answered(it, Reply.CodeRefused)) }     // les trois premières ne sont pas la bonne : « code refusé »
        val second = p.due(all, 1_500)
        assertEquals(listOf(tvs[3], tvs[4]), second, "le tour suivant interroge la 4e et la 5e (avant le correctif : liste vide, la bonne TV n'était jamais lue)")
        assertEquals(Event.Observed(Route.LAN, Cause(Cause.Kind.CODE_REFUSED, "CastBridge TV 24")), p.answered(tvs[3], Reply.CodeRefused))
        assertIs<Event.Reached>(p.answered(tvs[4], Reply.Request(request)), "la 5e est la bonne : sa demande est lue")
        assertTrue(p.due(all, 99_000).isEmpty(), "toutes ont répondu : plus aucune interrogation")
    }

    @Test fun theCapOfThreeAppliesToEachRoundAndEveryTvIsAskedOnceWhateverTheirNumber() {
        val tvs = (21..30).map { cand(it) }                                  // dix TV
        val all = ActivationRoutePlan.candidates(tvs, null)
        assertEquals(10, all.size)
        val p = ActivationRoutePlan.LanProbes()
        val rounds = ArrayList<List<Candidate>>()
        var t = 0L
        while (true) {
            val due = p.due(all, t)
            if (due.isEmpty()) break
            rounds += due; due.forEach { p.answered(it, Reply.CodeRefused) }; t += 500
        }
        assertEquals(listOf(3, 3, 3, 1), rounds.map { it.size }, "trois au plus par tour (le plafond d'essais d'un tour ne change pas)")
        assertEquals(tvs, rounds.flatten(), "toutes interrogées, une fois chacune, dans l'ordre annoncé")
    }

    @Test fun silentTvsDoNotStarveTheOnesBehindThem() {
        val tvs = (21..25).map { cand(it) }
        val all = ActivationRoutePlan.candidates(tvs, null)
        val p = ActivationRoutePlan.LanProbes()
        assertEquals(tvs.take(3), p.due(all, 0))                              // trois TV muettes (connexion refusée, délai) : rien n'est réglé
        assertEquals(listOf(tvs[3], tvs[4]), p.due(all, 500), "les muettes attendent leur délai de 2,5 s, les suivantes passent tout de suite")
        assertTrue(p.due(all, 1_000).isEmpty())
        assertEquals(tvs.take(3), p.due(all, ActivationRoutePlan.RE_PROBE_MS), "les muettes sont réinterrogées une fois leur délai écoulé")
    }

    @Test fun aTvThatAnsweredIsNeverAskedAgainEvenWhenItIsTheFourth() {
        val tvs = (21..24).map { cand(it) }
        val all = ActivationRoutePlan.candidates(tvs, null)
        val p = ActivationRoutePlan.LanProbes()
        p.due(all, 0).forEach { p.answered(it, Reply.CodeRefused) }
        assertEquals(listOf(tvs[3]), p.due(all, 500))
        p.answered(tvs[3], Reply.LockedOut(40))
        assertTrue(p.due(all, 1_000_000).isEmpty(), "chaque essai compte un code faux sur la TV : une TV qui a répondu n'est plus interrogée")
    }

    @Test fun theKeyGoesToTheTvThatAnsweredTheReadAndTheScreenShowsItsAddress() {
        // « le code n'est envoyé qu'aux TV qui l'ont accepté par une lecture sans effet » : la TV de « TV trouvée » est celle qui a répondu 200, c'est à son adresse que la clé et le code partiront
        val tvs = (21..25).map { cand(it) }
        val p = ActivationRoutePlan.LanProbes()
        val found = (p.answered(tvs[4], Reply.Request(request)) as Event.Reached).found
        assertEquals(tvs[4].base, found.base, "la clé part à l'adresse de la TV qui a répondu, pas à une autre TV annoncée")
        assertEquals("192.168.1.25", found.address())
        val connected = ActivationRoutePlan.reduce(ActivationRoutePlan.start(ActivationRoutePlan.Facts("482913", 34, true, true, ActivationRoutePlan.Bt.OFF), 0).run, Event.Reached(found)).run
        assertEquals("Réseau local : TV trouvée (CastBridge TV 25, 192.168.1.25).", connected.lines().first().text, "l'adresse est dite : à comparer avec celle de l'écran de la TV")
        assertNull(Found(Route.GROUP, "CastBridge-TV", "http://192.168.49.1:8765", null).address(), "l'adresse du groupe n'apprend rien : seulement le réseau local")
        assertNull(Found(Route.BLUETOOTH, "TV", null, null, btAddress = "AA:BB:CC:DD:EE:FF").address())
    }


    @Test fun aTvOfANeighbourThatIsAlreadyActivatedIsNeverGivenTheCode() {
        // announced but not locked, not the TV this phone is linked to: no probe
        assertTrue(ActivationRoutePlan.candidates(listOf(cand(30, locked = false)), null).isEmpty())
    }

    @Test fun onlyPrivateLocalAddressesAreProbed() {
        val bad = listOf("http://8.8.8.8:8765", "http://127.0.0.1:8766", "http://evil.example.com:8765", "https://192.168.1.21:8765", "http://192.168.1.21.evil.com:8765")
        for (b in bad) {
            assertTrue(ActivationRoutePlan.candidates(listOf(Candidate("TV", b, true)), null).isEmpty(), b)
            assertTrue(ActivationRoutePlan.candidates(emptyList(), Candidate("TV", b, false)).isEmpty(), "même la TV liée : $b")
        }
        assertEquals(1, ActivationRoutePlan.candidates(listOf(Candidate("TV", "http://10.0.0.5:8765", true), Candidate("TV bis", "http://10.0.0.5:8765", true)), null).size, "une adresse une fois")
    }

    // ------------------------------------------------------------------ qui est réinterrogé

    @Test fun aTvIsProbedAgainOnlyAfterTheDelayAndOnlyWhenItDidNotAnswer() {
        val p = ActivationRoutePlan.LanProbes()
        val c = listOf(cand(21), cand(22))
        assertEquals(c, p.due(c, 1_000))
        assertTrue(p.due(c, 1_000 + ActivationRoutePlan.RE_PROBE_MS - 1).isEmpty(), "pas deux fois dans la même période")
        assertEquals(c, p.due(c, 1_000 + ActivationRoutePlan.RE_PROBE_MS))
        // a new TV that appears is probed at once
        assertEquals(listOf(cand(23)), p.due(c + cand(23), 1_000 + ActivationRoutePlan.RE_PROBE_MS + 1))
        assertEquals(2_500L, ActivationRoutePlan.RE_PROBE_MS)
    }

    @Test fun silenceIsRetriedButAnyAnswerIsNot() {
        val p = ActivationRoutePlan.LanProbes()
        val c = cand(21)
        assertNull(p.answered(c, null), "pas de réponse : aucun événement, on réessaie")
        assertEquals(listOf(c), p.due(listOf(c), 100_000), "toujours à interroger")
        for (reply in listOf(Reply.CodeRefused, Reply.LockedOut(30), Reply.TermsNotAccepted, Reply.Closed, Reply.Unreadable("x"), Reply.RouteMissing)) {
            val q = ActivationRoutePlan.LanProbes()
            assertNotNull(q.answered(c, reply), "$reply")
            assertTrue(q.due(listOf(c), 1_000_000).isEmpty(), "$reply : une TV qui a répondu n'est pas réinterrogée (verrou de 60 s, plafond de codes faux)")
        }
    }

    @Test fun aCodeRefusedByOneTvDoesNotStopTheSearchOfTheOthers() {
        val p = ActivationRoutePlan.LanProbes()
        val a = cand(21); val b = cand(22)
        p.answered(a, Reply.CodeRefused)
        assertEquals(listOf(b), p.due(listOf(a, b), 5_000))
        assertIs<Event.Reached>(p.answered(b, Reply.Request(request)))
    }

    // ------------------------------------------------------------------ ce que dit chaque réponse (réseau local)

    @Test fun everyLanReplyBecomesTheRightEvent() {
        val c = cand(21, name = "  CastBridge${0.toChar()} TV salon ")
        val p = ActivationRoutePlan.LanProbes()
        assertEquals(Event.Reached(Found(Route.LAN, "CastBridge TV salon", c.base, request)), p.answered(c, Reply.Request(request)))
        assertEquals(Event.Reached(Found(Route.LAN, "CastBridge TV salon", c.base, null, note = Cause(Cause.Kind.NEEDS_UPDATE))), ActivationRoutePlan.LanProbes().answered(c, Reply.RouteMissing),
            "une TV verrouillée sans la route (0.14.43) est là : le code sera vérifié à l'envoi de la clé")
        assertEquals(Event.Observed(Route.LAN, Cause(Cause.Kind.NEEDS_UPDATE)), ActivationRoutePlan.LanProbes().answered(c.copy(locked = false), Reply.RouteMissing),
            "une TV liée qui n'a pas la route de lecture : rien n'est trouvé")
        assertEquals(Event.Observed(Route.LAN, Cause(Cause.Kind.CODE_REFUSED, "CastBridge TV salon")), ActivationRoutePlan.LanProbes().answered(c, Reply.CodeRefused))
        assertEquals(Event.Observed(Route.LAN, Cause(Cause.Kind.LOCKED_OUT, "42")), ActivationRoutePlan.LanProbes().answered(c, Reply.LockedOut(42)))
        assertEquals(Event.Observed(Route.LAN, Cause(Cause.Kind.TERMS)), ActivationRoutePlan.LanProbes().answered(c, Reply.TermsNotAccepted))
        assertEquals(Event.Observed(Route.LAN, Cause(Cause.Kind.CLOSED)), ActivationRoutePlan.LanProbes().answered(c, Reply.Closed))
        assertEquals(Event.Observed(Route.LAN, Cause(Cause.Kind.UNREADABLE)), ActivationRoutePlan.LanProbes().answered(c, Reply.Unreadable("illisible")))
    }

    @Test fun aSearchThatEndsOnARefusalSaysWhichTvRefusedAtTheBound() {
        var s = ActivationRoutePlan.start(ActivationRoutePlan.Facts("482913", 34, true, true, ActivationRoutePlan.Bt.OFF), 0)
        val ev = ActivationRoutePlan.LanProbes().answered(cand(21, name = "TV du salon"), Reply.CodeRefused)!!
        s = ActivationRoutePlan.reduce(s.run, ev)
        s = ActivationRoutePlan.reduce(s.run, Event.Tick(10_000))
        assertEquals(Cause(Cause.Kind.CODE_REFUSED, "TV du salon"), s.run.causes.getValue(Route.LAN))
    }

    // ------------------------------------------------------------------ le groupe

    @Test fun everyGroupReplyBecomesTheRightEvent() {
        val g = ActivationRoutePlan.GroupProbes()
        val base = castbridge.core.tv.WifiDirect.BASE_URL
        assertEquals(Event.Reached(Found(Route.GROUP, "CastBridge-TV", base, request)), g.answered(Reply.Request(request), 7))
        assertEquals(Event.Reached(Found(Route.GROUP, "CastBridge-TV", base, null, note = Cause(Cause.Kind.NEEDS_UPDATE))), g.answered(Reply.RouteMissing, 7))
        assertEquals(Event.Failed(Route.GROUP, Cause(Cause.Kind.CODE_REFUSED), 7), g.answered(Reply.CodeRefused, 7), "le réseau dérivé du code est rejoint mais la TV refuse : le code n'est plus le bon")
        assertEquals(Event.Failed(Route.GROUP, Cause(Cause.Kind.LOCKED_OUT, "42"), 7), g.answered(Reply.LockedOut(42), 7))
        assertEquals(Event.Failed(Route.GROUP, Cause(Cause.Kind.TERMS), 7), g.answered(Reply.TermsNotAccepted, 7))
        assertEquals(Event.Failed(Route.GROUP, Cause(Cause.Kind.CLOSED), 7), g.answered(Reply.Closed, 7))
        assertEquals(Event.Failed(Route.GROUP, Cause(Cause.Kind.UNREADABLE), 7), g.answered(Reply.Unreadable("x"), 7))
        assertEquals("CastBridge-TV", ActivationRoutePlan.cleanName(null))
    }

    @Test fun aGroupThatIsJoinedButSilentIsSaidAfterThreeTries() {
        val g = ActivationRoutePlan.GroupProbes()
        assertNull(g.answered(null, 1)); assertNull(g.answered(null, 2))
        assertEquals(Event.Observed(Route.GROUP, Cause(Cause.Kind.TV_SILENT)), g.answered(null, 3))
        assertNull(g.answered(null, 4), "dit une fois")
        assertNull(g.answered(null, 5))
        assertEquals(3, ActivationRoutePlan.GroupProbes.SILENT_TRIES)
        // an answer after the silence still wins
        assertIs<Event.Reached>(g.answered(Reply.Request(request), 6))
    }

    @Test fun noEventEverCarriesTheCodeTheKeyOrTheRequest() {
        val code = "482913"
        val events = mutableListOf<Event?>()
        val p = ActivationRoutePlan.LanProbes()
        for (r in listOf(Reply.Request(request), Reply.RouteMissing, Reply.CodeRefused, Reply.LockedOut(30), Reply.TermsNotAccepted, Reply.Closed, Reply.Unreadable("x"))) {
            events += ActivationRoutePlan.LanProbes().answered(cand(21), r); events += ActivationRoutePlan.GroupProbes().answered(r, 3)
        }
        for (e in events.filterNotNull()) {
            assertFalse(code in e.toString(), "$e")
            assertFalse(request.code in e.toString() || "0a1b2c3d4e5f60718293a4b5c6d7e8f9" in e.toString(), "la demande ne figure dans aucun événement : $e")
        }
    }
}
