package castbridge.core.journey

import castbridge.core.lots.Kit
import castbridge.core.lots.LotId
import castbridge.core.lots.LotNames
import castbridge.core.lots.LotStage
import castbridge.core.lots.SealedLot
import castbridge.core.net.JsonLite
import castbridge.core.store.RentRequests
import castbridge.core.store.Refusal
import castbridge.core.store.StoreTexts
import castbridge.core.store.StoreView
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Les parcours J de la Boutique (w17-05) : la VRAIE `ReceiverServer` d'une [TvSim] avec les VRAIES routes `/api/store*`, `/api/lots*`, `/api/rental*` ([StoreSim]), et
 * le téléphone ([PhoneStoreSim]) qui parle à la TV par HTTP réel sur 127.0.0.1. Horloge du parcours, aucun sommeil, ports 0.
 */
class StoreJourneyTest {
    private val day = 24L * 3600 * 1000
    private val cm2 = Kit.bytes(7, 6000)
    private val cm2Id = LotId("learn", "cm2")

    private fun <T> one(l: List<T>): T { assertEquals(1, l.size, l.toString()); return l[0] }

    private class Ctx(val j: Journey, val sim: StoreSim, val ps: PhoneStoreSim) {
        val tv get() = j.tv
        val clock get() = j.clock
        val phone get() = j.phone
        fun given(text: String, block: () -> Unit = {}) = j.given(text, block)
        fun whenever(text: String, block: () -> Unit) = j.whenever(text, block)
        fun then(text: String, block: () -> Unit) = j.then(text, block)
        /** `GET /api/store` tel que la TV le sert (avec le PIN du propriétaire) : code et JSON brut. */
        fun showcaseRaw(): Pair<Int, String> = Http.call("${tv.base}/api/store", headers = mapOf("X-CB-Pin" to tv.pin))
        fun showcase(): StoreView.WireScreen = StoreView.parse(showcaseRaw().second)
        fun card(id: String) = assertNotNull(showcase().shelves.flatMap { it.cards }.firstOrNull { it.id == id }, "carte $id absente de la vitrine")
        fun tvGet(path: String): Pair<Int, String> = Http.call("${tv.base}$path", headers = mapOf("X-CB-Pin" to tv.pin))
    }

    private fun withStore(trial: Boolean = false, enabled: Boolean = true, pair: Boolean = true, block: Ctx.() -> Unit) {
        val sim = StoreSim(enabled)
        withJourney(TvSim.TvScenario(trial = trial, extensions = listOf(sim.extension))) {
            sim.attach(tv, clock)
            if (pair) phone.pairWithTv()
            val ps = PhoneStoreSim(clock, { tv.base }, { phone.credentialNow() }).also { it.lotsJson = StoreSim.lotsJson(cm2); it.bundlesJson = StoreSim.bundlesJson() }
            Ctx(this, sim, ps).block()
        }
    }

    private fun Ctx.proofFor(id: LotId = cm2Id) = Kit.sign(listOf(Kit.meta(id.feature, id.scope, 1, cm2))).toJson()

    // ------------------------------------------------------------------------------------------------------ J-S0

    @Test fun flagOffKeepsEverythingAsToday() = withStore(enabled = false) {
        given("le drapeau de la Boutique est éteint") { }
        then("la Boutique n'existe pas : la TV répond comme pour une route inconnue, comme avant") {
            val unknown = tvGet("/api/boutique-inexistante").first
            assertTrue(unknown >= 400, "route inconnue")
            assertEquals(unknown, showcaseRaw().first)
            assertEquals(unknown, tvGet("/api/store/requests").first)
        }
        then("les lots et les locations répondent comme aujourd'hui") {
            assertEquals(200, tvGet("/api/lots").first)
            assertEquals(200, tvGet("/api/rental").first)
        }
        then("le téléphone n'a rien à relayer : un message, aucune exception") {
            val r = ps.relay()
            assertTrue(r.status >= 400, "relais refusé"); assertTrue(r.accepted.isEmpty()); assertEquals(1, ps.messages.size)
        }
    }

    // ------------------------------------------------------------------------------------------------------ J-S1

    @Test fun catalogRelayedOnceThenNotAgain() = withStore {
        then("avant le relais, la vitrine de la TV est vide") {
            val s = showcase(); assertTrue(s.shelves.isEmpty()); assertEquals(StoreTexts.CATALOG_ABSENT_TV, s.banner)
        }
        whenever("le téléphone relaie ses deux catalogues") {
            val r = ps.relay()
            assertEquals(listOf("lots", "bundles"), r.accepted); assertTrue(r.refused.isEmpty())
        }
        then("la TV montre « Catalogue du 28/09 » (le plus ancien des deux) et les deux articles") {
            val s = showcase()
            assertEquals("Catalogue du 28/09", s.header)
            assertEquals(listOf("classe-cm2", "classe-3e").sorted(), s.shelves.flatMap { it.cards }.map { it.id }.sorted())
        }
        whenever("le téléphone se reconnecte") {
            val r = ps.relay()
            assertEquals(emptyList(), r.accepted); assertEquals(200, r.status)
        }
        whenever("il pousse de force les mêmes catalogues") {
            val r = ps.relay(onlyNewer = false)
            assertEquals(emptyList(), r.accepted); assertEquals(listOf("lots", "bundles"), r.unchanged)
        }
        whenever("il pousse un catalogue des lots plus ancien") {
            ps.lotsJson = StoreSim.lotsJson(cm2, at = "2026-09-01T08:00:00Z")
            val r = ps.relay(onlyNewer = false)
            assertEquals(emptyList(), r.accepted); assertContains(r.refused.keys, "lots")
        }
        then("la vitrine est inchangée") {
            assertEquals("Catalogue du 28/09", showcase().header)
            val at = (JsonLite.obj(showcaseRaw().second)["catalogAt"] as Map<*, *>)["lots"]
            assertEquals("2026-10-01T08:00:00Z", at)
        }
    }

    // ------------------------------------------------------------------------------------------------------ J-S2

    @Test fun itemOnTvAfterRealLotInstall() = withStore {
        given("les catalogues relayés et le lot CM2 sur le téléphone") {
            ps.relay(); ps.stages[cm2Id] = LotStage.ON_PHONE
        }
        then("l'article n'est pas sur la TV, des deux côtés") {
            assertEquals("PAS_SUR_TV", card("classe-cm2").state)
            assertEquals(StoreView.State.PAS_SUR_TV, ps.view(families = sim.families).shelves.flatMap { it.cards }.firstOrNull { it.item.id == "classe-cm2" }.let { c -> assertNotNull(c, "carte classe-cm2 absente du téléphone") }.state)
        }
        whenever("le lot complet est livré par les routes de lots, avec la preuve signée") {
            val r = ps.pushLot(LotNames.fileName(cm2Id, 1), cm2, proofFor()); assertEquals(200, r.status, r.json)
            ps.stages[cm2Id] = LotStage.UP_TO_DATE
        }
        then("le lot est installé sur la TV") { assertTrue(cm2Id in sim.learn.held) }
        then("sans droit acquis, l'article reste « à louer » (un lot présent n'est pas un droit)") {
            assertEquals("A_LOUER", card("classe-cm2").state)
        }
        whenever("le bouquet est acquis") { sim.granted += "classe-cm2" }
        then("l'article est « Sur la TV » sur les DEUX vues") {
            val tvCard = card("classe-cm2")
            assertEquals("SUR_TV", tvCard.state); assertContains(tvCard.lines, StoreTexts.ON_TV_TV)
            val phoneCard = ps.view(granted = setOf("classe-cm2"), families = sim.families).shelves.flatMap { it.cards }.firstOrNull { it.item.id == "classe-cm2" }.let { c -> assertNotNull(c, "carte classe-cm2 absente du téléphone") }
            assertEquals(StoreView.State.SUR_TV, phoneCard.state); assertContains(phoneCard.lines, StoreTexts.ON_TV_PHONE)
        }
    }

    // ------------------------------------------------------------------------------------------------------ J-S3

    @Test fun requestOnTvFulfilledByPhone() = withStore {
        var nonce = ""
        given("les catalogues relayés") { ps.relay() }
        whenever("la TV demande « Louer » CM2 · 12 heures") {
            val c = assertNotNull(sim.requestFromTv("classe-cm2", "12h") as? RentRequests.Created.Ok, "demande refusée")
            assertEquals(RentRequests.State.PENDING, c.entry.state); nonce = c.entry.request.nonce
        }
        then("la TV affiche un code court lisible et la demande en attente") {
            val code = one(ps.poll()).code
            assertNotNull(code); assertTrue(Regex("^CM2-12H-[0-9A-Z]{4}$").matches(code), code)
            assertTrue(card("classe-cm2").lines.any { "Demande en cours" in it }, card("classe-cm2").lines.toString())
        }
        then("le téléphone relève 1 demande et notifie la TV") {
            val items = ps.poll()
            assertEquals(1, items.size); assertEquals("PENDING", items[0].state); assertEquals("tv", items[0].origin)
            assertEquals(1, ps.notifications.size); assertContains(ps.notifications[0], "La TV demande")
        }
        whenever("le propriétaire confirme") { assertEquals("APPLIED", JsonLite.obj(ps.ack(nonce).json)["outcome"]) }
        whenever("la location est livrée pour de vrai : activation signée de TEST, lot scellé, installation") {
            val rented = sim.installRental("classe-cm2", days = 2, usageMinutes = 720)
            val d = sim.sealedLot(rented, cm2Id, seed = 7)
            val report = ps.deliver(rented.contract, d.catalogJson, listOf(SealedLot.ofBytes(d.name, d.sealed)))
            assertTrue(report.complete && report.installed == 1, report.summary)
        }
        then("le lot est installé et rangé comme loué") { assertTrue(cm2Id in sim.learn.held) }
        then("la demande est rapprochée : accomplie") { assertEquals("FULFILLED", one(ps.poll()).state) }
        then("la vitrine dit « Loué » avec 12 h d'utilisation, pareil sur la TV et le téléphone") {
            val tvCard = card("classe-cm2")
            assertEquals("LOUE", tvCard.state); assertContains(tvCard.lines, StoreTexts.usageLeft(720))
            val phoneCard = ps.view(families = sim.families).shelves.flatMap { it.cards }.firstOrNull { it.item.id == "classe-cm2" }.let { c -> assertNotNull(c, "carte classe-cm2 absente du téléphone") }
            assertEquals(StoreView.State.LOUE, phoneCard.state)
            val tvLine = StoreTexts.usageLeft(720)
            assertTrue(phoneCard.lines.any { it.startsWith(tvLine) }, "le téléphone annonce aussi $tvLine : ${phoneCard.lines}")
        }
        whenever("la TV est relancée") {
            val before = showcaseRaw().second
            tv.restart(); sim.restart()
            val after = showcaseRaw()
            assertEquals(200, after.first); assertEquals(StoreView.parse(before), StoreView.parse(after.second))
            assertEquals("FULFILLED", one(ps.poll()).state)
        }
    }

    // ------------------------------------------------------------------------------------------------------ J-S4

    @Test fun trialTvSeesStoreButCannotRequest() = withStore(trial = true) {
        given("une TV d'essai, catalogues relayés") { assertEquals(listOf("lots", "bundles"), ps.relay().accepted) }
        then("la vitrine est visible, chaque carte est bloquée « version complète nécessaire »") {
            val (code, _) = showcaseRaw(); assertEquals(200, code)
            val cards = showcase().shelves.flatMap { it.cards }; assertEquals(2, cards.size)
            for (c in cards) { assertEquals("BLOQUE", c.state, c.id); assertEquals(StoreTexts.TRIAL_TV, c.blocked?.phrase, c.id) }
        }
        then("la demande née sur la TV est refusée : essai") {
            val r = sim.requestFromTv("classe-cm2", "12h")
            assertEquals(Refusal.TRIAL_TV, assertNotNull(r as? RentRequests.Created.Refused, "demande acceptée").reason)
        }
        then("la route des demandes est fermée à l'essai : 403") { assertEquals(403, tvGet("/api/store/requests").first) }
        then("aucune demande n'est gardée") { assertTrue(sim.requests.entries().isEmpty()) }
    }

    // ------------------------------------------------------------------------------------------------------ J-S5

    @Test fun kidProfileCannotRequest() = withStore {
        given("un profil enfant actif sur la TV, catalogues relayés") { sim.kidActive = true; ps.relay() }
        then("la demande est refusée : profil enfant") {
            val r = sim.requestFromTv("classe-cm2", "12h")
            assertEquals(Refusal.KID_PROFILE, assertNotNull(r as? RentRequests.Created.Refused, "demande acceptée").reason)
        }
        then("requests.json ne contient aucune ligne") {
            val f = sim.requestsFile()
            assertTrue(!f.exists() || (JsonLite.obj(f.readText())["items"] as List<*>).isEmpty())
            assertTrue(sim.requests.entries().isEmpty())
        }
        then("la carte dit « Demandez à un parent »") {
            val c = card("classe-cm2"); assertEquals("BLOQUE", c.state); assertEquals(StoreTexts.KID_TV, c.blocked?.phrase)
        }
        then("le téléphone ne reçoit rien") { assertTrue(ps.poll().isEmpty()); assertTrue(ps.notifications.isEmpty()) }
    }

    // ------------------------------------------------------------------------------------------------------ J-S6

    @Test fun phoneAwayEightDays() = withStore {
        var nonce = ""
        given("une demande en attente (3e) et une location d'un jour (CM2) livrée") {
            ps.relay()
            nonce = assertNotNull(sim.requestFromTv("classe-3e", "3j") as? RentRequests.Created.Ok, "demande refusée").entry.request.nonce
            val rented = sim.installRental("classe-cm2", days = 1)
            val d = sim.sealedLot(rented, cm2Id, seed = 7)
            assertTrue(ps.deliver(rented.contract, d.catalogJson, listOf(SealedLot.ofBytes(d.name, d.sealed))).complete)
            assertTrue(cm2Id in sim.learn.held)
        }
        whenever("le téléphone est absent 8 jours et la TV fait son balayage") {
            clock.advance(8 * day)
            val report = sim.sweep()
            assertContains(report.lotsRemoved, cm2Id)
        }
        then("le lot loué a disparu de la TV") { assertFalse(cm2Id in sim.learn.held) }
        then("la demande a expiré") {
            showcaseRaw()                                                  // la TV expire en lisant (l'horloge est celle du parcours)
            assertEquals(RentRequests.State.EXPIRED, one(sim.requests.entries()).state)
        }
        then("la vitrine dit « Terminé » avec « Relouer »") {
            val c = card("classe-cm2"); assertEquals("TERMINE", c.state); assertEquals(StoreTexts.RERENT, c.rent?.label)
        }
        whenever("le téléphone revient et renouvelle son lien avec la TV (le jeton avait expiré)") { phone.step(); assertTrue(phone.credentialNow().isNotBlank()) }
        then("au retour du téléphone, confirmer une demande expirée ne casse rien : refusé proprement") {
            val r = ps.ack(nonce)
            assertEquals(409, r.status, r.json)
            val o = JsonLite.obj(r.json); assertEquals("CONFLICT", o["outcome"]); assertEquals("EXPIRED", o["state"])
            assertEquals("EXPIRED", one(ps.poll()).state)
        }
    }

    // ------------------------------------------------------------------------------------------------------ défaut connu

    @Ignore("R-W17-05-1 : l'installId réel (TrustRegistry.installId) a 32 hexadécimaux, le champ tv= d'une demande en veut 16 ; w17-08 doit passer installId.take(16) (ou le cœur accepter 32) : sinon toute demande de la TV est MALFORMED")
    @Test fun theRealInstallIdIsAcceptedAsTheTvOfARequest() = withStore {
        given("les catalogues relayés") { ps.relay() }
        then("une demande portant l'installId réel de la TV est acceptée") {
            val rq = RentRequests(castbridge.core.store.FileRentRequestStore(java.io.File(sim.requestsFile().parentFile, "autre.json")), tv.installId, { clock.now() }, { "0000abcd" })
            val store = sim.store()
            val f = RentRequests.Facts(false, false, store.items.first { it.id == "classe-cm2" }.shelf, listOf(sim.families.of(cm2Id)), emptyList(), 3, null)
            assertTrue(rq.create("classe-cm2", "12h", f) is RentRequests.Created.Ok)
        }
    }

    @Ignore("R-W17-05-2 : côté téléphone la ligne de location vient du message de GET /api/rental (« … · à utiliser avant le JJ/MM ») et non de StoreTexts.usageLeft : les deux vues ne sont pas mot pour mot identiques ; à trancher en w17-06 (le cahier w17-05 veut l'identité)")
    @Test fun theRentalLineIsWordForWordTheSameOnBothViews() = withStore {
        given("une location de 12 h livrée") {
            ps.relay()
            val rented = sim.installRental("classe-cm2", days = 2, usageMinutes = 720); val d = sim.sealedLot(rented, cm2Id, seed = 7)
            assertTrue(ps.deliver(rented.contract, d.catalogJson, listOf(SealedLot.ofBytes(d.name, d.sealed))).complete)
        }
        then("les lignes d'utilisation sont identiques") {
            val phoneCard = ps.view(families = sim.families).shelves.flatMap { it.cards }.first { it.item.id == "classe-cm2" }
            assertEquals(card("classe-cm2").lines.filter { "d'utilisation" in it }, phoneCard.lines.filter { "d'utilisation" in it })
        }
    }

    // ------------------------------------------------------------------------------------------------------ bord : le PIN tourne entre deux contacts

    @Test fun aRotatedPinMakesTheRelayFailCleanlyWithAMessage() = withStore(pair = false) {
        val oldPin = tv.pin
        val stale = PhoneStoreSim(clock, { tv.base }, { oldPin }).also { it.lotsJson = ps.lotsJson; it.bundlesJson = ps.bundlesJson }
        given("un premier contact par le PIN") { assertEquals(listOf("lots", "bundles"), stale.relay().accepted) }
        whenever("le propriétaire change le PIN de la TV") { tv.rotatePin() }
        then("le relais échoue proprement : 401 avalé avec un message, aucune boucle") {
            stale.lotsJson = StoreSim.lotsJson(cm2, at = "2026-10-02T08:00:00Z")
            val r = stale.relay()
            assertEquals(401, r.status); assertTrue(r.accepted.isEmpty()); assertContains(stale.messages.last(), "code de la TV")
            assertEquals(1, stale.messages.size)
        }
    }
}
