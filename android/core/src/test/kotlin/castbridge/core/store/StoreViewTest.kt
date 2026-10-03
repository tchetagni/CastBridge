package castbridge.core.store

import castbridge.core.lots.Bundle
import castbridge.core.lots.BundleCatalog
import castbridge.core.lots.ClockDoubt
import castbridge.core.lots.Edition
import castbridge.core.lots.ExpiryReason
import castbridge.core.lots.LotFamilies
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import castbridge.core.lots.LotStage
import castbridge.core.lots.LotsToDeliver
import castbridge.core.lots.RentalContract
import castbridge.core.lots.RentalEngine
import castbridge.core.lots.RentalState
import castbridge.core.lots.RentalStatus
import castbridge.core.lots.RentalWarning
import castbridge.core.lots.TvLot
import castbridge.core.lots.TvManifest
import castbridge.core.lots.TvRentalView
import castbridge.core.store.StoreView.Reason
import castbridge.core.store.StoreView.RentKind
import castbridge.core.store.StoreView.Side
import castbridge.core.store.StoreView.State
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** États, lignes, boutons et blocages de la Boutique (w17-02) : une table de cas, puis les règles une à une. Aucun texte attendu n'est recopié du code : ce sont les phrases du cahier. */
class StoreViewTest {
    private val now = Instant.parse("2026-10-03T12:00:00Z").toEpochMilli()
    private val day = 86_400_000L
    private val families = LotFamilies.explicit(free = setOf("langues:zh-a0"), reserved = setOf("learn:cm2", "quiz:cm2", "learn:3e", "quiz:3e", "langues:ar-a0", "quiz:geo"))

    private fun meta(feature: String, scope: String, bytes: Long = 2_100_000) =
        LotMeta(LotId(feature, scope), 1, bytes, "0".repeat(63) + "1", "$feature $scope", 0, if (scope.endsWith("-trial")) Edition.TRIAL else Edition.FULL)

    private val lots = listOf(
        meta("learn", "cm2"), meta("quiz", "cm2"), meta("learn", "cm2-trial", 200_000), meta("quiz", "cm2-trial", 100_000),
        meta("learn", "3e"), meta("quiz", "3e"), meta("langues", "zh-a0", 1_000_000), meta("langues", "ar-a0", 1_000_000), meta("quiz", "geo", 900_000),
        meta("learn", "x2", 900_000),
    )
    private val bundles = BundleCatalog(listOf(
        Bundle("classe-cm2", "classe", setOf("learn:cm2", "quiz:cm2"), "Classe CM2"),
        Bundle("classe-3e", "classe", setOf("learn:3e", "quiz:3e"), "Classe 3e"),
        Bundle("langues-zh", "langues", setOf("langues:zh-a0"), "Chinois A0"),
        Bundle("langues-ar", "langues", setOf("langues:ar-a0"), "Arabe A0"),
    ))
    private val store = StoreCatalog.build(lots, bundles, families, "2026-10-01T08:00:00Z", "2026-10-01T09:00:00Z")

    private fun tv(vararg held: LotMeta, remaining: Long = 50_000_000L) =
        TvManifest(1, 100_000_000L, 100_000_000L - remaining, 0, held.map { TvLot(it, 0L) }, emptyList(), emptyList())

    private fun full(vararg scopes: String) = scopes.flatMap { listOf(meta("learn", it), meta("quiz", it)) }.toTypedArray()
    private fun contract(bundle: String, end: Long = now + 12 * day, usage: Long = 0) =
        RentalContract("loc-$bundle@1", "loc-$bundle", listOf(bundle), "lic", 1, now - day, end, 0, usage, 3, "box")

    private fun rental(bundle: String, state: RentalState = RentalState.ACTIVE, remainingMs: Long? = 12 * day, usage: Long? = null, message: String = "Il vous reste 12 jours", end: Long = now + 12 * day) =
        RentalStatus(contract(bundle, end, if (usage != null) 600 else 0), state, if (state == RentalState.EXPIRED) ExpiryReason.DATE else null,
            if (state == RentalState.SUSPENDED) ClockDoubt.BEHIND else null, remainingMs, usage, RentalWarning.NONE, message)

    private fun facts(side: Side = Side.PHONE, manifest: TvManifest? = tv()) = StoreView.Facts(store, now, side, manifest)

    private fun StoreView.Screen.card(id: String) = assertNotNull(shelves.flatMap { it.cards }.find { it.item.id == id }, "carte $id absente")
    private val cm2 = "classe-cm2"

    private data class Row(val name: String, val facts: StoreView.Facts, val id: String, val state: State, val rent: String?, val reason: Reason?, val lines: List<String>)

    @Test fun theStateTable() {
        val cm2Trial = arrayOf(meta("learn", "cm2-trial", 200_000), meta("quiz", "cm2-trial", 100_000))
        val granted = setOf(cm2)
        val rows = listOf(
            Row("gratuit téléphone", facts(), "langues-zh", State.GRATUIT, null, Reason.FREE, listOf("Gratuit")),
            Row("gratuit TV loin", facts(Side.TV), "langues-zh", State.GRATUIT, null, Reason.FREE, listOf("Gratuit · demandez-le au téléphone")),
            Row("gratuit TV sur la TV", facts(Side.TV, tv(meta("langues", "zh-a0"))), "langues-zh", State.GRATUIT, null, Reason.FREE, listOf("Gratuit · sur cette TV")),
            Row("langues réservées restent gratuites", facts(), "langues-ar", State.GRATUIT, null, Reason.FREE, listOf("Gratuit")),
            Row("gratuit même en essai", facts().copy(trialTv = true), "langues-zh", State.GRATUIT, null, Reason.FREE, listOf("Gratuit")),
            Row("échantillon téléphone", facts(manifest = tv(*cm2Trial)), cm2, State.ECHANTILLON, "Louer gratuitement", null, listOf("Sur la TV : échantillon")),
            Row("échantillon TV", facts(Side.TV, tv(*cm2Trial)), cm2, State.ECHANTILLON, "Louer", null, listOf("Échantillon")),
            Row("pas sur la TV téléphone", facts(), cm2, State.PAS_SUR_TV, "Louer gratuitement", null, listOf("Pas sur la TV")),
            Row("pas sur la TV TV", facts(Side.TV), cm2, State.PAS_SUR_TV, "Louer", null, listOf("À envoyer depuis le téléphone")),
            Row("envoi en cours", facts().copy(phoneStages = mapOf(LotId("learn", "cm2") to LotStage.SENDING)), cm2, State.ENVOYE, "Louer gratuitement", null, emptyList()),
            Row("sur la TV avec droit", facts(manifest = tv(*full("cm2"))).copy(granted = granted), cm2, State.SUR_TV, null, null, listOf("Sur la TV")),
            Row("sur la TV côté TV", facts(Side.TV, tv(*full("cm2"))).copy(granted = granted), cm2, State.SUR_TV, null, null, listOf("Sur cette TV")),
            Row("loué, minutes d'utilisation", facts(Side.TV, tv(*full("cm2"))).copy(rentals = listOf(rental(cm2, usage = 320))), cm2, State.LOUE, "Prolonger", null, listOf("Il vous reste 5 h 20 d'utilisation")),
            Row("loué, durée seule", facts(Side.TV, tv(*full("cm2"))).copy(rentals = listOf(rental(cm2))), cm2, State.LOUE, "Prolonger", null, listOf("Il vous reste 12 jours")),
            Row("loué suspendu (horloge)", facts(Side.TV, tv(*full("cm2"))).copy(rentals = listOf(rental(cm2, RentalState.SUSPENDED, null, null, RentalEngine.CHECK_CLOCK))), cm2, State.LOUE, "Prolonger", null, listOf("Vérifiez l'heure de la TV")),
            Row("loué vu du téléphone", facts().copy(tvRentals = TvRentalView(true, false, listOf(TvRentalView.Rental("loc-$cm2@1", "loc-$cm2", "ACTIVE", true, 12 * day, "Il vous reste 12 jours", listOf("learn:cm2"))))), cm2, State.LOUE, "Prolonger", null, listOf("Il vous reste 12 jours")),
            Row("droit illimité", facts(manifest = tv(*full("cm2"))).copy(superUnlimited = true), cm2, State.SUR_TV, null, null, listOf("Droit illimité")),
            Row("terminé depuis 10 jours", facts(Side.TV).copy(rentals = listOf(rental(cm2, RentalState.EXPIRED, null, null, RentalEngine.ENDED, now - 10 * day))), cm2, State.TERMINE, "Relouer", null, listOf("Location terminée le 23/09 · Relouer ?")),
            Row("terminé depuis 40 jours", facts(Side.TV).copy(rentals = listOf(rental(cm2, RentalState.EXPIRED, null, null, RentalEngine.ENDED, now - 40 * day))), cm2, State.PAS_SUR_TV, "Louer", null, listOf("À envoyer depuis le téléphone")),
            Row("terminé vu du téléphone", facts().copy(tvRentals = TvRentalView(true, false, listOf(TvRentalView.Rental("loc-$cm2@1", "loc-$cm2", "EXPIRED", false, null, "", emptyList())))), cm2, State.TERMINE, "Relouer", null, listOf("Location terminée · Relouer ?")),
            Row("TV pas encore lue", facts(manifest = null), cm2, State.A_LOUER, "Louer gratuitement", null, listOf("À louer")),
            Row("aucune TV", facts(manifest = null).copy(tvKnown = false), cm2, State.BLOQUE, null, Reason.NO_TV, emptyList()),
            Row("TV hors de portée", facts().copy(tvReachable = false), cm2, State.PAS_SUR_TV, "Louer gratuitement", Reason.TV_UNREACHABLE, listOf("Pas sur la TV")),
            Row("essai téléphone", facts().copy(trialTv = true), cm2, State.BLOQUE, null, Reason.TRIAL, emptyList()),
            Row("essai TV", facts(Side.TV).copy(trialTv = true), cm2, State.BLOQUE, null, Reason.TRIAL, emptyList()),
            Row("profil enfant", facts().copy(kidActive = true), cm2, State.BLOQUE, null, Reason.KID, emptyList()),
            Row("quota de 3 locations", facts().copy(tvRentals = TvRentalView(true, false, listOf("a", "b", "c").map { TvRentalView.Rental("loc-$it@1", "loc-$it", "ACTIVE", true, day, "m", emptyList()) })), cm2, State.BLOQUE, null, Reason.QUOTA, emptyList()),
            Row("famille inconnue", facts(), "lot:learn:x2", State.BLOQUE, null, Reason.UNKNOWN_FAMILY, emptyList()),
            Row("pilote fini, sans prix", facts().copy(pilotEndMs = now - day), cm2, State.BLOQUE, null, Reason.PILOT_ENDED, emptyList()),
            Row("place insuffisante", facts(manifest = tv(remaining = 1_000_000L)), cm2, State.BLOQUE, null, Reason.SPACE, emptyList()),
            Row("demande en cours", facts().copy(pendingRequests = mapOf(cm2 to now - day)), cm2, State.PAS_SUR_TV, null, null, listOf("Demande en cours (02/10)")),
        )
        assertTrue(rows.size >= 24, "table d'au moins 24 lignes : ${rows.size}")
        for (r in rows) {
            val c = StoreView.decide(r.facts).card(r.id)
            assertEquals(r.state, c.state, "état : ${r.name}")
            assertEquals(r.rent, c.rentButton?.label, "bouton de location : ${r.name}")
            assertEquals(r.reason, c.blocked?.reason, "blocage : ${r.name}")
            for (l in r.lines) assertTrue(c.lines.any { it.contains(l) }, "ligne « $l » : ${r.name} : ${c.lines}")
        }
    }

    // ---- libellés exacts ----

    @Test fun phonePhrasesAreTheOnesOfTheDesign() {
        fun phrase(f: StoreView.Facts) = StoreView.decide(f).card(cm2).blocked?.phrase
        assertEquals("Ajoutez d'abord votre TV (Accueil › Ajouter ma TV).", phrase(facts(manifest = null).copy(tvKnown = false)))
        assertEquals("Version d'essai : les locations demandent une clé de production. Voyez votre point focal.", phrase(facts().copy(trialTv = true)))
        assertEquals("Un profil enfant est actif sur la TV : demandez à un parent.", phrase(facts().copy(kidActive = true)))
        assertEquals("La TV n'est pas à portée : la demande partira dès qu'elle sera allumée à côté du téléphone.", phrase(facts().copy(tvReachable = false)))
        assertEquals("Le test gratuit est terminé ; tarifs bientôt disponibles.", phrase(facts().copy(pilotEndMs = now - 1)))
        assertEquals("Place insuffisante : il manque 1,0 Mo sur la TV.", phrase(facts(manifest = tv(remaining = 3_100_000L))))
    }

    @Test fun tvPhrasesAreTheShortOnes() {
        fun phrase(f: StoreView.Facts) = StoreView.decide(f).card(cm2).blocked?.phrase
        assertEquals("Demandez à un parent", phrase(facts(Side.TV).copy(kidActive = true)))
        assertEquals("Version complète nécessaire · Passer en production", phrase(facts(Side.TV).copy(trialTv = true)))
        assertEquals("Place insuffisante sur la TV (1,0 Mo)", phrase(facts(Side.TV, tv(remaining = 3_100_000L))))
    }

    @Test fun quotaPhraseNamesTheLimit() {
        val three = TvRentalView(true, false, listOf("a", "b", "c").map { TvRentalView.Rental("loc-$it@1", "loc-$it", "ACTIVE", true, day, "m", emptyList()) })
        assertEquals("3 locations en cours sur cette TV : attendez la fin de l'une d'elles.", StoreView.decide(facts().copy(tvRentals = three)).card(cm2).blocked?.phrase)
        val tvThree = listOf("a", "b", "c").map { rental(it) }
        assertEquals("3 locations en cours", StoreView.decide(facts(Side.TV).copy(rentals = tvThree)).card(cm2).blocked?.phrase)
    }

    @Test fun twoRentalsDoNotReachTheQuotaAndAnExtensionIsNeverBlockedByIt() {
        val two = listOf("a", "b").map { rental(it) }
        assertEquals(State.PAS_SUR_TV, StoreView.decide(facts(Side.TV).copy(rentals = two)).card(cm2).state)
        val three = listOf(cm2, "b", "c").map { rental(it) }
        val c = StoreView.decide(facts(Side.TV, tv(*full("cm2"))).copy(rentals = three)).card(cm2)
        assertEquals(State.LOUE, c.state); assertEquals(RentKind.EXTEND, c.rentButton?.kind); assertNull(c.blocked)
    }

    @Test fun maxConcurrentIsAParameter() {
        val one = listOf(rental("a"))
        assertEquals(Reason.QUOTA, StoreView.decide(facts(Side.TV).copy(rentals = one, maxConcurrent = 1)).card(cm2).blocked?.reason)
    }

    // ---- priorité et cas limites ----

    @Test fun rentedBeatsOnTvBeatsSampleBeatsNotOnTv() {
        val held = tv(*full("cm2"), meta("learn", "cm2-trial", 200_000))
        assertEquals(State.LOUE, StoreView.decide(facts(manifest = held).copy(granted = setOf(cm2), tvRentals = TvRentalView(true, false,
            listOf(TvRentalView.Rental("c", "loc-$cm2", "ACTIVE", true, day, "m", emptyList()))))).card(cm2).state)
        assertEquals(State.SUR_TV, StoreView.decide(facts(manifest = held).copy(granted = setOf(cm2))).card(cm2).state)
        assertEquals(State.ECHANTILLON, StoreView.decide(facts(manifest = tv(meta("learn", "cm2-trial", 200_000)))).card(cm2).state)
        assertEquals(State.PAS_SUR_TV, StoreView.decide(facts()).card(cm2).state)
    }

    @Test fun theTvNeverProducesSentAndHasNoSendButton() {
        val stages = mapOf(LotId("learn", "cm2") to LotStage.SENDING, LotId("quiz", "cm2") to LotStage.SENT)
        val c = StoreView.decide(facts(Side.TV).copy(phoneStages = stages)).card(cm2)
        assertNotEquals(State.ENVOYE, c.state); assertNull(c.sendButton)
    }

    private fun assertNotEquals(a: Any?, b: Any?) = assertFalse(a == b)

    @Test fun sendButtonIsExactlyTheOneOfLotsToDeliver() {
        for ((stage, label) in listOf(LotStage.ON_PHONE to "Envoyer à la TV", LotStage.UP_TO_DATE to "Déjà sur la TV", LotStage.SENDING to "Envoi en cours", LotStage.SENT to "Envoyé")) {
            val c = StoreView.decide(facts(manifest = tv(*full("cm2"))).copy(granted = setOf(cm2), phoneStages = mapOf(LotId("learn", "cm2") to stage))).card(cm2)
            assertEquals(LotsToDeliver.sendButton(stage, true), c.sendButton, "étape $stage")
            assertEquals(label, c.sendButton?.label)
        }
        assertNull(StoreView.decide(facts().copy(phoneStages = mapOf(LotId("learn", "cm2") to LotStage.NOT_DOWNLOADED))).card(cm2).sendButton)
        assertNull(StoreView.decide(facts()).card(cm2).sendButton)
    }

    @Test fun sendingWinsOverUpToDateWhenLotsDiffer() {
        val c = StoreView.decide(facts().copy(phoneStages = mapOf(LotId("learn", "cm2") to LotStage.UP_TO_DATE, LotId("quiz", "cm2") to LotStage.ON_PHONE))).card(cm2)
        assertEquals("Envoyer à la TV", c.sendButton?.label)
    }

    @Test fun pendingRequestHidesTheRentButtonAndShowsTheDate() {
        val c = StoreView.decide(facts().copy(pendingRequests = mapOf(cm2 to now - day))).card(cm2)
        assertNull(c.rentButton); assertTrue("Demande en cours (02/10)" in c.lines)
        val rented = StoreView.decide(facts(Side.TV, tv(*full("cm2"))).copy(rentals = listOf(rental(cm2)), pendingRequests = mapOf(cm2 to now))).card(cm2)
        assertEquals(State.LOUE, rented.state); assertNull(rented.rentButton)
        assertEquals("Classe CM2 est déjà loué (12 jours) : prolongez-le, ou attendez la fin pour changer d'unité.", rented.blocked?.phrase)
    }

    @Test fun freeNeverGetsARentButtonOrABlockingState() {
        val c = StoreView.decide(facts().copy(trialTv = true, kidActive = true, pilotEndMs = now - 1)).card("langues-zh")
        assertEquals(State.GRATUIT, c.state); assertNull(c.rentButton)
        assertEquals("Gratuit : rien à louer.", c.blocked?.phrase); assertFalse(c.blocked!!.hard)
    }

    @Test fun anExtensionIsBlockedByKidTrialAndEndedPilotButNotByMissingTv() {
        val base = facts(Side.TV, tv(*full("cm2"))).copy(rentals = listOf(rental(cm2)))
        assertNull(StoreView.decide(base.copy(kidActive = true)).card(cm2).rentButton)
        assertEquals(State.LOUE, StoreView.decide(base.copy(kidActive = true)).card(cm2).state)
        assertNull(StoreView.decide(base.copy(trialTv = true)).card(cm2).rentButton)
        assertNull(StoreView.decide(base.copy(pilotEndMs = now - 1)).card(cm2).rentButton)
    }

    @Test fun unreachableTvKeepsTheRentButtonEnabled() {
        val c = StoreView.decide(facts().copy(tvReachable = false)).card(cm2)
        assertEquals("Louer gratuitement", c.rentButton?.label); assertTrue(c.rentButton!!.enabled); assertFalse(c.blocked!!.hard)
    }

    @Test fun unlimitedRightMakesEveryItemOnTvOnTvAndNeverBlocksByQuota() {
        val held = tv(*full("cm2", "3e"))
        val many = listOf("a", "b", "c", "d").map { rental(it) }
        val s = StoreView.decide(facts(Side.TV, held).copy(superUnlimited = true, rentals = many))
        assertEquals(State.SUR_TV, s.card("classe-3e").state); assertEquals(State.SUR_TV, s.card(cm2).state)
        assertNull(s.card(cm2).rentButton)
        val notHeld = StoreView.decide(facts(Side.TV).copy(superUnlimited = true, rentals = many)).card(cm2)
        assertEquals(State.PAS_SUR_TV, notHeld.state); assertEquals("Louer", notHeld.rentButton?.label)
    }

    @Test fun usageMinutesAreFormatted() {
        fun line(min: Long) = StoreView.decide(facts(Side.TV, tv(*full("cm2"))).copy(rentals = listOf(rental(cm2, usage = min)))).card(cm2).lines.last()
        assertEquals("Il vous reste 5 h 20 d'utilisation", line(320))
        assertEquals("Il vous reste 2 h d'utilisation", line(120))
        assertEquals("Il vous reste 1 h 05 d'utilisation", line(65))
        assertEquals("Il vous reste 40 min d'utilisation", line(40))
    }

    @Test fun pilotBannerAndLabel() {
        val s = StoreView.decide(facts().copy(pilotEndMs = Instant.parse("2026-11-01T12:00:00Z").toEpochMilli()))
        assertEquals("Test gratuit jusqu'au 01/11 : toutes les locations sont offertes pendant le test.", s.banner)
        assertEquals("Louer gratuitement", s.card(cm2).rentButton?.label)
        assertNull(StoreView.decide(facts()).banner)
    }

    @Test fun oldCatalogueBanner() {
        val later = StoreView.decide(StoreView.Facts(store, now + 40 * day, Side.PHONE, tv()))
        assertEquals("Ce catalogue date de plus d'un mois : connectez le téléphone à Internet pour l'actualiser.", later.banner)
    }

    @Test fun missingCatalogueGivesAnEmptyScreenWithTheRightSentence() {
        val empty = StoreCatalog.build(emptyList(), null, families)
        val phone = StoreView.decide(StoreView.Facts(empty, now))
        assertTrue(phone.shelves.isEmpty()); assertEquals("Connectez le téléphone à Internet puis actualisez la Boutique.", phone.banner)
        assertEquals("Boutique vide : rapprochez le téléphone", StoreView.decide(StoreView.Facts(empty, now, Side.TV)).banner)
    }

    @Test fun headerAndShelvesFollowTheCatalogue() {
        val s = StoreView.decide(facts())
        assertEquals("Catalogue du 01/10", s.header)
        assertEquals(listOf("classe-cm2"), s.shelves[0].cards.map { it.item.id }); assertEquals("Primaire", s.shelves[0].section)
        assertEquals(listOf("classe-3e"), s.shelves[1].cards.map { it.item.id }); assertEquals("Secondaire", s.shelves[1].section)
        assertEquals(StoreCatalog.Shelf.APPRENDRE, s.shelves.first().shelf)
        assertTrue(s.shelves.any { it.shelf == StoreCatalog.Shelf.LANGUES })
    }

    @Test fun cardLinesStartWithTheContent() {
        assertEquals("Leçons + exercices · 2 lots · 4,0 Mo", StoreView.decide(facts()).card(cm2).lines.first())
    }

    @Test fun theViewIsTheSameOnBothSidesExceptPhrases() {
        val p = StoreView.decide(facts(Side.PHONE, tv(*full("cm2"))).copy(granted = setOf(cm2))).shelves.flatMap { it.cards }
        val t = StoreView.decide(facts(Side.TV, tv(*full("cm2"))).copy(granted = setOf(cm2))).shelves.flatMap { it.cards }
        assertEquals(p.map { it.item.id to it.state }, t.map { it.item.id to it.state })
    }

    @Test fun jsonRoundTripKeepsStateLinesButtonsAndBlocking() {
        val screen = StoreView.decide(facts().copy(pilotEndMs = now + 20 * day, phoneStages = mapOf(LotId("learn", "3e") to LotStage.ON_PHONE), kidActive = false))
        val back = StoreView.parse(StoreView.json(screen))
        assertEquals(screen.header, back.header); assertEquals(screen.banner, back.banner)
        val flat = screen.shelves.flatMap { it.cards }; val wire = back.shelves.flatMap { it.cards }
        assertEquals(flat.map { it.item.id }, wire.map { it.id })
        assertEquals(flat.map { it.state.name }, wire.map { it.state })
        assertEquals(flat.map { it.lines }, wire.map { it.lines })
        assertEquals(flat.map { it.rentButton }, wire.map { it.rent })
        assertEquals(flat.map { it.sendButton?.label }, wire.map { it.send?.label })
        assertEquals(flat.map { it.blocked }, wire.map { it.blocked })
        assertEquals(screen.shelves.map { it.shelf.name to it.section }, back.shelves.map { it.shelf to it.section })
    }

    @Test fun jsonOfABlockedCardCarriesThePhrase() {
        val back = StoreView.parse(StoreView.json(StoreView.decide(facts().copy(kidActive = true))))
        val c = back.shelves.flatMap { it.cards }.first { it.id == cm2 }
        assertEquals("BLOQUE", c.state); assertNull(c.rent); assertEquals("KID", c.blocked?.reason?.name)
    }

    @Test fun parseToleratesMissingFields() {
        val back = StoreView.parse("""{"shelves":[{"cards":[{"id":"x"}]}]}""")
        assertEquals("x", back.shelves.single().cards.single().id); assertEquals("", back.header); assertNull(back.banner)
    }

    @Test fun suspendedClockKeepsTheExistingSentence() {
        val c = StoreView.decide(facts(Side.TV, tv(*full("cm2"))).copy(rentals = listOf(rental(cm2, RentalState.SUSPENDED, null, null, RentalEngine.CHECK_CLOCK)))).card(cm2)
        assertEquals(State.LOUE, c.state); assertTrue(c.lines.any { it.startsWith("Vérifiez l'heure de la TV") })
    }
}
